import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.util.List;

public class Main {
    public static void main(String[] args) throws IOException {
        String source = new String(Files.readAllBytes(Paths.get("test2.genz")));

        Lexer lexer = new Lexer(source);
        List<Token> tokens = lexer.tokenize();

        // Muestra errores si los hay
        if (lexer.hasErrors()) {
            System.out.println("=== LEXER ERRORS ===");
            for (LexerError error : lexer.getErrors()) {
                System.out.println(error);
            }
            System.out.println("====================\n");
            return;
        }

        // Muestra los tokens igualmente
        System.out.println("=== TOKENS ===");
        for (Token token : tokens) {
            System.out.println(token);
        }

        Parser parser = new Parser(tokens);
        ParseTree parseTree = parser.parse();
        parseTree.print();






    }
}
