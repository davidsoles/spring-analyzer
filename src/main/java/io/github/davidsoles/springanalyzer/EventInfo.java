package io.github.davidsoles.springanalyzer;

import java.util.List;

/**
 * An event type found in the application, with its publishers and listeners.
 *
 * @param eventType       fully qualified event type when resolvable, otherwise the simple name
 * @param eventSimpleName simple event type name
 * @param declaredIn      type where the event class is declared, if found in the repository
 * @param file            source file of the event class, if found
 * @param line            line of the event class declaration, or -1
 * @param publishers      places where the event is published
 * @param listeners       methods that listen for the event
 */
public record EventInfo(
        String eventType,
        String eventSimpleName,
        String declaredIn,
        String file,
        int line,
        List<EventPublisherInfo> publishers,
        List<EventListenerInfo> listeners) {

    public EventInfo {
        publishers = List.copyOf(publishers);
        listeners = List.copyOf(listeners);
    }

    public int publisherCount() {
        return publishers.size();
    }

    public int listenerCount() {
        return listeners.size();
    }

    public boolean isOrphan() {
        return publishers.isEmpty() || listeners.isEmpty();
    }
}
