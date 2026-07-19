package com.healthcare.domain.diet.external.client;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.cfg.CoercionAction;
import com.fasterxml.jackson.databind.cfg.CoercionInputShape;
import com.healthcare.domain.diet.entity.FoodCatalog.FoodCategory;
import com.healthcare.domain.diet.external.config.ExternalApiProperties;
import com.healthcare.domain.diet.external.dto.ExternalFoodResult;
import com.healthcare.domain.diet.external.dto.ExternalFoodResult.FoodDataSource;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;

import java.net.URLDecoder;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

@Slf4j
@Component
public class PublicFoodApiClientImpl implements PublicFoodApiClient {

    /** 가공식품 API 클라이언트 */
    private final RestClient processedFoodClient;

    /** 음식 API 클라이언트 */
    private final RestClient generalFoodClient;

    /** API 설정 */
    private final ExternalApiProperties properties;

    private final ObjectMapper objectMapper;

    public PublicFoodApiClientImpl(
            @Qualifier("processedFoodRestClient") RestClient processedFoodClient,
            @Qualifier("generalFoodRestClient") RestClient generalFoodClient,
            ExternalApiProperties properties,
            ObjectMapper objectMapper) {
        this.processedFoodClient = processedFoodClient;
        this.generalFoodClient = generalFoodClient;
        this.properties = properties;
        // 결과 없음일 때 "items": "" 로 내려오는 포털 응답 방어 — 빈 문자열을 null로 수용
        this.objectMapper = objectMapper.copy();
        this.objectMapper.coercionConfigDefaults(cfg ->
                cfg.setCoercion(CoercionInputShape.EmptyString, CoercionAction.AsNull));
    }

    // 식품 대분류 → FoodCategory 매핑
    private static final Map<String, FoodCategory> CATEGORY_MAPPING = Map.ofEntries(
            Map.entry("곡류", FoodCategory.GRAIN),
            Map.entry("서류", FoodCategory.GRAIN),
            Map.entry("당류", FoodCategory.PROCESSED),
            Map.entry("두류", FoodCategory.PROTEIN_SOURCE),
            Map.entry("견과류", FoodCategory.FAT),
            Map.entry("채소류", FoodCategory.VEGETABLE),
            Map.entry("과일류", FoodCategory.FRUIT),
            Map.entry("버섯류", FoodCategory.VEGETABLE),
            Map.entry("육류", FoodCategory.PROTEIN_SOURCE),
            Map.entry("가금류", FoodCategory.PROTEIN_SOURCE),
            Map.entry("난류", FoodCategory.PROTEIN_SOURCE),
            Map.entry("어패류", FoodCategory.PROTEIN_SOURCE),
            Map.entry("해조류", FoodCategory.VEGETABLE),
            Map.entry("우유류", FoodCategory.DAIRY),
            Map.entry("유제품류", FoodCategory.DAIRY),
            Map.entry("유지류", FoodCategory.FAT),
            Map.entry("음료류", FoodCategory.BEVERAGE),
            Map.entry("주류", FoodCategory.BEVERAGE),
            Map.entry("즉석", FoodCategory.PROCESSED),
            Map.entry("가공", FoodCategory.PROCESSED)
    );

    @Override
    public List<ExternalFoodResult> search(String query, int page, int size) {
        String serviceKey = normalizeServiceKey(properties.getPublicApiKey());
        if (serviceKey.isEmpty()) {
            log.warn("공공 식품 API 키 미설정 (PUBLIC_FOOD_API_KEY) — 외부 식품 검색을 건너뜀");
            return List.of();
        }

        List<ExternalFoodResult> processedResults = List.of();
        List<ExternalFoodResult> generalResults = List.of();

        // 1. 가공식품 API 검색
        try {
            processedResults = callApi(processedFoodClient, serviceKey, query, page, size);
            log.debug("가공식품 API 검색 결과: {} 건", processedResults.size());
        } catch (Exception e) {
            log.warn("가공식품 API 검색 실패: {}", e.getMessage());
        }

        // 2. 음식 API 검색
        try {
            generalResults = callApi(generalFoodClient, serviceKey, query, page, size);
            log.debug("음식 API 검색 결과: {} 건", generalResults.size());
        } catch (Exception e) {
            log.warn("음식 API 검색 실패: {}", e.getMessage());
        }

        // 두 소스를 균등 비율로 인터리브한 뒤 limit 적용
        // → 한 API 결과만으로 size 슬롯이 꽉 차는 문제 방지
        return interleave(processedResults, generalResults, size);
    }

