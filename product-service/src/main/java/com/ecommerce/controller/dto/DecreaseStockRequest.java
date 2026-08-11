package com.ecommerce.controller.dto;

import jakarta.validation.constraints.NotNull;

/**
 * Inbound payload for a relative stock adjustment.
 * {@code delta} may be positive (restock) or negative (reserve/sell); the service
 * rejects the request if applying it would drive stock below zero.
 */
public record DecreaseStockRequest(

        @NotNull(message = "quantity is required")
        Integer quantity

) {
}
