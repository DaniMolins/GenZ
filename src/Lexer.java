import compiler.error.*;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Lexical analyzer for GenZ language.
 * Features: tokenization, comment handling, spell checking for keywords.
 */
public class Lexer {

    private final String source;
    private int pos;
    private int line;
    private int column;
    private int lineStart;
    private final List<Token> tokens;
    private final ErrorHandler errorHandler;

    private static final Map<String, TokenType> KEYWORDS = new HashMap<>();

    static {
        KEYWORDS.put("number",        TokenType.NUMBER);
        KEYWORDS.put("real",          TokenType.REAL);
        KEYWORDS.put("muchotexto",    TokenType.MUCHOTEXTO);
        KEYWORDS.put("maybe",         TokenType.MAYBE);
        KEYWORDS.put("letter",        TokenType.LETTER);
        KEYWORDS.put("solid",         TokenType.CONST);
        KEYWORDS.put("catalog",       TokenType.CATALOG);
        KEYWORDS.put("nocap",         TokenType.TRUE);
        KEYWORDS.put("cap",           TokenType.FALSE);
        KEYWORDS.put("hearmeout",     TokenType.HEARMEOUT);
        KEYWORDS.put("nvm",           TokenType.NVM);
        KEYWORDS.put("perhaps",       TokenType.PERHAPS);
        KEYWORDS.put("consider",      TokenType.CONSIDER);
        KEYWORDS.put("checkmeout",    TokenType.CHECKMEOUT);
        KEYWORDS.put("idc",           TokenType.IDC);
        KEYWORDS.put("far",           TokenType.FAR);
        KEYWORDS.put("takeas",        TokenType.TAKEAS);
        KEYWORDS.put("post_once",     TokenType.POST_ONCE);
        KEYWORDS.put("keep_it_going", TokenType.KEEP_IT_GOING);
        KEYWORDS.put("ragequit",      TokenType.RAGEQUIT);
        KEYWORDS.put("sayless",       TokenType.SAYLESS);
        KEYWORDS.put("sidequest",     TokenType.SIDEQUEST);
        KEYWORDS.put("micdrop",       TokenType.MICDROP);
        KEYWORDS.put("maincharacter", TokenType.MAINCHARACTER);
        KEYWORDS.put("yap",           TokenType.YAP);
        KEYWORDS.put("listenclosely", TokenType.LISTENCLOSELY);
        KEYWORDS.put("fr",            TokenType.ASSIGN);
        KEYWORDS.put("notfr",         TokenType.NOTEQUAL);
        KEYWORDS.put("highkey",       TokenType.GREATER);
        KEYWORDS.put("lowkey",        TokenType.LESS);
        KEYWORDS.put("same",          TokenType.AND);
        KEYWORDS.put("or",            TokenType.OR);
        KEYWORDS.put("nahfam",        TokenType.NOT);
        KEYWORDS.put("inject",        TokenType.INJECT);
        KEYWORDS.put("outfit",        TokenType.OUTFIT);
        KEYWORDS.put("picks",         TokenType.PICKS);
        KEYWORDS.put("shortyap",      TokenType.SHORTYAP);
        KEYWORDS.put("longyap",       TokenType.LONGYAP);
    }

    public static Set<String> getKeywords() {
        return KEYWORDS.keySet();
    }

    public Lexer(String source, ErrorHandler errorHandler) {
        this.source = source;
        this.pos = 0;
        this.line = 1;
        this.column = 1;
        this.lineStart = 0;
        this.tokens = new ArrayList<>();
        this.errorHandler = errorHandler;
    }

    private char current() {
        return pos < source.length() ? source.charAt(pos) : '\0';
    }

    private char peek() {
        return (pos + 1) < source.length() ? source.charAt(pos + 1) : '\0';
    }

    private char advance() {
        char c = source.charAt(pos++);
        if (c == '\n') {
            line++;
            column = 1;
            lineStart = pos;
        } else {
            column++;
        }
        return c;
    }

