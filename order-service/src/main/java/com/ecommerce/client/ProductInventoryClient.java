package com.ecommerce.client;

import com.ecommerce.controller.dto.DecreaseStockRequest;
import com.ecommerce.exception.ProductNotAvailableException;
import com.ecommerce.exception.ProductNotFoundException;
import com.ecommerce.exception.ProductServiceUnavailableException;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.http.HttpStatusCode;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.client.ResourceAccessException;
import org.springframework.web.client.RestClient;

/**
 * Synchronous client for product-service (the intentional REST-only service).
 * Resolves the authoritative name and price of a product from its business key,
 * so the client of order-service can never dictate the price.
 */
@Slf4j
@Component
public class ProductInventoryClient {

    private final RestClient productRestClient;

    // Explicit constructor (no Lombok) so we can @Qualifier the specific RestClient
    // bean unambiguously, even if more HTTP clients are added later.
    public ProductInventoryClient(@Qualifier("productRestClient") RestClient productRestClient) {
        this.productRestClient = productRestClient;
    }

    public ProductResponse decreaseStock(String productId, Integer quantity){
        log.debug("[ORDER-SERVICE] Decreasing product stock | productId={}, quantity={}", productId, quantity);

        DecreaseStockRequest body = new DecreaseStockRequest(
                quantity

        );

    try {
            ProductResponse product = productRestClient.patch()
                    .uri("/api/products/{productId}/decreaseStock", productId)
                    .contentType(MediaType.APPLICATION_JSON)
                    .body(body) //Serialize
                    .retrieve()
                    .onStatus(
                            status -> status.value() == 404,
                            (request, response) -> {
                                throw new ProductNotFoundException(
                                        "Product not found: " + productId
                                );
                            }
                    )
                    .onStatus(
                            status -> status.value() == 409,
                            (request, response) -> {
                                throw new ProductNotAvailableException(
                                        "Product has insufficient stock or is unavailable: "
                                                + productId
                                );
                            }
                    )
                    .onStatus(
                            HttpStatusCode::is5xxServerError,
                            (request, response) -> {
                                throw new ProductServiceUnavailableException(
                                        "product-service returned "
                                                + response.getStatusCode()
                                                + " for productId="
                                                + productId
                                );
                            }
                    ).body(ProductResponse.class);

            if(product == null){
                throw new ProductServiceUnavailableException(
                        "product-service returned an empty response for productId="
                                + productId
                );
            }

            return product;

            } catch (ResourceAccessException exception) {
        throw new ProductServiceUnavailableException(
                "Could not connect to product-service for productId="
                        + productId,
                exception
        );

        }
    }
}