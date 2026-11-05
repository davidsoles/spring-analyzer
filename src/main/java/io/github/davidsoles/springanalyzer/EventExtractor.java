package io.github.davidsoles.springanalyzer;

import com.github.javaparser.ast.CompilationUnit;
import com.github.javaparser.ast.Node;
import com.github.javaparser.ast.body.AnnotationDeclaration;
import com.github.javaparser.ast.body.BodyDeclaration;
import com.github.javaparser.ast.body.ClassOrInterfaceDeclaration;
import com.github.javaparser.ast.body.FieldDeclaration;
import com.github.javaparser.ast.body.MethodDeclaration;
import com.github.javaparser.ast.body.Parameter;
import com.github.javaparser.ast.body.RecordDeclaration;
import com.github.javaparser.ast.body.TypeDeclaration;
import com.github.javaparser.ast.body.VariableDeclarator;
import com.github.javaparser.ast.expr.AnnotationExpr;
import com.github.javaparser.ast.expr.ArrayInitializerExpr;
import com.github.javaparser.ast.expr.AssignExpr;
import com.github.javaparser.ast.expr.CastExpr;
import com.github.javaparser.ast.expr.ClassExpr;
import com.github.javaparser.ast.expr.ConditionalExpr;
import com.github.javaparser.ast.expr.EnclosedExpr;
import com.github.javaparser.ast.expr.Expression;
import com.github.javaparser.ast.expr.FieldAccessExpr;
import com.github.javaparser.ast.expr.LambdaExpr;
import com.github.javaparser.ast.expr.MemberValuePair;
import com.github.javaparser.ast.expr.MethodCallExpr;
import com.github.javaparser.ast.expr.NameExpr;
import com.github.javaparser.ast.expr.NormalAnnotationExpr;
import com.github.javaparser.ast.expr.ObjectCreationExpr;
import com.github.javaparser.ast.expr.SingleMemberAnnotationExpr;
import com.github.javaparser.ast.expr.StringLiteralExpr;
import com.github.javaparser.ast.expr.ThisExpr;
import com.github.javaparser.ast.nodeTypes.NodeWithAnnotations;
import com.github.javaparser.ast.type.ClassOrInterfaceType;
import com.github.javaparser.ast.type.Type;

import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * Extracts Spring application events, their publishers, and their listeners.
 *
 * <p>Subscribers are methods annotated with {@code @EventListener},
 * {@code @TransactionalEventListener}, or custom meta-annotations containing
 * one of them. Publishers are calls to {@code publishEvent} or
 * {@code multicastEvent}. Events are grouped by simple type name so a
 * publisher using a simple name can still be linked to a listener using an
 * imported fully qualified name.</p>
 */
final class EventExtractor {

    private static final Set<String> PUBLISHER_TYPES = Set.of(
            "ApplicationEventPublisher",
            "ApplicationContext",
            "ConfigurableApplicationContext",
            "ApplicationEventMulticaster");

    private final SourceIndex index;
    private final Set<String> warnings;
    private final Path root;
    private final ScanOptions options;
    private final Map<String, EventBuilder> events = new LinkedHashMap<>();

    EventExtractor(SourceIndex index, Set<String> warnings, Path root, ScanOptions options) {
        this.index = index;
        this.warnings = warnings;
        this.root = root;
        this.options = options;
    }

    List<EventInfo> extract() {
        for (SourceIndex.TypeInfo type : index.types()) {
            if (isConcrete(type)) {
                extractListeners(type);
            }
            extractPublishers(type);
        }

        // Include event classes that extend ApplicationEvent even when no
        // publisher or listener references them yet.
        for (SourceIndex.TypeInfo type : index.types()) {
            if (extendsApplicationEvent(type)) {
                EventBuilder builder = builderFor(type.fqn(), type.unit().unit());
                builder.fillDeclaration(type);
            }
        }

        List<EventInfo> result = new ArrayList<>();
        for (EventBuilder builder : events.values()) {
            result.add(builder.build());
        }
        result.sort(Comparator.comparing(EventInfo::eventSimpleName)
                .thenComparing(EventInfo::eventType));
        return result;
    }

