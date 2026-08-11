package com.ecommerce.config;

import com.ecommerce.domain.Product;
import com.ecommerce.domain.ProductRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.boot.DefaultApplicationArguments;
import org.springframework.core.io.ByteArrayResource;
import org.springframework.core.io.ResourceLoader;
import org.springframework.test.util.ReflectionTestUtils;

import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class CatalogCsvLoaderTest {

    private final Map<String, Product> persistedProducts = new LinkedHashMap<>();
    private ProductRepository productRepository;
    private ResourceLoader resourceLoader;
    private CatalogCsvLoader loader;

    @BeforeEach
    void setUp() {
        productRepository = mock(ProductRepository.class);
        resourceLoader = mock(ResourceLoader.class);
        loader = new CatalogCsvLoader(productRepository, resourceLoader);
        ReflectionTestUtils.setField(loader, "csvLocation", "test:catalog.csv");

        when(productRepository.findByProductId(any())).thenAnswer(invocation ->
                Optional.ofNullable(persistedProducts.get(invocation.getArgument(0))));
        when(productRepository.save(any(Product.class))).thenAnswer(invocation -> {
            Product product = invocation.getArgument(0);
            persistedProducts.put(product.getProductId(), product);
            return product;
        });
    }

    @Test
    void emptyDatabaseLoadsCsvAndSecondRunDoesNotCreateDuplicates() {
        useCsv("""
                productId,name,price,stock,active
                P0001,Cebolla 1kg,1.13,7,true
                P0002,Chocolate 100g,2.20,63,false
                """);

        runLoader();
        runLoader();

        assertThat(persistedProducts).hasSize(2);
        assertThat(persistedProducts.get("P0001").getStock()).isEqualTo(7);
        assertThat(persistedProducts.get("P0002").isActive()).isFalse();
    }

    @Test
    void existingProductKeepsEveryPersistedField() {
        Product existing = Product.create("P0001", "Database name", new BigDecimal("9.99"), 7, true);
        existing.decreaseStock(2);
        existing.updateDetails("Changed in database", new BigDecimal("8.75"), false);
        persistedProducts.put(existing.getProductId(), existing);
        useCsv("""
                productId,name,price,stock,active
                P0001,CSV product name,1.13,100,true
                """);

        runLoader();

        Product persisted = persistedProducts.get("P0001");
        assertThat(persisted).isSameAs(existing);
        assertThat(persisted.getName()).isEqualTo("Changed in database");
        assertThat(persisted.getPrice()).isEqualByComparingTo("8.75");
        assertThat(persisted.getStock()).isEqualTo(5);
        assertThat(persisted.isActive()).isFalse();
    }

    @Test
    void productAddedToCsvIsInsertedOnLaterRun() {
        useCsv("""
                productId,name,price,stock,active
                P0001,Cebolla 1kg,1.13,7,true
                """);
        runLoader();

        useCsv("""
                productId,name,price,stock,active
                P0001,Cebolla 1kg,1.13,7,true
                P0002,Chocolate 100g,2.20,63,true
                """);
        runLoader();

        assertThat(persistedProducts).containsOnlyKeys("P0001", "P0002");
    }

    @Test
    void negativeStockRowIsSkipped() {
        useCsv("""
                productId,name,price,stock,active
                P0001,Cebolla 1kg,1.13,-1,true
                """);

        runLoader();

        assertThat(persistedProducts).isEmpty();
    }

    @Test
    void invalidActiveRowIsSkipped() {
        useCsv("""
                productId,name,price,stock,active
                P0001,Cebolla 1kg,1.13,7,enabled
                """);

        runLoader();

        assertThat(persistedProducts).isEmpty();
    }

    private void useCsv(String csv) {
        ByteArrayResource resource = new ByteArrayResource(csv.getBytes(StandardCharsets.UTF_8));
        when(resourceLoader.getResource("test:catalog.csv")).thenReturn(resource);
    }

    private void runLoader() {
        loader.run(new DefaultApplicationArguments(new String[0]));
    }
}
