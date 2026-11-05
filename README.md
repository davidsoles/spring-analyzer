# SpringAnalyzer

[![CI](https://github.com/davidsoles/spring-analyzer/actions/workflows/ci.yml/badge.svg)](https://github.com/davidsoles/spring-analyzer/actions/workflows/ci.yml)
[![License](https://img.shields.io/badge/license-Apache--2.0-blue.svg)](LICENSE)
[![Java](https://img.shields.io/badge/Java-21%2B-orange.svg)](https://adoptium.net/)

Static analysis CLI for Spring Boot applications. It scans Java source code and
extracts metadata that is otherwise scattered across annotations, interfaces,
and configuration.

The first analyzer extracts **REST endpoints** and links each one to the exact
source file and line. The project is designed to grow into a general Spring Boot
scanner: `@EventListener` publishers and subscribers, scheduled tasks, and
component wiring.

> This is an independent open-source project. It is not affiliated with,
> endorsed by, or sponsored by Spring or Broadcom.

## Status

- REST endpoint extraction: **available**
- Event publisher/subscriber mapping: **available**
- JSON output with a versioned envelope: **available**
- Scheduled task extraction: **planned**

## Requirements

- Java 21 or newer
- Maven 3.9+ (or use the included Maven Wrapper)

The scanner parses Java source up to Java 25 syntax with JavaParser. It does not
need to compile or run the analyzed project.

## Build

```bash
./mvnw clean package
```

The shaded, runnable JAR is generated at:

```text
target/spring-analyzer-1.1.0.jar
```

## Usage

```bash
java -jar target/spring-analyzer-1.1.0.jar [options] [repository-path]
```

If no repository path is provided, the current directory is used.

### Examples

Print a table:

```bash
java -jar target/spring-analyzer-1.1.0.jar /path/to/project
```

Write JSON:

```bash
java -jar target/spring-analyzer-1.1.0.jar \
  --format json \
  --output endpoints.json \
  /path/to/project
```

Write CSV:

```bash
java -jar target/spring-analyzer-1.1.0.jar -f csv -o endpoints.csv /path/to/project
```

Include MVC view methods and test sources:

```bash
java -jar target/spring-analyzer-1.1.0.jar \
  --include-mvc \
  --include-tests \
  --absolute-paths \
  /path/to/project
```

Show warnings and scan statistics:

```bash
java -jar target/spring-analyzer-1.1.0.jar --verbose /path/to/project
```

Show the event graph instead of endpoints:

```bash
java -jar target/spring-analyzer-1.1.0.jar --events /path/to/project
```

### Options

| Option | Description |
|---|---|
| `-f, --format <table\|json\|csv>` | Output format. Default: `table`. |
| `-o, --output <file>` | Write output to a file. |
| `--include-tests` | Include `src/test`, `src/integrationTest`, and `src/testFixtures`. |
| `--include-mvc` | Include `@Controller` methods without `@ResponseBody`. |
| `--absolute-paths` | Show absolute file paths instead of paths relative to the root. |
| `--events` | Show the event graph (table/CSV) instead of endpoints. |
| `-v, --verbose` | Print warnings and statistics to `stderr`. |
| `-h, --help` | Show help. |
| `--version` | Show the version. |

## Output

### Table

```text
METHOD  PATH                       HANDLER                            SOURCE
------  -------------------------  ---------------------------------  ----------------------------------------------
GET     /api/users                 UserController#list                src/main/java/.../UserController.java:20
POST    /api/users                 UserController#create              src/main/java/.../UserController.java:30
GET     /api/users/{id}            UserController#get                 src/main/java/.../UserController.java:25
DELETE  /api/users/{id}            UserController#delete              src/main/java/.../UserController.java:40

25 endpoints | 10 controllers | 17 Java files
```

### JSON

JSON output uses a versioned envelope so future analyzers can add fields without
breaking clients:

```json
{
  "schemaVersion": 2,
  "scannerVersion": "1.1.0",
  "endpoints": [
    {
      "httpMethod": "GET",
      "path": "/api/users",
      "handler": "UserController#list",
      "controller": "UserController",
      "controllerFqn": "com.example.web.UserController",
      "methodName": "list",
      "declaredIn": "com.example.web.UserController",
      "file": "src/main/java/com/example/web/UserController.java",
      "line": 20,
      "methodLine": 20,
      "consumes": [],
      "produces": ["application/json"]
    }
  ]
}
```

### Events

`--events` prints an event -> publisher -> subscriber table:

```text
EVENT                   ROLE     HANDLER                         SOURCE
----------------------  -------  ------------------------------  ----------------------------------------------
OrderCreatedEvent       EVENT                                    src/main/java/.../OrderCreatedEvent.java:3
OrderCreatedEvent       PUBLISH  OrderService#create             src/main/java/.../OrderService.java:21
OrderCreatedEvent       LISTEN   OrderListener#onCreated         src/main/java/.../OrderListener.java:8
OrderShippedEvent       EVENT                                    src/main/java/.../OrderShippedEvent.java:3
OrderShippedEvent       PUBLISH  OrderService#notifyUnknown      src/main/java/.../OrderService.java:33
OrderShippedEvent       LISTEN   OrderListener#onShipped         src/main/java/.../OrderListener.java:12

3 events | 4 publishers | 6 listeners | 8 Java files
```

The JSON output always includes the `events` array, grouped by event type:

```json
{
  "eventType": "com.example.events.OrderCreatedEvent",
  "eventSimpleName": "OrderCreatedEvent",
  "declaredIn": "com.example.events.OrderCreatedEvent",
  "file": "src/main/java/com/example/events/OrderCreatedEvent.java",
  "line": 3,
  "publishers": [
    {
      "handler": "OrderService#create",
      "expression": "new OrderCreatedEvent(1L)",
      "receiver": "publisher",
      "receiverType": "org.springframework.context.ApplicationEventPublisher",
      "confidence": "high",
      "file": "src/main/java/com/example/events/OrderService.java",
      "line": 21
    }
  ],
  "listeners": [
    {
      "handler": "OrderListener#onCreated",
      "annotation": "EventListener",
      "condition": "",
      "file": "src/main/java/com/example/events/OrderListener.java",
      "line": 8
    }
  ]
}
```

### CSV

CSV columns:

```text
http_method,path,handler,controller,controller_fqn,method_name,
declared_in,file,line,method_line,consumes,produces
```

## What it detects

- `@RestController` and `@Controller` (including custom meta-annotations)
- `@ResponseBody` at class or method level
- `@RequestMapping`, `@GetMapping`, `@PostMapping`, `@PutMapping`,
  `@PatchMapping`, and `@DeleteMapping`
- Class-level base paths combined with method paths
- Multiple paths in one annotation, e.g. `@GetMapping({"/a", "/b"})`
- Multiple verbs in `@RequestMapping(method = {RequestMethod.GET, RequestMethod.POST})`
- Composed annotations: a custom annotation annotated with `@GetMapping`,
  `@RequestMapping`, etc.
- String constants declared in the repository and concatenations:
  `@RequestMapping(ApiPaths.API + "/users")`
- Known Spring constants such as `MediaType.APPLICATION_JSON_VALUE`
- Inherited methods from base classes and interfaces
- Nested controllers
- `consumes` and `produces` at class and method level
- Spring events:
  - `@EventListener`, `@TransactionalEventListener`, and composed listener annotations
  - Event types from listener parameters or `classes` attributes
  - `publishEvent` and `multicastEvent` calls
  - Event types from `new Event(...)`, variables, fields, and event factory return types
  - `ApplicationEvent` subclasses, even when currently unused
  - Publisher confidence (`high` vs `name-only`) and receiver type

## Limitations

This is static analysis. It does not execute Spring or resolve the full project
classpath, so results can differ from runtime mappings:

- Constants from external dependencies or generated sources are not resolved.
- Dynamic expressions (SpEL, method calls, `String.format`, etc.) are not evaluated.
- `@AliasFor` semantics in composed annotations are approximated.
- Conditional beans (`@Profile`, `@ConditionalOn...`) are not evaluated.
- `server.servlet.context-path` and other runtime prefixes are not included.
- Only Java is analyzed; Kotlin and Groovy are not supported.
- Abstract controllers are skipped; their methods are attributed to concrete
  subclasses.
- Publisher detection can be name-only when the receiver type cannot be resolved;
  synchronous and asynchronous listeners are treated the same.

## Project layout

The source code is organized by feature:

```text
src/main/java/io/github/davidsoles/springanalyzer/
├── Main.java                 # CLI entry point
├── common/                   # Shared models, index, rendering, version
│   ├── OutputFormat.java
│   ├── Renderer.java
│   ├── ScanOptions.java
│   ├── ScanResult.java
│   ├── SourceIndex.java
│   └── Version.java
├── endpoints/                # REST endpoint analysis
│   ├── ConstantResolver.java
│   ├── Endpoint.java
│   └── EndpointScanner.java
└── events/                   # Spring event analysis
    ├── EventExtractor.java
    ├── EventInfo.java
    ├── EventListenerInfo.java
    └── EventPublisherInfo.java
```

## Development

```bash
./mvnw test
```

The test suite uses a Spring fixture under
`src/test/resources/fixtures/demo` and covers constants, composed annotations,
inheritance, interfaces, multiple paths/verbs, nested controllers, and filters.

Build with Java 21 or 25:

```bash
JAVA_HOME=/path/to/jdk-21 ./mvnw clean verify
```

## Roadmap

- v1.1: event publisher/subscriber extraction and JSON schema 2.
- Next: scheduled tasks and component wiring.
- Later: additional output formats and integrations.

## License

Apache License 2.0. See [LICENSE](LICENSE) and [NOTICE](NOTICE).

JavaParser is dual-licensed under LGPL-3.0 and Apache-2.0. This project uses the
Apache-2.0 option.
