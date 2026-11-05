package io.github.davidsoles.springanalyzer.common;

import io.github.davidsoles.springanalyzer.endpoints.Endpoint;
import io.github.davidsoles.springanalyzer.events.EventInfo;
import io.github.davidsoles.springanalyzer.events.EventListenerInfo;
import io.github.davidsoles.springanalyzer.events.EventPublisherInfo;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.stream.Collectors;

public final class Renderer {

    private Renderer() {
    }

    public static String render(ScanResult result, OutputFormat format) {
        return render(result, format, false);
    }

    public static String render(ScanResult result, OutputFormat format, boolean events) {
        return switch (format) {
            case TABLE -> events ? eventTable(result) : table(result);
            case JSON -> json(result);
            case CSV -> events ? eventCsv(result) : csv(result.endpoints());
        };
    }

    private static String table(ScanResult result) {
        List<Endpoint> endpoints = result.endpoints();

        int methodWidth = Math.max(6, endpoints.stream()
                .mapToInt(endpoint -> endpoint.httpMethod().length())
                .max()
                .orElse(6));
        int pathWidth = Math.min(70, Math.max(4, endpoints.stream()
                .mapToInt(endpoint -> endpoint.path().length())
                .max()
                .orElse(4)));
        int handlerWidth = Math.min(48, Math.max(7, endpoints.stream()
                .mapToInt(endpoint -> endpoint.handler().length())
                .max()
                .orElse(7)));
        int sourceWidth = Math.min(80, Math.max(6, endpoints.stream()
                .mapToInt(endpoint -> source(endpoint).length())
                .max()
                .orElse(6)));

        String rowFormat = "%-" + methodWidth + "s  %-" + pathWidth + "s  %-"
                + handlerWidth + "s  %-" + sourceWidth + "s%n";

        StringBuilder output = new StringBuilder();
        output.append(String.format(Locale.ROOT, rowFormat, "METHOD", "PATH", "HANDLER", "SOURCE"));
        output.append(String.format(Locale.ROOT, rowFormat,
                "-".repeat(methodWidth),
                "-".repeat(pathWidth),
                "-".repeat(handlerWidth),
                "-".repeat(sourceWidth)));

        for (Endpoint endpoint : endpoints) {
            output.append(String.format(Locale.ROOT, rowFormat,
                    truncate(endpoint.httpMethod(), methodWidth),
                    truncate(endpoint.path(), pathWidth),
                    truncate(endpoint.handler(), handlerWidth),
                    truncate(source(endpoint), sourceWidth)));
        }

        if (endpoints.isEmpty()) {
            output.append("(no endpoints found)\n");
        }

        output.append('\n')
                .append(endpoints.size()).append(" endpoints | ")
                .append(result.controllerCount()).append(" controllers | ")
                .append(result.eventCount()).append(" events | ")
                .append(result.filesScanned()).append(" Java files\n");

        if (!result.warnings().isEmpty()) {
            output.append(result.warnings().size())
                    .append(" warnings (use --verbose to view them)\n");
        }

        return output.toString();
    }

    private static String eventTable(ScanResult result) {
        List<EventRow> rows = eventRows(result);

        int eventWidth = Math.max(5, rows.stream()
                .mapToInt(row -> row.event().length())
                .max()
                .orElse(5));
        int roleWidth = Math.max(7, rows.stream()
                .mapToInt(row -> row.role().length())
                .max()
                .orElse(7));
        int handlerWidth = Math.min(52, Math.max(7, rows.stream()
                .mapToInt(row -> row.handler().length())
                .max()
                .orElse(7)));
        int sourceWidth = Math.min(80, Math.max(6, rows.stream()
                .mapToInt(row -> row.source().length())
                .max()
                .orElse(6)));

        String rowFormat = "%-" + eventWidth + "s  %-" + roleWidth + "s  %-"
                + handlerWidth + "s  %-" + sourceWidth + "s%n";

        StringBuilder output = new StringBuilder();
        output.append(String.format(Locale.ROOT, rowFormat, "EVENT", "ROLE", "HANDLER", "SOURCE"));
        output.append(String.format(Locale.ROOT, rowFormat,
                "-".repeat(eventWidth),
                "-".repeat(roleWidth),
                "-".repeat(handlerWidth),
                "-".repeat(sourceWidth)));

        for (EventRow row : rows) {
            output.append(String.format(Locale.ROOT, rowFormat,
                    truncate(row.event(), eventWidth),
                    truncate(row.role(), roleWidth),
                    truncate(row.handler(), handlerWidth),
                    truncate(row.source(), sourceWidth)));
        }

        if (rows.isEmpty()) {
            output.append("(no events found)\n");
        }

        output.append('\n')
                .append(result.eventCount()).append(" events | ")
                .append(result.publisherCount()).append(" publishers | ")
                .append(result.listenerCount()).append(" listeners | ")
                .append(result.filesScanned()).append(" Java files\n");

        if (!result.warnings().isEmpty()) {
            output.append(result.warnings().size())
                    .append(" warnings (use --verbose to view them)\n");
        }

        return output.toString();
    }

