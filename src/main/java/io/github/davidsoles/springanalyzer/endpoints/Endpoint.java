package io.github.davidsoles.springanalyzer.endpoints;

import java.util.List;

/**
 * A REST endpoint found in source code.
 *
 * @param httpMethod    HTTP verb (GET, POST, ... or ANY)
 * @param path          full path, already combined with the controller base path
 * @param handler       controller#method
 * @param controller    simple controller name
 * @param controllerFqn fully qualified controller name
 * @param methodName    handler method name
 * @param declaredIn    type where the mapping annotation is declared
 * @param file          source file containing the annotation
 * @param line          line of the mapping annotation
 * @param methodLine    line where the method starts
 * @param consumes      declared consumed media types
 * @param produces      declared produced media types
 */
public record Endpoint(
        String httpMethod,
        String path,
        String handler,
        String controller,
        String controllerFqn,
        String methodName,
        String declaredIn,
        String file,
        int line,
        int methodLine,
        List<String> consumes,
        List<String> produces) {

    public Endpoint {
        consumes = List.copyOf(consumes);
        produces = List.copyOf(produces);
    }
}
