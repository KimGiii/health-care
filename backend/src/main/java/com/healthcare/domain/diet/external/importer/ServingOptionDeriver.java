package com.healthcare.domain.diet.external.importer;

import com.healthcare.domain.diet.entity.FoodCatalog.FoodCategory;
import com.healthcare.domain.diet.entity.FoodServingOption;
import com.healthcare.domain.diet.entity.FoodServingOption.ServingType;
import org.springframework.stereotype.Component;

import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * 추천이 쓸 수 있는 1회 제공량 옵션을 도출한다(§7.1).
 *
 * <p>우선순위: source의 영양 기준(servingReference)에서 깨끗한 단일 g 값을 얻되,
 * "100g/100ml"처럼 단순 영양 기준이거나 복수·비정상 값이면 카테고리 표준 1회 제공량으로 대체한다.
 * 도출한 1회 제공량을 카테고리별 현실 범위로 캡한 뒤 0.5/1/1.5/2회 multiplier 옵션으로 전개한다.
 *
 * <p>source의 식품 전체 중량(servingSizeG)은 제품 포장 단위(최대 수십 kg)라 1회 제공량으로 신뢰하지 않는다.
 */
@Component
public class ServingOptionDeriver {

    /** 카테고리별 표준 1회 제공량(g). 영양표시 기준 통상 섭취량 근사. */
    private static final Map<FoodCategory, Double> CATEGORY_DEFAULT_G = Map.of(
            FoodCategory.GRAIN, 150.0,
            FoodCategory.PROTEIN_SOURCE, 120.0,
            FoodCategory.VEGETABLE, 75.0,
            FoodCategory.FRUIT, 150.0,
            FoodCategory.DAIRY, 200.0,
            FoodCategory.FAT, 15.0,
            FoodCategory.BEVERAGE, 200.0,
            FoodCategory.PROCESSED, 50.0,
            FoodCategory.OTHER, 100.0
    );

    /** 카테고리별 1회 제공량 현실 범위[min,max] (g). 파싱값이 이를 벗어나면 캡한다. */
    private static final Map<FoodCategory, double[]> CATEGORY_RANGE_G = Map.of(
            FoodCategory.GRAIN, new double[]{30, 300},
            FoodCategory.PROTEIN_SOURCE, new double[]{30, 250},
            FoodCategory.VEGETABLE, new double[]{20, 200},
            FoodCategory.FRUIT, new double[]{50, 300},
            FoodCategory.DAIRY, new double[]{50, 300},
            FoodCategory.FAT, new double[]{5, 50},
            FoodCategory.BEVERAGE, new double[]{100, 350},
            FoodCategory.PROCESSED, new double[]{20, 200},
            FoodCategory.OTHER, new double[]{20, 300}
    );

    private static final double[] MULTIPLIERS = {1.0, 0.5, 1.5, 2.0};
    private static final Pattern SINGLE_GRAM = Pattern.compile("^([0-9]+(?:\\.[0-9]+)?)\\s*(g|ml)$");

    /** 한 식품의 제공량 옵션을 도출한다. 항상 1개 이상의 검증 옵션을 반환한다. */
    public List<FoodServingOption> derive(
            Long foodCatalogId, FoodCategory category, String servingReference) {
        double base = resolveBaseServingG(category, servingReference);
        OffsetDateTime now = OffsetDateTime.now();

        List<FoodServingOption> options = new ArrayList<>(MULTIPLIERS.length);
        for (int i = 0; i < MULTIPLIERS.length; i++) {
            double grams = Math.round(base * MULTIPLIERS[i]);
            String label = formatMultiplier(MULTIPLIERS[i]) + "회 제공량";
            options.add(FoodServingOption.builder()
                    .foodCatalogId(foodCatalogId)
                    .label(label)
                    .labelKo(label)
                    .equivalentG(grams)
                    .sortOrder(i)
                    .servingType(ServingType.OFFICIAL_SERVING)
                    .verifiedAt(now)
                    .build());
        }
        return options;
    }

    /** source 기준 또는 카테고리 표준에서 1회 제공량(g)을 정한 뒤 현실 범위로 캡한다. */
    double resolveBaseServingG(FoodCategory category, String servingReference) {
        FoodCategory resolved = category != null ? category : FoodCategory.OTHER;
        Double parsed = parseSingleServingGram(servingReference);
        double base = parsed != null ? parsed : CATEGORY_DEFAULT_G.get(resolved);
        double[] range = CATEGORY_RANGE_G.get(resolved);
        return Math.max(range[0], Math.min(range[1], base));
    }

    /**
     * servingReference에서 1회 제공량 g를 파싱한다. 단순 영양 기준(100g/100ml)·복수 값·미상은 null.
     */
    private Double parseSingleServingGram(String servingReference) {
        if (servingReference == null) {
            return null;
        }
        String normalized = servingReference.trim().toLowerCase();
        if (normalized.isEmpty() || normalized.contains(",") || normalized.contains("·")) {
            return null;
        }
        Matcher matcher = SINGLE_GRAM.matcher(normalized);
        if (!matcher.matches()) {
            return null;
        }
        double grams = Double.parseDouble(matcher.group(1));
        // 100g/100ml은 영양 기준일 뿐 1회 제공량이 아니므로 무시한다.
        if (grams == 100.0) {
            return null;
        }
        return grams;
    }

    private String formatMultiplier(double multiplier) {
        return multiplier == Math.floor(multiplier)
                ? String.valueOf((int) multiplier)
                : String.valueOf(multiplier);
    }
}
