public class Token {
    public final TokenType type;
    public final String value;
    public final int line;
    public final String suggestion;  // For spell check: "Did you mean 'maincharacter'?"

    public Token(TokenType type, String value, int line) {
        this(type, value, line, null);
    }

    public Token(TokenType type, String value, int line, String suggestion) {
        this.type = type;
        this.value = value;
        this.line = line;
        this.suggestion = suggestion;
    }

    public boolean hasSuggestion() {
        return suggestion != null;
    }

    @Override
    public String toString() {
        if (suggestion != null) {
            return "Token(" + type + ", \"" + value + "\", line=" + line + ", suggest=\"" + suggestion + "\")";
        }
        return "Token(" + type + ", \"" + value + "\", line=" + line + ")";
    }
}
