package com.ecommerce.domain;

import com.ecommerce.exception.InsufficientStockException;
import com.ecommerce.exception.InvalidQuantityException;
import jakarta.persistence.*;
import lombok.*;

import java.math.BigDecimal;

/**
 * Product catalog entity.
 * </ul>
 * <p>Products are never physically deleted (soft delete via {@code active = false}) so
 * that referential integrity with existing orders is preserved.
 */
@Entity
@Table(
        name = "products",
        // Enforce the business-key uniqueness at the schema level as well, not only
        // through the column definition, to make the invariant explicit.
        uniqueConstraints = @UniqueConstraint(name = "uk_products_product_id", columnNames = "product_id")
)
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)

public class Product {

    /** Internal technical PK. Database-generated; the CSV "id" column is ignored. */
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    /** Unique, immutable business key used by all public endpoints. */
    @Column(name = "product_id", nullable = false, updatable = false, length = 64)
    private String productId;

    /** Human-readable name (3-150 chars, validated on the DTO layer). */
    @Column(nullable = false, length = 150)
    private String name;

    /** Monetary price. BigDecimal (never double) to avoid rounding errors. Must be > 0. */
    @Column(nullable = false, precision = 12, scale = 2)
    private BigDecimal price;

    /** Available stock. Never negative, not even transiently. */
    @Column(nullable = false)
    private Integer stock;

    /** Visibility / purchasability flag. Soft-delete marker; defaults to true on creation. */
    @Column(name = "is_active", nullable = false)
    private boolean active;

    /**
     * Optimistic-locking version.
     *
     * <p>Even though this service is synchronous, order-service can issue concurrent
     * stock adjustments against the same product. @Version makes Hibernate detect
     * lost updates and fail the losing transaction with an optimistic-lock exception
     * (mapped to HTTP 409 by the global exception handler) instead of silently
     * overwriting stock.
     */
    @Version
    @Column(nullable = false)
    private Long version;

    public Product(
            String productId,
            String name,
            BigDecimal price,
            int initialStock
    ) {
        this(productId, name, price, initialStock, true);
    }

    public static Product create(
            String productId,
            String name,
            BigDecimal price,
            int initialStock,
            boolean active
    ) {
        return new Product(productId, name, price, initialStock, active);
    }

    private Product(
            String productId,
            String name,
            BigDecimal price,
            int initialStock,
            boolean active
    ) {
        if (productId == null || productId.isBlank()) {
            throw new IllegalArgumentException("Product ID is required");
        }
        if (productId.length() > 64) {
            throw new IllegalArgumentException("Product ID must be at most 64 characters");
        }
        if (name == null || name.length() < 3 || name.length() > 150) {
            throw new IllegalArgumentException("Name must be between 3 and 150 characters");
        }
        if (price == null || price.signum() <= 0) {
            throw new IllegalArgumentException("Price must be greater than zero");
        }
        if (initialStock < 0) {
            throw new IllegalArgumentException("Initial stock cannot be negative");
        }

        this.productId = productId;
        this.name = name;
        this.price = price;
        this.stock = initialStock;
        this.active = active;
    }

    public void decreaseStock(int requestedQuantity){
        if(requestedQuantity<= 0){
            throw new InvalidQuantityException(requestedQuantity);
        }
        if(stock < requestedQuantity){
            throw new InsufficientStockException(productId, stock, requestedQuantity);
        }

        this.stock -= requestedQuantity;
    }

    public void increaseStock(int quantity){
        if (quantity <= 0){
            throw new InvalidQuantityException(quantity);
        }
        this.stock += quantity;
    }

    public void updateDetails(String newName, BigDecimal newPrice, boolean active) {
        if (newName == null || newName.length() < 3 || newName.length() > 150) {
            throw new IllegalArgumentException("Name must be between 3 and 150 characters");
        }
        if (newPrice == null || newPrice.signum() <= 0){
            throw new IllegalArgumentException("Price must be positive");
        }
        this.name = newName;
        this.price = newPrice;
        this.active = active;
    }

    public void deactivate(){
        this.active = false;
    }

}
