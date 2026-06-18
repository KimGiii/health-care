package com.healthcare.domain.diet.external.importer;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * MFDS 원본 식품명({@code 대분류_세분류} 표기)을 사용자 친화적인 자연어 표시명으로 정규화한다.
 *
 * <p>MFDS 식품영양 DB는 분류어를 앞에 두고 세분류/수식어를 {@code _}로 잇는다(예: {@code 경단_깨}).
 * 사용자는 자연어 어순({@code 깨경단})으로 검색·인지하므로, 검색 매칭 실패와 어색한 노출을 막기 위해
 * 표시명을 정규화하고 원본 어순까지 포함한 검색 별칭을 함께 만든다.
 *
 * <p>휴리스틱(드롭/접미 목록은 실데이터 근거로 확정):
 * <ul>
 *   <li>{@code _}는 괄호 밖에서만 구분자로 본다({@code 냉동혼합야채(4종_볶음밥용)} 보호)</li>
 *   <li>{@code 반}(포장단위)·순수 영문/숫자 코드 세그먼트 → 제거</li>
 *   <li>사이즈({@code S/M/L}·{@code N호})·세트·보관상태({@code 냉장/냉동/실온}) → {@code (…)} 접미로 보존</li>
 *   <li>분류어 반복({@code 국수_막국수}) → 더 구체적인 세그먼트 채택</li>
 *   <li>긴 제품명 세그먼트({@code 닭튀김_마늘스태미나 치킨}) → 분류어 제거</li>
 *   <li>그 외({@code 경단_깨}) → 수식어를 분류어 앞으로 결합</li>
 * </ul>
 */
public final class FoodDisplayNameNormalizer {

    private static final char SEGMENT_DELIMITER = '_';

    /** 음식명으로 단독 사용되지 않는 포장단위/코드 한글 세그먼트. */
    private static final Set<String> DROP_SEGMENTS = Set.of("반");

    /** 순수 영문/숫자/단위 코드 세그먼트(예: JH, 25X, v1, 750ML). 사이즈는 별도로 보존한다. */
    private static final Pattern CODE_SEGMENT = Pattern.compile("^[A-Za-z0-9.&·\\-]+$");

    /** 단품과 영양/구성이 다른 사이즈 코드. 표시명에 접미로 보존한다. */
    private static final Pattern SIZE_SEGMENT = Pattern.compile("^(?i)(XS|S|M|L|XL|XXL)$");

    /** 케이크 호수처럼 사이즈성 세그먼트(예: 1호, 2호). 접미로 보존한다. */
    private static final Pattern HO_SIZE_SEGMENT = Pattern.compile("^[0-9]+호$");

    /** 보관 상태 단독 세그먼트. 접미로 보존한다. */
    private static final Set<String> STORAGE_STATES = Set.of("냉장", "냉동", "실온");

    /** 연도 코드 뒤에 보관 상태가 붙은 꼬리(예: 26(냉장)). 연도는 버리고 상태만 접미로 남긴다. */
    private static final Pattern YEAR_WITH_STATE = Pattern.compile("^[0-9]+\\((냉장|냉동|실온)\\)$");

    /** 세트 구성을 가리키는 세그먼트(단품과 다른 상품). */
    private static final String SET_MARKER = "세트";

    /**
     * 분류어 앞에 붙일 짧은 수식어로 인정하는 최대 길이.
     * 이보다 길거나 공백을 포함하면 그 자체를 제품명으로 간주해 분류어를 떼어낸다
     * (예: {@code 닭튀김_마늘스태미나 치킨} → {@code 마늘스태미나 치킨}).
     */
    private static final int MODIFIER_MAX_LENGTH = 4;

    private FoodDisplayNameNormalizer() {
    }

    /**
     * 정규화 결과.
     *
     * @param displayName 사용자에게 보여줄 자연어 표시명
     * @param searchAlias 원본·표시명 어순을 모두 담은 검색 보조 문자열(재정렬이 없으면 {@code null})
     */
    public record NormalizedName(String displayName, String searchAlias) {
    }

