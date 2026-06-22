package com.healthcare.domain.diet.external.importer;

import com.healthcare.domain.diet.external.config.ExternalApiProperties;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestClient;

@Service
public class StandardProcessedFoodPageFetcher extends StandardFoodApiPageFetcher {

    public StandardProcessedFoodPageFetcher(
            @Qualifier("processedFoodRestClient") RestClient client,
            ExternalApiProperties properties) {
        super(client, properties);
    }
}
