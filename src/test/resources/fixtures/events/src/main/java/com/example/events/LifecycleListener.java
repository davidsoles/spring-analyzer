package com.example.events;

import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;

public class LifecycleListener {

    @EventListener(ApplicationReadyEvent.class)
    public void onReady() {
    }

    @EventListener(ExternalAuditEvent.class)
    public void onExternal() {
    }
}