    public static NormalizedName normalize(String rawName) {
        String normalized = FoodCatalogImportText.normalize(rawName);
        if (normalized == null) {
            return new NormalizedName(rawName, null);
        }
        if (normalized.indexOf(SEGMENT_DELIMITER) < 0) {
            return new NormalizedName(normalized, null);
        }

        List<String> segments = splitSegments(normalized);
        List<String> content = new ArrayList<>();
        List<String> suffixes = new ArrayList<>();
        for (String segment : segments) {
            String state = storageState(segment);
            if (state != null) {
                addUnique(suffixes, state);
            } else if (isSize(segment) || isHoSize(segment) || segment.contains(SET_MARKER)) {
                addUnique(suffixes, segment);
            } else if (isDroppable(segment)) {
                // 코드/포장단위는 표시명에서 제거 (검색 별칭에는 원본이 남는다)
            } else {
                content.add(segment);
            }
        }

        // 괄호 밖 구분자가 없으면(괄호 안 _만 존재) 재정렬 대상이 아니다: 가독성만 보정한다.
        if (segments.size() <= 1 && suffixes.isEmpty()) {
            return new NormalizedName(spacesForInnerDelimiters(normalized), null);
        }

        String base = buildBase(content);
        String display = spacesForInnerDelimiters(appendSuffixes(base, suffixes));
        if (display.isBlank()) {
            // 모든 세그먼트가 드롭된 극단 케이스: 언더스코어만 공백으로 치환해 가독성 확보
            display = normalized.replace(SEGMENT_DELIMITER, ' ').trim();
        }

        return new NormalizedName(display, buildSearchAlias(normalized, display));
    }

    /** 괄호 밖 {@code _}만 구분자로 보고 나눈다. 괄호 안 {@code _}는 세그먼트에 남긴다. */
    private static List<String> splitSegments(String value) {
        List<String> segments = new ArrayList<>();
        StringBuilder current = new StringBuilder();
        int depth = 0;
        for (int i = 0; i < value.length(); i++) {
            char c = value.charAt(i);
            if (c == '(') {
                depth++;
            } else if (c == ')') {
                depth = Math.max(0, depth - 1);
            }
            if (c == SEGMENT_DELIMITER && depth == 0) {
                addTrimmed(segments, current.toString());
                current.setLength(0);
            } else {
                current.append(c);
            }
        }
        addTrimmed(segments, current.toString());
        return segments;
    }

    private static void addTrimmed(List<String> target, String value) {
        String trimmed = value.trim();
        if (!trimmed.isEmpty()) {
            target.add(trimmed);
        }
    }

    private static void addUnique(List<String> target, String value) {
        if (!target.contains(value)) {
            target.add(value);
        }
    }

    private static boolean isSize(String segment) {
        return SIZE_SEGMENT.matcher(segment).matches();
    }

    private static boolean isHoSize(String segment) {
        return HO_SIZE_SEGMENT.matcher(segment).matches();
    }

    /** 보관 상태 세그먼트면 상태 문자열을, 아니면 {@code null}을 반환한다. */
    private static String storageState(String segment) {
        if (STORAGE_STATES.contains(segment)) {
            return segment;
        }
        Matcher matcher = YEAR_WITH_STATE.matcher(segment);
        return matcher.matches() ? matcher.group(1) : null;
    }

    private static boolean isDroppable(String segment) {
        return DROP_SEGMENTS.contains(segment) || CODE_SEGMENT.matcher(segment).matches();
    }

    private static String buildBase(List<String> content) {
        if (content.isEmpty()) {
            return "";
        }
        if (content.size() == 1) {
            return content.get(0);
        }

        String category = content.get(0);
        List<String> rest = content.subList(1, content.size());

        // 1) 분류어 반복: 더 구체적인 세그먼트가 분류어를 포함하면 그것을 기준명으로 삼는다.
        //    (예: 국수_막국수 → 막국수, 도넛_링도넛 → 링도넛)
        String repeated = rest.stream().filter(seg -> seg.contains(category)).findFirst().orElse(null);
        if (repeated != null) {
            return prefixModifiers(rest, repeated);
        }

        // 2) 제품명 세그먼트(긴 구/문장)가 있으면 그것을 기준명으로 삼고 분류어는 떼어낸다.
        //    (예: 닭튀김_마늘스태미나 치킨 → 마늘스태미나 치킨)
        String productName = rest.stream().filter(FoodDisplayNameNormalizer::isProductName).findFirst().orElse(null);
        if (productName != null) {
            return prefixModifiers(rest, productName);
        }

        // 3) 분류어 처리:
        //    - 분류어가 공백을 포함한 구/문장이면 그 자체가 제품명이므로 원래 순서를 유지한다.
        //      (예: 블랙춘 초코 크레이프 케이크_춘식이 → 블랙춘 초코 크레이프 케이크 춘식이)
        //    - 공백 없는 분류어(짧은 분류어·한글 합성어)면 수식어(원래 순서)를 앞으로 보내 결합한다.
        //      (예: 경단_깨 → 깨경단, 6곡단팥빵_밤 → 밤6곡단팥빵, 샌드위치_햄_치즈 → 햄치즈샌드위치)
        List<String> ordered = new ArrayList<>();
        if (category.indexOf(' ') >= 0) {
            ordered.addAll(content);
        } else {
            ordered.addAll(rest);
            ordered.add(category);
        }
        return joinDeduped(ordered);
    }

