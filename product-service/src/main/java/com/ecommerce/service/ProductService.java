package com.ecommerce.service;

import com.ecommerce.controller.dto.CreateProductRequest;
import com.ecommerce.controller.dto.ProductResponse;
import com.ecommerce.controller.dto.UpdateProductRequest;
import com.ecommerce.domain.Product;
import com.ecommerce.domain.ProductRepository;
import com.ecommerce.exception.DuplicateProductException;
import com.ecommerce.exception.InsufficientStockException;
import com.ecommerce.exception.ProductNotFoundException;
import com.ecommerce.mapper.ProductMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@Slf4j
@RequiredArgsConstructor
public class ProductService {

    private final ProductRepository productRepository;
    private final ProductMapper productMapper;

    /** Resolve a single product by business key. */
    @Transactional(readOnly = true)
    public ProductResponse getProduct(String productId) {
        log.debug("[PRODUCT-SERVICE] Fetching product | productId={}", productId);
        Product product = productRepository.findByProductId(productId)
                .orElseThrow(() -> new ProductNotFoundException(productId));
        return productMapper.toResponse(product);
    }

    /**
     * Paginated listing with an optional active filter.
     * When {@code active} is null, all products are returned.
     */
    @Transactional(readOnly = true)
    public Page<ProductResponse> listProducts(Boolean active, Pageable pageable) {
        Page<Product> page = (active == null)
                ? productRepository.findAll(pageable)
                : productRepository.findByActive(active, pageable);
        return page.map(productMapper::toResponse);
    }

    /** Create a new product. Fails with 409 if the business key already exists. */
    @Transactional
    public ProductResponse createProduct(CreateProductRequest request) {
        if (productRepository.existsByProductId(request.productId())) {
            throw new DuplicateProductException(request.productId());
        }
        Product saved = productRepository.save(productMapper.toEntity(request));
        log.info("[PRODUCT-SERVICE] Product created | productId={}", saved.getProductId());
        return productMapper.toResponse(saved);
    }

    /**
     * Full update of a product. productId cannot be changed (it is not part of the
     * request DTO); stock is not modified here (managed only via adjustStock).
     * Changes are flushed by JPA dirty checking, no explicit save needed.
     */
    @Transactional
    public ProductResponse updateProduct(String productId, UpdateProductRequest request) {
        Product product = productRepository.findByProductId(productId)
                .orElseThrow(() -> new ProductNotFoundException(productId));
        productMapper.applyUpdate(product, request);
        log.info("[PRODUCT-SERVICE] Product updated | productId={}", productId);
        return productMapper.toResponse(product);
    }

    /** Increase the stock from the database. Fails with 404 NOT_FOUND if the product is no found*/
    @Transactional
    public ProductResponse increaseStock(String productId, int quantity){
        Product product = productRepository.findByProductId(productId)
                .orElseThrow(()-> new ProductNotFoundException(productId));

        product.increaseStock(quantity);

        log.info("[PRODUCT-SERVICE] Product updated | productId={}", productId);
        return productMapper.toResponse(product);

    }
    /** Decrease the stock from the database. Fails with 404 NOT_FOUND if the product is no found*/
    @Transactional
    public ProductResponse decreaseStock(String productId, int quantity){
       Product product = productRepository.findByProductId(productId)
               .orElseThrow(()-> new ProductNotFoundException(productId));

       product.decreaseStock(quantity);

       log.info("[PRODUCT-SERVICE] Product updated | productId={}", productId);
       return  productMapper.toResponse(product);

    }

    /**
     * Soft delete: mark the product inactive so referential integrity with existing
     * orders is preserved. Idempotent: deleting an already-inactive product is a no-op.
     */
    @Transactional
    public void softDelete(String productId) {
        Product product = productRepository.findByProductId(productId)
                .orElseThrow(() -> new ProductNotFoundException(productId));
        if (product.isActive()) {
            product.deactivate();
            log.info("[PRODUCT-SERVICE] Product soft-deleted | productId={}", productId);
        }
    }


}
