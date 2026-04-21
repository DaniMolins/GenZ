package compiler.error;

/**
 * Immutable value object representing a compilation error.
 * Also contains the enums for error classification.
 */
public final class CompilerError {

    // ═══════════════════════════════════════════════════════════════
    // ENUMS
    // ═══════════════════════════════════════════════════════════════

    public enum Phase {
        LEXER("LEXER"),
        PARSER("PARSER"),
        SEMANTIC("SEMANTIC ANALYSIS"),
        CODEGEN("CODE GENERATION");

        private final String displayName;

        Phase(String displayName) {
            this.displayName = displayName;
        }

        public String getDisplayName() {
            return displayName;
        }
    }

    public enum Type {
        // Lexer errors
        UNCLOSED_STRING(Phase.LEXER),
        INVALID_CHAR_LITERAL(Phase.LEXER),
        UNKNOWN_CHARACTER(Phase.LEXER),

        // Parser errors
        UNEXPECTED_TOKEN(Phase.PARSER),
        SYNTAX_ERROR(Phase.PARSER),
        MISSING_TOKEN(Phase.PARSER),
        UNKNOWN_IDENTIFIER(Phase.PARSER),  // For typos like "maincharacte"

        // Semantic errors
        UNDEFINED_VARIABLE(Phase.SEMANTIC),
        TYPE_MISMATCH(Phase.SEMANTIC),
        DUPLICATE_DECLARATION(Phase.SEMANTIC),
        INVALID_OPERATION(Phase.SEMANTIC),

        // Code generation errors
        CODEGEN_ERROR(Phase.CODEGEN);

        private final Phase phase;

        Type(Phase phase) {
            this.phase = phase;
        }

        public Phase getPhase() {
            return phase;
        }
    }

    // ═══════════════════════════════════════════════════════════════
    // FIELDS (immutable)
    // ═══════════════════════════════════════════════════════════════

    private final Type type;
    private final String message;
    private final int line;
    private final int column;
    private final String sourceLine;
    private final String suggestion;
    private final int length;  // Length of the error token (for ~~~~ underline)

    private CompilerError(Builder builder) {
        this.type = builder.type;
        this.message = builder.message;
        this.line = builder.line;
        this.column = builder.column;
        this.sourceLine = builder.sourceLine;
        this.suggestion = builder.suggestion;
        this.length = builder.length;
    }

    // ═══════════════════════════════════════════════════════════════
    // GETTERS
    // ═══════════════════════════════════════════════════════════════

    public Type getType() { return type; }
    public Phase getPhase() { return type.getPhase(); }
    public String getMessage() { return message; }
    public int getLine() { return line; }
    public int getColumn() { return column; }
    public String getSourceLine() { return sourceLine; }
    public String getSuggestion() { return suggestion; }
    public int getLength() { return length; }

    // ═══════════════════════════════════════════════════════════════
    // BUILDER
    // ═══════════════════════════════════════════════════════════════

    public static Builder builder(Type type) {
        return new Builder(type);
    }

    public static class Builder {
        private final Type type;
        private String message = "";
        private int line = 1;
        private int column = 1;
        private String sourceLine = "";
        private String suggestion = "";
        private int length = 1;

        private Builder(Type type) {
            this.type = type;
        }

        public Builder message(String message) {
            this.message = message;
            return this;
        }

        public Builder line(int line) {
            this.line = line;
            return this;
        }

        public Builder column(int column) {
            this.column = column;
            return this;
        }

        public Builder sourceLine(String sourceLine) {
            this.sourceLine = sourceLine;
            return this;
        }

        public Builder suggestion(String suggestion) {
            this.suggestion = suggestion;
            return this;
        }

        public Builder length(int length) {
            this.length = Math.max(1, length);
            return this;
        }

        public CompilerError build() {
            return new CompilerError(this);
        }
    }

    @Override
    public String toString() {
        return String.format("[%s] Line %d, Col %d: %s", type.name(), line, column, message);
    }
}
