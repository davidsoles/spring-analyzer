package io.github.davidsoles.springanalyzer.common;

import io.github.davidsoles.springanalyzer.endpoints.Endpoint;
import io.github.davidsoles.springanalyzer.endpoints.EndpointExtractor;
import io.github.davidsoles.springanalyzer.events.EventExtractor;
import io.github.davidsoles.springanalyzer.events.EventInfo;

import com.github.javaparser.JavaParser;
import com.github.javaparser.ParseResult;
import com.github.javaparser.ParserConfiguration;
import com.github.javaparser.Problem;
import com.github.javaparser.ast.CompilationUnit;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;
import java.util.stream.Stream;

/**
 * Parses a Spring Boot repository, builds the shared source index, and delegates
 * to the endpoint and event analyzers.
 */
public final class ProjectScanner {

    private static final Set<String> EXCLUDED_DIRS = Set.of(
            ".git",
            ".idea",
            ".vscode",
            ".gradle",
            "node_modules",
            "target",
            "build",
            "out");

    public ScanResult scan(Path root, ScanOptions options) throws IOException {
        Path absoluteRoot = root.toAbsolutePath().normalize();
        if (!Files.isDirectory(absoluteRoot)) {
            throw new IllegalArgumentException("Directory does not exist: " + absoluteRoot);
        }

        Set<String> warnings = new LinkedHashSet<>();
        JavaParser parser = new JavaParser(new ParserConfiguration()
                .setLanguageLevel(ParserConfiguration.LanguageLevel.JAVA_25)
                .setCharacterEncoding(StandardCharsets.UTF_8));

        List<SourceIndex.UnitInfo> units = new ArrayList<>();
        int filesScanned = 0;

        try (Stream<Path> stream = Files.walk(absoluteRoot)) {
            List<Path> files = stream
                    .filter(Files::isRegularFile)
                    .filter(file -> shouldScan(absoluteRoot, options, file))
                    .sorted()
                    .toList();

            for (Path file : files) {
                try {
                    ParseResult<CompilationUnit> result = parser.parse(file);
                    String displayFile = SourceFiles.display(absoluteRoot, options, file);
                    if (result.getResult().isEmpty()) {
                        warnings.add("Could not parse " + displayFile + ": " + problems(result));
                        continue;
                    }
                    if (!result.isSuccessful()) {
                        warnings.add("Problems while parsing " + displayFile + ": " + problems(result));
                    }
                    units.add(new SourceIndex.UnitInfo(file, result.getResult().get()));
                    filesScanned++;
                } catch (Exception e) {
                    warnings.add("Could not read "
                            + SourceFiles.display(absoluteRoot, options, file)
                            + ": " + e.getMessage());
                }
            }
        }

        SourceIndex index = new SourceIndex();
        units.forEach(index::add);

        List<Endpoint> endpoints = new EndpointExtractor(index, warnings, absoluteRoot, options).extract();
        endpoints.sort(Comparator.comparing(Endpoint::path)
                .thenComparing(Endpoint::httpMethod)
                .thenComparing(Endpoint::handler));

        List<EventInfo> events = new EventExtractor(index, warnings, absoluteRoot, options).extract();

        return new ScanResult(endpoints, events, List.copyOf(warnings), filesScanned);
    }

    private static boolean shouldScan(Path root, ScanOptions options, Path file) {
        String relative = SourceFiles.relative(root, file);
        if (!relative.endsWith(".java")) {
            return false;
        }

        for (String part : relative.split("/")) {
            if (EXCLUDED_DIRS.contains(part)) {
                return false;
            }
        }

        if (!options.includeTests()) {
            return !relative.startsWith("src/test/")
                    && !relative.contains("/src/test/")
                    && !relative.startsWith("src/integrationTest/")
                    && !relative.contains("/src/integrationTest/")
                    && !relative.startsWith("src/testFixtures/")
                    && !relative.contains("/src/testFixtures/");
        }
        return true;
    }

    private static String problems(ParseResult<?> result) {
        String message = result.getProblems().stream()
                .limit(3)
                .map(Problem::getMessage)
                .collect(Collectors.joining("; "));
        return message.isEmpty() ? "unknown error" : message;
    }
}