    /** {@code base}를 기준명으로, 나머지 세그먼트를 원래 순서의 수식어로 앞에 붙인다. */
    private static String prefixModifiers(List<String> segments, String base) {
        List<String> ordered = new ArrayList<>();
        for (String seg : segments) {
            if (!seg.equals(base)) {
                ordered.add(seg);
            }
        }
        ordered.add(base);
        return joinDeduped(ordered);
    }

    /** 짧은 수식어가 아니라 그 자체로 완결된 제품명으로 볼 수 있는 세그먼트인지. */
    private static boolean isProductName(String segment) {
        return segment.length() > MODIFIER_MAX_LENGTH || segment.indexOf(' ') >= 0;
    }

    /**
     * 이미 누적 문자열에 포함된 조각은 건너뛰며 이어 붙인다.
     * 조각 중 공백을 포함한 제품명이 있으면 가독성을 위해 공백으로, 아니면 한글 합성형으로 잇는다.
     */
    private static String joinDeduped(List<String> pieces) {
        boolean spaced = pieces.stream().anyMatch(piece -> piece.indexOf(' ') >= 0);
        List<String> kept = new ArrayList<>();
        StringBuilder accumulated = new StringBuilder();
        for (String piece : pieces) {
            if (accumulated.indexOf(piece) < 0) {
                kept.add(piece);
                accumulated.append(piece);
            }
        }
        return String.join(spaced ? " " : "", kept);
    }

    private static String appendSuffixes(String base, List<String> suffixes) {
        StringBuilder sb = new StringBuilder(base);
        for (String suffix : suffixes) {
            sb.append('(').append(suffix).append(')');
        }
        return sb.toString();
    }

    /** 괄호 안에 남은 구분자({@code _})는 가독성을 위해 공백으로 바꾼다. */
    private static String spacesForInnerDelimiters(String value) {
        return value.indexOf(SEGMENT_DELIMITER) < 0
                ? value
                : value.replace(SEGMENT_DELIMITER, ' ').trim();
    }

    /**
     * 수동 오버라이드 표시명에 대해서도 휴리스틱과 동일한 검색 별칭을 만든다.
     * 원본 어순({@code 경단_깨}의 {@code 경단깨})과 교정 표시명을 모두 담아 양방향 검색을 보존한다.
     */
    public static String searchAliasFor(String rawName, String displayName) {
        String normalized = FoodCatalogImportText.normalize(rawName);
        if (normalized == null) {
            normalized = rawName == null ? "" : rawName;
        }
        return buildSearchAlias(normalized, displayName);
    }

    /**
     * 원본(언더스코어 제거형)과 표시명을 모두 담아 양방향 검색 매칭을 가능하게 한다.
     * 기존 검색 쿼리가 공백을 제거하고 부분일치하므로, 두 어순을 공백으로 이어 두면
     * {@code 경단}·{@code 깨경단} 어느 쪽으로 검색해도 매칭된다.
     */
    private static String buildSearchAlias(String normalized, String display) {
        String original = normalized.replace(String.valueOf(SEGMENT_DELIMITER), "");
        Set<String> forms = new LinkedHashSet<>(Arrays.asList(original, display));
        forms.removeIf(form -> form == null || form.isBlank());
        return String.join(" ", forms);
    }
}
