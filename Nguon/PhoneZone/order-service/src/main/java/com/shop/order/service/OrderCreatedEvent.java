package com.shop.order.service;

import java.time.Instant;

/** Immutable integration event published after an order is accepted. */
public record OrderCreatedEvent(Long orderId, Long userId, Long total, String paymentMethod,
                                Instant occurredAt) {
}
