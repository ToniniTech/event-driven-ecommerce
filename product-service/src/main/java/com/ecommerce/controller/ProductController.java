package com.ecommerce.controller;

import com.ecommerce.controller.dto.*;
import com.ecommerce.domain.Product;
import com.ecommerce.service.ProductService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Pageable;
import org.springframework.data.web.PageableDefault;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.net.URI;

/**
 * Synchronous REST catalog API. Consumed primarily by order-service to resolve
 * price and availability in real time. All responses are DTOs; the JPA entity is
 * never exposed.
 */
@RestController
@RequestMapping("/api/products")
@RequiredArgsConstructor
@Slf4j
public class ProductController {

    private final ProductService productService;

    /** Create a product. 201 Created with a Location header; 409 if productId exists. */
    @PostMapping
    public ResponseEntity<ProductResponse> createProduct(@Valid @RequestBody CreateProductRequest request) {
        ProductResponse created = productService.createProduct(request);
        URI location = URI.create("/api/products/" + created.productId());
        return ResponseEntity.created(location).body(created);
    }

    /** Paginated listing with an optional active filter (?active=true|false). */
    @GetMapping
    public ResponseEntity<PagedResponse<ProductResponse>> listProducts(
            @RequestParam(required = false) Boolean active,
            @PageableDefault(size = 20, sort = "productId") Pageable pageable) {
        return ResponseEntity.ok(PagedResponse.from(productService.listProducts(active, pageable)));
    }

    /** Resolve a single product by business key. 404 if not found. */
    @GetMapping("/{productId}")
    public ResponseEntity<ProductResponse> getProduct(@PathVariable String productId) {
        log.info("[PRODUCT-SERVICE] Resolving product | productId={}", productId);
        return ResponseEntity.ok(productService.getProduct(productId));
    }

    /** Full update. The path productId is authoritative and immutable. 404 if not found. */
    @PutMapping("/{productId}")
    public ResponseEntity<ProductResponse> updateProduct(
            @PathVariable String productId,
            @Valid @RequestBody UpdateProductRequest request) {
        return ResponseEntity.ok(productService.updateProduct(productId, request));
    }

    /**
     * Relative stock adjustment. PATCH because it modifies a single aspect of the
     * resource. 409 if the resulting stock would be negative.
     */
    @PatchMapping("/{productId}/increaseStock")
    public ResponseEntity<ProductResponse> increaseStock(
            @PathVariable String productId,
            @Valid @RequestBody Integer quantity) {
        return ResponseEntity.ok(productService.increaseStock(productId, quantity));
    }

    @PatchMapping("/{productId}/decreaseStock")
    public ResponseEntity<ProductResponse> decreaseStock(
            @PathVariable String productId,
            @Valid @RequestBody DecreaseStockRequest request) {
        return ResponseEntity.ok(productService.decreaseStock(productId, request.quantity()));
    }

    /** Soft delete (active = false). 204 No Content; 404 if not found. */
    @DeleteMapping("/{productId}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void deleteProduct(@PathVariable String productId) {
        productService.softDelete(productId);
    }

}


