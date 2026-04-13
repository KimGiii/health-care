package com.healthcare.domain.diet.external.config;

import lombok.RequiredArgsConstructor;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.client.RestClient;

import java.time.Duration;

@Configuration
@RequiredArgsConstructor
public class ExternalApiConfig {

    private final ExternalApiProperties props;

    @Bean("usdaRestClient")
    public RestClient usdaRestClient() {
        return RestClient.builder()
                .baseUrl(props.getUsdaBaseUrl())
                .defaultHeader("Accept", "application/json")
                .build();
    }

    /** 바코드 조회용 (world.openfoodfacts.org) */
    @Bean("offRestClient")
    public RestClient offRestClient() {
        return RestClient.builder()
                .baseUrl(props.getOffBaseUrl())
                .defaultHeader("Accept", "application/json")
                .defaultHeader("User-Agent",
                        "HealthCareApp/1.0 (contact@healthcare.example.com)")
                .build();
    }

    /** 텍스트 검색용 (search.openfoodfacts.org — v2 Elasticsearch API) */
    @Bean("offSearchRestClient")
    public RestClient offSearchRestClient() {
        return RestClient.builder()
                .baseUrl(props.getOffSearchBaseUrl())
                .defaultHeader("Accept", "application/json")
                .defaultHeader("User-Agent",
                        "HealthCareApp/1.0 (contact@healthcare.example.com)")
                .build();
    }
}
