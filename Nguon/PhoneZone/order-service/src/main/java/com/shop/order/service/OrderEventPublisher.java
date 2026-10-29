package com.shop.order.service;

import com.shop.order.entity.Order;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.enterprise.event.Event;
import jakarta.enterprise.event.Observes;
import jakarta.enterprise.event.TransactionPhase;
import jakarta.inject.Inject;
import org.eclipse.microprofile.reactive.messaging.Channel;
import org.eclipse.microprofile.reactive.messaging.Emitter;

import java.time.Instant;

/** Outbound adapter: the domain service depends only on this application-facing publisher. */
@ApplicationScoped
public class OrderEventPublisher {
    @Inject
    @Channel("order-events")
    Emitter<OrderCreatedEvent> emitter;

    @Inject
    Event<OrderCreatedEvent> orderCreatedEvents;

    public void orderCreated(Order order) {
        orderCreatedEvents.fire(new OrderCreatedEvent(order.id, order.userId, order.total,
                order.paymentMethod, Instant.now()));
    }

    /** Kafka is an asynchronous integration. A broker outage must not roll
     * back an order that was already saved successfully. */
    void publishAfterCommit(@Observes(during = TransactionPhase.AFTER_SUCCESS) OrderCreatedEvent event) {
        emitter.send(event).whenComplete((ignored, failure) -> {
            if (failure != null) {
                org.jboss.logging.Logger.getLogger(OrderEventPublisher.class)
                        .warnf(failure, "Không gửi được sự kiện tạo đơn %s lên Kafka", event.orderId());
            }
        });
    }
}
