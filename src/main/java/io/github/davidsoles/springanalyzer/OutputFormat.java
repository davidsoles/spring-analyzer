package io.github.davidsoles.springanalyzer;

/**
 * The scanner's output formats.
 */
public enum OutputFormat {
    TABLE,
    JSON,
    CSV;

    public static OutputFormat parse(String value) {
        return switch (value.toLowerCase(java.util.Locale.ROOT)) {
            case "table", "tabla" -> TABLE;
            case "json" -> JSON;
            case "csv" -> CSV;
            default -> throw new IllegalArgumentException(
                    "Invalid output format: " + value + " (use table, json, or csv)");
        };
    }
}
