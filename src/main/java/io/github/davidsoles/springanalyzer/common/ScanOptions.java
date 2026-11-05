package io.github.davidsoles.springanalyzer.common;

/**
 * Scan options.
 *
 * @param includeTests         include src/test, src/integrationTest, and src/testFixtures
 * @param includeMvcControllers include methods from @Controller classes that are not REST
 * @param absolutePaths        show absolute file paths instead of paths relative to the root
 */
public record ScanOptions(
        boolean includeTests,
        boolean includeMvcControllers,
        boolean absolutePaths) {

    public static ScanOptions defaults() {
        return new ScanOptions(false, false, false);
    }
}
