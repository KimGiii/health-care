package com.healthcare.domain.diet.external.config;

import lombok.Getter;
import lombok.Setter;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

@Component
@ConfigurationProperties(prefix = "app.food-api")
@Getter
@Setter
public class ExternalApiProperties {

    // 공공데이터 포털 API
    private String publicApiKey        = "";
    private String processedFoodApiUrl = "https://api.data.go.kr/openapi/tn_pubr_public_nutri_process_info_api";
    private String generalFoodApiUrl   = "https://api.data.go.kr/openapi/tn_pubr_public_nutri_food_info_api";
}
