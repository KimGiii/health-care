package com.healthcare.domain.diet.admin;

import com.healthcare.common.exception.ValidationException;
import com.healthcare.domain.diet.entity.FoodCatalog;
import com.healthcare.domain.diet.repository.FoodCatalogRepository;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.io.ByteArrayInputStream;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class FoodCatalogNameOverrideServiceTest {

    @Mock
    private FoodCatalogRepository repository;

    @InjectMocks
    private FoodCatalogNameOverrideService service;

    private static InputStream csv(String body) {
        return new ByteArrayInputStream(body.getBytes(StandardCharsets.UTF_8));
    }

    private static FoodCatalog rowNamed(String name) {
        return FoodCatalog.builder().name(name).nameKo(name).isCustom(false).build();
    }

    @Test
    @DisplayName("corrected_display가 있는 행만 동일 원본명 모든 행에 적용한다")
    void appliesCorrectionToMatchingRows() throws Exception {
        FoodCatalog a = rowNamed("경단_깨");
        FoodCatalog b = rowNamed("경단_깨");
        when(repository.findByName("경단_깨")).thenReturn(List.of(a, b));

        String body = """
                raw_name,heuristic_display,corrected_display
                경단_깨,깨경단,깨 경단
                """;
        var result = service.applyFromCsv(csv(body));

        assertThat(result.entryCount()).isEqualTo(1);
        assertThat(result.matchedNameCount()).isEqualTo(1);
        assertThat(result.updatedRowCount()).isEqualTo(2);
        assertThat(a.getName()).isEqualTo("깨 경단");
        assertThat(a.getNameKo()).isEqualTo("깨 경단");
        // 검색 별칭에 원본 어순(경단깨)과 교정 표시명(깨 경단)이 모두 포함
        assertThat(a.getSearchAlias().replace(" ", "")).contains("경단깨").contains("깨경단");
        verify(repository).saveAll(anyList());
    }

    @Test
    @DisplayName("corrected_display가 빈 행은 건너뛴다")
    void skipsBlankCorrections() throws Exception {
        String body = """
                raw_name,heuristic_display,corrected_display
                도넛_링도넛,링도넛,
                경단_깨,깨경단,깨 경단
                """;
        lenient().when(repository.findByName("도넛_링도넛")).thenReturn(List.of(rowNamed("도넛_링도넛")));
        when(repository.findByName("경단_깨")).thenReturn(List.of(rowNamed("경단_깨")));

        var result = service.applyFromCsv(csv(body));

        assertThat(result.entryCount()).isEqualTo(1);
        verify(repository, never()).findByName("도넛_링도넛");
    }

    @Test
    @DisplayName("이미 재정규화돼 raw_name이 사라졌으면 heuristic_display로 폴백 매칭한다")
    void fallsBackToHeuristicDisplayWhenRawNameMissing() throws Exception {
        // 재정규화로 name이 '깨경단'으로 바뀐 상태
        FoodCatalog renamed = rowNamed("깨경단");
        when(repository.findByName("경단_깨")).thenReturn(List.of());
        when(repository.findByName("깨경단")).thenReturn(List.of(renamed));

        var result = service.applyFromCsv(csv("""
                raw_name,heuristic_display,corrected_display
                경단_깨,깨경단,깨 경단
                """));

        assertThat(result.matchedNameCount()).isEqualTo(1);
        assertThat(result.updatedRowCount()).isEqualTo(1);
        assertThat(renamed.getName()).isEqualTo("깨 경단");
        // 별칭은 항상 원본 raw 기준으로 만들어 양방향 검색 유지
        assertThat(renamed.getSearchAlias().replace(" ", "")).contains("경단깨");
    }

    @Test
    @DisplayName("DB에 일치 행이 없으면 unmatched로 집계하고 갱신하지 않는다")
    void countsUnmatchedNames() throws Exception {
        when(repository.findByName("없는_이름")).thenReturn(List.of());

        var result = service.applyFromCsv(csv("""
                raw_name,heuristic_display,corrected_display
                없는_이름,x,교정값
                """));

        assertThat(result.matchedNameCount()).isZero();
        assertThat(result.unmatchedNameCount()).isEqualTo(1);
        assertThat(result.updatedRowCount()).isZero();
        verify(repository, never()).saveAll(anyList());
    }

    @Test
    @DisplayName("필수 헤더가 없으면 ValidationException")
    void rejectsMissingHeaders() {
        assertThatThrownBy(() -> service.applyFromCsv(csv("foo,bar\n1,2\n")))
                .isInstanceOf(ValidationException.class)
                .hasMessageContaining("corrected_display");
    }

    @Test
    @DisplayName("name 길이 상한(150)을 넘으면 잘라서 저장한다")
    void truncatesOverlongNames() throws Exception {
        String longName = "가".repeat(200);
        FoodCatalog row = rowNamed("긴_이름");
        when(repository.findByName("긴_이름")).thenReturn(List.of(row));

        ArgumentCaptor<List<FoodCatalog>> captor = ArgumentCaptor.forClass(List.class);
        service.applyFromCsv(csv("raw_name,heuristic_display,corrected_display\n긴_이름,x," + longName + "\n"));

        verify(repository).saveAll(captor.capture());
        assertThat(row.getName()).hasSize(150);
    }
}
