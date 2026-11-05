package com.example.events;

public class OrderEventFactory {

    public OrderCreatedEvent create(Long orderId) {
        return new OrderCreatedEvent(orderId);
    }
}
