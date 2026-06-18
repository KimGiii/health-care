package com.healthcare.domain.diet.external.importer;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.NullAndEmptySource;
import org.junit.jupiter.params.provider.ValueSource;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * MFDS 원본명({@code 대분류_세분류})을 자연어 표시명으로 정규화하는 휴리스틱 검증.
 *
 * <p>규칙 요약:
 * <ul>
 *   <li>분류어 반복({@code 국수_막국수}) → 뒷부분만</li>
 *   <li>분류_수식어({@code 경단_깨}) → 수식어+분류어 뒤집기</li>
 *   <li>드롭 세그먼트({@code 반}, 순수 영문/숫자 코드) → 제거</li>
 *   <li>유지 세그먼트({@code 세트}, 사이즈 {@code S/M/L}) → 접미 {@code (…)} 보존</li>
 * </ul>
 */
class FoodDisplayNameNormalizerTest {

    private static String display(String raw) {
        return FoodDisplayNameNormalizer.normalize(raw).displayName();
    }

    @Nested
    @DisplayName("언더스코어가 없으면 원본 유지")
    class NoUnderscore {

        @ParameterizedTest
        @ValueSource(strings = {"꿀떡", "모듬찰떡", "백설기", "시루떡", "약식", "인절미", "증편"})
        void keepsPlainName(String name) {
            assertThat(display(name)).isEqualTo(name);
        }

        @ParameterizedTest
        @NullAndEmptySource
        void returnsInputForBlank(String blank) {
            assertThat(FoodDisplayNameNormalizer.normalize(blank).displayName()).isEqualTo(blank);
        }
    }

    @Nested
    @DisplayName("분류_수식어 → 수식어를 앞으로")
    class ModifierPrefix {

        @ParameterizedTest
        @CsvSource({
                "경단_깨, 깨경단",
                "송편_깨, 깨송편",
                "절편_쑥, 쑥절편",
                "도넛_찹쌀, 찹쌀도넛",
                "볶음밥_새우, 새우볶음밥",
                "샤브샤브_버섯, 버섯샤브샤브",
                "찹쌀떡_팥, 팥찹쌀떡"
        })
        void prefixesModifier(String raw, String expected) {
            assertThat(display(raw)).isEqualTo(expected);
        }

        @Test
        @DisplayName("다중 수식어는 원래 순서로 분류어 앞에 결합")
        void multipleModifiers() {
            assertThat(display("샌드위치_햄_치즈")).isEqualTo("햄치즈샌드위치");
        }
    }

    @Nested
    @DisplayName("긴 제품명 세그먼트는 분류어를 떼고 그대로 사용")
    class ProductNameSegment {

        @ParameterizedTest
        @CsvSource({
                "닭튀김_마늘스태미나 치킨, 마늘스태미나 치킨",
                "샌드위치_스파이시치킨샐러드랩, 스파이시치킨샐러드랩",
                "달걀찜_새우젓, 새우젓달걀찜"
        })
        void dropsCategoryWhenProductNameLongOrPhrased(String raw, String expected) {
            assertThat(display(raw)).isEqualTo(expected);
        }
    }

    @Nested
    @DisplayName("분류어 반복 → 더 구체적인 세그먼트 채택")
    class CategoryRepeat {

        @ParameterizedTest
        @CsvSource({
                "도넛_링도넛, 링도넛",
                "국수_막국수, 막국수",
                "냉면_물냉면, 물냉면",
                "케이크_롤케이크, 롤케이크"
        })
        void usesSpecificSegment(String raw, String expected) {
            assertThat(display(raw)).isEqualTo(expected);
        }

        @Test
        @DisplayName("반복 + 추가 수식어")
        void repeatWithExtraModifier() {
            assertThat(display("냉면_회냉면_홍어")).isEqualTo("홍어회냉면");
        }
    }

    @Nested
    @DisplayName("드롭 세그먼트 제거")
    class DroppedSegments {

        @ParameterizedTest
        @CsvSource({
                "반_티라미수도넛, 티라미수도넛",
                "반_산타벨리도넛, 산타벨리도넛",
                "반_링도넛, 링도넛"
        })
        void dropsBandasPrefix(String raw, String expected) {
            assertThat(display(raw)).isEqualTo(expected);
        }

