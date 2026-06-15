package com.healthcare.domain.diet.identity;

import com.healthcare.domain.diet.entity.FoodCatalog;

import java.text.Normalizer;
import java.util.Locale;

public final class FoodCatalogIdentity {

    private static final String DEDUP_STRIP_PATTERN = "[\\s\\-_/()（）]";

    private FoodCatalogIdentity() {
    }

    public static String brandOfficialFoodCode(String brandName, String menuName) {
        return sourceCodePart(brandName) + ":" + sourceCodePart(menuName);
    }

    public static String duplicateKey(FoodCatalog food) {
        String nameKey = duplicateNameKey(displayName(food));
        String brandKey = duplicateNameKey(food.getBrandName());
        return brandKey == null ? nameKey : brandKey + ":" + nameKey;
    }

    public static String duplicateNameKey(String value) {
        String normalized = normalizeDisplayName(value);
        if (normalized == null) {
            return null;
        }
        return normalized.toLowerCase(Locale.ROOT).replaceAll(DEDUP_STRIP_PATTERN, "");
    }

    public static String normalizeDisplayName(String value) {
        if (value == null) {
            return null;
        }
        String nfc = Normalizer.normalize(value, Normalizer.Form.NFC);
        String normalized = nfc.trim().replaceAll("\\s+", " ");
        return normalized.isBlank() ? null : normalized;
    }

    private static String sourceCodePart(String value) {
        String normalized = normalizeDisplayName(value);
        if (normalized == null) {
            throw new IllegalArgumentException("source identity part must not be blank");
        }
        return normalized.toLowerCase(Locale.ROOT).replaceAll("\\s+", "_");
    }

    private static String displayName(FoodCatalog food) {
        String nameKo = normalizeDisplayName(food.getNameKo());
        if (nameKo != null) {
            return nameKo;
        }
        return normalizeDisplayName(food.getName());
    }
}
