package io.github.davidsoles.springanalyzer;

import java.util.List;
import java.util.Locale;
import java.util.stream.Collectors;

public final class Renderer {

    private Renderer() {
    }

    public static String render(ScanResult result, OutputFormat format) {
        return switch (format) {
            case TABLE -> table(result);
            case JSON -> json(result);
            case CSV -> csv(result.endpoints());
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
                .append(result.filesScanned()).append(" Java files\n");

        if (!result.warnings().isEmpty()) {
            output.append(result.warnings().size())
                    .append(" warnings (use --verbose to view them)\n");
        }

        return output.toString();
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
                .append("  \"schemaVersion\": 1,\n")
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

        output.append("  ]\n").append("}\n");
        return output.toString();
    }

    private static String jsonArray(List<String> values) {
        return values.stream()
                .map(Renderer::jsonString)
                .collect(Collectors.joining(", ", "[", "]"));
    }

    private static String jsonString(String value) {
        StringBuilder output = new StringBuilder("\"");
        for (int i = 0; i < value.length(); i++) {
            char character = value.charAt(i);
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

    private static String csvField(String value) {
        return "\"" + value.replace("\"", "\"\"") + "\"";
    }
}
