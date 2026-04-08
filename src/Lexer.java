package src;
import java.io.File;
import java.io.FileReader;
import java.io.IOException;
import java.util.HashMap;

public class Lexer {

    File reservedWordsFile = new File("files/reserved_words.csv");

    FileReader codeReader;

    private StringBuilder token;

    private char currentChar;
    private int lineNumber;
    private boolean eof;

    private HashMap<String, String> dictionary;

    public Lexer(File sourceFile) {
        dictionary = new HashMap<>();
        token = new StringBuilder();
        lineNumber = 1;
        eof = false;

        loadKeywordsFromCSV();
        try {
            codeReader = new FileReader(sourceFile);
            nextChar();
        } catch (IOException e) {
            throw new RuntimeException("Cannot read source file", e);
        }
    }

    private void loadKeywordsFromCSV() {
        try (FileReader reader = new FileReader(reservedWordsFile)) {

            while (reader.ready()) {
                StringBuilder keyword = new StringBuilder();
                StringBuilder tokenType = new StringBuilder();

                char c;
                while ((c = (char) reader.read()) != ',') {
                    keyword.append(c);
                }
                while ((c = (char) reader.read()) != '\n') {
                    tokenType.append(c);
                }

                dictionary.put(keyword.toString(), tokenType.toString());
            }

        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    private char nextChar() {
        try {
            int c = codeReader.read();
            if (c == -1) {
                eof = true;
                currentChar = '\0';
            } else {
                currentChar = (char) c;
            }
        } catch (IOException e) {
            eof = true;
            currentChar = '\0';
        }
        return currentChar;
    }

    private void skipWhitespace() {
        while (!eof && Character.isWhitespace(currentChar)) {
            if (currentChar == '\n') {
                lineNumber++;
            }
            nextChar();
        }
    }

    public Token getToken() {
        skipWhitespace();

        if (eof) {
            return new Token(TokenType.EOF, "");
        }

        token.setLength(0);

        if (Character.isLetter(currentChar) || currentChar == '_') {
            return readIdentifierOrKeyword();
        }
        if (Character.isDigit(currentChar)) {
            return readNumber();
        }
        if (currentChar == '"' || currentChar == '\'') {
            return readString();
        }
        if ("=!<>+-&|".indexOf(currentChar) >= 0) {
            return readOperator();
        }
        if ("*/%".indexOf(currentChar) >= 0) {
            return readSingleCharOperator();
        }
        if ("(){}[];,.:".indexOf(currentChar) >= 0) {
            return readSeparator();
        }

        token.append(currentChar);
        nextChar();
        return new Token(TokenType.ERROR, token.toString());
    }

    private Token readIdentifierOrKeyword() {
        while (!eof && (Character.isLetterOrDigit(currentChar) || currentChar == '_')) {
            token.append(currentChar);
            nextChar();
        }
        String word = token.toString();
        if (dictionary.containsKey(word)) {
            return new Token(TokenType.KEYWORD, word);
        }
        return new Token(TokenType.IDENTIFIER, word);
    }

    private Token readNumber() {
        boolean hasDot = false;
        while (!eof && (Character.isDigit(currentChar) || (currentChar == '.' && !hasDot))) {
            if (currentChar == '.') hasDot = true;
            token.append(currentChar);
            nextChar();
        }
        return new Token(TokenType.NUMBER, token.toString());
    }

    private Token readString() {
        char quote = currentChar;
        nextChar();
        while (!eof && currentChar != quote) {
            if (currentChar == '\\') {
                token.append(currentChar);
                nextChar();
            }
            token.append(currentChar);
            nextChar();
        }
        if (!eof) nextChar();
        return new Token(TokenType.STRING, token.toString());
    }

    private Token readOperator() {
        token.append(currentChar);
        char first = currentChar;
        nextChar();
        if (!eof &&
            ((first == '=' && currentChar == '=') ||
             (first == '!' && currentChar == '=') ||
             (first == '<' && currentChar == '=') ||
             (first == '>' && currentChar == '=') ||
             (first == '+' && currentChar == '+') ||
             (first == '-' && currentChar == '-') ||
             (first == '&' && currentChar == '&') ||
             (first == '|' && currentChar == '|'))) {
            token.append(currentChar);
            nextChar();
        }
        return new Token(TokenType.OPERATOR, token.toString());
    }

    private Token readSingleCharOperator() {
        token.append(currentChar);
        nextChar();
        return new Token(TokenType.OPERATOR, token.toString());
    }

    private Token readSeparator() {
        token.append(currentChar);
        nextChar();
        return new Token(TokenType.SEPARATOR, token.toString());
    }

    public int getLineNumber() {
        return lineNumber;
    }


}