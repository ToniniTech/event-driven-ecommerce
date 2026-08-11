package com.ecommerce;

import com.ecommerce.client.ProductInventoryClient;
import com.ecommerce.client.ProductResponse;
import org.junit.jupiter.api.BeforeEach;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.containers.MySQLContainer;
import org.testcontainers.containers.RabbitMQContainer;

import java.math.BigDecimal;

import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.lenient;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
public abstract class IntegrationTestBase {

    @MockBean
    protected ProductInventoryClient productInventoryClient;

    static final MySQLContainer<?> mysql = new MySQLContainer<>("mysql:8.0")
            .withDatabaseName("order_db_test")
            .withUsername("testuser")
            .withPassword("testpass");

    static final RabbitMQContainer rabbitmq =
            new RabbitMQContainer("rabbitmq:3.12-management");

    static {
        mysql.start();
        rabbitmq.start();   // arrancan una vez por JVM, nunca se cierran
    }

    @BeforeEach
    void stubInventory() {
        lenient().when(productInventoryClient.decreaseStock(anyString(), anyInt()))
                .thenAnswer(inv -> new ProductResponse(
                        inv.getArgument(0), "Teclado Mecanico", new BigDecimal("129.99"), 10, true));
    }

    @DynamicPropertySource
    static void overrideProperties(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url",      mysql::getJdbcUrl);
        registry.add("spring.datasource.username", mysql::getUsername);
        registry.add("spring.datasource.password", mysql::getPassword);

        registry.add("spring.rabbitmq.host",       rabbitmq::getHost);
        registry.add("spring.rabbitmq.port",       rabbitmq::getAmqpPort);
        registry.add("spring.rabbitmq.username",   () -> "guest");
        registry.add("spring.rabbitmq.password",   () -> "guest");

        registry.add("jwt.secret",
                () -> "3d8f2c1a9b4e7f0d5c2a8b3e6f1d4c7a0b5e8f2d1c4a7b0e3f6d9c2a5b8e1f4");
    }
}
