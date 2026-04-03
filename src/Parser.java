package src;
import java.util.List;

public class Parser {

    private Token currentToken; 
    private List<Token> tokens;
    private int position;

    public Parser(List<Token> tokens) {
        this.tokens = tokens;
        this.position = 0;
        this.currentToken = tokens.get(position);
    }



}
