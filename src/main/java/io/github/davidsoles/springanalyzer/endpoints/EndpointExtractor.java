package io.github.davidsoles.springanalyzer.endpoints;

import io.github.davidsoles.springanalyzer.common.ScanOptions;
import io.github.davidsoles.springanalyzer.common.SourceFiles;
import io.github.davidsoles.springanalyzer.common.SourceIndex;

import com.github.javaparser.ast.CompilationUnit;
import com.github.javaparser.ast.Node;
import com.github.javaparser.ast.body.AnnotationDeclaration;
import com.github.javaparser.ast.body.BodyDeclaration;
import com.github.javaparser.ast.body.ClassOrInterfaceDeclaration;
import com.github.javaparser.ast.body.MethodDeclaration;
import com.github.javaparser.ast.expr.AnnotationExpr;
import com.github.javaparser.ast.expr.ArrayInitializerExpr;
import com.github.javaparser.ast.expr.Expression;
import com.github.javaparser.ast.expr.MemberValuePair;
import com.github.javaparser.ast.expr.NormalAnnotationExpr;
import com.github.javaparser.ast.expr.SingleMemberAnnotationExpr;
import com.github.javaparser.ast.nodeTypes.NodeWithAnnotations;

import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * Extracts REST endpoints from Spring MVC controllers.
 */
public final class EndpointExtractor {

    private static final Set<String> DIRECT_MAPPINGS = Set.of(
            "RequestMapping",
            "GetMapping",
            "PostMapping",
            "PutMapping",
            "PatchMapping",
            "DeleteMapping");

    private final SourceIndex index;
    private final ConstantResolver resolver;
    private final Path root;
    private final ScanOptions options;

    public EndpointExtractor(
            SourceIndex index,
            Set<String> warnings,
            Path root,
            ScanOptions options) {
        this.index = index;
        this.resolver = new ConstantResolver(index, warnings);
        this.root = root;
        this.options = options;
    }

