package com.example.events;

import org.springframework.context.ApplicationEventPublisher;

public class OrderService {

    private final ApplicationEventPublisher publisher;
    private final UnknownPublisher unknown;
    private final OrderEventFactory eventFactory;

    public OrderService(
            ApplicationEventPublisher publisher,
            UnknownPublisher unknown,
            OrderEventFactory eventFactory) {
        this.publisher = publisher;
        this.unknown = unknown;
        this.eventFactory = eventFactory;
    }

    public void create() {
        publisher.publishEvent(new OrderCreatedEvent(1L));
    }

    public void ship(OrderCreatedEvent event) {
        publisher.publishEvent(event);
    }

    public void createFromFactory() {
        publisher.publishEvent(eventFactory.create(2L));
    }

    public void notifyUnknown(OrderShippedEvent event) {
        unknown.publishEvent(event);
    }
}
