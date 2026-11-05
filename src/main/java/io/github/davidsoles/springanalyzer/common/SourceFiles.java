package io.github.davidsoles.springanalyzer.common;

import java.nio.file.Path;

/**
 * Helpers for displaying source paths consistently across analyzers.
 */
public final class SourceFiles {

    private SourceFiles() {
    }

    public static String relative(Path root, Path file) {
        return root.relativize(file).toString().replace('\\', '/');
    }

    public static String display(Path root, ScanOptions options, Path file) {
        if (options.absolutePaths()) {
            return file.toString().replace('\\', '/');
        }
        return relative(root, file);
    }
}
