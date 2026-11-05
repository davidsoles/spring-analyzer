package com.example.events;

public class OrderShippedEvent {

    private final Long orderId;

    public OrderShippedEvent(Long orderId) {
        this.orderId = orderId;
    }

    public Long getOrderId() {
        return orderId;
    }
}
