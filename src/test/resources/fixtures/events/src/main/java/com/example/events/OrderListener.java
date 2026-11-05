package com.example.events;

import org.springframework.context.event.EventListener;
import org.springframework.transaction.event.TransactionalEventListener;

public class OrderListener {

    @EventListener
    public void onCreated(OrderCreatedEvent event) {
    }

    @EventListener(classes = {OrderShippedEvent.class})
    public void onShipped() {
    }

    @TransactionalEventListener
    public void afterCommit(OrderCreatedEvent event) {
    }

    @OnOrderCreated
    public void onCustom(OrderCreatedEvent event) {
    }

    @EventListener(classes = {OrderCreatedEvent.class, OrderShippedEvent.class})
    public void onMultiple(OrderCreatedEvent event) {
    }
}