    private int currentColumn() {
        return pos - lineStart + 1;
    }

    public List<Token> tokenize() {
        while (pos < source.length()) {
            skipWhitespace();
            if (pos >= source.length()) break;

            char c = current();

            if (Character.isLetter(c) || c == '_') {
                readWord();
            } else if (Character.isDigit(c)) {
                readNumber();
            } else if (c == '"') {
                readString();
            } else if (c == '\'') {
                readChar();
            } else {
                readSymbol();
            }
        }
        tokens.add(new Token(TokenType.EOF, "", line));
        return tokens;
    }

    private void skipWhitespace() {
        while (pos < source.length() && Character.isWhitespace(current())) {
            advance();
        }
    }

    private void readWord() {
        int startCol = currentColumn();
        int start = pos;

        while (pos < source.length() &&
                (Character.isLetterOrDigit(current()) || current() == '_')) {
            advance();
        }

        String word = source.substring(start, pos);
        TokenType type = KEYWORDS.getOrDefault(word, TokenType.ID);

        // Handle comments
        if (type == TokenType.SHORTYAP) {
            skipToEndOfLine();
            return;
        }
        if (type == TokenType.LONGYAP) {
            skipMultiLineComment();
            return;
        }

        // Check for typos in keywords (only for identifiers)
        if (type == TokenType.ID) {
            String suggestion = errorHandler.findSimilarKeyword(word);
            if (suggestion != null) {
                // Store suggestion in token for parser to use
                tokens.add(new Token(TokenType.ID, word, line, suggestion));
                return;
            }
        }

        tokens.add(new Token(type, word, line));
    }

    private void skipToEndOfLine() {
        while (pos < source.length() && current() != '\n') {
            advance();
        }
    }

    private void skipMultiLineComment() {
        int startLine = line;
        int startCol = currentColumn();

        while (pos < source.length()) {
            skipWhitespace();
            if (pos >= source.length()) {
                errorHandler.error(
                        CompilerError.Type.SYNTAX_ERROR,
                        "Unclosed multi-line comment - missing closing 'longyap'",
                        startLine,
                        startCol
                );
                return;
            }
            char c = current();
            if (Character.isLetter(c)) {
                int wordStart = pos;
                while (pos < source.length() &&
                        (Character.isLetterOrDigit(current()) || current() == '_')) {
                    advance();
                }
                String word = source.substring(wordStart, pos);
                if (word.equals("longyap")) {
                    return;
                }
            } else {
                advance();
            }
        }
    }

    private void readNumber() {
        int start = pos;
        while (pos < source.length() && Character.isDigit(current())) advance();

        if (current() == '.' && Character.isDigit(peek())) {
            advance();
            while (pos < source.length() && Character.isDigit(current())) advance();
            tokens.add(new Token(TokenType.FLOAT_LITERAL, source.substring(start, pos), line));
        } else {
            tokens.add(new Token(TokenType.INT_LITERAL, source.substring(start, pos), line));
        }
    }

    private void readString() {
        int startLine = line;
        int startCol = currentColumn();
        advance();
        int start = pos;

        while (pos < source.length() && current() != '"' && current() != '\n') {
            advance();
        }

        if (pos >= source.length() || current() == '\n') {
            errorHandler.error(
                    CompilerError.Type.UNCLOSED_STRING,
                    "Unclosed string literal - missing closing quote",
                    startLine,
                    startCol
            );
            tokens.add(new Token(TokenType.UNKNOWN, source.substring(start, pos), startLine));
            return;
        }

        String value = source.substring(start, pos);
        advance();
        tokens.add(new Token(TokenType.STRING_LITERAL, value, startLine));
    }

