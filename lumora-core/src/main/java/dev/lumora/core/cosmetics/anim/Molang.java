package dev.lumora.core.cosmetics.anim;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/**
 * The part of Molang that keyframes of GeckoLib/Blockbench animations use: numbers, {@code + - * / %}, comparisons,
 * {@code && || !}, {@code ?:}, {@code ??}, parentheses, {@code math.*} functions (angles in degrees, as in Bedrock),
 * {@code query.*}/{@code q.*} values of the player and {@code variable.*}/{@code v.*} variables with assignments
 * ({@code v.a = 3; v.a * 2}). Expressions are compiled once; constant parts are folded, so plain numbers cost nothing
 * per frame. Unknown queries and functions are 0, as in GeckoLib.
 * <p>
 * Modelled on GeckoLib's {@code MathParser} (MIT, see {@link Easing}); own, smaller implementation.
 */
public final class Molang {
    private Molang() {
    }

    /** A compiled expression. */
    public interface Expr {
        double eval(Scope scope);

        /** {@code true} if the value never changes (then {@link #eval} may get {@code null}). */
        default boolean constant() {
            return false;
        }
    }

    /** Player values readable as {@code query.<name>}. */
    public enum Query {
        ANIM_TIME("anim_time"),
        LIFE_TIME("life_time"),
        GROUND_SPEED("ground_speed"),
        VERTICAL_SPEED("vertical_speed"),
        IS_ON_GROUND("is_on_ground"),
        IS_SNEAKING("is_sneaking"),
        IS_SPRINTING("is_sprinting"),
        IS_SWIMMING("is_swimming"),
        IS_GLIDING("is_gliding"),
        IS_MOVING("is_moving"),
        HEAD_X_ROTATION("head_x_rotation"),
        HEAD_Y_ROTATION("head_y_rotation"),
        LIMB_SWING("limb_swing"),
        LIMB_SWING_AMOUNT("limb_swing_amount"),
        BODY_Y_ROTATION("body_y_rotation"),
        IS_FIRST_PERSON("is_first_person"),
        DELTA_TIME("delta_time");

        final String id;

        Query(String id) {
            this.id = id;
        }

        static Query of(String name) {
            for (Query q : values()) {
                if (q.id.equals(name)) {
                    return q;
                }
            }
            return null;
        }
    }

    /** Values the expressions read: queries and variables. One per animated thing, reused every frame. */
    public static final class Scope {
        final double[] queries = new double[Query.values().length];
        private String[] varNames = new String[0];
        private double[] varValues = new double[0];

        public void set(Query q, double value) {
            queries[q.ordinal()] = value;
        }

        public double get(Query q) {
            return queries[q.ordinal()];
        }

        double var(String name) {
            for (int i = 0; i < varNames.length; i++) {
                if (varNames[i].equals(name)) {
                    return varValues[i];
                }
            }
            return 0;
        }

        boolean hasVar(String name) {
            for (String n : varNames) {
                if (n.equals(name)) {
                    return true;
                }
            }
            return false;
        }

        void setVar(String name, double value) {
            for (int i = 0; i < varNames.length; i++) {
                if (varNames[i].equals(name)) {
                    varValues[i] = value;
                    return;
                }
            }
            int n = varNames.length;
            varNames = java.util.Arrays.copyOf(varNames, n + 1);
            varValues = java.util.Arrays.copyOf(varValues, n + 1);
            varNames[n] = name;
            varValues[n] = value;
        }
    }

    /** A constant expression. */
    public static Expr constant(double value) {
        return new Const(value);
    }

    /**
     * Compiles {@code source}.
     *
     * @throws IllegalArgumentException on a syntax error (the message names the position)
     */
    public static Expr compile(String source) {
        if (source == null || source.isBlank()) {
            return new Const(0);
        }
        Parser p = new Parser(source);
        Expr e = p.statements();
        if (p.pos < p.src.length()) {
            throw p.error("unexpected '" + p.src.charAt(p.pos) + "'");
        }
        return e;
    }

    // --- nodes

    private record Const(double value) implements Expr {
        @Override
        public double eval(Scope scope) {
            return value;
        }

        @Override
        public boolean constant() {
            return true;
        }
    }

    private record QueryValue(Query query) implements Expr {
        @Override
        public double eval(Scope scope) {
            return scope == null ? 0 : scope.queries[query.ordinal()];
        }
    }

    private record Variable(String name) implements Expr {
        @Override
        public double eval(Scope scope) {
            return scope == null ? 0 : scope.var(name);
        }
    }