    private void extractListeners(SourceIndex.TypeInfo concrete) {
        for (List<MethodRef> refs : methodRefs(concrete).values()) {
            for (MethodRef ref : refs) {
                if (ref.method().isPrivate() || ref.method().isStatic()) {
                    continue;
                }

                List<ListenerSpec> specs = listenerSpecs(ref.method());
                if (specs.isEmpty()) {
                    continue;
                }

                for (ListenerSpec spec : specs) {
                    for (String eventType : spec.eventTypes()) {
                        String resolved = resolveTypeName(ref.declaringType().unit().unit(), eventType);
                        EventBuilder builder = builderFor(resolved, ref.declaringType().unit().unit());
                        builder.addListener(new EventListenerInfo(
                                concrete.simpleName() + "#" + ref.method().getNameAsString(),
                                concrete.fqn(),
                                ref.method().getNameAsString(),
                                ref.declaringType().fqn(),
                                spec.annotationName(),
                                spec.condition(),
                                displayFile(ref.declaringType().unit().file()),
                                lineOf(spec.origin(), ref.method()),
                                methodLine(ref.method())));
                    }
                }
                break;
            }
        }
    }

    private void extractPublishers(SourceIndex.TypeInfo type) {
        CompilationUnit unit = type.unit().unit();

        for (BodyDeclaration<?> member : type.declaration().getMembers()) {
            if (!(member instanceof MethodDeclaration method)) {
                continue;
            }
            if (method.isPrivate() || method.isStatic()) {
                continue;
            }

            for (MethodCallExpr call : method.findAll(MethodCallExpr.class)) {
                String callName = call.getNameAsString();
                if (!callName.equals("publishEvent") && !callName.equals("multicastEvent")) {
                    continue;
                }
                if (call.getArguments().isEmpty()) {
                    continue;
                }

                List<String> eventTypes = resolveEventTypes(call.getArgument(0), call, method, type);
                String receiver = call.getScope().map(Node::toString).orElse("");
                String receiverType = call.getScope()
                        .map(scope -> resolveExpressionType(scope, call, method, type, unit))
                        .orElse("");

                boolean knownPublisher = isPublisherType(receiverType)
                        || (receiverType.isEmpty() && typeIsPublisher(type));
                String confidence = knownPublisher ? "high" : "name-only";

                for (String eventType : eventTypes) {
                    String resolved = resolveTypeName(unit, eventType);
                    EventBuilder builder = builderFor(resolved, unit);
                    builder.addPublisher(new EventPublisherInfo(
                            type.simpleName() + "#" + method.getNameAsString(),
                            type.fqn(),
                            method.getNameAsString(),
                            call.getArgument(0).toString(),
                            receiver,
                            receiverType,
                            confidence,
                            displayFile(type.unit().file()),
                            lineOf(call, method),
                            methodLine(method)));
                }
            }
        }
    }

    private List<ListenerSpec> listenerSpecs(MethodDeclaration method) {
        List<ListenerSpec> specs = new ArrayList<>();
        for (AnnotationExpr annotation : method.getAnnotations()) {
            specs.addAll(findListenerSpecs(annotation, method, new HashSet<>()));
        }

        Map<String, ListenerSpec> unique = new LinkedHashMap<>();
        for (ListenerSpec spec : specs) {
            String key = spec.annotationName() + "|" + spec.eventTypes() + "|"
                    + spec.condition() + "|" + lineOf(spec.origin(), method);
            unique.putIfAbsent(key, spec);
        }
        return new ArrayList<>(unique.values());
    }

