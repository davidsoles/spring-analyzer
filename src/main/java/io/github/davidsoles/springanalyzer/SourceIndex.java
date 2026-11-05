package io.github.davidsoles.springanalyzer;

import com.github.javaparser.ast.CompilationUnit;
import com.github.javaparser.ast.ImportDeclaration;
import com.github.javaparser.ast.body.AnnotationDeclaration;
import com.github.javaparser.ast.body.BodyDeclaration;
import com.github.javaparser.ast.body.ClassOrInterfaceDeclaration;
import com.github.javaparser.ast.body.EnumDeclaration;
import com.github.javaparser.ast.body.FieldDeclaration;
import com.github.javaparser.ast.body.RecordDeclaration;
import com.github.javaparser.ast.body.TypeDeclaration;
import com.github.javaparser.ast.body.VariableDeclarator;
import com.github.javaparser.ast.expr.Expression;
import com.github.javaparser.ast.type.ClassOrInterfaceType;
import com.github.javaparser.ast.type.Type;

import java.nio.file.Path;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Deque;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

/**
 * Index of all parsed files: types, hierarchy, annotations, and String constants.
 * It is built in a first pass so references across files can be resolved.
 */
final class SourceIndex {

    record UnitInfo(Path file, CompilationUnit unit) {
    }

    record TypeInfo(String fqn, String simpleName, TypeDeclaration<?> declaration, UnitInfo unit) {
    }

    private final Map<String, TypeInfo> typesByFqn = new LinkedHashMap<>();
    private final Map<String, List<TypeInfo>> typesBySimpleName = new HashMap<>();
    private final Map<String, List<AnnotationDeclaration>> annotationsBySimpleName = new HashMap<>();
    private final Map<String, Expression> constants = new LinkedHashMap<>();
    private final Set<String> ambiguousConstants = new HashSet<>();

    void add(UnitInfo unitInfo) {
        String packageName = unitInfo.unit().getPackageDeclaration()
                .map(pd -> pd.getNameAsString())
                .orElse("");
        for (TypeDeclaration<?> type : unitInfo.unit().getTypes()) {
            indexType(type, packageName, unitInfo);
        }
    }

    private void indexType(TypeDeclaration<?> type, String prefix, UnitInfo unit) {
        String fqn = prefix.isEmpty() ? type.getNameAsString() : prefix + "." + type.getNameAsString();
        TypeInfo info = new TypeInfo(fqn, type.getNameAsString(), type, unit);
        typesByFqn.putIfAbsent(fqn, info);
        typesBySimpleName.computeIfAbsent(type.getNameAsString(), k -> new ArrayList<>()).add(info);

        if (type instanceof AnnotationDeclaration annotation) {
            annotationsBySimpleName.computeIfAbsent(type.getNameAsString(), k -> new ArrayList<>()).add(annotation);
        }

        indexFields(type, fqn);

        for (BodyDeclaration<?> member : type.getMembers()) {
            if (member instanceof TypeDeclaration<?> nested) {
                indexType(nested, fqn, unit);
            }
        }
    }

    private void indexFields(TypeDeclaration<?> type, String fqn) {
        for (BodyDeclaration<?> member : type.getMembers()) {
            if (!(member instanceof FieldDeclaration field)) {
                continue;
            }
            for (VariableDeclarator variable : field.getVariables()) {
                if (!isStringType(variable.getType())) {
                    continue;
                }
                variable.getInitializer().ifPresent(initializer -> {
                    addConstant(variable.getNameAsString(), initializer);
                    addConstant(type.getNameAsString() + "." + variable.getNameAsString(), initializer);
                    addConstant(fqn + "." + variable.getNameAsString(), initializer);
                });
            }
        }
    }

    private static boolean isStringType(Type type) {
        if (!type.isClassOrInterfaceType()) {
            return false;
        }
        ClassOrInterfaceType classType = type.asClassOrInterfaceType();
        String typeName = classType.getNameWithScope();
        return typeName.equals("String") || typeName.equals("java.lang.String");
    }

    private void addConstant(String key, Expression expression) {
        Expression existing = constants.get(key);
        if (existing == null) {
            constants.put(key, expression);
        } else if (existing != expression) {
            ambiguousConstants.add(key);
        }
    }

    Collection<TypeInfo> types() {
        return typesByFqn.values();
    }

    Optional<Expression> constant(String key) {
        if (ambiguousConstants.contains(key)) {
            return Optional.empty();
        }
        Expression expression = constants.get(key);
        if (expression != null) {
            return Optional.of(expression);
        }

        int dot = key.lastIndexOf('.');
        if (dot > 0) {
            String alternative = simpleName(key.substring(0, dot)) + "." + key.substring(dot + 1);
            if (!alternative.equals(key) && !ambiguousConstants.contains(alternative)) {
                expression = constants.get(alternative);
                if (expression != null) {
                    return Optional.of(expression);
                }
            }
        }
        return Optional.empty();
    }

