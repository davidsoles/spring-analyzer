package io.github.davidsoles.springanalyzer;

import io.github.davidsoles.springanalyzer.common.OutputFormat;
import io.github.davidsoles.springanalyzer.common.ProjectScanner;
import io.github.davidsoles.springanalyzer.common.Renderer;
import io.github.davidsoles.springanalyzer.common.ScanOptions;
import io.github.davidsoles.springanalyzer.common.ScanResult;
import io.github.davidsoles.springanalyzer.common.Version;

import java.io.PrintStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

public final class Main {

    public static void main(String[] args) {
        System.exit(new Main().run(args));
    }

    int run(String[] args) {
        CliOptions cli;
        try {
            cli = parse(args);
        } catch (CliException e) {
            System.err.println("Error: " + e.getMessage());
            System.err.println();
            printUsage(System.err);
            return 2;
        }

        if (cli.help()) {
            printUsage(System.out);
            return 0;
        }
        if (cli.version()) {
            System.out.println("spring-analyzer " + Version.VERSION);
            return 0;
        }

        try {
            ScanResult result = new ProjectScanner().scan(
                    cli.root(),
                    new ScanOptions(
                            cli.includeTests(),
                            cli.includeMvcControllers(),
                            cli.absolutePaths()));

            String rendered = Renderer.render(result, cli.format(), cli.events());

            if (cli.output() != null) {
                Path target = cli.output().toAbsolutePath().normalize();
                Path parent = target.getParent();
                if (parent != null) {
                    Files.createDirectories(parent);
                }
                Files.writeString(target, rendered, StandardCharsets.UTF_8);
                if (cli.verbose()) {
                    System.err.println("Output written to " + target);
                }
            } else {
                System.out.print(rendered);
            }

            if (cli.verbose()) {
                result.warnings().forEach(warning -> System.err.println("Warning: " + warning));
                System.err.println("Java files scanned: " + result.filesScanned());
            }

            return 0;
        } catch (Exception e) {
            System.err.println("Error: " + e.getMessage());
            if (cli.verbose()) {
                e.printStackTrace(System.err);
            }
            return 1;
        }
    }

    private static CliOptions parse(String[] args) {
        boolean help = false;
        boolean version = false;
        boolean includeTests = false;
        boolean includeMvc = false;
        boolean absolutePaths = false;
        boolean events = false;
        boolean verbose = false;
        OutputFormat format = OutputFormat.TABLE;
        Path output = null;
        List<String> positional = new ArrayList<>();

        for (int i = 0; i < args.length; i++) {
            String argument = args[i];

            if (argument.equals("--")) {
                for (int j = i + 1; j < args.length; j++) {
                    positional.add(args[j]);
                }
                break;
            }

            switch (argument) {
                case "-h", "--help" -> help = true;
                case "--version" -> version = true;
                case "--include-tests" -> includeTests = true;
                case "--include-mvc" -> includeMvc = true;
                case "--absolute-paths" -> absolutePaths = true;
                case "--events" -> events = true;
                case "-v", "--verbose" -> verbose = true;
                case "-f", "--format" -> format = parseFormat(requireValue(args, ++i, argument));
                case "-o", "--output" -> output = Path.of(requireValue(args, ++i, argument));
                default -> {
                    if (argument.startsWith("--format=")) {
                        format = parseFormat(argument.substring("--format=".length()));
                    } else if (argument.startsWith("--output=")) {
                        String value = argument.substring("--output=".length());
                        if (value.isEmpty()) {
                            throw new CliException("Missing value for --output");
                        }
                        output = Path.of(value);
                    } else if (argument.startsWith("-")) {
                        throw new CliException("Unknown option: " + argument);
                    } else {
                        positional.add(argument);
                    }
                }
            }
        }

        if (positional.size() > 1) {
            throw new CliException("Only one repository path is allowed");
        }

        Path root = positional.isEmpty() ? Path.of(".") : Path.of(positional.get(0));
        return new CliOptions(root, format, output, includeTests, includeMvc, absolutePaths, events, verbose, help, version);
    }

    private static OutputFormat parseFormat(String value) {
        try {
            return OutputFormat.parse(value);
        } catch (IllegalArgumentException e) {
            throw new CliException(e.getMessage());
        }
    }

    private static String requireValue(String[] args, int index, String option) {
        if (index >= args.length) {
            throw new CliException("Missing value for " + option);
        }
        return args[index];
    }

    private static void printUsage(PrintStream output) {
        output.println("""
                Usage: java -jar spring-analyzer.jar [options] [repository-path]

                Static analysis for Spring Boot: list REST endpoints and application events.

                Options:
                  -f, --format <table|json|csv>  Output format (default: table)
                  -o, --output <file>            Write output to a file
                      --include-tests            Include src/test and similar directories
                      --include-mvc              Include @Controller methods without @ResponseBody
                      --absolute-paths           Show absolute file paths
                      --events                   Show the event graph instead of endpoints
                  -v, --verbose                  Show warnings and statistics
                  -h, --help                     Show this help
                      --version                  Show the version

                If no repository path is given, the current directory is used.
                """);
    }

    private record CliOptions(
            Path root,
            OutputFormat format,
            Path output,
            boolean includeTests,
            boolean includeMvcControllers,
            boolean absolutePaths,
            boolean events,
            boolean verbose,
            boolean help,
            boolean version) {
    }

    private static final class CliException extends RuntimeException {
        private CliException(String message) {
            super(message);
        }
    }
}