    private List<ListenerSpec> findListenerSpecs(
            AnnotationExpr annotation,
            MethodDeclaration method,
            Set<String> visited) {

        String name = SourceIndex.simpleName(annotation.getNameAsString());
        CompilationUnit unit = compilationUnitOf(annotation);

        if (name.equals("EventListener") || name.equals("TransactionalEventListener")) {
            List<String> explicit = explicitEventTypes(annotation, unit);
            List<String> eventTypes = explicit.isEmpty()
                    ? parameterEventTypes(method, unit)
                    : explicit;
            return List.of(new ListenerSpec(
                    eventTypes,
                    conditionOf(annotation),
                    name,
                    annotation));
        }

        if (!visited.add(name)) {
            return List.of();
        }

        List<ListenerSpec> result = new ArrayList<>();
        if (unit != null) {
            for (AnnotationDeclaration declaration : index.annotationDeclarations(unit, annotation.getNameAsString())) {
                List<ListenerSpec> meta = new ArrayList<>();
                for (AnnotationExpr metaAnnotation : declaration.getAnnotations()) {
                    meta.addAll(findListenerSpecs(metaAnnotation, method, new HashSet<>(visited)));
                }
                if (meta.isEmpty()) {
                    continue;
                }

                List<String> useTypes = explicitEventTypes(annotation, unit);
                String useCondition = conditionOf(annotation);
                for (ListenerSpec spec : meta) {
                    result.add(new ListenerSpec(
                            useTypes.isEmpty() ? spec.eventTypes() : useTypes,
                            useCondition.isEmpty() ? spec.condition() : useCondition,
                            spec.annotationName(),
                            annotation));
                }
            }
        }
        return result;
    }

    private List<String> explicitEventTypes(AnnotationExpr annotation, CompilationUnit unit) {
        List<String> eventTypes = new ArrayList<>();

        for (Expression expression : memberValues(annotation, "classes", "value")) {
            for (Expression value : flatten(expression)) {
                if (value instanceof ClassExpr classExpr) {
                    eventTypes.add(resolveTypeName(unit, classExpr.getType().asString()));
                }
            }
        }

        return eventTypes.stream().distinct().toList();
    }

    private List<String> parameterEventTypes(MethodDeclaration method, CompilationUnit unit) {
        if (method.getParameters().isEmpty()) {
            return List.of("<unknown>");
        }
        return List.of(eventTypeFromType(method.getParameter(0).getType(), unit));
    }

    private String eventTypeFromType(Type type, CompilationUnit unit) {
        if (type.isClassOrInterfaceType()) {
            ClassOrInterfaceType classType = type.asClassOrInterfaceType();
            if (classType.getNameAsString().equals("PayloadApplicationEvent")
                    && classType.getTypeArguments().isPresent()
                    && !classType.getTypeArguments().get().isEmpty()) {
                return resolveTypeName(unit, classType.getTypeArguments().get().get(0).asString());
            }
            return resolveTypeName(unit, classType.getNameWithScope());
        }
        return resolveTypeName(unit, type.asString());
    }

    private List<String> resolveEventTypes(
            Expression expression,
            Node context,
            MethodDeclaration method,
            SourceIndex.TypeInfo type) {

        CompilationUnit unit = type.unit().unit();
        Expression value = unwrap(expression);

        if (value instanceof ConditionalExpr conditional) {
            List<String> types = new ArrayList<>();
            types.addAll(resolveEventTypes(conditional.getThenExpr(), context, method, type));
            types.addAll(resolveEventTypes(conditional.getElseExpr(), context, method, type));
            return types.stream().distinct().toList();
        }

        String typeName = resolveExpressionType(value, context, method, type, unit);
        if (typeName == null || typeName.isBlank()) {
            return List.of("<unknown>");
        }
        return List.of(typeName);
    }

