package com.ecommerce.service;

import com.ecommerce.client.ProductInventoryClient;
import com.ecommerce.client.ProductResponse;
import com.ecommerce.controller.dto.CreateOrderRequest;
import com.ecommerce.controller.dto.OrderResponse;
import com.ecommerce.domain.*;
import com.ecommerce.exception.DuplicateOrderException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import org.mockito.Spy;
import com.ecommerce.messaging.events.PaymentFailedEvent;
import com.ecommerce.messaging.events.PaymentProcessedEvent;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.util.ReflectionTestUtils;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
@DisplayName("OrderService Unit Tests")
@ActiveProfiles("test")
class OrderServiceTest {

    @Mock private OrderRepository orderRepository;
    @Mock private OutboxEventRepository outboxEventRepository;
    @Mock private ProductInventoryClient productInventoryClient;
    @Spy private ObjectMapper objectMapper = new ObjectMapper().registerModule(new JavaTimeModule());
    @Mock private OrderMetrics orderMetrics;

    @InjectMocks private OrderService orderService;

    // ── Test data ──────────────────────────────────────────────────────────────

    private static final String CUSTOMER_ID = "cust-test-001";
    private static final String CUSTOMER_EMAIL = "test@example.com";
    private static final String IDEM_KEY = "idem-key-001";


    private CreateOrderRequest validRequest;

    @BeforeEach

    void setUp() {

        // @Value fields are not injected by Mockito, so set the store currency manually.
        ReflectionTestUtils.setField(orderService, "defaultCurrency", "CLP");

        // New request shape: only productId + quantity (no name, no price)
        validRequest = CreateOrderRequest.builder()
                .items(List.of(
                        CreateOrderRequest.OrderItemRequest.builder()
                                .productId("prod-001")
                                .quantity(2)
                                .build()
                ))
                .build();

    }

    // ── Tests ──────────────────────────────────────────────────────────────────

    @Test
    @DisplayName("should create order, decrease stock and publish OrderCreated event")
    void shouldCreateOrderAndPublishEvent() {
        // Arrange - dado este caso
        when(orderRepository.existsByIdempotencyKey(any())).thenReturn(false);
        when(productInventoryClient.decreaseStock("prod-001", 2))
                .thenReturn(new ProductResponse(
                        "prod-001", "Teclado Mecánico", new BigDecimal("129.99"), 8, true));
        when(orderRepository.save(any(Order.class))).thenAnswer(inv -> {
            Order o = inv.getArgument(0);
            o.setId(1L);
            return o;
        });

        // Act — note: customerId is now a separate parameter - cuando hago esto
        OrderResponse response = orderService.createOrder(CUSTOMER_ID, CUSTOMER_EMAIL, IDEM_KEY, validRequest);

        // Assert
        assertThat(response).isNotNull();
        assertThat(response.getCustomerId()).isEqualTo(CUSTOMER_ID);
        // 2 units × $129.99 = $259.98
        assertThat(response.getTotalAmount()).isEqualByComparingTo(new BigDecimal("259.98"));

        verify(outboxEventRepository, times(1)).save(any());
        // Save called twice: once PENDING, once PAYMENT_PROCESSING
        verify(orderRepository, times(2)).save(any(Order.class));
        verify(productInventoryClient).decreaseStock("prod-001", 2);
    }

    @Test
    @DisplayName("should autogenerate idempotencyKey when not provided by client")
    void shouldAutogenerateIdempotencyKey() {
        // Arrange — request without idempotencyKey
        CreateOrderRequest requestWithoutKey = CreateOrderRequest.builder()
                .items(List.of(
                        CreateOrderRequest.OrderItemRequest.builder()
                                .productId("prod-001")
                                .quantity(1)
                                .build()
                ))
                .build();

        when(orderRepository.existsByIdempotencyKey(any())).thenReturn(false);
        when(productInventoryClient.decreaseStock("prod-001", 1))
                .thenReturn(new ProductResponse(
                        "prod-001", "Teclado Mecánico", new BigDecimal("129.99"), 9, true));
        when(orderRepository.save(any(Order.class))).thenAnswer(inv -> inv.getArgument(0));

        ArgumentCaptor<Order> captor = ArgumentCaptor.forClass(Order.class);

        // Act
        orderService.createOrder(CUSTOMER_ID, CUSTOMER_EMAIL,null, requestWithoutKey);

        // Assert — idempotencyKey must be set even though client didn't send it
        verify(orderRepository, atLeastOnce()).save(captor.capture());
        Order saved = captor.getAllValues().get(0);
        assertThat(saved.getIdempotencyKey()).isNotNull().isNotBlank();
    }

