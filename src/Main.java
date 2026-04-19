import compiler.error.*;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.util.List;

/**
 * Main entry point for the GenZ compiler.
 */
public class Main {
    public static void main(String[] args) throws IOException {
        String path = args.length > 0 ? args[0] : "files/calculator_errors.genz";
        String source = new String(Files.readAllBytes(Paths.get(path)));

        ErrorHandler errorHandler = new ErrorHandler(source, Lexer.getKeywords(), 100);

        Lexer lexer = new Lexer(source, errorHandler);
        List<Token> tokens = lexer.tokenize();

        if (lexer.hasErrors()) {
            errorHandler.printErrors(CompilerError.Phase.LEXER);
            //return;
        }

        System.out.println("=== TOKENS ===");
        for (Token token : tokens) {
            System.out.println(token);
        }

        // Parser phase
        Parser parser = new Parser(tokens, errorHandler);
        ParseTree parseTree = parser.parse();

        // Check for parser errors
        if (parser.hasErrors()) {
            System.out.println();
            errorHandler.printErrors(CompilerError.Phase.PARSER);
            //return;
        }

        parseTree.print();
        Semantic semantic = new Semantic(parseTree, errorHandler);
        semantic.analyze();
        if (semantic.hasErrors()) {
            System.out.println();
            errorHandler.printErrors(CompilerError.Phase.SEMANTIC);
            System.out.println("Semantic incorrect");
            return;
        }
        System.out.println("Semantic correct");
    }
}
