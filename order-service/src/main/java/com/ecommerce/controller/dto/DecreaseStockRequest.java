package com.ecommerce.controller.dto;

import jakarta.validation.constraints.*;
import lombok.*;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class DecreaseStockRequest {

    @NotBlank(message = "quantity is required")
    Integer quantity;

}