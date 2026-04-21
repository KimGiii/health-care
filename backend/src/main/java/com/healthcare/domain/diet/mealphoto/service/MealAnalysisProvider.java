package com.healthcare.domain.diet.mealphoto.service;

import java.util.List;

public interface MealAnalysisProvider {

    AnalysisResult analyze(String imageDataUrl, String contentType);

    record AnalysisResult(
            String provider,
            String analysisVersion,
            String rawOutput,
            List<String> warnings,
            List<DetectedItem> items
    ) {
    }

    record DetectedItem(
            String label,
            Double estimatedServingG,
            Double calories,
            Double proteinG,
            Double carbsG,
            Double fatG,
            Double confidence,
            boolean needsReview,
            String unknownOrUncertain
    ) {
    }
}
