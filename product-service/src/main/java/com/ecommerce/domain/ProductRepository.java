package com.ecommerce.domain;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Optional;

@Repository
public interface ProductRepository extends JpaRepository<Product, Long> {

    /** Look up a product by its business key. */
    Optional<Product> findByProductId(String productId);

    /** Fast existence check used by product creation duplicate detection. */
    boolean existsByProductId(String productId);

    /**
     * Paginated listing filtered by the active flag.
     * The unfiltered listing uses the inherited {@link JpaRepository#findAll(Pageable)}.
     */
    Page<Product> findByActive(boolean active, Pageable pageable);

}
