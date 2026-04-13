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

    private String usdaBaseUrl      = "https://api.nal.usda.gov/fdc/v1";
    private String usdaApiKey       = "";
    private String offBaseUrl       = "https://world.openfoodfacts.org";
    private String offSearchBaseUrl = "https://search.openfoodfacts.org";
}
