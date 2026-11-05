package io.github.davidsoles.springanalyzer.endpoints;

import io.github.davidsoles.springanalyzer.common.SourceIndex;

import com.github.javaparser.ast.CompilationUnit;
import com.github.javaparser.ast.Node;
import com.github.javaparser.ast.expr.BinaryExpr;
import com.github.javaparser.ast.expr.CastExpr;
import com.github.javaparser.ast.expr.EnclosedExpr;
import com.github.javaparser.ast.expr.Expression;
import com.github.javaparser.ast.expr.FieldAccessExpr;
import com.github.javaparser.ast.expr.NameExpr;
import com.github.javaparser.ast.expr.StringLiteralExpr;
import com.github.javaparser.ast.expr.TextBlockLiteralExpr;

import java.util.HashSet;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

/**
 * Resolves String expressions from annotations: literals, concatenations,
 * constants declared in the repository, and a few well-known Spring constants.
 */
final class ConstantResolver {

    private static final Map<String, String> BUILT_INS = Map.ofEntries(
            Map.entry("APPLICATION_JSON_VALUE", "application/json"),
            Map.entry("APPLICATION_JSON", "application/json"),
            Map.entry("APPLICATION_XML_VALUE", "application/xml"),
            Map.entry("APPLICATION_XML", "application/xml"),
            Map.entry("TEXT_PLAIN_VALUE", "text/plain"),
            Map.entry("TEXT_PLAIN", "text/plain"),
            Map.entry("TEXT_HTML_VALUE", "text/html"),
            Map.entry("TEXT_HTML", "text/html"),
            Map.entry("APPLICATION_FORM_URLENCODED_VALUE", "application/x-www-form-urlencoded"),
            Map.entry("MULTIPART_FORM_DATA_VALUE", "multipart/form-data"),
            Map.entry("APPLICATION_OCTET_STREAM_VALUE", "application/octet-stream"),
            Map.entry("ALL_VALUE", "*/*"),
            Map.entry("ALL", "*/*"));

    private final SourceIndex index;
    private final Set<String> warnings;

    ConstantResolver(SourceIndex index, Set<String> warnings) {
        this.index = index;
        this.warnings = warnings;
    }

    String resolveOrRaw(Expression expression, Node context) {
        Optional<String> resolved = resolve(expression, context);
        if (resolved.isPresent()) {
            return resolved.get();
        }

        warnings.add("Could not resolve expression '" + expression
                + "' at " + location(context) + "; using the raw expression text.");
        return expression.toString();
    }

    private Optional<String> resolve(Expression expression, Node context) {
        return resolve(expression, context, new HashSet<>());
    }

    private Optional<String> resolve(Expression expression, Node context, Set<String> seen) {
        if (expression instanceof StringLiteralExpr literal) {
            return Optional.of(literal.asString());
        }
        if (expression instanceof TextBlockLiteralExpr textBlock) {
            return Optional.of(textBlock.asString());
        }
        if (expression instanceof EnclosedExpr enclosed) {
            return resolve(enclosed.getInner(), context, seen);
        }
        if (expression instanceof CastExpr cast) {
            return resolve(cast.getExpression(), context, seen);
        }
        if (expression instanceof BinaryExpr binary && binary.getOperator() == BinaryExpr.Operator.PLUS) {
            Optional<String> left = resolve(binary.getLeft(), context, seen);
            Optional<String> right = resolve(binary.getRight(), context, seen);
            if (left.isPresent() && right.isPresent()) {
                return Optional.of(left.get() + right.get());
            }
            return Optional.empty();
        }
        if (expression instanceof NameExpr name) {
            return resolveName(name.getNameAsString(), context, seen);
        }
        if (expression instanceof FieldAccessExpr fieldAccess) {
            return resolveName(fieldAccess.toString(), context, seen);
        }
        return Optional.empty();
    }

    private Optional<String> resolveName(String key, Node context, Set<String> seen) {
        String builtIn = BUILT_INS.get(key);
        if (builtIn == null) {
            builtIn = BUILT_INS.get(SourceIndex.simpleName(key));
        }
        if (builtIn != null) {
            return Optional.of(builtIn);
        }

        if (!seen.add(key)) {
            return Optional.empty();
        }
        try {
            Optional<Expression> expression = index.constant(key);
            if (expression.isEmpty()) {
                if (index.isAmbiguousConstant(key)) {
                    warnings.add("Constant '" + key + "' is ambiguous at "
                            + location(context) + "; using the raw expression text.");
                }
                return Optional.empty();
            }
            return resolve(expression.get(), context, seen);
        } finally {
            seen.remove(key);
        }
    }

    private static String location(Node node) {
        String file = node.findCompilationUnit()
                .flatMap(CompilationUnit::getStorage)
                .map(storage -> storage.getPath().toString())
                .orElse("<unknown file>");
        int line = node.getBegin().map(position -> position.line).orElse(-1);
        return file + ":" + line;
    }
}