    boolean isAmbiguousConstant(String key) {
        if (ambiguousConstants.contains(key)) {
            return true;
        }
        int dot = key.lastIndexOf('.');
        if (dot <= 0) {
            return false;
        }
        return ambiguousConstants.contains(simpleName(key.substring(0, dot)) + "." + key.substring(dot + 1));
    }

    List<AnnotationDeclaration> annotationDeclarations(CompilationUnit unit, String name) {
        String fqn = resolveTypeName(unit, name);
        TypeInfo info = typesByFqn.get(fqn);
        if (info != null) {
            if (info.declaration() instanceof AnnotationDeclaration annotation) {
                return List.of(annotation);
            }
            return List.of();
        }

        List<AnnotationDeclaration> candidates = annotationsBySimpleName.get(simpleName(name));
        return candidates == null ? List.of() : candidates;
    }

    TypeInfo resolveType(CompilationUnit unit, String name) {
        String fqn = resolveTypeName(unit, name);
        TypeInfo byFqn = typesByFqn.get(fqn);
        if (byFqn != null) {
            return byFqn;
        }

        List<TypeInfo> candidates = typesBySimpleName.get(simpleName(name));
        if (candidates != null && candidates.size() == 1) {
            return candidates.get(0);
        }
        return null;
    }

    String resolveTypeName(CompilationUnit unit, String name) {
        if (typesByFqn.containsKey(name)) {
            return name;
        }

        String packageName = unit.getPackageDeclaration()
                .map(pd -> pd.getNameAsString())
                .orElse("");

        if (!name.contains(".")) {
            for (ImportDeclaration importDeclaration : unit.getImports()) {
                if (importDeclaration.isStatic()) {
                    continue;
                }
                String imported = importDeclaration.getNameAsString();
                if (importDeclaration.isAsterisk()) {
                    String candidate = imported + "." + name;
                    if (typesByFqn.containsKey(candidate)) {
                        return candidate;
                    }
                } else if (simpleName(imported).equals(name)) {
                    return imported;
                }
            }
            if (!packageName.isEmpty()) {
                String candidate = packageName + "." + name;
                if (typesByFqn.containsKey(candidate)) {
                    return candidate;
                }
            }
            return name;
        }

        int firstDot = name.indexOf('.');
        String head = name.substring(0, firstDot);
        String tail = name.substring(firstDot + 1);
        for (ImportDeclaration importDeclaration : unit.getImports()) {
            if (importDeclaration.isStatic() || importDeclaration.isAsterisk()) {
                continue;
            }
            String imported = importDeclaration.getNameAsString();
            if (simpleName(imported).equals(head)) {
                String candidate = imported + "." + tail;
                if (typesByFqn.containsKey(candidate)) {
                    return candidate;
                }
            }
        }
        if (!packageName.isEmpty()) {
            String candidate = packageName + "." + name;
            if (typesByFqn.containsKey(candidate)) {
                return candidate;
            }
        }
        return name;
    }

    List<TypeInfo> hierarchy(TypeInfo type) {
        Map<String, TypeInfo> result = new LinkedHashMap<>();
        Deque<TypeInfo> queue = new ArrayDeque<>();
        queue.add(type);

        while (!queue.isEmpty()) {
            TypeInfo current = queue.removeFirst();
            if (result.putIfAbsent(current.fqn(), current) != null) {
                continue;
            }
            queue.addAll(supertypes(current));
        }
        return new ArrayList<>(result.values());
    }

    private List<TypeInfo> supertypes(TypeInfo type) {
        List<ClassOrInterfaceType> references = new ArrayList<>();
        TypeDeclaration<?> declaration = type.declaration();

        if (declaration instanceof ClassOrInterfaceDeclaration classDeclaration) {
            references.addAll(classDeclaration.getExtendedTypes());
            references.addAll(classDeclaration.getImplementedTypes());
        } else if (declaration instanceof EnumDeclaration enumDeclaration) {
            references.addAll(enumDeclaration.getImplementedTypes());
        } else if (declaration instanceof RecordDeclaration recordDeclaration) {
            references.addAll(recordDeclaration.getImplementedTypes());
        }

        List<TypeInfo> result = new ArrayList<>();
        Set<String> seen = new HashSet<>();
        for (ClassOrInterfaceType reference : references) {
            TypeInfo resolved = resolveType(type.unit().unit(), reference.getNameWithScope());
            if (resolved != null && seen.add(resolved.fqn())) {
                result.add(resolved);
            }
        }
        return result;
    }

    static String simpleName(String name) {
        int dot = name.lastIndexOf('.');
        return dot < 0 ? name : name.substring(dot + 1);
    }
}
