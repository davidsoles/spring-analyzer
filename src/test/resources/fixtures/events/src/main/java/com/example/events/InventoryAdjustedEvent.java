package com.example.events;

import org.springframework.context.ApplicationEvent;

public class InventoryAdjustedEvent extends ApplicationEvent {

    public InventoryAdjustedEvent(Object source) {
        super(source);
    }
}