    private record Assign(String name, Expr value) implements Expr {
        @Override
        public double eval(Scope scope) {
            double v = value.eval(scope);
            if (scope != null) {
                scope.setVar(name, v);
            }
            return v;
        }
    }

    private record Sequence(Expr[] parts) implements Expr {
        @Override
        public double eval(Scope scope) {
            double v = 0;
            for (Expr e : parts) {
                v = e.eval(scope);
            }
            return v;
        }
    }

    private interface Op2 {
        double apply(double a, double b);
    }

    private record Binary(Expr a, Expr b, Op2 op) implements Expr {
        @Override
        public double eval(Scope scope) {
            return op.apply(a.eval(scope), b.eval(scope));
        }
    }

    private record And(Expr a, Expr b) implements Expr {
        @Override
        public double eval(Scope scope) {
            return a.eval(scope) != 0 && b.eval(scope) != 0 ? 1 : 0;
        }
    }

    private record Or(Expr a, Expr b) implements Expr {
        @Override
        public double eval(Scope scope) {
            return a.eval(scope) != 0 || b.eval(scope) != 0 ? 1 : 0;
        }
    }

    private record Ternary(Expr test, Expr yes, Expr no) implements Expr {
        @Override
        public double eval(Scope scope) {
            return test.eval(scope) != 0 ? yes.eval(scope) : no.eval(scope);
        }
    }

    private record Negate(Expr a) implements Expr {
        @Override
        public double eval(Scope scope) {
            return -a.eval(scope);
        }
    }

    private record Not(Expr a) implements Expr {
        @Override
        public double eval(Scope scope) {
            return a.eval(scope) == 0 ? 1 : 0;
        }
    }

    private interface Fn {
        double apply(double[] args);
    }

    /** {@code a ?? b}: b while the variable a was never set. */
    private record Coalesce(Expr a, Expr b) implements Expr {
        @Override
        public double eval(Scope scope) {
            if (a instanceof Variable v && (scope == null || !scope.hasVar(v.name))) {
                return b.eval(scope);
            }
            return a.eval(scope);
        }
    }

    /** A function call; the argument buffer is reused (expressions are evaluated on the render thread only). */
    private static final class Call implements Expr {
        final Fn fn;
        final Expr[] args;
        private final double[] values;

        Call(Fn fn, Expr[] args) {
            this.fn = fn;
            this.args = args;
            this.values = new double[args.length];
        }

        @Override
        public double eval(Scope scope) {
            for (int i = 0; i < args.length; i++) {
                values[i] = args[i].eval(scope);
            }
            return fn.apply(values);
        }
    }

    private record Random(double min, double max, boolean integer) implements Expr {
        @Override
        public double eval(Scope scope) {
            double r = min + Math.random() * (max - min);
            return integer ? Math.floor(r) : r;
        }
    }

    private static Expr fold(Expr e) {
        if (e instanceof Binary b && b.a.constant() && b.b.constant()
                || e instanceof Negate n && n.a.constant()
                || e instanceof Not o && o.a.constant()
                || e instanceof And x && x.a.constant() && x.b.constant()
                || e instanceof Or y && y.a.constant() && y.b.constant()
                || e instanceof Ternary t && t.test.constant() && t.yes.constant() && t.no.constant()) {
            return new Const(e.eval(null));
        }
        if (e instanceof Call c) {
            for (Expr a : c.args) {
                if (!a.constant()) {
                    return e;
                }
            }
            return new Const(e.eval(null));
        }
        return e;
    }

    // --- parser

    private static final class Parser {
        final String src;
        int pos;

        Parser(String src) {
            this.src = src.toLowerCase(Locale.ROOT);
        }

        IllegalArgumentException error(String what) {
            return new IllegalArgumentException("Molang: " + what + " at " + pos + " in \"" + src + "\"");
        }

        void skip() {
            while (pos < src.length() && Character.isWhitespace(src.charAt(pos))) {
                pos++;
            }
        }

        boolean eat(String token) {
            skip();
            if (src.startsWith(token, pos)) {
                pos += token.length();
                return true;
            }
            return false;
        }

        boolean peek(String token) {
            skip();
            return src.startsWith(token, pos);
        }

        Expr statements() {
            List<Expr> parts = new ArrayList<>();
            do {
                skip();
                if (pos >= src.length()) {
                    break;
                }
                if (eat("return")) {
                    parts.add(ternary());
                } else {
                    parts.add(assignment());
                }
            } while (eat(";"));
            if (parts.isEmpty()) {
                return new Const(0);
            }
            return parts.size() == 1 ? parts.get(0) : new Sequence(parts.toArray(new Expr[0]));
        }

