package compiler.error;

import java.io.PrintStream;
import java.util.*;

/**
 * Unified error handling: collection, spell checking, printing, and recovery.
 * Consolidates all error-related functionality in one place.
 */
public class ErrorHandler {

    // ═══════════════════════════════════════════════════════════════
    // CONFIGURATION
    // ═══════════════════════════════════════════════════════════════

    private static final int DEFAULT_MAX_ERRORS = 10;
    private static final int MAX_EDIT_DISTANCE = 2;  // For typo detection

    // ═══════════════════════════════════════════════════════════════
    // FIELDS
    // ═══════════════════════════════════════════════════════════════

    private final List<CompilerError> errors = new ArrayList<>();
    private final List<String> sourceLines;
    private final Set<String> keywords;
    private final PrintStream out;
    private final int maxErrors;
    private boolean limitReached = false;

    // ═══════════════════════════════════════════════════════════════
    // CONSTRUCTORS
    // ═══════════════════════════════════════════════════════════════

    public ErrorHandler(String source, Set<String> keywords) {
        this(source, keywords, System.out, DEFAULT_MAX_ERRORS);
    }

    public ErrorHandler(String source, Set<String> keywords, int maxErrors) {
        this(source, keywords, System.out, maxErrors);
    }

    public ErrorHandler(String source, Set<String> keywords, PrintStream out, int maxErrors) {
        this.sourceLines = splitLines(source);
        this.keywords = keywords;
        this.out = out;
        this.maxErrors = maxErrors;
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
        if (start < source.length()) {
            lines.add(source.substring(start));
        }
        return lines;
    }

    // ═══════════════════════════════════════════════════════════════
    // SOURCE ACCESS
    // ═══════════════════════════════════════════════════════════════

    public String getSourceLine(int lineNumber) {
        if (lineNumber > 0 && lineNumber <= sourceLines.size()) {
            return sourceLines.get(lineNumber - 1);
        }
        return "";
    }

    // ═══════════════════════════════════════════════════════════════
    // ERROR REPORTING
    // ═══════════════════════════════════════════════════════════════

    public void reportError(CompilerError error) {
        if (limitReached) return;

        errors.add(error);

        if (errors.size() >= maxErrors) {
            limitReached = true;
        }
    }

    public void error(CompilerError.Type type, String message, int line, int column) {
        error(type, message, line, column, 1, null);
    }

    public void error(CompilerError.Type type, String message, int line, int column, String suggestion) {
        error(type, message, line, column, 1, suggestion);
    }

    public void error(CompilerError.Type type, String message, int line, int column, int length, String suggestion) {
        if (limitReached) return;

        String sourceLine = getSourceLine(line);
        String finalSuggestion = suggestion != null ? suggestion : getDefaultSuggestion(type);

        CompilerError error = CompilerError.builder(type)
                .message(message)
                .line(line)
                .column(column)
                .sourceLine(sourceLine)
                .suggestion(finalSuggestion)
                .length(length)
                .build();

        reportError(error);
    }

    private String getDefaultSuggestion(CompilerError.Type type) {
        switch (type) {
            case UNCLOSED_STRING:
                return "Add a closing double quote (\") at the end of the string.";
            case INVALID_CHAR_LITERAL:
                return "Character literals must contain exactly one character.";
            case UNKNOWN_CHARACTER:
                return "Remove the invalid character or check for typos.";
            case UNEXPECTED_TOKEN:
                return "Check the syntax and ensure all statements are properly formed.";
            case SYNTAX_ERROR:
                return "Review the grammar rules and correct the syntax.";
            case MISSING_TOKEN:
                return "Add the missing token as indicated.";
            case UNKNOWN_IDENTIFIER:
                return "Check the spelling of the identifier.";
            case UNDEFINED_VARIABLE:
                return "Declare the variable before using it.";
            case TYPE_MISMATCH:
                return "Ensure the types are compatible.";
            case DUPLICATE_DECLARATION:
                return "Remove the duplicate declaration or use a different name.";
            default:
                return "";
        }
    }