    private static List<EventRow> eventRows(ScanResult result) {
        List<EventRow> rows = new ArrayList<>();

        for (EventInfo event : result.events()) {
            String declaration = event.file().isEmpty()
                    ? ""
                    : event.file() + ":" + event.line();
            rows.add(new EventRow(
                    event.eventSimpleName(),
                    "EVENT",
                    publisherOriginLabel(event),
                    declaration));

            for (EventPublisherInfo publisher : event.publishers()) {
                rows.add(new EventRow(
                        event.eventSimpleName(),
                        "PUBLISH",
                        publisher.handler(),
                        publisher.file() + ":" + publisher.line()));
            }

            for (EventListenerInfo listener : event.listeners()) {
                rows.add(new EventRow(
                        event.eventSimpleName(),
                        "LISTEN",
                        listener.handler(),
                        listener.file() + ":" + listener.line()));
            }
        }

        return rows;
    }

    private static String publisherOriginLabel(EventInfo event) {
        return switch (event.publisherOrigin()) {
            case "framework" -> "Spring Boot lifecycle";
            case "external" -> "external publisher";
            case "none" -> "no publisher";
            default -> "";
        };
    }

    private static String source(Endpoint endpoint) {
        return endpoint.file() + ":" + endpoint.line();
    }

    private static String truncate(String value, int max) {
        if (value.length() <= max) {
            return value;
        }
        if (max <= 3) {
            return value.substring(0, max);
        }
        return value.substring(0, max - 3) + "...";
    }

