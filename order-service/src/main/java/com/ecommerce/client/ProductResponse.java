package com.ecommerce.client;

import java.math.BigDecimal;

/**
 * Order-service's local view of the JSON returned by product-service
 * (PATCH /api/products/{productId}/decreaseStock).
 *
 * <p>This is an anti-corruption boundary: we deliberately keep our OWN copy of the
 * external contract instead of importing product-service classes. If product-service
 * evolves, only this record changes, not the rest of order-service.
 */
public record ProductResponse(
        String productId,
        String name,
        BigDecimal price,
        Integer stock,
        boolean active
) {
}
