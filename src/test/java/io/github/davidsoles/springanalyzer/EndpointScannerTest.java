package io.github.davidsoles.springanalyzer;

import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.file.Path;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class EndpointScannerTest {

    private final Path root = Path.of("src/test/resources/fixtures/demo")
            .toAbsolutePath()
            .normalize();

    @Test
    void extractsEndpointsFromExampleRepository() throws IOException {
        ScanResult result = scan(ScanOptions.defaults());

        assertEquals(25, result.endpoints().size());
        assertEquals(10, result.controllerCount());
        assertEquals(17, result.filesScanned());
        assertTrue(result.warnings().isEmpty(), () -> String.join("\n", result.warnings()));
    }

    @Test
    void resolvesConstantsAndComposedAnnotations() throws IOException {
        ScanResult result = scan(ScanOptions.defaults());

        Endpoint list = find(result, "GET", "/api/users");
        assertEquals("UserController#list", list.handler());
        assertEquals("com.example.demo.web.UserController", list.controllerFqn());
        assertTrue(list.file().endsWith("UserController.java"));
        assertTrue(list.line() > 0);
        assertTrue(list.methodLine() > 0);
        assertEquals(List.of("application/json"), list.produces());

        Endpoint create = find(result, "POST", "/api/users");
        assertEquals(List.of("application/json"), create.consumes());
        assertEquals(List.of("application/json"), create.produces());

        Endpoint hello = find(result, "GET", "/meta/hello");
        assertEquals("MetaController#hello", hello.handler());
        assertEquals(List.of(), hello.produces());

        Endpoint bye = find(result, "GET", "/meta/bye");
        assertEquals(List.of("text/plain"), bye.produces());

        assertTrue(has(result, "GET", "/api/v1/items/{id}"));
        assertTrue(has(result, "GET", "/api/users/{id}"));
        assertTrue(has(result, "DELETE", "/api/users/{id}"));
    }

    @Test
    void supportsMultiplePathsAndVerbs() throws IOException {
        ScanResult result = scan(ScanOptions.defaults());

        for (String base : List.of("/multi-a", "/multi-b")) {
            for (String path : List.of("/x", "/y")) {
                assertTrue(has(result, "GET", base + path));
            }
            assertTrue(has(result, "GET", base));
            assertTrue(has(result, "POST", base));
        }

        assertEquals(8, result.endpoints().stream()
                .filter(endpoint -> endpoint.controllerFqn().equals("com.example.demo.web.MultiPathController"))
                .count());
    }

    @Test
    void supportsInheritanceAndInterfaces() throws IOException {
        ScanResult result = scan(ScanOptions.defaults());

        Endpoint inherited = find(result, "GET", "/inherited/from-interface");
        assertEquals("InheritedController#fromInterface", inherited.handler());
        assertEquals("com.example.demo.web.UserApi", inherited.declaredIn());
        assertTrue(inherited.file().endsWith("UserApi.java"));

        Endpoint fromInterface = find(result, "GET", "/iface/ping");
        assertEquals("IfaceController#ping", fromInterface.handler());
        assertEquals("com.example.demo.web.IfaceApi", fromInterface.declaredIn());

        Endpoint child = find(result, "GET", "/base/common");
        assertEquals("ChildController#common", child.handler());
        assertEquals("com.example.demo.web.AbstractBaseController", child.declaredIn());

        Endpoint grandChild = find(result, "GET", "/grand/common");
        assertEquals("GrandChildController#common", grandChild.handler());
    }

    @Test
    void supportsNestedControllers() throws IOException {
        ScanResult result = scan(ScanOptions.defaults());

        Endpoint nested = find(result, "GET", "/nested/ping");
        assertEquals("NestedController#ping", nested.handler());
        assertEquals("com.example.demo.web.Outer.NestedController", nested.controllerFqn());
    }

    @Test
    void filtersMvcControllersWithoutResponseBodyByDefault() throws IOException {
        ScanResult defaults = scan(ScanOptions.defaults());
        assertTrue(has(defaults, "GET", "/admin/status"));
        assertFalse(has(defaults, "GET", "/admin/view"));

        ScanResult withMvc = scan(new ScanOptions(false, true, false));
        assertEquals(26, withMvc.endpoints().size());
        assertTrue(has(withMvc, "GET", "/admin/view"));
    }

    @Test
    void includesTestsOnlyWithOption() throws IOException {
        ScanResult defaults = scan(ScanOptions.defaults());
        assertFalse(has(defaults, "GET", "/test-only/ping"));

        ScanResult withTests = scan(new ScanOptions(true, false, false));
        assertEquals(26, withTests.endpoints().size());
        assertEquals(18, withTests.filesScanned());
        assertTrue(has(withTests, "GET", "/test-only/ping"));
    }

    @Test
    void canShowAbsolutePaths() throws IOException {
        ScanResult result = scan(new ScanOptions(false, false, true));

        assertTrue(result.endpoints().stream()
                .allMatch(endpoint -> Path.of(endpoint.file()).isAbsolute()));
    }

    @Test
    void joinsPathsCorrectly() {
        assertEquals("/api/users", EndpointScanner.joinPaths("/api/users", ""));
        assertEquals("/api/users/{id}", EndpointScanner.joinPaths("/api/users", "/{id}"));
        assertEquals("/", EndpointScanner.joinPaths("", ""));
        assertEquals("/x", EndpointScanner.joinPaths("/", "/x"));
        assertEquals("/api/x", EndpointScanner.joinPaths("/api/", "/x"));
    }

    @Test
    void rendersTableJsonAndCsv() throws IOException {
        ScanResult result = scan(ScanOptions.defaults());

        String table = Renderer.render(result, OutputFormat.TABLE);
        assertTrue(table.contains("METHOD"));
        assertTrue(table.contains("/api/users"));
        assertTrue(table.contains("endpoints"));

        String json = Renderer.render(result, OutputFormat.JSON);
        assertTrue(json.startsWith("{\n"));
        assertTrue(json.contains("\"schemaVersion\": 1"));
        assertTrue(json.contains("\"scannerVersion\": \"" + Version.VERSION + "\""));
        assertTrue(json.contains("\"endpoints\": ["));
        assertTrue(json.contains("\"path\": \"/api/users\""));
        assertTrue(json.contains("\"controllerFqn\": \"com.example.demo.web.UserController\""));

        String csv = Renderer.render(result, OutputFormat.CSV);
        assertTrue(csv.startsWith("http_method,path,"));
        assertTrue(csv.contains("\"/api/users\""));
    }

    private ScanResult scan(ScanOptions options) throws IOException {
        return new EndpointScanner().scan(root, options);
    }

    private static Endpoint find(ScanResult result, String verb, String path) {
        return result.endpoints().stream()
                .filter(endpoint -> endpoint.httpMethod().equals(verb) && endpoint.path().equals(path))
                .findFirst()
                .orElseThrow(() -> new AssertionError(
                        "Endpoint not found: " + verb + " " + path + "\n" + result.endpoints()));
    }

    private static boolean has(ScanResult result, String verb, String path) {
        return result.endpoints().stream()
                .anyMatch(endpoint -> endpoint.httpMethod().equals(verb) && endpoint.path().equals(path));
    }
}
