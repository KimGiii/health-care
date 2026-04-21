package com.healthcare.domain.diet.mealphoto.service;

import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.stereotype.Component;

import java.util.List;

@Component
@ConditionalOnMissingBean(name = "openAiMealAnalysisProvider")
public class FallbackMealAnalysisProvider implements MealAnalysisProvider {

    @Override
    public AnalysisResult analyze(String imageDataUrl, String contentType) {
        return new AnalysisResult(
                "fallback",
                "heuristic-v1",
                "{\"provider\":\"fallback\"}",
                List.of(
                        "OpenAI API 키가 설정되지 않아 자동 분석 대신 검토용 초안을 생성했습니다.",
                        "사진만으로는 소스, 오일, 국물 칼로리를 정확히 알기 어렵습니다."
                ),
                List.of(new DetectedItem(
                        "사진 기반 식사",
                        100.0,
                        180.0,
                        8.0,
                        18.0,
                        8.0,
                        0.15,
                        true,
                        "실제 음식 종류와 양을 사용자가 직접 수정해야 합니다."
                ))
        );
    }
}