    // ═══════════════════════════════════════════════════════════════
    // SPELL CHECKING (Levenshtein Distance)
    // ═══════════════════════════════════════════════════════════════

    /**
     * Check if a word is a potential typo of a keyword.
     * Returns the suggested keyword or null if no close match.
     */
    public String findSimilarKeyword(String word) {
        if (word == null || word.length() < 3) return null;

        String bestMatch = null;
        int bestDistance = Integer.MAX_VALUE;

        for (String keyword : keywords) {
            int distance = levenshteinDistance(word.toLowerCase(), keyword.toLowerCase());

            // Only suggest if distance is small relative to word length
            int threshold = Math.min(MAX_EDIT_DISTANCE, word.length() / 3 + 1);

            if (distance <= threshold && distance < bestDistance) {
                bestDistance = distance;
                bestMatch = keyword;
            }
        }

        return bestMatch;
    }

    /**
     * Levenshtein distance algorithm - measures edit distance between two strings.
     */
    private int levenshteinDistance(String s1, String s2) {
        int m = s1.length();
        int n = s2.length();

        int[][] dp = new int[m + 1][n + 1];

        for (int i = 0; i <= m; i++) dp[i][0] = i;
        for (int j = 0; j <= n; j++) dp[0][j] = j;

        for (int i = 1; i <= m; i++) {
            for (int j = 1; j <= n; j++) {
                int cost = (s1.charAt(i - 1) == s2.charAt(j - 1)) ? 0 : 1;
                dp[i][j] = Math.min(
                        Math.min(dp[i - 1][j] + 1, dp[i][j - 1] + 1),
                        dp[i - 1][j - 1] + cost
                );
            }
        }

        return dp[m][n];
    }

    /**
     * Report an unknown identifier error with spell check suggestion.
     */
    public void reportUnknownIdentifier(String identifier, int line, int column) {
        String suggestion = findSimilarKeyword(identifier);
        String message = "Unknown identifier '" + identifier + "'";

        if (suggestion != null) {
            error(CompilerError.Type.UNKNOWN_IDENTIFIER, message, line, column,
                    identifier.length(), "Did you mean '" + suggestion + "'?");
        } else {
            error(CompilerError.Type.UNKNOWN_IDENTIFIER, message, line, column,
                    identifier.length(), "Check the spelling or declare this identifier.");
        }
    }

    // ═══════════════════════════════════════════════════════════════
    // PANIC MODE RECOVERY (for Parser)
    // ═══════════════════════════════════════════════════════════════

    /**
     * Set of token types that act as synchronization points.
     * Parser can use this to recover from errors.
     */
    public static final Set<String> SYNC_TOKENS = Set.of(
            "SEMICOLON", "RBRACE", "MAINCHARACTER", "SIDEQUEST",
            "OUTFIT", "PICKS", "INJECT", "HEARMEOUT", "FAR",
            "TAKEAS", "POST_ONCE", "MICDROP", "EOF"
    );

    public boolean isSyncToken(String tokenType) {
        return SYNC_TOKENS.contains(tokenType);
    }

    // ═══════════════════════════════════════════════════════════════
    // ERROR QUERIES
    // ═══════════════════════════════════════════════════════════════

    public boolean hasErrors() {
        return !errors.isEmpty();
    }

    public boolean hasErrors(CompilerError.Phase phase) {
        for (CompilerError error : errors) {
            if (error.getPhase() == phase) return true;
        }
        return false;
    }

    public int errorCount() {
        return errors.size();
    }

    public int errorCount(CompilerError.Phase phase) {
        int count = 0;
        for (CompilerError error : errors) {
            if (error.getPhase() == phase) count++;
        }
        return count;
    }

    public List<CompilerError> getErrors() {
        return Collections.unmodifiableList(errors);
    }

