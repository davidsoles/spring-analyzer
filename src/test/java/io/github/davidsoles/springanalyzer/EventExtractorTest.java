package io.github.davidsoles.springanalyzer;

import io.github.davidsoles.springanalyzer.common.OutputFormat;
import io.github.davidsoles.springanalyzer.common.Renderer;
import io.github.davidsoles.springanalyzer.common.ScanOptions;
import io.github.davidsoles.springanalyzer.common.ScanResult;
import io.github.davidsoles.springanalyzer.endpoints.EndpointScanner;
import io.github.davidsoles.springanalyzer.events.EventInfo;
import io.github.davidsoles.springanalyzer.events.EventListenerInfo;
import io.github.davidsoles.springanalyzer.events.EventPublisherInfo;

import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class EventExtractorTest {

    private final Path root = Path.of("src/test/resources/fixtures/events")
            .toAbsolutePath()
            .normalize();

    @Test
    void extractsEventsPublishersAndListeners() throws IOException {
        ScanResult result = new EndpointScanner().scan(root, ScanOptions.defaults());

        assertEquals(3, result.events().size());
        assertEquals(4, result.publisherCount());
        assertEquals(6, result.listenerCount());

        EventInfo created = find(result, "OrderCreatedEvent");
        assertEquals("com.example.events.OrderCreatedEvent", created.eventType());
        assertEquals("src/main/java/com/example/events/OrderCreatedEvent.java", created.file());
        assertTrue(created.line() > 0);
        assertEquals(3, created.publisherCount());
        assertEquals(4, created.listenerCount());

        EventInfo shipped = find(result, "OrderShippedEvent");
        assertEquals(1, shipped.publisherCount());
        assertEquals(2, shipped.listenerCount());

        EventInfo inventory = find(result, "InventoryAdjustedEvent");
        assertEquals(0, inventory.publisherCount());
        assertEquals(0, inventory.listenerCount());
        assertEquals("com.example.events.InventoryAdjustedEvent", inventory.eventType());
    }

    @Test
    void resolvesPublisherReceiversAndEventTypes() throws IOException {
        ScanResult result = new EndpointScanner().scan(root, ScanOptions.defaults());
        EventInfo created = find(result, "OrderCreatedEvent");

        EventPublisherInfo create = findPublisher(created, "OrderService#create");
        assertEquals("com.example.events.OrderCreatedEvent",
                result.events().stream()
                        .filter(event -> event.publishers().contains(create))
                        .findFirst()
                        .orElseThrow()
                        .eventType());
        assertEquals("publisher", create.receiver());
        assertEquals("org.springframework.context.ApplicationEventPublisher", create.receiverType());
        assertEquals("high", create.confidence());
        assertEquals("new OrderCreatedEvent(1L)", create.expression());

        EventPublisherInfo factory = findPublisher(created, "OrderService#createFromFactory");
        assertEquals("high", factory.confidence());
        assertEquals("eventFactory.create(2L)", factory.expression());

        EventInfo shipped = find(result, "OrderShippedEvent");
        EventPublisherInfo unknown = findPublisher(shipped, "OrderService#notifyUnknown");
        assertEquals("name-only", unknown.confidence());
        assertEquals("com.example.events.UnknownPublisher", unknown.receiverType());
    }

    @Test
    void detectsEventListenerVariants() throws IOException {
        ScanResult result = new EndpointScanner().scan(root, ScanOptions.defaults());
        EventInfo created = find(result, "OrderCreatedEvent");

        assertTrue(hasListener(created, "OrderListener#onCreated"));
        assertTrue(hasListener(created, "OrderListener#afterCommit"));
        assertTrue(hasListener(created, "OrderListener#onCustom"));
        assertTrue(hasListener(created, "OrderListener#onMultiple"));

        EventInfo shipped = find(result, "OrderShippedEvent");
        assertTrue(hasListener(shipped, "OrderListener#onShipped"));
        assertTrue(hasListener(shipped, "OrderListener#onMultiple"));

        EventListenerInfo afterCommit = findListener(created, "OrderListener#afterCommit");
        assertEquals("TransactionalEventListener", afterCommit.annotation());

        EventListenerInfo custom = findListener(created, "OrderListener#onCustom");
        assertEquals("EventListener", custom.annotation());
    }

    @Test
    void includesEventsInJsonOutput() throws IOException {
        ScanResult result = new EndpointScanner().scan(root, ScanOptions.defaults());
        String json = Renderer.render(result, OutputFormat.JSON);

        assertTrue(json.contains("\"schemaVersion\": 2"));
        assertTrue(json.contains("\"events\": ["));
        assertTrue(json.contains("\"eventSimpleName\": \"OrderCreatedEvent\""));
        assertTrue(json.contains("\"publishers\": ["));
        assertTrue(json.contains("\"listeners\": ["));
    }

    @Test
    void rendersEventTreeTable() throws IOException {
        ScanResult result = new EndpointScanner().scan(root, ScanOptions.defaults());
        String table = Renderer.render(result, OutputFormat.TABLE, true);

        assertTrue(table.contains("EVENT"));
        assertTrue(table.contains("PUBLISH"));
        assertTrue(table.contains("LISTEN"));
        assertTrue(table.contains("OrderCreatedEvent"));
        assertTrue(table.contains("OrderService#create"));
        assertTrue(table.contains("OrderListener#onCreated"));
    }

    private static EventInfo find(ScanResult result, String simpleName) {
        return result.events().stream()
                .filter(event -> event.eventSimpleName().equals(simpleName))
                .findFirst()
                .orElseThrow(() -> new AssertionError("Event not found: " + simpleName));
    }

    private static EventPublisherInfo findPublisher(EventInfo event, String handler) {
        return event.publishers().stream()
                .filter(publisher -> publisher.handler().equals(handler))
                .findFirst()
                .orElseThrow(() -> new AssertionError("Publisher not found: " + handler));
    }

    private static EventListenerInfo findListener(EventInfo event, String handler) {
        return event.listeners().stream()
                .filter(listener -> listener.handler().equals(handler))
                .findFirst()
                .orElseThrow(() -> new AssertionError("Listener not found: " + handler));
    }

    private static boolean hasListener(EventInfo event, String handler) {
        return event.listeners().stream().anyMatch(listener -> listener.handler().equals(handler));
    }
}