    private void readChar() {
        int startLine = line;
        int startCol = currentColumn();
        advance();

        if (pos >= source.length()) {
            errorHandler.error(
                    CompilerError.Type.INVALID_CHAR_LITERAL,
                    "Unclosed character literal - unexpected end of file",
                    startLine,
                    startCol
            );
            return;
        }

        char value = advance();
        if (pos >= source.length() || current() != '\'') {
            StringBuilder extraChars = new StringBuilder();
            extraChars.append(value);
            while (pos < source.length() && current() != '\'' && current() != '\n') {
                extraChars.append(advance());
            }
            errorHandler.error(
                    CompilerError.Type.INVALID_CHAR_LITERAL,
                    "Invalid character literal '" + extraChars + "' - expected single character",
                    startLine,
                    startCol,
                    extraChars.length() + 2,
                    "Use muchotexto (string) for \"" + extraChars + "\" instead."
            );
            if (check('\'')) advance();
            tokens.add(new Token(TokenType.UNKNOWN, extraChars.toString(), startLine));
            return;
        }

        advance();
        tokens.add(new Token(TokenType.CHAR_LITERAL, String.valueOf(value), startLine));
    }

    private boolean check(char c) {
        return pos < source.length() && source.charAt(pos) == c;
    }

    private void readSymbol() {
        char c = advance();
        switch (c) {
            case '+':
                if (current() == '+') { advance(); tokens.add(new Token(TokenType.PLUS,  "++", line)); }
                else tokens.add(new Token(TokenType.PLUS,  "+", line));
                break;
            case '-':
                if (current() == '-') { advance(); tokens.add(new Token(TokenType.MINUS, "--", line)); }
                else tokens.add(new Token(TokenType.MINUS, "-", line));
                break;
            case '*': tokens.add(new Token(TokenType.STAR,      "*",  line)); break;
            case '/': tokens.add(new Token(TokenType.SLASH,     "/",  line)); break;
            case '%': tokens.add(new Token(TokenType.MOD,       "%",  line)); break;
            case '(': tokens.add(new Token(TokenType.LPAREN,    "(",  line)); break;
            case ')': tokens.add(new Token(TokenType.RPAREN,    ")",  line)); break;
            case '{': tokens.add(new Token(TokenType.LBRACE,    "{",  line)); break;
            case '}': tokens.add(new Token(TokenType.RBRACE,    "}",  line)); break;
            case '[': tokens.add(new Token(TokenType.LBRACKET,  "[",  line)); break;
            case ']': tokens.add(new Token(TokenType.RBRACKET,  "]",  line)); break;
            case ';': tokens.add(new Token(TokenType.SEMICOLON, ";",  line)); break;
            case ',': tokens.add(new Token(TokenType.COMMA,     ",",  line)); break;
            case ':': tokens.add(new Token(TokenType.COLON,     ":",  line)); break;
            case '.': tokens.add(new Token(TokenType.DOT,       ".",  line)); break;
            case '|': tokens.add(new Token(TokenType.PIPE,      "|",  line)); break;
            case '&': tokens.add(new Token(TokenType.AMPERSAND, "&",  line)); break;
            case '<':
                if (current() == '=') { advance(); tokens.add(new Token(TokenType.LESSEQUAL,    "<=", line)); }
                else tokens.add(new Token(TokenType.LANGLE, "<", line));
                break;
            case '>':
                if (current() == '=') { advance(); tokens.add(new Token(TokenType.GREATEREQUAL, ">=", line)); }
                else tokens.add(new Token(TokenType.RANGLE, ">", line));
                break;
            case '=':
                if (current() == '=') { advance(); tokens.add(new Token(TokenType.EQUALEQUAL,   "==", line)); }
                else tokens.add(new Token(TokenType.UNKNOWN, "=", line));
                break;
            default:
                errorHandler.error(
                        CompilerError.Type.UNKNOWN_CHARACTER,
                        "Unknown character '" + c + "' (ASCII: " + (int)c + ")",
                        line,
                        currentColumn() - 1
                );
                tokens.add(new Token(TokenType.UNKNOWN, String.valueOf(c), line));
        }
    }

    public boolean hasErrors() {
        return errorHandler.hasErrors(CompilerError.Phase.LEXER);
    }
}
