package com.healthcare.domain.diet.external.importer;

final class FoodCatalogImportText {

    static final int FOOD_CODE_MAX_LENGTH = 60;
    static final int NAME_MAX_LENGTH = 150;
    static final int SEARCH_ALIAS_MAX_LENGTH = 400;
    static final int ORGANIZATION_NAME_MAX_LENGTH = 150;
    static final int SERVING_REFERENCE_MAX_LENGTH = 80;
    static final int DATA_VERSION_MAX_LENGTH = 80;

    private FoodCatalogImportText() {
    }

    static String normalize(String value) {
        if (value == null) {
            return null;
        }
        String normalized = value.trim().replaceAll("\\s+", " ");
        return normalized.isBlank() ? null : normalized;
    }

    static String normalizeToMaxLength(String value, int maxLength) {
        String normalized = normalize(value);
        if (normalized == null || normalized.length() <= maxLength) {
            return normalized;
        }
        return normalized.substring(0, maxLength);
    }

    static String firstNonBlank(String... values) {
        for (String value : values) {
            String normalized = normalize(value);
            if (normalized != null) {
                return normalized;
            }
        }
        return null;
    }

    static String firstNonBlankToMaxLength(int maxLength, String... values) {
        return normalizeToMaxLength(firstNonBlank(values), maxLength);
    }
}