    public List<Endpoint> extract() {
        List<Endpoint> endpoints = new ArrayList<>();
        Set<String> seen = new HashSet<>();

        for (SourceIndex.TypeInfo type : index.types()) {
            if (!(type.declaration() instanceof ClassOrInterfaceDeclaration declaration)) {
                continue;
            }
            if (declaration.isInterface() || declaration.isAbstract()) {
                continue;
            }

            boolean restController = hasAnnotationInHierarchy(type, "RestController");
            boolean controller = restController || hasAnnotationInHierarchy(type, "Controller");
            if (!controller) {
                continue;
            }

            boolean classResponseBody = restController || hasAnnotationInHierarchy(type, "ResponseBody");
            List<MappingInfo> classMappings = classMappings(type);

            for (List<MethodRef> refs : methods(type).values()) {
                boolean methodResponseBody = refs.stream()
                        .anyMatch(ref -> hasAnnotation(ref.method(), "ResponseBody", new HashSet<>()));
                if (!classResponseBody && !options.includeMvcControllers() && !methodResponseBody) {
                    continue;
                }

                List<MethodMapping> methodMappings = new ArrayList<>();
                for (MethodRef ref : refs) {
                    if (ref.method().isPrivate() || ref.method().isStatic()) {
                        continue;
                    }
                    // The most specific implementation wins, matching Spring's
                    // annotation lookup. If it has no mapping, the interface or
                    // base class mapping is used instead.
                    List<MappingInfo> mappings = extractMappings(ref.method());
                    if (!mappings.isEmpty()) {
                        for (MappingInfo mapping : mappings) {
                            methodMappings.add(new MethodMapping(mapping, ref));
                        }
                        break;
                    }
                }
                if (methodMappings.isEmpty()) {
                    continue;
                }

                for (MappingInfo classMapping : classMappings) {
                    for (MethodMapping methodMapping : methodMappings) {
                        List<String> verbs = combineVerbs(classMapping.verbs(), methodMapping.mapping().verbs());
                        if (verbs.isEmpty()) {
                            continue;
                        }

                        List<String> consumes = methodMapping.mapping().consumes().isEmpty()
                                ? classMapping.consumes()
                                : methodMapping.mapping().consumes();
                        List<String> produces = methodMapping.mapping().produces().isEmpty()
                                ? classMapping.produces()
                                : methodMapping.mapping().produces();

                        for (String basePath : pathsOf(classMapping)) {
                            for (String methodPath : pathsOf(methodMapping.mapping())) {
                                String fullPath = joinPaths(basePath, methodPath);
                                for (String verb : verbs) {
                                    Endpoint endpoint = createEndpoint(
                                            verb, fullPath, type, methodMapping, consumes, produces);
                                    if (seen.add(dedupeKey(endpoint))) {
                                        endpoints.add(endpoint);
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }

        return endpoints;
    }

    private Endpoint createEndpoint(
            String verb,
            String path,
            SourceIndex.TypeInfo controller,
            MethodMapping methodMapping,
            List<String> consumes,
            List<String> produces) {

        MethodDeclaration method = methodMapping.ref().method();
        SourceIndex.TypeInfo declaringType = methodMapping.ref().declaringType();
        int methodLine = method.getBegin().map(position -> position.line).orElse(-1);
        int line = methodMapping.mapping().origin() == null
                ? methodLine
                : methodMapping.mapping().origin()
                        .getBegin()
                        .map(position -> position.line)
                        .orElse(methodLine);

        return new Endpoint(
                verb,
                path,
                controller.simpleName() + "#" + method.getNameAsString(),
                controller.simpleName(),
                controller.fqn(),
                method.getNameAsString(),
                declaringType.fqn(),
                displayFile(declaringType.unit().file()),
                line,
                methodLine,
                consumes,
                produces);
    }

    private List<MappingInfo> classMappings(SourceIndex.TypeInfo controller) {
        List<MappingInfo> own = extractMappings(controller.declaration());
        if (!own.isEmpty()) {
            return own;
        }

        for (SourceIndex.TypeInfo parent : index.hierarchy(controller)) {
            if (parent.fqn().equals(controller.fqn())) {
                continue;
            }
            List<MappingInfo> inherited = extractMappings(parent.declaration());
            if (!inherited.isEmpty()) {
                return inherited;
            }
        }

        return List.of(new MappingInfo(List.of(""), List.of("ANY"), List.of(), List.of(), null));
    }

    private Map<String, List<MethodRef>> methods(SourceIndex.TypeInfo controller) {
        Map<String, List<MethodRef>> byKey = new LinkedHashMap<>();
        for (SourceIndex.TypeInfo type : index.hierarchy(controller)) {
            for (BodyDeclaration<?> member : type.declaration().getMembers()) {
                if (member instanceof MethodDeclaration method) {
                    byKey.computeIfAbsent(methodKey(method), key -> new ArrayList<>())
                            .add(new MethodRef(method, type));
                }
            }
        }
        return byKey;
    }

    private static String methodKey(MethodDeclaration method) {
        String parameters = method.getParameters().stream()
                .map(parameter -> parameter.getType().asString())
                .collect(Collectors.joining(","));
        return method.getNameAsString() + "(" + parameters + ")";
    }

    private boolean hasAnnotationInHierarchy(SourceIndex.TypeInfo type, String target) {
        for (SourceIndex.TypeInfo candidate : index.hierarchy(type)) {
            if (hasAnnotation(candidate.declaration(), target, new HashSet<>())) {
                return true;
            }
        }
        return false;
    }

    private boolean hasAnnotation(NodeWithAnnotations<?> node, String target, Set<String> visited) {
        for (AnnotationExpr annotation : node.getAnnotations()) {
            String name = SourceIndex.simpleName(annotation.getNameAsString());
            if (stereotypeMatches(name, target)) {
                return true;
            }
        }

        for (AnnotationExpr annotation : node.getAnnotations()) {
            String name = SourceIndex.simpleName(annotation.getNameAsString());
            if (!visited.add(name)) {
                continue;
            }
            CompilationUnit unit = compilationUnitOf(node);
            if (unit == null) {
                continue;
            }
            for (AnnotationDeclaration declaration : index.annotationDeclarations(unit, annotation.getNameAsString())) {
                if (hasAnnotation(declaration, target, visited)) {
                    return true;
                }
            }
        }
        return false;
    }

    private static CompilationUnit compilationUnitOf(NodeWithAnnotations<?> node) {
        return node instanceof Node astNode
                ? astNode.findCompilationUnit().orElse(null)
                : null;
    }

    private static boolean stereotypeMatches(String annotation, String target) {
        if (annotation.equals(target)) {
            return true;
        }
        // In Spring, @RestController is @Controller + @ResponseBody.
        if (target.equals("Controller") && annotation.equals("RestController")) {
            return true;
        }
        return target.equals("ResponseBody") && annotation.equals("RestController");
    }

    private List<MappingInfo> extractMappings(NodeWithAnnotations<?> node) {
        return extractMappings(node, new HashSet<>());
    }

    private List<MappingInfo> extractMappings(NodeWithAnnotations<?> node, Set<String> visited) {
        List<MappingInfo> result = new ArrayList<>();

        for (AnnotationExpr annotation : node.getAnnotations()) {
            String name = SourceIndex.simpleName(annotation.getNameAsString());
            if (DIRECT_MAPPINGS.contains(name)) {
                result.add(directMapping(annotation, name));
                continue;
            }

            if (!visited.add(name)) {
                continue;
            }
            CompilationUnit unit = compilationUnitOf(node);
            if (unit == null) {
                continue;
            }

            for (AnnotationDeclaration declaration : index.annotationDeclarations(unit, annotation.getNameAsString())) {
                List<MappingInfo> metaMappings = extractMappings(declaration, new HashSet<>(visited));
                if (metaMappings.isEmpty()) {
                    continue;
                }

                List<String> usePaths = pathsOf(annotation);
                List<String> useConsumes = valuesOf(annotation, "consumes");
                List<String> useProduces = valuesOf(annotation, "produces");

                for (MappingInfo meta : metaMappings) {
                    result.add(new MappingInfo(
                            usePaths.isEmpty() ? meta.paths() : usePaths,
                            meta.verbs(),
                            useConsumes.isEmpty() ? meta.consumes() : useConsumes,
                            useProduces.isEmpty() ? meta.produces() : useProduces,
                            annotation));
                }
            }
        }

        return result;
    }

    private MappingInfo directMapping(AnnotationExpr annotation, String name) {
        List<String> paths = pathsOf(annotation);
        List<String> consumes = valuesOf(annotation, "consumes");
        List<String> produces = valuesOf(annotation, "produces");

        List<String> verbs;
        if (name.equals("RequestMapping")) {
            verbs = requestMappingVerbs(annotation);
        } else {
            verbs = List.of(name.substring(0, name.length() - "Mapping".length()).toUpperCase(Locale.ROOT));
        }

        return new MappingInfo(paths, verbs, consumes, produces, annotation);
    }

    private List<String> pathsOf(AnnotationExpr annotation) {
        List<String> result = new ArrayList<>();
        for (Expression expression : memberValues(annotation, "value", "path")) {
            result.addAll(stringsOf(expression, annotation));
        }
        return result;
    }

    private List<String> valuesOf(AnnotationExpr annotation, String... names) {
        List<String> result = new ArrayList<>();
        for (Expression expression : memberValues(annotation, names)) {
            result.addAll(stringsOf(expression, annotation));
        }
        return result;
    }

    private List<String> stringsOf(Expression expression, Node context) {
        if (expression instanceof ArrayInitializerExpr array) {
            List<String> result = new ArrayList<>();
            for (Expression value : array.getValues()) {
                result.add(resolver.resolveOrRaw(value, context));
            }
            return result;
        }
        return List.of(resolver.resolveOrRaw(expression, context));
    }

    private static List<Expression> memberValues(AnnotationExpr annotation, String... names) {
        Set<String> wanted = Set.of(names);
        List<Expression> result = new ArrayList<>();

        if (annotation instanceof SingleMemberAnnotationExpr single && wanted.contains("value")) {
            result.add(single.getMemberValue());
        } else if (annotation instanceof NormalAnnotationExpr normal) {
            for (MemberValuePair pair : normal.getPairs()) {
                if (wanted.contains(pair.getNameAsString())) {
                    result.add(pair.getValue());
                }
            }
        }

        return result;
    }

    private static List<String> requestMappingVerbs(AnnotationExpr annotation) {
        List<String> verbs = new ArrayList<>();

        for (Expression expression : memberValues(annotation, "method")) {
            List<Expression> values = expression instanceof ArrayInitializerExpr array
                    ? new ArrayList<>(array.getValues())
                    : List.of(expression);

            for (Expression value : values) {
                String verb = SourceIndex.simpleName(value.toString());
                if (!verb.isEmpty() && !verbs.contains(verb)) {
                    verbs.add(verb);
                }
            }
        }

        return verbs.isEmpty() ? List.of("ANY") : verbs;
    }

    private static List<String> combineVerbs(List<String> classVerbs, List<String> methodVerbs) {
        boolean classAny = classVerbs.isEmpty() || classVerbs.contains("ANY");
        boolean methodAny = methodVerbs.isEmpty() || methodVerbs.contains("ANY");

        if (classAny && methodAny) {
            return List.of("ANY");
        }
        if (classAny) {
            return methodVerbs;
        }
        if (methodAny) {
            return classVerbs;
        }

        List<String> intersection = new ArrayList<>();
        for (String verb : methodVerbs) {
            if (classVerbs.contains(verb) && !intersection.contains(verb)) {
                intersection.add(verb);
            }
        }
        return intersection;
    }

    private static List<String> pathsOf(MappingInfo mapping) {
        return mapping.paths().isEmpty() ? List.of("") : mapping.paths();
    }

    public static String joinPaths(String base, String path) {
        String normalizedBase = base == null ? "" : base;
        String normalizedPath = path == null ? "" : path;

        if (normalizedBase.isEmpty() && normalizedPath.isEmpty()) {
            return "/";
        }

        String joined = ("/" + normalizedBase + "/" + normalizedPath).replaceAll("/{2,}", "/");
        if (joined.length() > 1 && joined.endsWith("/")) {
            joined = joined.substring(0, joined.length() - 1);
        }
        return joined;
    }

    private static String dedupeKey(Endpoint endpoint) {
        return endpoint.httpMethod() + "|" + endpoint.path() + "|"
                + endpoint.controllerFqn() + "|" + endpoint.methodName();
    }

    private String displayFile(Path file) {
        return SourceFiles.display(root, options, file);
    }

    private record MethodRef(MethodDeclaration method, SourceIndex.TypeInfo declaringType) {
    }

    private record MethodMapping(MappingInfo mapping, MethodRef ref) {
    }

    private record MappingInfo(
            List<String> paths,
            List<String> verbs,
            List<String> consumes,
            List<String> produces,
            Node origin) {

        MappingInfo {
            paths = List.copyOf(paths);
            verbs = List.copyOf(verbs);
            consumes = List.copyOf(consumes);
            produces = List.copyOf(produces);
        }
    }
}