    @Test
    @DisplayName("should reject duplicate order with same idempotency key")
    void shouldRejectDuplicateIdempotencyKey() {
        // Arrange
        when(orderRepository.existsByIdempotencyKey("idem-key-001")).thenReturn(true);

        // Act & Assert /
        assertThatThrownBy(() -> orderService.createOrder(CUSTOMER_ID, CUSTOMER_EMAIL, IDEM_KEY, validRequest))
                .isInstanceOf(DuplicateOrderException.class)
                .hasMessageContaining("idem-key-001");

        // OrderService no longer publishes directly: it writes to the outbox.
        verify(outboxEventRepository, never()).save(any());
        verify(orderRepository, never()).save(any());
        verify(productInventoryClient, never()).decreaseStock(anyString(), anyInt());
    }

        @Test
        @DisplayName("should update order to PAID when PaymentProcessed received")
        void shouldMarkOrderAsPaidOnPaymentProcessed() {
            // Arrange
            Order order = Order.builder()
                    .orderId("order-123")
                    .status(OrderStatus.PAYMENT_PROCESSING)
                    .build();

            PaymentProcessedEvent event = PaymentProcessedEvent.builder()
                    .eventId(UUID.randomUUID().toString())
                    .orderId("order-123")
                    .paymentId("pay-abc")
                    .amount(new BigDecimal("259.98"))
                    .currency("USD")
                    .processedAt(LocalDateTime.now())
                    .build();

            when(orderRepository.findByOrderId("order-123")).thenReturn(Optional.of(order));
            when(orderRepository.save(any())).thenReturn(order);

            // Act
            orderService.handlePaymentProcessed(event);

            // Assert
            assertThat(order.getStatus()).isEqualTo(OrderStatus.PAID);
            assertThat(order.getPaymentId()).isEqualTo("pay-abc");
            verify(orderRepository).save(order);
            }

    @Test
    @DisplayName("should update order to PAYMENT_FAILED when PaymentFailed received")
    void shouldMarkOrderAsFailedOnPaymentFailed() {
        // Arrange
        Order order = Order.builder()
                .orderId("ord-456")
                .status(OrderStatus.PAYMENT_PROCESSING)
                .build();

        PaymentFailedEvent event = PaymentFailedEvent.builder()
                .eventId(UUID.randomUUID().toString())
                .orderId("ord-456")
                .failureReason("Insufficient funds")
                .failureCode("INSUFFICIENT_FUNDS")
                .failedAt(LocalDateTime.now())
                .build();

        when(orderRepository.findByOrderId("ord-456")).thenReturn(Optional.of(order));
        when(orderRepository.save(any())).thenReturn(order);

        // Act
        orderService.handlePaymentFailed(event);

        // Assert
        assertThat(order.getStatus()).isEqualTo(OrderStatus.PAYMENT_FAILED);
        assertThat(order.getFailureReason()).isEqualTo("Insufficient funds");
    }

    @Test
    @DisplayName("should resolve product name and price from inventory response, not from request")
    void shouldResolvePriceFromInventoryNotFromRequest() {
        // Arrange — client sends prod-002, product-service returns Mouse at $49.99
        when(productInventoryClient.decreaseStock("prod-002", 3))
                .thenReturn(new ProductResponse(
                        "prod-002", "Mouse Inalámbrico", new BigDecimal("49.99"), 7, true));

        CreateOrderRequest request = CreateOrderRequest.builder()
                .items(List.of(
                        CreateOrderRequest.OrderItemRequest.builder()
                                .productId("prod-002")
                                .quantity(3)
                                .build()
                ))
                .build();

        when(orderRepository.existsByIdempotencyKey(any())).thenReturn(false);
        when(orderRepository.save(any(Order.class))).thenAnswer(inv -> inv.getArgument(0));

        ArgumentCaptor<Order> captor = ArgumentCaptor.forClass(Order.class);

        // Act
        orderService.createOrder(CUSTOMER_ID, CUSTOMER_EMAIL, IDEM_KEY, request);

        // Assert — total = 3 × $49.99 = $149.97, name comes from product-service
        verify(orderRepository, atLeastOnce()).save(captor.capture());
        Order saved = captor.getAllValues().get(0);
        assertThat(saved.getTotalAmount()).isEqualByComparingTo(new BigDecimal("149.97"));
        assertThat(saved.getItems().get(0).getProductName()).isEqualTo("Mouse Inalámbrico");
        assertThat(saved.getItems().get(0).getUnitPrice()).isEqualByComparingTo(new BigDecimal("49.99"));
        verify(productInventoryClient).decreaseStock("prod-002", 3);
    }
}
