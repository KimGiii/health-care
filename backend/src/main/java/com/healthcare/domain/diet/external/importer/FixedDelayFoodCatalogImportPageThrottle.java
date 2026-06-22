package com.healthcare.domain.diet.external.importer;

import com.healthcare.domain.diet.entity.FoodCatalogSource;
import com.healthcare.domain.diet.external.config.ExternalApiProperties;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
public class FixedDelayFoodCatalogImportPageThrottle implements FoodCatalogImportPageThrottle {

    private final ExternalApiProperties properties;

    @Override
    public void pauseAfterPage(FoodCatalogSource source, int completedPageNo) {
        long delayMillis = properties.getImportPageDelayMillis();
        if (delayMillis <= 0) {
            return;
        }

        try {
            Thread.sleep(delayMillis);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException("식품 카탈로그 적재 rate limit 대기 중 인터럽트되었습니다.", e);
        }
    }
}