    /**
     * 두 리스트를 교대로 섞은 뒤 maxSize 개로 자른다.
     * 한쪽이 소진되면 나머지 소스로 채운다.
     */
    private List<ExternalFoodResult> interleave(
            List<ExternalFoodResult> a,
            List<ExternalFoodResult> b,
            int maxSize) {
        List<ExternalFoodResult> merged = new ArrayList<>(maxSize);
        int ai = 0, bi = 0;
        while (merged.size() < maxSize && (ai < a.size() || bi < b.size())) {
            if (ai < a.size()) merged.add(a.get(ai++));
            if (merged.size() < maxSize && bi < b.size()) merged.add(b.get(bi++));
        }
        return merged;
    }

    /**
     * 공공데이터 포털 영양정보 API 호출 (가공식품/음식 공통 — 요청 파라미터 동일).
     *
     * 모든 값은 URI 템플릿 변수로 전달한다. 리터럴 queryParam은 Spring이 '+'를
     * 인코딩하지 않아(서버는 공백으로 해석) 디코딩 인증키가 깨지고, 인코딩 키는
     * '%'가 %25로 이중 인코딩되는 문제가 있다. 템플릿 변수는 예약 문자를 엄격히
     * 인코딩하므로 디코딩 키('+', '=', '/' 포함)가 항상 올바르게 전송된다.
     */
    private List<ExternalFoodResult> callApi(
            RestClient client, String serviceKey, String query, int page, int size) {
        String raw = client.get()
                .uri(uriBuilder -> uriBuilder
                        .queryParam("serviceKey", "{serviceKey}")
                        .queryParam("type", "json")
                        .queryParam("pageNo", "{pageNo}")
                        .queryParam("numOfRows", "{numOfRows}")
                        .queryParam("foodNm", "{foodNm}")
                        .build(serviceKey, page + 1, size * 2, query))
                .retrieve()
                .body(String.class);
        return parseResponse(raw);
    }

    /**
     * 포털 응답 파싱. 인증 실패·시스템 오류는 type=json 요청에도 XML로 내려오므로
     * 원인 메시지를 로그에 남긴다 (기존에는 파싱 예외로 삼켜져 원인 추적 불가).
     */
    private List<ExternalFoodResult> parseResponse(String raw) {
        if (raw == null || raw.isBlank()) {
            return List.of();
        }

        String body = raw.stripLeading();
        if (body.startsWith("<")) {
            log.warn("공공 식품 API 오류 응답(XML): {}", extractXmlError(body));
            return List.of();
        }

        ApiResponseWrapper response;
        try {
            response = objectMapper.readValue(body, ApiResponseWrapper.class);
        } catch (JsonProcessingException e) {
            log.warn("공공 식품 API 응답 파싱 실패: {} — 본문: {}",
                    e.getOriginalMessage(), snippet(body));
            return List.of();
        }

        if (response == null || response.getResponse() == null) {
            return List.of();
        }

        Header header = response.getResponse().getHeader();
        if (header != null && header.getResultCode() != null && !"00".equals(header.getResultCode())) {
            log.warn("공공 식품 API 비정상 resultCode={} resultMsg={}",
                    header.getResultCode(), header.getResultMsg());
        }

        if (response.getResponse().getBody() == null ||
            response.getResponse().getBody().getItems() == null) {
            return List.of();
        }

        return response.getResponse().getBody().getItems().stream()
                .map(this::toExternalResult)
                .filter(Objects::nonNull)
                .toList();
    }

    /**
     * 설정된 인증키를 디코딩 형태로 정규화한다.
     * 공공데이터포털은 Encoding/Decoding 두 형태의 키를 제공하는데, '%'가 포함되어
     * 있으면 Encoding 키로 보고 한 번 디코딩한다 (전송 시 URI 템플릿이 다시 인코딩).
     */
    static String normalizeServiceKey(String key) {
        if (key == null) {
            return "";
        }
        String trimmed = key.trim();
        if (trimmed.contains("%")) {
            try {
                return URLDecoder.decode(trimmed, StandardCharsets.UTF_8);
            } catch (IllegalArgumentException e) {
                return trimmed;
            }
        }
        return trimmed;
    }

    private static final Pattern XML_ERROR_PATTERN =
            Pattern.compile("<(returnAuthMsg|returnReasonCode|errMsg|resultMsg|resultCode)>([^<]*)</\\1>");

