package com.healthcare.domain.diet.admin;

import com.healthcare.common.exception.ValidationException;
import com.healthcare.domain.diet.entity.FoodCatalog;
import com.healthcare.domain.diet.external.importer.FoodDisplayNameNormalizer;
import com.healthcare.domain.diet.repository.FoodCatalogRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.csv.CSVFormat;
import org.apache.commons.csv.CSVParser;
import org.apache.commons.csv.CSVRecord;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 검수 CSV의 수동 교정 표시명을 적재된 카탈로그 행에 적용한다.
 *
 * <p>휴리스틱({@link FoodDisplayNameNormalizer})으로 해결되지 않는 예외가 많아, 사람이 검수한
 * {@code corrected_display}를 휴리스틱보다 우선 반영한다. CSV는 검수 시트와 동일한 컬럼
 * ({@code raw_name,heuristic_display,corrected_display})을 쓰며, {@code corrected_display}가 빈 행은 건너뛴다.
 * 동일 원본명을 가진 모든 행에 적용하고 검색 별칭도 함께 갱신해 양방향 검색을 유지한다.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class FoodCatalogNameOverrideService {

    /** name(150) / search_alias(400) 컬럼 길이에 맞춘 상한. */
    private static final int NAME_MAX_LENGTH = 150;
    private static final int SEARCH_ALIAS_MAX_LENGTH = 400;

    private static final String COLUMN_RAW_NAME = "raw_name";
    private static final String COLUMN_HEURISTIC = "heuristic_display";
    private static final String COLUMN_CORRECTED = "corrected_display";

    private final FoodCatalogRepository repository;

    @Transactional
    public OverrideResult applyFromCsv(InputStream csvStream) throws IOException {
        List<OverrideEntry> overrides = parseOverrides(csvStream);

        int matchedNames = 0;
        int unmatchedNames = 0;
        int updatedRows = 0;
        for (OverrideEntry entry : overrides) {
            // raw_name(MFDS 원본)으로 먼저 찾고, 이미 재정규화돼 원본명이 바뀐 경우 heuristic_display로 폴백한다.
            // 덕분에 재정규화 전/후 어느 순서로 실행해도 동일하게 매칭된다.
            List<FoodCatalog> rows = repository.findByName(entry.rawName());
            if (rows.isEmpty() && entry.heuristicDisplay() != null
                    && !entry.heuristicDisplay().equals(entry.rawName())) {
                rows = repository.findByName(entry.heuristicDisplay());
            }
            if (rows.isEmpty()) {
                unmatchedNames++;
                continue;
            }
            matchedNames++;

            String display = truncate(entry.corrected(), NAME_MAX_LENGTH);
            String alias = truncate(
                    FoodDisplayNameNormalizer.searchAliasFor(entry.rawName(), entry.corrected()),
                    SEARCH_ALIAS_MAX_LENGTH);
            for (FoodCatalog row : rows) {
                row.applyDisplayNormalization(display, alias);
                updatedRows++;
            }
            repository.saveAll(rows);
        }

        OverrideResult result = new OverrideResult(overrides.size(), matchedNames, unmatchedNames, updatedRows);
        log.info(
                "표시명 수동 오버라이드 완료: entries={}, matchedNames={}, unmatchedNames={}, updatedRows={}",
                result.entryCount(), result.matchedNameCount(), result.unmatchedNameCount(), result.updatedRowCount());
        return result;
    }

    /** corrected가 있는 행만 추출. 중복 raw는 마지막 값으로 덮어쓴다(입력 순서 유지). */
    private List<OverrideEntry> parseOverrides(InputStream csvStream) throws IOException {
        CSVFormat format = CSVFormat.DEFAULT.builder()
                .setHeader()
                .setSkipHeaderRecord(true)
                .setIgnoreEmptyLines(true)
                .setTrim(true)
                .build();

        Map<String, OverrideEntry> overrides = new LinkedHashMap<>();
        try (CSVParser parser = new CSVParser(
                new InputStreamReader(csvStream, StandardCharsets.UTF_8), format)) {
            validateHeaders(parser.getHeaderMap());
            for (CSVRecord record : parser) {
                String rawName = value(record, COLUMN_RAW_NAME);
                String heuristic = value(record, COLUMN_HEURISTIC);
                String corrected = value(record, COLUMN_CORRECTED);
                if (rawName == null || rawName.isBlank() || corrected == null || corrected.isBlank()) {
                    continue;
                }
                overrides.put(rawName, new OverrideEntry(rawName, blankToNull(heuristic), corrected));
            }
        }
        return List.copyOf(overrides.values());
    }

    private static String blankToNull(String value) {
        return (value == null || value.isBlank()) ? null : value;
    }

    private record OverrideEntry(String rawName, String heuristicDisplay, String corrected) {
    }

    private void validateHeaders(Map<String, Integer> headerMap) {
        if (!headerMap.containsKey(COLUMN_RAW_NAME) || !headerMap.containsKey(COLUMN_CORRECTED)) {
            throw new ValidationException(
                    "CSV 헤더에 '" + COLUMN_RAW_NAME + "'와 '" + COLUMN_CORRECTED + "' 컬럼이 필요합니다.");
        }
    }

    private static String value(CSVRecord record, String column) {
        return record.isMapped(column) ? record.get(column) : null;
    }

    private static String truncate(String value, int maxLength) {
        if (value == null || value.length() <= maxLength) {
            return value;
        }
        return value.substring(0, maxLength);
    }

    /**
     * 오버라이드 적용 결과.
     *
     * @param entryCount         corrected 값이 있는 CSV 항목 수
     * @param matchedNameCount   DB에서 동일 원본명을 찾은 항목 수
     * @param unmatchedNameCount DB에 일치하는 행이 없던 항목 수
     * @param updatedRowCount    실제로 갱신한 카탈로그 행 수
     */
    public record OverrideResult(
            int entryCount,
            int matchedNameCount,
            int unmatchedNameCount,
            int updatedRowCount) {
    }
}
