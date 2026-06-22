package com.healthcare.domain.diet.external.importer;

import com.healthcare.domain.diet.external.config.ExternalApiProperties;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestClient;

@Service
public class StandardDishFoodPageFetcher extends StandardFoodApiPageFetcher {

    public StandardDishFoodPageFetcher(
            @Qualifier("generalFoodRestClient") RestClient client,
            ExternalApiProperties properties) {
        super(client, properties);
    }
}