    /** 포털 XML 오류 본문에서 원인 필드만 추출. 실패 시 앞부분 스니펫 반환. */
    static String extractXmlError(String xml) {
        Matcher matcher = XML_ERROR_PATTERN.matcher(xml);
        StringBuilder sb = new StringBuilder();
        while (matcher.find()) {
            if (!sb.isEmpty()) sb.append(", ");
            sb.append(matcher.group(1)).append('=').append(matcher.group(2));
        }
        return sb.isEmpty() ? snippet(xml) : sb.toString();
    }

    private static String snippet(String body) {
        String flat = body.replaceAll("\\s+", " ").trim();
        return flat.length() <= 200 ? flat : flat.substring(0, 200) + "…";
    }

    private ExternalFoodResult toExternalResult(PublicFoodItem item) {
        // 필수 필드 검증
        if (item.getFoodNm() == null || item.getFoodNm().isBlank()) return null;

        // 칼로리 파싱 (문자열일 수 있음)
        Double calories = parseDouble(item.getEnerc());
        if (calories == null || calories == 0) return null;

        return ExternalFoodResult.builder()
                .source(FoodDataSource.PUBLIC_FOOD_API)
                .externalId(item.getFoodCd())
                .name(item.getFoodNm())
                .nameKo(item.getFoodNm())
                .brand(item.getMfrNm())
                .category(mapCategory(item.getFoodLv3Nm()))
                .caloriesPer100g(calories)
                .proteinPer100g(parseDouble(item.getProt()))
                .carbsPer100g(parseDouble(item.getChocdf()))
                .fatPer100g(parseDouble(item.getFatce()))
                .sugarsPer100g(parseDouble(item.getSugar()))
                .dietaryFiberPer100g(parseDouble(item.getFibtg()))
                .saturatedFatPer100g(parseDouble(item.getFasat()))
                .transFatPer100g(parseDouble(item.getFatrn()))
                .cholesterolPer100gMg(parseDouble(item.getChole()))
                .sodiumPer100gMg(parseDouble(item.getNat()))
                .build();
    }

    private Double parseDouble(String value) {
        if (value == null || value.isBlank()) return null;
        try {
            return Double.parseDouble(value.trim());
        } catch (NumberFormatException e) {
            return null;
        }
    }

    private FoodCategory mapCategory(String majorCategory) {
        if (majorCategory == null || majorCategory.isBlank()) {
            return FoodCategory.OTHER;
        }
        return CATEGORY_MAPPING.entrySet().stream()
                .filter(e -> majorCategory.contains(e.getKey()))
                .map(Map.Entry::getValue)
                .findFirst()
                .orElse(FoodCategory.PROCESSED);
    }

    // ─────────────────────────── 내부 응답 DTO ───────────────────────────

    @Getter @Setter @NoArgsConstructor
    @JsonIgnoreProperties(ignoreUnknown = true)
    static class ApiResponseWrapper {
        private ResponseData response;
    }

    @Getter @Setter @NoArgsConstructor
    @JsonIgnoreProperties(ignoreUnknown = true)
    static class ResponseData {
        private Header header;
        private Body body;
    }

    @Getter @Setter @NoArgsConstructor
    @JsonIgnoreProperties(ignoreUnknown = true)
    static class Header {
        private String resultCode;
        private String resultMsg;
        private String type;
    }

    @Getter @Setter @NoArgsConstructor
    @JsonIgnoreProperties(ignoreUnknown = true)
    static class Body {
        private List<PublicFoodItem> items;
        private String totalCount;
        private String numOfRows;
        private String pageNo;
    }

    @Getter @Setter @NoArgsConstructor
    @JsonIgnoreProperties(ignoreUnknown = true)
    static class PublicFoodItem {
        private String foodCd;        // 식품코드
        private String foodNm;        // 식품명
        private String dataCd;        // 데이터구분코드
        private String typeNm;        // 데이터구분명
        private String foodLv3Cd;     // 식품대분류코드
        private String foodLv3Nm;     // 식품대분류명
        private String nutConSrtrQua; // 영양성분함량 기준량
        private String enerc;         // 에너지(kcal)
        private String prot;          // 단백질(g)
        private String fatce;         // 지방(g)
        private String chocdf;        // 탄수화물(g)
        private String sugar;         // 당류(g)
        private String fibtg;         // 식이섬유(g)
        private String fasat;         // 포화지방산(g)
        private String fatrn;         // 트랜스지방산(g)
        private String chole;         // 콜레스테롤(mg)
        private String nat;           // 나트륨(mg)
        private String mfrNm;         // 제조사명
        private String itemMnftrRptNo; // 품목제조보고번호
    }
}
