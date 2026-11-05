package io.github.davidsoles.springanalyzer;

import java.util.List;

/**
 * Complete result of a scan.
 *
 * @param endpoints    endpoints found, sorted by path and HTTP verb
 * @param warnings     non-fatal problems (unparsable files, unresolved constants, etc.)
 * @param filesScanned number of Java files parsed successfully
 */
public record ScanResult(
        List<Endpoint> endpoints,
        List<String> warnings,
        int filesScanned) {

    public ScanResult {
        endpoints = List.copyOf(endpoints);
        warnings = List.copyOf(warnings);
    }

    public int controllerCount() {
        return (int) endpoints.stream()
                .map(Endpoint::controllerFqn)
                .distinct()
                .count();
    }
}
