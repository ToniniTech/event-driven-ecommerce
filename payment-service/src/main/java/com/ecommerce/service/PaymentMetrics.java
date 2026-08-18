package com.ecommerce.service;

import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.MeterRegistry;
import org.springframework.stereotype.Component;

@Component
public class PaymentMetrics {
    private final Counter successfulPayments;
    private final Counter failedPayments;


    public PaymentMetrics(MeterRegistry registry){
        successfulPayments = Counter.builder("payments.processed")
                .tag("status", "successful")
                .description("Total processed payments")
                .register(registry);

        failedPayments = Counter.builder("payments.processed")
                .tag("status", "failed")
                .description("Total processed payments")
                .register(registry);
    }

    public void paymentSucceeded() {
        successfulPayments.increment();
    }

    public void paymentFailed() {
        failedPayments.increment();
    }
}