    private static String json(ScanResult result) {
        StringBuilder output = new StringBuilder();
        output.append("{\n")
                .append("  \"schemaVersion\": 2,\n")
                .append("  \"scannerVersion\": ").append(jsonString(Version.VERSION)).append(",\n")
                .append("  \"endpoints\": [\n");

        List<Endpoint> endpoints = result.endpoints();
        for (int i = 0; i < endpoints.size(); i++) {
            Endpoint endpoint = endpoints.get(i);
            output.append("    {\n")
                    .append("      \"httpMethod\": ").append(jsonString(endpoint.httpMethod())).append(",\n")
                    .append("      \"path\": ").append(jsonString(endpoint.path())).append(",\n")
                    .append("      \"handler\": ").append(jsonString(endpoint.handler())).append(",\n")
                    .append("      \"controller\": ").append(jsonString(endpoint.controller())).append(",\n")
                    .append("      \"controllerFqn\": ").append(jsonString(endpoint.controllerFqn())).append(",\n")
                    .append("      \"methodName\": ").append(jsonString(endpoint.methodName())).append(",\n")
                    .append("      \"declaredIn\": ").append(jsonString(endpoint.declaredIn())).append(",\n")
                    .append("      \"file\": ").append(jsonString(endpoint.file())).append(",\n")
                    .append("      \"line\": ").append(endpoint.line()).append(",\n")
                    .append("      \"methodLine\": ").append(endpoint.methodLine()).append(",\n")
                    .append("      \"consumes\": ").append(jsonArray(endpoint.consumes())).append(",\n")
                    .append("      \"produces\": ").append(jsonArray(endpoint.produces())).append("\n")
                    .append("    }");

            if (i + 1 < endpoints.size()) {
                output.append(',');
            }
            output.append('\n');
        }

        output.append("  ],\n")
                .append("  \"events\": [\n");

        List<EventInfo> events = result.events();
        for (int i = 0; i < events.size(); i++) {
            EventInfo event = events.get(i);
            output.append("    {\n")
                    .append("      \"eventType\": ").append(jsonString(event.eventType())).append(",\n")
                    .append("      \"eventSimpleName\": ").append(jsonString(event.eventSimpleName())).append(",\n")
                    .append("      \"declaredIn\": ").append(jsonString(event.declaredIn())).append(",\n")
                    .append("      \"file\": ").append(jsonString(event.file())).append(",\n")
                    .append("      \"line\": ").append(event.line()).append(",\n")
                    .append("      \"publisherOrigin\": ").append(jsonString(event.publisherOrigin())).append(",\n")
                    .append("      \"publishers\": [\n");

            for (int j = 0; j < event.publishers().size(); j++) {
                EventPublisherInfo publisher = event.publishers().get(j);
                output.append("        {\n")
                        .append("          \"handler\": ").append(jsonString(publisher.handler())).append(",\n")
                        .append("          \"classFqn\": ").append(jsonString(publisher.classFqn())).append(",\n")
                        .append("          \"methodName\": ").append(jsonString(publisher.methodName())).append(",\n")
                        .append("          \"expression\": ").append(jsonString(publisher.expression())).append(",\n")
                        .append("          \"receiver\": ").append(jsonString(publisher.receiver())).append(",\n")
                        .append("          \"receiverType\": ").append(jsonString(publisher.receiverType())).append(",\n")
                        .append("          \"confidence\": ").append(jsonString(publisher.confidence())).append(",\n")
                        .append("          \"file\": ").append(jsonString(publisher.file())).append(",\n")
                        .append("          \"line\": ").append(publisher.line()).append(",\n")
                        .append("          \"methodLine\": ").append(publisher.methodLine()).append("\n")
                        .append("        }");
                if (j + 1 < event.publishers().size()) {
                    output.append(',');
                }
                output.append('\n');
            }

            output.append("      ],\n")
                    .append("      \"listeners\": [\n");

            for (int j = 0; j < event.listeners().size(); j++) {
                EventListenerInfo listener = event.listeners().get(j);
                output.append("        {\n")
                        .append("          \"handler\": ").append(jsonString(listener.handler())).append(",\n")
                        .append("          \"classFqn\": ").append(jsonString(listener.classFqn())).append(",\n")
                        .append("          \"methodName\": ").append(jsonString(listener.methodName())).append(",\n")
                        .append("          \"declaredIn\": ").append(jsonString(listener.declaredIn())).append(",\n")
                        .append("          \"annotation\": ").append(jsonString(listener.annotation())).append(",\n")
                        .append("          \"condition\": ").append(jsonString(listener.condition())).append(",\n")
                        .append("          \"file\": ").append(jsonString(listener.file())).append(",\n")
                        .append("          \"line\": ").append(listener.line()).append(",\n")
                        .append("          \"methodLine\": ").append(listener.methodLine()).append("\n")
                        .append("        }");
                if (j + 1 < event.listeners().size()) {
                    output.append(',');
                }
                output.append('\n');
            }

            output.append("      ]\n")
                    .append("    }");
            if (i + 1 < events.size()) {
                output.append(',');
            }
            output.append('\n');
        }

        output.append("  ]\n").append("}\n");
        return output.toString();
    }

    private static String jsonArray(List<String> values) {
        return values.stream()
                .map(Renderer::jsonString)
                .collect(Collectors.joining(", ", "[", "]"));
    }

    private static String jsonString(String value) {
        String text = value == null ? "" : value;
        StringBuilder output = new StringBuilder("\"");
        for (int i = 0; i < text.length(); i++) {
            char character = text.charAt(i);
            switch (character) {
                case '"' -> output.append("\\\"");
                case '\\' -> output.append("\\\\");
                case '\b' -> output.append("\\b");
                case '\f' -> output.append("\\f");
                case '\n' -> output.append("\\n");
                case '\r' -> output.append("\\r");
                case '\t' -> output.append("\\t");
                default -> {
                    if (character < 0x20) {
                        output.append(String.format(Locale.ROOT, "\\u%04x", (int) character));
                    } else {
                        output.append(character);
                    }
                }
            }
        }
        return output.append('"').toString();
    }

