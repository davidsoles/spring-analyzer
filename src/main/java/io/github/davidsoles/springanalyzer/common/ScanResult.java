package io.github.davidsoles.springanalyzer.common;

import io.github.davidsoles.springanalyzer.endpoints.Endpoint;
import io.github.davidsoles.springanalyzer.events.EventInfo;

import java.util.List;

/**
 * Complete result of a scan.
 *
 * @param endpoints    endpoints found, sorted by path and HTTP verb
 * @param events       event types found, with publishers and listeners
 * @param warnings     non-fatal problems (unparsable files, unresolved constants, etc.)
 * @param filesScanned number of Java files parsed successfully
 */
public record ScanResult(
        List<Endpoint> endpoints,
        List<EventInfo> events,
        List<String> warnings,
        int filesScanned) {

    public ScanResult {
        endpoints = List.copyOf(endpoints);
        events = List.copyOf(events);
        warnings = List.copyOf(warnings);
    }

    public int controllerCount() {
        return (int) endpoints.stream()
                .map(Endpoint::controllerFqn)
                .distinct()
                .count();
    }

    public int eventCount() {
        return events.size();
    }

    public int publisherCount() {
        return events.stream().mapToInt(EventInfo::publisherCount).sum();
    }

    public int listenerCount() {
        return events.stream().mapToInt(EventInfo::listenerCount).sum();
    }
}
