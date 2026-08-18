package com.ecommerce.service;

import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.MeterRegistry;
import org.springframework.stereotype.Component;

@Component
public class OrderMetrics {
    private final Counter ordersCreatedCounter;

    public OrderMetrics(MeterRegistry registry) {
        this.ordersCreatedCounter = Counter.builder("orders.created")
                .description("Total number of successfully created orders")
                .register(registry);
    }

    public void orderCreated() {
        ordersCreatedCounter.increment();
    }
}