        Expr assignment() {
            int start = pos;
            skip();
            String name = variableName();
            if (name != null && peek("=") && !peek("==")) {
                eat("=");
                return new Assign(name, ternary());
            }
            pos = start;
            return ternary();
        }

        /** {@code v.x} / {@code variable.x} / {@code t.x} at the position, or {@code null} (position unchanged). */
        String variableName() {
            int start = pos;
            String ident = identifier();
            if (ident != null && (ident.startsWith("v.") || ident.startsWith("variable.") || ident.startsWith("t.")
                    || ident.startsWith("temp."))) {
                return ident.substring(ident.indexOf('.') + 1);
            }
            pos = start;
            return null;
        }

        Expr ternary() {
            Expr test = coalesce();
            if (eat("?")) {
                Expr yes = ternary();
                if (!eat(":")) {
                    throw error("':' expected");
                }
                Expr no = ternary();
                return fold(new Ternary(test, yes, no));
            }
            return test;
        }

        Expr coalesce() {
            Expr a = or();
            while (eat("??")) {
                a = new Coalesce(a, or());
            }
            return a;
        }

        Expr or() {
            Expr a = and();
            while (eat("||")) {
                a = fold(new Or(a, and()));
            }
            return a;
        }

        Expr and() {
            Expr a = equality();
            while (eat("&&")) {
                a = fold(new And(a, equality()));
            }
            return a;
        }

        Expr equality() {
            Expr a = comparison();
            while (true) {
                if (eat("==")) {
                    a = fold(new Binary(a, comparison(), (x, y) -> x == y ? 1 : 0));
                } else if (eat("!=")) {
                    a = fold(new Binary(a, comparison(), (x, y) -> x != y ? 1 : 0));
                } else {
                    return a;
                }
            }
        }

        Expr comparison() {
            Expr a = additive();
            while (true) {
                if (eat("<=")) {
                    a = fold(new Binary(a, additive(), (x, y) -> x <= y ? 1 : 0));
                } else if (eat(">=")) {
                    a = fold(new Binary(a, additive(), (x, y) -> x >= y ? 1 : 0));
                } else if (eat("<")) {
                    a = fold(new Binary(a, additive(), (x, y) -> x < y ? 1 : 0));
                } else if (eat(">")) {
                    a = fold(new Binary(a, additive(), (x, y) -> x > y ? 1 : 0));
                } else {
                    return a;
                }
            }
        }

        Expr additive() {
            Expr a = multiplicative();
            while (true) {
                if (eat("+")) {
                    a = fold(new Binary(a, multiplicative(), Double::sum));
                } else if (peek("-")) {
                    eat("-");
                    a = fold(new Binary(a, multiplicative(), (x, y) -> x - y));
                } else {
                    return a;
                }
            }
        }

        Expr multiplicative() {
            Expr a = unary();
            while (true) {
                if (eat("*")) {
                    a = fold(new Binary(a, unary(), (x, y) -> x * y));
                } else if (eat("/")) {
                    a = fold(new Binary(a, unary(), (x, y) -> y == 0 ? 0 : x / y));
                } else if (eat("%")) {
                    a = fold(new Binary(a, unary(), (x, y) -> y == 0 ? 0 : x % y));
                } else {
                    return a;
                }
            }
        }

        Expr unary() {
            if (eat("-")) {
                return fold(new Negate(unary()));
            }
            if (eat("+")) {
                return unary();
            }
            if (eat("!")) {
                return fold(new Not(unary()));
            }
            return primary();
        }

        Expr primary() {
            skip();
            if (pos >= src.length()) {
                throw error("value expected");
            }
            char c = src.charAt(pos);
            if (c == '(') {
                pos++;
                Expr e = ternary();
                if (!eat(")")) {
                    throw error("')' expected");
                }
                return e;
            }
            if (Character.isDigit(c) || c == '.') {
                return number();
            }
            String ident = identifier();
            if (ident == null) {
                throw error("unexpected '" + c + "'");
            }
            if (eat("(")) {
                List<Expr> args = new ArrayList<>();
                if (!eat(")")) {
                    do {
                        args.add(ternary());
                    } while (eat(","));
                    if (!eat(")")) {
                        throw error("')' expected");
                    }
                }
                return function(ident, args.toArray(new Expr[0]));
            }
            return value(ident);
        }