    private String resolveExpressionType(
            Expression expression,
            Node context,
            MethodDeclaration method,
            SourceIndex.TypeInfo type,
            CompilationUnit unit) {

        Expression value = unwrap(expression);

        if (value instanceof ObjectCreationExpr creation) {
            return resolveTypeName(unit, creation.getType().asString());
        }
        if (value instanceof ClassExpr classExpr) {
            return resolveTypeName(unit, classExpr.getType().asString());
        }
        if (value instanceof NameExpr name) {
            return variableType(name.getNameAsString(), context, method, type, unit);
        }
        if (value instanceof FieldAccessExpr field) {
            if (field.getScope() instanceof ThisExpr) {
                return variableType(field.getNameAsString(), context, method, type, unit);
            }
            String owner = resolveExpressionType(field.getScope(), context, method, type, unit);
            String fieldType = fieldType(owner, field.getNameAsString());
            return fieldType.isBlank()
                    ? variableType(field.getNameAsString(), context, method, type, unit)
                    : fieldType;
        }
        if (value instanceof MethodCallExpr call) {
            return returnTypeOf(call, context, method, type, unit);
        }
        if (value instanceof AssignExpr assign) {
            return resolveExpressionType(assign.getValue(), context, method, type, unit);
        }

        return "";
    }

    private String variableType(
            String name,
            Node context,
            MethodDeclaration method,
            SourceIndex.TypeInfo type,
            CompilationUnit unit) {

        for (Parameter parameter : method.getParameters()) {
            if (parameter.getNameAsString().equals(name)) {
                return resolveTypeName(unit, parameter.getType().asString());
            }
        }

        for (VariableDeclarator variable : method.findAll(VariableDeclarator.class)) {
            if (variable.getNameAsString().equals(name)) {
                return resolveTypeName(unit, variable.getType().asString());
            }
        }

        Optional<LambdaExpr> lambda = context.findAncestor(LambdaExpr.class);
        if (lambda.isPresent()) {
            for (Parameter parameter : lambda.get().getParameters()) {
                if (parameter.getNameAsString().equals(name) && parameter.getType() != null) {
                    return resolveTypeName(unit, parameter.getType().asString());
                }
            }
        }

        for (SourceIndex.TypeInfo owner : index.hierarchy(type)) {
            for (BodyDeclaration<?> member : owner.declaration().getMembers()) {
                if (!(member instanceof FieldDeclaration field)) {
                    continue;
                }
                for (VariableDeclarator variable : field.getVariables()) {
                    if (variable.getNameAsString().equals(name)) {
                        return resolveTypeName(owner.unit().unit(), variable.getType().asString());
                    }
                }
            }
        }

        return "";
    }

    private String fieldType(String ownerType, String fieldName) {
        if (ownerType == null || ownerType.isBlank()) {
            return "";
        }

        SourceIndex.TypeInfo owner = index.byFqn(ownerType);
        if (owner == null) {
            return "";
        }

        for (SourceIndex.TypeInfo type : index.hierarchy(owner)) {
            for (BodyDeclaration<?> member : type.declaration().getMembers()) {
                if (!(member instanceof FieldDeclaration field)) {
                    continue;
                }
                for (VariableDeclarator variable : field.getVariables()) {
                    if (variable.getNameAsString().equals(fieldName)) {
                        return resolveTypeName(type.unit().unit(), variable.getType().asString());
                    }
                }
            }
        }
        return "";
    }

    private String returnTypeOf(
            MethodCallExpr call,
            Node context,
            MethodDeclaration method,
            SourceIndex.TypeInfo type,
            CompilationUnit unit) {

        SourceIndex.TypeInfo owner = call.getScope()
                .map(scope -> resolveExpressionType(scope, context, method, type, unit))
                .map(index::byFqn)
                .orElse(type);

        if (owner == null) {
            return "";
        }

        int argumentCount = call.getArguments().size();
        for (SourceIndex.TypeInfo candidate : index.hierarchy(owner)) {
            for (BodyDeclaration<?> member : candidate.declaration().getMembers()) {
                if (member instanceof MethodDeclaration candidateMethod
                        && candidateMethod.getNameAsString().equals(call.getNameAsString())
                        && candidateMethod.getParameters().size() == argumentCount) {
                    return resolveTypeName(candidate.unit().unit(), candidateMethod.getType().asString());
                }
            }
        }
        return "";
    }

