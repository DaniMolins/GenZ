package src;

import java.io.File;

public class Main {
    
    public static void main(String[] args) {
        Lexer lexer = new Lexer(new File("GenZ/files/hello.genz"));
        Token token = lexer.getToken();
        while (token.getType() != TokenType.EOF) {
            System.out.println(token);
            token = lexer.getToken();
        }

    }
}