        Expr number() {
            int start = pos;
            while (pos < src.length() && (Character.isDigit(src.charAt(pos)) || src.charAt(pos) == '.')) {
                pos++;
            }
            if (pos < src.length() && src.charAt(pos) == 'f') {
                pos++; // "1.5f" as Blockbench sometimes writes it
                return new Const(Double.parseDouble(src.substring(start, pos - 1)));
            }
            try {
                return new Const(Double.parseDouble(src.substring(start, pos)));
            } catch (NumberFormatException e) {
                throw error("bad number");
            }
        }

        String identifier() {
            skip();
            int start = pos;
            while (pos < src.length()) {
                char c = src.charAt(pos);
                if (Character.isLetterOrDigit(c) || c == '_' || c == '.') {
                    pos++;
                } else {
                    break;
                }
            }
            if (pos == start || !Character.isLetter(src.charAt(start)) && src.charAt(start) != '_') {
                pos = start;
                return null;
            }
            return src.substring(start, pos);
        }

        Expr value(String ident) {
            switch (ident) {
                case "math.pi":
                    return new Const(Math.PI);
                case "true":
                    return new Const(1);
                case "false":
                    return new Const(0);
                default:
                    break;
            }
            if (ident.startsWith("query.") || ident.startsWith("q.")) {
                Query q = Query.of(ident.substring(ident.indexOf('.') + 1));
                return q == null ? new Const(0) : new QueryValue(q);
            }
            if (ident.startsWith("v.") || ident.startsWith("variable.") || ident.startsWith("t.") || ident.startsWith("temp.")) {
                return new Variable(ident.substring(ident.indexOf('.') + 1));
            }
            return new Const(0);
        }

        Expr function(String name, Expr[] args) {
            String n = name.startsWith("math.") ? name.substring(5) : name;
            if ((n.equals("random") || n.equals("random_integer")) && args.length == 2 && args[0].constant() && args[1].constant()) {
                return new Random(args[0].eval(null), args[1].eval(null) + (n.equals("random_integer") ? 1 : 0), n.equals("random_integer"));
            }
            Fn fn = switch (n) {
                case "abs" -> a -> Math.abs(arg(a, 0));
                case "sin" -> a -> Math.sin(Math.toRadians(arg(a, 0)));
                case "cos" -> a -> Math.cos(Math.toRadians(arg(a, 0)));
                case "asin" -> a -> Math.toDegrees(Math.asin(arg(a, 0)));
                case "acos" -> a -> Math.toDegrees(Math.acos(arg(a, 0)));
                case "atan" -> a -> Math.toDegrees(Math.atan(arg(a, 0)));
                case "atan2" -> a -> Math.toDegrees(Math.atan2(arg(a, 0), arg(a, 1)));
                case "sqrt" -> a -> Math.sqrt(Math.max(0, arg(a, 0)));
                case "pow" -> a -> Math.pow(arg(a, 0), arg(a, 1));
                case "exp" -> a -> Math.exp(arg(a, 0));
                case "ln" -> a -> arg(a, 0) <= 0 ? 0 : Math.log(arg(a, 0));
                case "floor" -> a -> Math.floor(arg(a, 0));
                case "ceil" -> a -> Math.ceil(arg(a, 0));
                case "round" -> a -> Math.round(arg(a, 0));
                case "trunc" -> a -> (long) arg(a, 0);
                case "clamp" -> a -> Math.max(arg(a, 1), Math.min(arg(a, 2), arg(a, 0)));
                case "lerp" -> a -> arg(a, 0) + (arg(a, 1) - arg(a, 0)) * arg(a, 2);
                case "lerprotate" -> a -> {
                    double from = arg(a, 0);
                    double diff = ((arg(a, 1) - from) % 360 + 540) % 360 - 180;
                    return from + diff * arg(a, 2);
                };
                case "min" -> a -> Math.min(arg(a, 0), arg(a, 1));
                case "max" -> a -> Math.max(arg(a, 0), arg(a, 1));
                case "mod" -> a -> arg(a, 1) == 0 ? 0 : arg(a, 0) % arg(a, 1);
                case "sign" -> a -> Math.signum(arg(a, 0));
                case "hermite_blend" -> a -> {
                    double t = arg(a, 0);
                    return 3 * t * t - 2 * t * t * t;
                };
                case "random" -> a -> arg(a, 0) + Math.random() * (arg(a, 1) - arg(a, 0));
                case "random_integer" -> a -> Math.floor(arg(a, 0) + Math.random() * (arg(a, 1) - arg(a, 0) + 1));
                default -> a -> 0;
            };
            return fold(new Call(fn, args));
        }

        static double arg(double[] a, int i) {
            return i < a.length ? a[i] : 0;
        }
    }
}
