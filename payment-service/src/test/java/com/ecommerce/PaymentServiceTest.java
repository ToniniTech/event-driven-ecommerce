package com.ecommerce;

import com.ecommerce.domain.Payment;
import com.ecommerce.domain.PaymentRepository;
import com.ecommerce.domain.PaymentStatus;
import com.ecommerce.exception.PaymentAlreadyExistsException;
import com.ecommerce.messaging.PaymentEventPublisher;
import com.ecommerce.messaging.events.OrderCreatedEvent;
import com.ecommerce.messaging.events.PaymentFailedEvent;
import com.ecommerce.messaging.events.PaymentProcessedEvent;
import com.ecommerce.service.PaymentGatewaySimulator;
import com.ecommerce.service.PaymentMetrics;
import com.ecommerce.service.PaymentService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.util.UUID;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
@DisplayName("PaymentService Unit Tests")
class PaymentServiceTest {

    @Mock
    private PaymentRepository paymentRepository;
    @Mock
    private PaymentGatewaySimulator gatewaySimulator;
    @Mock
    private PaymentEventPublisher eventPublisher;

    @InjectMocks
    private PaymentService paymentService;
    @Mock
    private PaymentMetrics paymentMetrics;

    private OrderCreatedEvent orderCreatedEvent;

    @BeforeEach
    void setUp() {
        orderCreatedEvent = OrderCreatedEvent.builder()
                .eventId(UUID.randomUUID().toString())
                .orderId("ord-test-001")
                .customerId("cust-001")
                .customerEmail("test@example.com")
                .totalAmount(new BigDecimal("149.99"))
                .currency("USD")
                .build();
    }

    @Test
    @DisplayName("should publish PaymentProcessed when gateway approves")
    void shouldPublishPaymentProcessedOnApproval() {
        // Arrange
        when(paymentRepository.existsByOrderId("ord-test-001")).thenReturn(false);
        when(paymentRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));
        when(gatewaySimulator.charge(any(), any(), any()))
                .thenReturn(PaymentGatewaySimulator.GatewayResult.success("gw-txn-abc123"));

        // Act
        paymentService.processPayment(orderCreatedEvent);

        // Assert
        ArgumentCaptor<PaymentProcessedEvent> captor =
                ArgumentCaptor.forClass(PaymentProcessedEvent.class);
        verify(eventPublisher).publishPaymentProcessed(captor.capture());
        verify(eventPublisher, never()).publishPaymentFailed(any());

        PaymentProcessedEvent published = captor.getValue();
        assertThat(published.getOrderId()).isEqualTo("ord-test-001");
        assertThat(published.getGatewayTransactionId()).isEqualTo("gw-txn-abc123");
        assertThat(published.getAmount()).isEqualByComparingTo(new BigDecimal("149.99"));
    }

    @Test
    @DisplayName("Should public payment on declined")
    void shouldPublishPaymentFailedOnDecline() {
        //Arrange
        when(paymentRepository.existsByOrderId("ord-test-001")).thenReturn(false);
        when(paymentRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));
        when(gatewaySimulator.charge(any(), any(), any())).thenReturn(PaymentGatewaySimulator.GatewayResult.failure("INSUFFICIENT_FUNDS", "Payment declined by issuing bank"));

        //Act
        paymentService.processPayment(orderCreatedEvent);

        //Verify

        ArgumentCaptor<PaymentFailedEvent> captor = ArgumentCaptor
                .forClass(PaymentFailedEvent.class);
        verify(eventPublisher).publishPaymentFailed(captor.capture());
        verify(eventPublisher, never()).publishPaymentProcessed(any());

        PaymentFailedEvent published = captor.getValue();
        assertThat(published.getOrderId()).isEqualTo("ord-test-001");
        assertThat(published.getFailureCode()).isEqualTo("INSUFFICIENT_FUNDS");
        assertThat(published.getFailureReason()).isEqualTo("Payment declined by issuing bank");
    }

    @Test
    @DisplayName("should throw PaymentAlreadyExistsException on duplicate orderId")
    void shouldRejectDuplicateOrderPayment() {
        // Arrange
        when(paymentRepository.existsByOrderId("ord-test-001")).thenReturn(true);

        // Act & Assert
        assertThatThrownBy(() -> paymentService.processPayment(orderCreatedEvent))
                .isInstanceOf(PaymentAlreadyExistsException.class)
                .hasMessageContaining("ord-test-001");


        verify(gatewaySimulator, never()).charge(any(), any(), any());
        verify(eventPublisher, never()).publishPaymentProcessed(any());
        verify(eventPublisher, never()).publishPaymentFailed(any());
    }

    @Test
    @DisplayName("should persist payment with COMPLETED status on approval")
    void shouldPersistCompletedPaymentOnApproval() {
        // Arrange
        when(paymentRepository.existsByOrderId(any())).thenReturn(false);
        when(paymentRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));
        when(gatewaySimulator.charge(any(), any(), any()))
                .thenReturn(PaymentGatewaySimulator.GatewayResult.success("gw-txn-xyz"));

        ArgumentCaptor<Payment> paymentCaptor = ArgumentCaptor.forClass(Payment.class);

        // Act
        paymentService.processPayment(orderCreatedEvent);

        // Assert — capture last save (after status update)
        verify(paymentRepository, times(2)).save(paymentCaptor.capture());
        Payment finalPayment = paymentCaptor.getAllValues().get(1);
        assertThat(finalPayment.getStatus()).isEqualTo(PaymentStatus.COMPLETED);
        assertThat(finalPayment.getGatewayTransactionId()).isEqualTo("gw-txn-xyz");
    }

    @Test
    @DisplayName("Should reject duplicated orders")
    void shouldRejectDuplicatedOrders() {
        //Arrange
        when(paymentRepository.existsByOrderId("ord-test-001")).thenReturn(true);

        //Act & Assert
        assertThatThrownBy(() -> paymentService.processPayment(orderCreatedEvent))
                .isInstanceOf(PaymentAlreadyExistsException.class)
                .hasMessageContaining("ord-test-001");

        //Verify
        verify(eventPublisher, never()).publishPaymentFailed(any());
        verify(eventPublisher, never()).publishPaymentProcessed(any());
        verify(gatewaySimulator, never()).charge(any(), any(), any());
    }


    @Test
    @DisplayName("should publish PaymentProcessed when gateway approves")
    void shouldPublishPaymentProcessedOnApproval2(){
        //Arrange
        when(paymentRepository.existsByOrderId("ord-test-001")).thenReturn(false);
        when(paymentRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));
        when(gatewaySimulator.charge(any(), any(), any())).thenReturn(PaymentGatewaySimulator.GatewayResult.success("as123"));

        //Act
        paymentService.processPayment(orderCreatedEvent);

        //Assert
        ArgumentCaptor<PaymentProcessedEvent> captor = ArgumentCaptor
                .forClass(PaymentProcessedEvent.class);
        verify(eventPublisher).publishPaymentProcessed(captor.capture());
        verify(eventPublisher, never()).publishPaymentFailed(any());

        PaymentProcessedEvent published = captor.getValue();
        assertThat(published.getOrderId()).isEqualTo("ord-test-001");
        assertThat(published.getGatewayTransactionId()).isEqualTo("as123");
        assertThat(published.getAmount()).isEqualTo("149.99");
    }

}