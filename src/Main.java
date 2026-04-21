import compiler.error.*;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Paths;


public class Main {
    public static void main(String[] args) throws IOException {
        String path = args.length > 0 ? args[0] : "files/calculator_errors.genz";
        String source = new String(Files.readAllBytes(Paths.get(path)));

        ErrorHandler errorHandler = new ErrorHandler(source, Lexer.getKeywords(), 100);

        Lexer lexer = new Lexer(source, errorHandler);
        Semantic semantic = new Semantic(errorHandler);
        Parser parser = new Parser(lexer, errorHandler, semantic);

        ParseTree parseTree = parser.parse();

        if (lexer.hasErrors()) {
            errorHandler.printErrors(CompilerError.Phase.LEXER);
        }
        if (parser.hasErrors()) {
            System.out.println();
            errorHandler.printErrors(CompilerError.Phase.PARSER);
        }

        parseTree.print();

        if (semantic.hasErrors()) {
            System.out.println();
            errorHandler.printErrors(CompilerError.Phase.SEMANTIC);
            System.out.println("Semantic incorrect");
            return;
        }
        System.out.println("Semantic correct");
    }
}