    private Map<String, List<MethodRef>> methodRefs(SourceIndex.TypeInfo concrete) {
        Map<String, List<MethodRef>> methods = new LinkedHashMap<>();
        for (SourceIndex.TypeInfo type : index.hierarchy(concrete)) {
            for (BodyDeclaration<?> member : type.declaration().getMembers()) {
                if (member instanceof MethodDeclaration method) {
                    methods.computeIfAbsent(methodKey(method), key -> new ArrayList<>())
                            .add(new MethodRef(method, type));
                }
            }
        }
        return methods;
    }

    private static String methodKey(MethodDeclaration method) {
        return method.getNameAsString() + "(" + method.getParameters().stream()
                .map(parameter -> parameter.getType().asString())
                .collect(Collectors.joining(",")) + ")";
    }

    private EventBuilder builderFor(String eventType, CompilationUnit unit) {
        String resolved = unit == null ? eventType : index.resolveTypeName(unit, eventType);
        String simple = SourceIndex.simpleName(resolved);
        EventBuilder builder = events.computeIfAbsent(simple.toLowerCase(), key -> new EventBuilder(simple));
        builder.mergeType(resolved);

        SourceIndex.TypeInfo declaration = unit == null ? index.byFqn(resolved) : index.resolveType(unit, resolved);
        if (declaration != null) {
            builder.fillDeclaration(declaration);
        }
        return builder;
    }

    private boolean extendsApplicationEvent(SourceIndex.TypeInfo type) {
        return extendsApplicationEvent(type, new HashSet<>());
    }

    private boolean extendsApplicationEvent(SourceIndex.TypeInfo type, Set<String> visited) {
        if (!visited.add(type.fqn())) {
            return false;
        }
        if (!(type.declaration() instanceof ClassOrInterfaceDeclaration classDeclaration)
                || classDeclaration.isInterface()) {
            return false;
        }

        for (ClassOrInterfaceType parent : classDeclaration.getExtendedTypes()) {
            if (SourceIndex.simpleName(parent.getNameWithScope()).equals("ApplicationEvent")) {
                return true;
            }
            SourceIndex.TypeInfo parentType = index.resolveType(
                    type.unit().unit(),
                    parent.getNameWithScope());
            if (parentType != null && extendsApplicationEvent(parentType, visited)) {
                return true;
            }
        }
        return false;
    }

    private boolean typeIsPublisher(SourceIndex.TypeInfo type) {
        return index.hierarchy(type).stream()
                .anyMatch(candidate -> PUBLISHER_TYPES.contains(candidate.simpleName()));
    }

    private static boolean isPublisherType(String typeName) {
        return typeName != null && !typeName.isBlank()
                && PUBLISHER_TYPES.contains(SourceIndex.simpleName(typeName));
    }

    private boolean isConcrete(SourceIndex.TypeInfo type) {
        TypeDeclaration<?> declaration = type.declaration();
        if (declaration instanceof ClassOrInterfaceDeclaration classDeclaration) {
            return !classDeclaration.isInterface() && !classDeclaration.isAbstract();
        }
        return declaration instanceof RecordDeclaration;
    }

    private String resolveTypeName(CompilationUnit unit, String name) {
        if (name == null || name.isBlank() || name.equals("<unknown>")) {
            return "<unknown>";
        }
        if (unit == null) {
            return name;
        }
        return index.resolveTypeName(unit, name);
    }

    private String displayFile(Path file) {
        if (options.absolutePaths()) {
            return file.toString().replace('\\', '/');
        }
        return root.relativize(file).toString().replace('\\', '/');
    }

