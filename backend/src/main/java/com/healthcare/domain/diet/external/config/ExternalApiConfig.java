package com.healthcare.domain.diet.external.config;

import lombok.RequiredArgsConstructor;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.client.RestClient;

@Configuration
@RequiredArgsConstructor
public class ExternalApiConfig {

    private final ExternalApiProperties props;

    /** 공공데이터 포털 - 가공식품 영양정보 API */
    @Bean("processedFoodRestClient")
    public RestClient processedFoodRestClient() {
        return RestClient.builder()
                .baseUrl(props.getProcessedFoodApiUrl())
                .defaultHeader("Accept", "application/json")
                .build();
    }

    /** 공공데이터 포털 - 음식 영양정보 API */
    @Bean("generalFoodRestClient")
    public RestClient generalFoodRestClient() {
        return RestClient.builder()
                .baseUrl(props.getGeneralFoodApiUrl())
                .defaultHeader("Accept", "application/json")
                .build();
    }
}