    public boolean isLimitReached() {
        return limitReached;
    }

    public void clear() {
        errors.clear();
        limitReached = false;
    }

    // ═══════════════════════════════════════════════════════════════
    // ERROR PRINTING
    // ═══════════════════════════════════════════════════════════════

    public void printErrors() {
        if (errors.isEmpty()) return;

        // Group errors by phase
        Map<CompilerError.Phase, List<CompilerError>> errorsByPhase = new LinkedHashMap<>();
        for (CompilerError error : errors) {
            errorsByPhase.computeIfAbsent(error.getPhase(), k -> new ArrayList<>()).add(error);
        }

        out.println();

        int errorNum = 0;
        for (Map.Entry<CompilerError.Phase, List<CompilerError>> entry : errorsByPhase.entrySet()) {
            CompilerError.Phase phase = entry.getKey();
            List<CompilerError> phaseErrors = entry.getValue();

            printPhaseHeader(phase);

            for (CompilerError error : phaseErrors) {
                errorNum++;
                out.printf("Error %d of %d:%n", errorNum, errors.size());
                printError(error);
                out.println();
            }
        }

        printFooter();
    }

    public void printErrors(CompilerError.Phase phase) {
        List<CompilerError> phaseErrors = new ArrayList<>();
        for (CompilerError error : errors) {
            if (error.getPhase() == phase) phaseErrors.add(error);
        }

        if (phaseErrors.isEmpty()) return;

        out.println();
        printPhaseHeader(phase);

        for (int i = 0; i < phaseErrors.size(); i++) {
            out.printf("Error %d of %d:%n", i + 1, phaseErrors.size());
            printError(phaseErrors.get(i));
            out.println();
        }

        out.println("────────────────────────────────────────────────────────────────");
        out.printf("Total: %d %s error(s) found.%n",
                phaseErrors.size(), phase.getDisplayName().toLowerCase());
        out.println("────────────────────────────────────────────────────────────────");
    }

    private void printPhaseHeader(CompilerError.Phase phase) {
        String title = phase.getDisplayName() + " ERRORS";
        int padding = (62 - title.length()) / 2;
        String paddedTitle = " ".repeat(Math.max(0, padding)) + title +
                " ".repeat(Math.max(0, 62 - padding - title.length()));

        out.println("╔══════════════════════════════════════════════════════════════╗");
        out.println("║" + paddedTitle + "║");
        out.println("╚══════════════════════════════════════════════════════════════╝");
        out.println();
    }

    private void printError(CompilerError error) {
        // Error header
        out.printf("[%s] Line %d, Column %d: %s%n",
                error.getType().name(), error.getLine(), error.getColumn(), error.getMessage());

        // Source line with underline
        String sourceLine = error.getSourceLine();
        if (sourceLine != null && !sourceLine.isEmpty()) {
            out.println("    " + sourceLine);

            // Build underline: spaces + ^~~~
            StringBuilder underline = new StringBuilder("    ");
            for (int i = 0; i < error.getColumn() - 1 && i < sourceLine.length(); i++) {
                underline.append(sourceLine.charAt(i) == '\t' ? '\t' : ' ');
            }
            underline.append("^");
            for (int i = 1; i < error.getLength(); i++) {
                underline.append("~");
            }
            out.println(underline);
        }

        // Suggestion
        String suggestion = error.getSuggestion();
        if (suggestion != null && !suggestion.isEmpty()) {
            out.println("    " + suggestion);
        }
    }

    private void printFooter() {
        out.println("────────────────────────────────────────────────────────────────");
        if (limitReached) {
            out.printf("Showing first %d errors. Fix these and recompile to see more.%n", maxErrors);
        } else {
            out.printf("Total: %d error(s) found. Fix the errors above and try again.%n", errors.size());
        }
        out.println("────────────────────────────────────────────────────────────────");
    }
}
