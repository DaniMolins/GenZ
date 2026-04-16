import java.util.ArrayList;
import java.util.List;

public class ErrorCatcher {

    // Compilation phase for error categorization
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

    public enum ErrorType {
        // Lexer errors
        UNCLOSED_STRING,
        INVALID_CHAR_LITERAL,
        UNKNOWN_CHARACTER,
        // Parser errors
        UNEXPECTED_TOKEN,
        SYNTAX_ERROR,
        MISSING_TOKEN,
        // Semantic errors (for future use)
        UNDEFINED_VARIABLE,
        TYPE_MISMATCH,
        DUPLICATE_DECLARATION,
        INVALID_OPERATION,
        // Code generation errors (for future use)
        CODEGEN_ERROR
    }

    public static class Error {
        public final Phase phase;
        public final ErrorType type;
        public final String message;
        public final int line;
        public final int column;
        public final String sourceLine;
        public final String suggestion;

        public Error(Phase phase, ErrorType type, String message, int line, int column,
                     String sourceLine, String suggestion) {
            this.phase = phase;
            this.type = type;
            this.message = message;
            this.line = line;
            this.column = column;
            this.sourceLine = sourceLine;
            this.suggestion = suggestion;
        }

        @Override
        public String toString() {
            StringBuilder sb = new StringBuilder();

            // Error header with type and location
            sb.append(String.format("[%s] Line %d, Column %d: %s%n",
                    type.name(), line, column, message));

            // Show the source line if available
            if (sourceLine != null && !sourceLine.isEmpty()) {
                sb.append("    ").append(sourceLine).append("\n");

                // Add caret pointing to the error position
                sb.append("    ");
                for (int i = 0; i < column - 1 && i < sourceLine.length(); i++) {
                    sb.append(sourceLine.charAt(i) == '\t' ? '\t' : ' ');
                }
                sb.append("^");
            }

            // Add suggestion if available
            if (suggestion != null && !suggestion.isEmpty()) {
                sb.append("\n    Suggestion: ").append(suggestion);
            }

            return sb.toString();
        }
    }

    private final List<Error> errors = new ArrayList<>();
    private final List<String> sourceLines;
    private Phase currentPhase = Phase.LEXER;

    public ErrorCatcher(String source) {
        this.sourceLines = splitLines(source);
    }

    public void setPhase(Phase phase) {
        this.currentPhase = phase;
    }

    public Phase getPhase() {
        return currentPhase;
    }

    private List<String> splitLines(String source) {
        List<String> lines = new ArrayList<>();
        int start = 0;
        for (int i = 0; i < source.length(); i++) {
            if (source.charAt(i) == '\n') {
                lines.add(source.substring(start, i));
                start = i + 1;
            }
        }
        // Add the last line if there's remaining content
        if (start < source.length()) {
            lines.add(source.substring(start));
        }
        return lines;
    }

    public String getSourceLine(int lineNumber) {
        if (lineNumber > 0 && lineNumber <= sourceLines.size()) {
            return sourceLines.get(lineNumber - 1);
        }
        return "";
    }

    public void addError(ErrorType type, String message, int line, int column) {
        String sourceLine = getSourceLine(line);
        String suggestion = getSuggestion(type, message);
        errors.add(new Error(currentPhase, type, message, line, column, sourceLine, suggestion));
    }

    public void addError(ErrorType type, String message, int line, int column, String customSuggestion) {
        String sourceLine = getSourceLine(line);
        errors.add(new Error(currentPhase, type, message, line, column, sourceLine, customSuggestion));
    }

    // Add error with explicit phase (for cases where you need to specify a different phase)
    public void addError(Phase phase, ErrorType type, String message, int line, int column) {
        String sourceLine = getSourceLine(line);
        String suggestion = getSuggestion(type, message);
        errors.add(new Error(phase, type, message, line, column, sourceLine, suggestion));
    }

    public void addError(Phase phase, ErrorType type, String message, int line, int column, String customSuggestion) {
        String sourceLine = getSourceLine(line);
        errors.add(new Error(phase, type, message, line, column, sourceLine, customSuggestion));
    }