    private static int methodLine(MethodDeclaration method) {
        return method.getBegin().map(position -> position.line).orElse(-1);
    }

    private static int lineOf(Node node, MethodDeclaration fallback) {
        return node.getBegin().map(position -> position.line).orElse(methodLine(fallback));
    }

    private static CompilationUnit compilationUnitOf(Node node) {
        return node.findCompilationUnit().orElse(null);
    }

    private static CompilationUnit compilationUnitOf(NodeWithAnnotations<?> node) {
        return node instanceof Node astNode ? compilationUnitOf(astNode) : null;
    }

    private static Expression unwrap(Expression expression) {
        Expression current = expression;
        while (true) {
            if (current instanceof EnclosedExpr enclosed) {
                current = enclosed.getInner();
            } else if (current instanceof CastExpr cast) {
                current = cast.getExpression();
            } else {
                return current;
            }
        }
    }

    private static List<Expression> flatten(Expression expression) {
        if (expression instanceof ArrayInitializerExpr array) {
            return new ArrayList<>(array.getValues());
        }
        return List.of(expression);
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

    private static String conditionOf(AnnotationExpr annotation) {
        for (Expression expression : memberValues(annotation, "condition")) {
            if (expression instanceof StringLiteralExpr literal) {
                return literal.asString();
            }
            return expression.toString();
        }
        return "";
    }

    private record MethodRef(MethodDeclaration method, SourceIndex.TypeInfo declaringType) {
    }

    private record ListenerSpec(
            List<String> eventTypes,
            String condition,
            String annotationName,
            AnnotationExpr origin) {
    }

    private final class EventBuilder {
        private final String simpleName;
        private String eventType;
        private String declaredIn = "";
        private String file = "";
        private int line = -1;
        private final List<EventPublisherInfo> publishers = new ArrayList<>();
        private final List<EventListenerInfo> listeners = new ArrayList<>();
        private final Set<String> publisherKeys = new HashSet<>();
        private final Set<String> listenerKeys = new HashSet<>();

        private EventBuilder(String simpleName) {
            this.simpleName = simpleName;
            this.eventType = simpleName;
        }

        private void mergeType(String type) {
            if (type == null || type.isBlank() || type.equals("<unknown>")) {
                return;
            }
            if (eventType.equals(simpleName) && type.contains(".")) {
                eventType = type;
            }
        }

        private void fillDeclaration(SourceIndex.TypeInfo type) {
            if (!declaredIn.isEmpty()) {
                return;
            }
            declaredIn = type.fqn();
            file = displayFile(type.unit().file());
            line = type.declaration().getBegin().map(position -> position.line).orElse(-1);
            mergeType(type.fqn());
        }

        private void addPublisher(EventPublisherInfo publisher) {
            String key = publisher.file() + ":" + publisher.line() + ":"
                    + publisher.expression() + ":" + publisher.handler();
            if (publisherKeys.add(key)) {
                publishers.add(publisher);
            }
        }

        private void addListener(EventListenerInfo listener) {
            String key = listener.file() + ":" + listener.line() + ":"
                    + listener.handler() + ":" + listener.annotation();
            if (listenerKeys.add(key)) {
                listeners.add(listener);
            }
        }

        private EventInfo build() {
            publishers.sort(Comparator.comparing(EventPublisherInfo::file)
                    .thenComparingInt(EventPublisherInfo::line)
                    .thenComparing(EventPublisherInfo::handler));
            listeners.sort(Comparator.comparing(EventListenerInfo::file)
                    .thenComparingInt(EventListenerInfo::line)
                    .thenComparing(EventListenerInfo::handler));
            return new EventInfo(
                    eventType,
                    simpleName,
                    declaredIn,
                    file,
                    line,
                    publishers,
                    listeners);
        }
    }
}
