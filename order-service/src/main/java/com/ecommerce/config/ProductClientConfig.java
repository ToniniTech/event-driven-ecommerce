package com.ecommerce.config;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.client.JdkClientHttpRequestFactory;
import org.springframework.web.client.RestClient;

import java.net.http.HttpClient;
import java.time.Duration;

/**
 * Builds the RestClient used to call product-service.
 *
 * <p>Timeouts are set explicitly and on purpose: a synchronous inter-service call
 * must never block an order thread indefinitely if product-service is slow or hung.
 * Without them, a stalled downstream can exhaust the Tomcat thread pool of
 * order-service (cascading failure).
 */
@Configuration
public class ProductClientConfig {

    @Bean("productRestClient")
    public RestClient productRestClient(
            RestClient.Builder builder,
            @Value("${product.service.url}") String baseUrl
    ) {
        HttpClient httpClient = HttpClient.newBuilder()
                .connectTimeout(Duration.ofSeconds(5))
                .build();

        JdkClientHttpRequestFactory requestFactory =
                new JdkClientHttpRequestFactory(httpClient);

        requestFactory.setReadTimeout(Duration.ofSeconds(5));

        return builder
                .baseUrl(baseUrl)
                .requestFactory(requestFactory)
                .build();
    }
}
