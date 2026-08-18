package com.ecommerce.service;

import com.ecommerce.domain.Payment;
import com.ecommerce.domain.PaymentRepository;
import com.ecommerce.domain.PaymentStatus;
import com.ecommerce.exception.PaymentAlreadyExistsException;
import com.ecommerce.messaging.PaymentEventPublisher;
import com.ecommerce.messaging.events.OrderCreatedEvent;
import com.ecommerce.messaging.events.PaymentFailedEvent;
import com.ecommerce.messaging.events.PaymentProcessedEvent;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;

/**
 * Core payment processing logic.
 *
 * Flow:
 * 1. Validate no duplicate payment for same orderId
 * 2. Persist payment record with PROCESSING status
 * 3. Call payment gateway simulator
 * 4. Update payment status based on gateway response
 * 5. Publish PaymentProcessed or PaymentFailed event
 *
 * @Transactional ensures DB write + event publish are atomic.
 * If publishing fails after DB write, the transaction rolls back —
 * the consumer will retry the original OrderCreated event and
 * attempt the payment again (idempotency table protects against duplication).
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class PaymentService {

    private final PaymentRepository paymentRepository;
    private final PaymentGatewaySimulator gatewaySimulator;
    private final PaymentEventPublisher eventPublisher;
    private final PaymentMetrics paymentMetrics;

    @Transactional
    public void processPayment(OrderCreatedEvent event) {
        log.info("[PAYMENT-SERVICE] Starting payment processing | orderId={} | amount={} {}",
                event.getOrderId(), event.getTotalAmount(), event.getCurrency());

        // ─── Guard against duplicate processing at domain level ───────────────
        if (paymentRepository.existsByOrderId(event.getOrderId())) {
            log.warn("[PAYMENT-SERVICE] Payment already exists for orderId={}, skipping",
                    event.getOrderId());
            throw new PaymentAlreadyExistsException(
                    "Payment already processed for order: " + event.getOrderId());
        }

        // ─── Create payment record ────────────────────────────────────────────
        Payment payment = Payment.builder()
                .orderId(event.getOrderId())
                .customerId(event.getCustomerId())
                .customerEmail(event.getCustomerEmail())
                .amount(event.getTotalAmount())
                .currency(event.getCurrency())
                .status(PaymentStatus.PROCESSING)
                .build();

        payment = paymentRepository.save(payment);
        log.info("[PAYMENT-SERVICE] Payment record created | paymentId={}", payment.getPaymentId());

        // ─── Call payment gateway ──────────────────────────────────────────────
        PaymentGatewaySimulator.GatewayResult result =
                gatewaySimulator.charge(event.getOrderId(), event.getTotalAmount(), event.getCurrency());

        // ─── Handle gateway response ───────────────────────────────────────────
        if (result.approved()) {
            handlePaymentApproved(payment, event, result);
        } else {
            handlePaymentDeclined(payment, event, result);
        }
    }

    private void handlePaymentApproved(Payment payment, OrderCreatedEvent event,
                                        PaymentGatewaySimulator.GatewayResult result) {
        log.info("[PAYMENT-SERVICE] Payment approved | paymentId={} | transactionId={}",
                payment.getPaymentId(), result.transactionId());

        payment.markAsCompleted(result.transactionId());
        paymentRepository.save(payment);

        paymentMetrics.paymentSucceeded();

        PaymentProcessedEvent processedEvent = PaymentProcessedEvent.builder()
                .paymentId(payment.getPaymentId())
                .orderId(payment.getOrderId())
                .customerId(payment.getCustomerId())
                .customerEmail(payment.getCustomerEmail())
                .amount(payment.getAmount())
                .currency(payment.getCurrency())
                .paymentMethod(payment.getPaymentMethod())
                .gatewayTransactionId(result.transactionId())
                .processedAt(LocalDateTime.now())
                .build();

        eventPublisher.publishPaymentProcessed(processedEvent);

    }

    private void handlePaymentDeclined(Payment payment, OrderCreatedEvent event,
                                        PaymentGatewaySimulator.GatewayResult result) {
        log.warn("[PAYMENT-SERVICE] Payment declined | paymentId={} | code={} | reason={}",
                payment.getPaymentId(), result.failureCode(), result.failureReason());

        payment.markAsFailed(result.failureReason(), result.failureCode());
        paymentRepository.save(payment);

        paymentMetrics.paymentFailed();

        PaymentFailedEvent failedEvent = PaymentFailedEvent.builder()
                .paymentId(payment.getPaymentId())
                .orderId(payment.getOrderId())
                .customerId(payment.getCustomerId())
                .customerEmail(payment.getCustomerEmail())
                .amount(payment.getAmount())
                .currency(payment.getCurrency())
                .failureReason(result.failureReason())
                .failureCode(result.failureCode())
                .failedAt(LocalDateTime.now())
                .build();

        eventPublisher.publishPaymentFailed(failedEvent);
    }

    @Transactional(readOnly = true)
    public Payment getPaymentByOrderId(String orderId) {
        return paymentRepository.findByOrderId(orderId)
                .orElseThrow(() -> new RuntimeException("Payment not found for orderId: " + orderId));
    }
}
