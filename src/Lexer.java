import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class Lexer {

    private final String source;   // código fuente completo
    private int pos;               // posición actual
    private int line;              // línea actual (para errores)
    private final List<Token> tokens;
    private final List<LexerError> errors = new ArrayList<>();

    // Mapa de palabras reservadas → TokenType
    private static final Map<String, TokenType> KEYWORDS = new HashMap<>();

    static {
        KEYWORDS.put("number",        TokenType.NUMBER);
        KEYWORDS.put("real",          TokenType.REAL);
        KEYWORDS.put("muchotexto",    TokenType.MUCHOTEXTO);
        KEYWORDS.put("maybe",         TokenType.MAYBE);
        KEYWORDS.put("letter",        TokenType.LETTER);
        KEYWORDS.put("sixseven",      TokenType.SIXSEVEN);
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

    public Lexer(String source) {
        this.source = source;
        this.pos    = 0;
        this.line   = 1;
        this.tokens = new ArrayList<>();
    }

    // Carácter actual
    private char current() {
        return pos < source.length() ? source.charAt(pos) : '\0';
    }

    // Carácter siguiente (lookahead)
    private char peek() {
        return (pos + 1) < source.length() ? source.charAt(pos + 1) : '\0';
    }

    // Avanzar posición
    private char advance() {
        char c = source.charAt(pos++);
        if (c == '\n') line++;
        return c;
    }

    // Método principal — devuelve todos los tokens
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

    // Lee identificadores y palabras reservadas
    private void readWord() {
        int start = pos;
        while (pos < source.length() &&
                (Character.isLetterOrDigit(current()) || current() == '_')) {
            advance();
        }
        String word = source.substring(start, pos);
        // Si está en el mapa → keyword, si no → identificador
        TokenType type = KEYWORDS.getOrDefault(word, TokenType.ID);
        tokens.add(new Token(type, word, line));
    }

    // Lee enteros y floats
    private void readNumber() {
        int start = pos;
        while (pos < source.length() && Character.isDigit(current())) advance();

        if (current() == '.' && Character.isDigit(peek())) {
            advance(); // consume '.'
            while (pos < source.length() && Character.isDigit(current())) advance();
            tokens.add(new Token(TokenType.FLOAT_LITERAL, source.substring(start, pos), line));
        } else {
            tokens.add(new Token(TokenType.INT_LITERAL, source.substring(start, pos), line));
        }
    }

    // Lee strings "..."
    private void readString() {
        int startLine = line;
        advance(); // consume '"'
        int start = pos;
        while (pos < source.length() && current() != '"' && current() != '\n') {
            advance();
        }
        if (pos >= source.length() || current() == '\n') {
            errors.add(new LexerError("Unclosed string literal", startLine));
            tokens.add(new Token(TokenType.UNKNOWN, source.substring(start, pos), startLine));
            return;
        }
        String value = source.substring(start, pos);
        advance(); // consume '"' de cierre
        tokens.add(new Token(TokenType.STRING_LITERAL, value, startLine));
    }

    // Lee chars '.'
    private void readChar() {
        int startLine = line;
        advance(); // consume '\''
        if (pos >= source.length()) {
            errors.add(new LexerError("Unclosed char literal", startLine));
            return;
        }
        char value = advance();
        if (pos >= source.length() || current() != '\'') {
            errors.add(new LexerError(
                    "Invalid char literal: expected closing ' after '" + value + "'", startLine));
            // consume hasta el cierre o fin de línea
            while (pos < source.length() && current() != '\'' && current() != '\n') advance();
            if (check('\'')) advance();
            tokens.add(new Token(TokenType.UNKNOWN, String.valueOf(value), startLine));
            return;
        }
        advance(); // consume '\'' de cierre
        tokens.add(new Token(TokenType.CHAR_LITERAL, String.valueOf(value), startLine));
    }

    private boolean check(char c) {
        return pos < source.length() && source.charAt(pos) == c;
    }

    // Lee símbolos (+, -, ==, >=, etc.)
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
                errors.add(new LexerError(
                        "Unknown character '" + c + "'", line));
                tokens.add(new Token(TokenType.UNKNOWN, String.valueOf(c), line));
        }
    }

    public boolean hasErrors() {
        return !errors.isEmpty();
    }

    public LexerError[] getErrors() {
        return errors.toArray(new LexerError[0]);
    }
}