public class LexerError {
    public final String message;
    public final int line;

    public LexerError(String message, int line) {
        this.message = message;
        this.line    = line;
    }

    @Override
    public String toString() {
        return "LEXER ERROR at line " + line + ": " + message;
    }
}