        @ParameterizedTest
        @CsvSource({
                "JH_고추양념소스_v1, 고추양념소스",
                "JH_고추양념소스_v2, 고추양념소스",
                "냉동 생중화면_F, 냉동 생중화면"
        })
        void dropsLatinDigitCodes(String raw, String expected) {
            assertThat(display(raw)).isEqualTo(expected);
        }
    }

    @Nested
    @DisplayName("유지 세그먼트는 접미로 보존")
    class KeptSuffixes {

        @Test
        @DisplayName("세트는 단품과 구분되므로 (세트) 접미로 유지")
        void keepsSetAsSuffix() {
            assertThat(display("닭볶음탕_간편조리세트_곱도리탕"))
                    .contains("곱도리탕")
                    .contains("(간편조리세트)");
        }

        @ParameterizedTest
        @CsvSource({
                "S_무화과크림치즈휘낭시에, 무화과크림치즈휘낭시에(S)",
                "L_치즈케이크, 치즈케이크(L)"
        })
        void keepsSizeAsSuffix(String raw, String expected) {
            assertThat(display(raw)).isEqualTo(expected);
        }
    }

    @Nested
    @DisplayName("실데이터에서 발견된 엣지케이스 보정")
    class RealDataEdgeCases {

        @Test
        @DisplayName("괄호 안 언더스코어는 분리하지 않는다 (이름 파괴 방지)")
        void doesNotSplitUnderscoreInsideParens() {
            assertThat(display("냉동혼합야채(4종_볶음밥용)")).isEqualTo("냉동혼합야채(4종 볶음밥용)");
        }

        @ParameterizedTest
        @DisplayName("보관 상태(냉장/냉동/실온)는 접미로 보존")
        @CsvSource({
                "소고기무국_냉동, 소고기무국(냉동)",
                "경상도식소고기무국_냉동, 경상도식소고기무국(냉동)"
        })
        void keepsStorageStateAsSuffix(String raw, String expected) {
            assertThat(display(raw)).isEqualTo(expected);
        }

        @ParameterizedTest
        @DisplayName("케이크 호수는 사이즈성 접미로 보존")
        @CsvSource({
                "더블초코케이크_1호, 더블초코케이크(1호)",
                "블루베리무스케이크_1호, 블루베리무스케이크(1호)"
        })
        void keepsHoSizeAsSuffix(String raw, String expected) {
            assertThat(display(raw)).isEqualTo(expected);
        }

        @Test
        @DisplayName("연도+보관상태 꼬리는 상태만 접미로 남기고 제품명 보존")
        void dropsYearKeepsStateSuffix() {
            assertThat(display("(반)시그니처 스트로베리 케이크_26(냉장)"))
                    .isEqualTo("(반)시그니처 스트로베리 케이크(냉장)");
        }

        @Test
        @DisplayName("코드+상태+포장단위 혼합 꼬리 정리")
        void cleansMixedCodeStatePackTail() {
            assertThat(display("23XMAS_윈터겹겹이크레이프_냉동_EA")).isEqualTo("윈터겹겹이크레이프(냉동)");
        }

        @Test
        @DisplayName("앞 세그먼트가 공백 포함 구/문장이면 원래 순서를 유지")
        void keepsOrderWhenFirstSegmentIsPhrase() {
            assertThat(display("블랙춘 초코 크레이프 케이크_춘식이"))
                    .isEqualTo("블랙춘 초코 크레이프 케이크 춘식이");
        }

        @Test
        @DisplayName("공백 없는 한글 합성어 분류어는 수식어를 앞으로 보낸다")
        void prefixesModifierForSpacelessCompoundCategory() {
            assertThat(display("6곡단팥빵_밤")).isEqualTo("밤6곡단팥빵");
        }
    }

    @Nested
    @DisplayName("검색 별칭은 원본·표시명 양쪽 어순을 포함")
    class SearchAlias {

        @Test
        @DisplayName("언더스코어 제거형과 표시명을 모두 담아 양방향 검색 매칭")
        void containsBothOrders() {
            String alias = FoodDisplayNameNormalizer.normalize("경단_깨").searchAlias();
            // 사용자가 '경단'으로도 '깨경단'으로도 찾을 수 있어야 한다.
            assertThat(alias.replace(" ", "")).contains("경단깨");
            assertThat(alias.replace(" ", "")).contains("깨경단");
        }

        @Test
        @DisplayName("언더스코어가 없으면 별칭은 null (불필요)")
        void nullWhenNoUnderscore() {
            assertThat(FoodDisplayNameNormalizer.normalize("약식").searchAlias()).isNull();
        }
    }
}