    private String getSuggestion(ErrorType type, String message) {
        switch (type) {
            // Lexer errors
            case UNCLOSED_STRING:
                return "Add a closing double quote (\") at the end of the string.";
            case INVALID_CHAR_LITERAL:
                return "Character literals must contain exactly one character. Use double quotes for strings.";
            case UNKNOWN_CHARACTER:
                return "Remove the invalid character or check for typos.";
            // Parser errors
            case UNEXPECTED_TOKEN:
                return "Check the syntax and ensure all statements are properly formed.";
            case SYNTAX_ERROR:
                return "Review the grammar rules and correct the syntax.";
            case MISSING_TOKEN:
                return "Add the missing token as indicated.";
            // Semantic errors
            case UNDEFINED_VARIABLE:
                return "Declare the variable before using it.";
            case TYPE_MISMATCH:
                return "Ensure the types are compatible or add an explicit conversion.";
            case DUPLICATE_DECLARATION:
                return "Remove the duplicate declaration or use a different name.";
            case INVALID_OPERATION:
                return "Check that the operation is valid for the given types.";
            // Code generation errors
            case CODEGEN_ERROR:
                return "Internal compiler error. Please report this issue.";
            default:
                return null;
        }
    }

    public boolean hasErrors() {
        return !errors.isEmpty();
    }

    public List<Error> getErrors() {
        return errors;
    }

    public int errorCount() {
        return errors.size();
    }

    public void printErrors() {
        if (errors.isEmpty()) {
            return;
        }

        // Group errors by phase
        java.util.Map<Phase, List<Error>> errorsByPhase = new java.util.LinkedHashMap<>();
        for (Error error : errors) {
            errorsByPhase.computeIfAbsent(error.phase, k -> new ArrayList<>()).add(error);
        }

        System.out.println();

        int errorNum = 0;
        for (java.util.Map.Entry<Phase, List<Error>> entry : errorsByPhase.entrySet()) {
            Phase phase = entry.getKey();
            List<Error> phaseErrors = entry.getValue();

            String title = phase.getDisplayName() + " ERRORS";
            int padding = (62 - title.length()) / 2;
            String paddedTitle = " ".repeat(Math.max(0, padding)) + title +
                                 " ".repeat(Math.max(0, 62 - padding - title.length()));

            System.out.println("╔══════════════════════════════════════════════════════════════╗");
            System.out.println("║" + paddedTitle + "║");
            System.out.println("╚══════════════════════════════════════════════════════════════╝");
            System.out.println();

            for (Error error : phaseErrors) {
                errorNum++;
                System.out.printf("Error %d of %d:%n", errorNum, errors.size());
                System.out.println(error);
                System.out.println();
            }
        }

        System.out.println("────────────────────────────────────────────────────────────────");
        System.out.printf("Total: %d error(s) found. Fix the errors above and try again.%n", errors.size());
        System.out.println("────────────────────────────────────────────────────────────────");
    }

    // Print errors for a specific phase only
    public void printErrors(Phase phase) {
        List<Error> phaseErrors = new ArrayList<>();
        for (Error error : errors) {
            if (error.phase == phase) {
                phaseErrors.add(error);
            }
        }

        if (phaseErrors.isEmpty()) {
            return;
        }

        String title = phase.getDisplayName() + " ERRORS";
        int padding = (62 - title.length()) / 2;
        String paddedTitle = " ".repeat(Math.max(0, padding)) + title +
                             " ".repeat(Math.max(0, 62 - padding - title.length()));

        System.out.println();
        System.out.println("╔══════════════════════════════════════════════════════════════╗");
        System.out.println("║" + paddedTitle + "║");
        System.out.println("╚══════════════════════════════════════════════════════════════╝");
        System.out.println();

        for (int i = 0; i < phaseErrors.size(); i++) {
            System.out.printf("Error %d of %d:%n", i + 1, phaseErrors.size());
            System.out.println(phaseErrors.get(i));
            System.out.println();
        }

        System.out.println("────────────────────────────────────────────────────────────────");
        System.out.printf("Total: %d %s error(s) found.%n", phaseErrors.size(), phase.getDisplayName().toLowerCase());
        System.out.println("────────────────────────────────────────────────────────────────");
    }

    // Check if there are errors for a specific phase
    public boolean hasErrors(Phase phase) {
        for (Error error : errors) {
            if (error.phase == phase) {
                return true;
            }
        }
        return false;
    }

    // Get error count for a specific phase
    public int errorCount(Phase phase) {
        int count = 0;
        for (Error error : errors) {
            if (error.phase == phase) {
                count++;
            }
        }
        return count;
    }

    // Clear all errors
    public void clear() {
        errors.clear();
    }

    // Clear errors for a specific phase
    public void clear(Phase phase) {
        errors.removeIf(error -> error.phase == phase);
    }
}
