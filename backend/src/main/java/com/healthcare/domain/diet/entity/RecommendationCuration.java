package com.healthcare.domain.diet.entity;

public sealed interface RecommendationCuration
        permits RecommendationCuration.Recommendable,
                RecommendationCuration.WithCaution,
                RecommendationCuration.SearchOnly,
                RecommendationCuration.Disabled {

    int MAX_REASON_LENGTH = 255;

    RecommendationStatus status();

    String reasonForStorage();

    String cautionForResponse();

    record Recommendable() implements RecommendationCuration {
        public RecommendationStatus status() { return RecommendationStatus.RECOMMENDABLE; }
        public String reasonForStorage() { return null; }
        public String cautionForResponse() { return null; }
    }

    record WithCaution(String reason) implements RecommendationCuration {
        public WithCaution {
            if (reason == null || reason.isBlank())
                throw new IllegalArgumentException("WithCaution requires a non-blank reason");
        }
        public RecommendationStatus status() { return RecommendationStatus.RECOMMENDABLE_WITH_CAUTION; }
        public String reasonForStorage() { return reason; }
        public String cautionForResponse() { return reason; }
    }

    record SearchOnly() implements RecommendationCuration {
        public RecommendationStatus status() { return RecommendationStatus.SEARCH_ONLY; }
        public String reasonForStorage() { return null; }
        public String cautionForResponse() { return null; }
    }

    record Disabled() implements RecommendationCuration {
        public RecommendationStatus status() { return RecommendationStatus.DISABLED; }
        public String reasonForStorage() { return null; }
        public String cautionForResponse() { return null; }
    }

    record ImportResult(
            RecommendationCuration curation,
            String rejectionReason
    ) {
        public ImportResult {
            if ((curation == null) == (rejectionReason == null)) {
                throw new IllegalArgumentException("ImportResult must be accepted or rejected");
            }
        }

        public static ImportResult accepted(RecommendationCuration curation) {
            return new ImportResult(curation, null);
        }

        public static ImportResult rejected(String reason) {
            return new ImportResult(null, reason);
        }

        public boolean rejected() {
            return rejectionReason != null;
        }
    }

    static RecommendationCuration of(RecommendationStatus status, String reason) {
        return switch (status) {
            case RECOMMENDABLE -> new Recommendable();
            case RECOMMENDABLE_WITH_CAUTION -> new WithCaution(reason);
            case SEARCH_ONLY -> new SearchOnly();
            case DISABLED -> new Disabled();
        };
    }

    static ImportResult fromImport(RecommendationStatus status, String reason) {
        if (reason != null && reason.length() > MAX_REASON_LENGTH) {
            return ImportResult.rejected(MAX_REASON_LENGTH + "자 이하여야 합니다.");
        }
        if (status == RecommendationStatus.RECOMMENDABLE_WITH_CAUTION) {
            if (reason == null) {
                return ImportResult.rejected("주의 추천 상태는 사유가 필요합니다.");
            }
            return ImportResult.accepted(new WithCaution(reason));
        }
        return ImportResult.accepted(of(status, null));
    }
}