    private static String csv(List<Endpoint> endpoints) {
        StringBuilder output = new StringBuilder();
        output.append("http_method,path,handler,controller,controller_fqn,method_name,"
                + "declared_in,file,line,method_line,consumes,produces\n");

        for (Endpoint endpoint : endpoints) {
            output.append(csvField(endpoint.httpMethod())).append(',')
                    .append(csvField(endpoint.path())).append(',')
                    .append(csvField(endpoint.handler())).append(',')
                    .append(csvField(endpoint.controller())).append(',')
                    .append(csvField(endpoint.controllerFqn())).append(',')
                    .append(csvField(endpoint.methodName())).append(',')
                    .append(csvField(endpoint.declaredIn())).append(',')
                    .append(csvField(endpoint.file())).append(',')
                    .append(endpoint.line()).append(',')
                    .append(endpoint.methodLine()).append(',')
                    .append(csvField(String.join(";", endpoint.consumes()))).append(',')
                    .append(csvField(String.join(";", endpoint.produces()))).append('\n');
        }

        return output.toString();
    }

    private static String eventCsv(ScanResult result) {
        StringBuilder output = new StringBuilder();
        output.append("event,role,handler,class_fqn,method,expression,receiver,"
                + "receiver_type,confidence,file,line,method_line,annotation,condition,"
                + "publisher_origin\n");

        for (EventInfo event : result.events()) {
            output.append(csvField(event.eventSimpleName())).append(',')
                    .append(csvField("EVENT")).append(',')
                    .append(csvField("")).append(',')
                    .append(csvField(event.declaredIn())).append(',')
                    .append(csvField("")).append(',')
                    .append(csvField("")).append(',')
                    .append(csvField("")).append(',')
                    .append(csvField("")).append(',')
                    .append(csvField("")).append(',')
                    .append(csvField(event.file())).append(',')
                    .append(event.line()).append(',')
                    .append(-1).append(',')
                    .append(csvField("")).append(',')
                    .append(csvField("")).append(',')
                    .append(csvField(event.publisherOrigin())).append('\n');

            for (EventPublisherInfo publisher : event.publishers()) {
                output.append(csvField(event.eventSimpleName())).append(',')
                        .append(csvField("PUBLISH")).append(',')
                        .append(csvField(publisher.handler())).append(',')
                        .append(csvField(publisher.classFqn())).append(',')
                        .append(csvField(publisher.methodName())).append(',')
                        .append(csvField(publisher.expression())).append(',')
                        .append(csvField(publisher.receiver())).append(',')
                        .append(csvField(publisher.receiverType())).append(',')
                        .append(csvField(publisher.confidence())).append(',')
                        .append(csvField(publisher.file())).append(',')
                        .append(publisher.line()).append(',')
                        .append(publisher.methodLine()).append(',')
                        .append(csvField("")).append(',')
                        .append(csvField("")).append(',')
                        .append(csvField(event.publisherOrigin())).append('\n');
            }

            for (EventListenerInfo listener : event.listeners()) {
                output.append(csvField(event.eventSimpleName())).append(',')
                        .append(csvField("LISTEN")).append(',')
                        .append(csvField(listener.handler())).append(',')
                        .append(csvField(listener.classFqn())).append(',')
                        .append(csvField(listener.methodName())).append(',')
                        .append(csvField("")).append(',')
                        .append(csvField("")).append(',')
                        .append(csvField("")).append(',')
                        .append(csvField("")).append(',')
                        .append(csvField(listener.file())).append(',')
                        .append(listener.line()).append(',')
                        .append(listener.methodLine()).append(',')
                        .append(csvField(listener.annotation())).append(',')
                        .append(csvField(listener.condition())).append(',')
                        .append(csvField(event.publisherOrigin())).append('\n');
            }
        }

        return output.toString();
    }

    private static String csvField(String value) {
        return "\"" + (value == null ? "" : value).replace("\"", "\"\"") + "\"";
    }

    private record EventRow(String event, String role, String handler, String source) {
    }
}
