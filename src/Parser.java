import compiler.error.*;
import java.util.Set;
import java.util.EnumSet;



















public class Parser {

    private final Lexer lexer;
    private Token currentToken;
    private Token previousToken;
    private final ErrorHandler errorHandler;
    private final SemanticListener listener;
    private boolean panicMode = false;
    private boolean mainFunctionTypoRecovered = false;



    private static final Set<TokenType> FOLLOW_DECLARATION = EnumSet.of(
            TokenType.INJECT, TokenType.OUTFIT, TokenType.PICKS,
            TokenType.SIDEQUEST, TokenType.MAINCHARACTER, TokenType.EOF
    );


    private static final Set<TokenType> FOLLOW_STATEMENT = EnumSet.of(

            TokenType.HEARMEOUT, TokenType.CONSIDER, TokenType.FAR,
            TokenType.TAKEAS, TokenType.POST_ONCE, TokenType.MICDROP,
            TokenType.RAGEQUIT, TokenType.SAYLESS, TokenType.YAP, TokenType.LISTENCLOSELY,

            TokenType.LBRACE, TokenType.RBRACE,

            TokenType.CONST, TokenType.CATALOG, TokenType.NUMBER, TokenType.REAL,
            TokenType.MUCHOTEXTO, TokenType.MAYBE, TokenType.LETTER, TokenType.SIXSEVEN,

            TokenType.ID,

            TokenType.PLUS, TokenType.MINUS,

            TokenType.INT_LITERAL, TokenType.FLOAT_LITERAL, TokenType.STRING_LITERAL,
            TokenType.CHAR_LITERAL, TokenType.TRUE, TokenType.FALSE,

            TokenType.LPAREN,

            TokenType.EOF
    );


    private static final Set<TokenType> FOLLOW_EXPRESSION = EnumSet.of(
            TokenType.SEMICOLON, TokenType.RPAREN, TokenType.COMMA,
            TokenType.RBRACKET, TokenType.RBRACE, TokenType.COLON, TokenType.EOF
    );


    private static final Set<TokenType> FOLLOW_BLOCK = EnumSet.of(

            TokenType.INJECT, TokenType.OUTFIT, TokenType.PICKS, TokenType.SIDEQUEST,
            TokenType.MAINCHARACTER, TokenType.EOF,

            TokenType.NVM, TokenType.PERHAPS,

            TokenType.KEEP_IT_GOING,

            TokenType.HEARMEOUT, TokenType.CONSIDER, TokenType.FAR,
            TokenType.TAKEAS, TokenType.POST_ONCE, TokenType.MICDROP,
            TokenType.RAGEQUIT, TokenType.SAYLESS, TokenType.YAP, TokenType.LISTENCLOSELY,
            TokenType.LBRACE, TokenType.RBRACE,
            TokenType.CONST, TokenType.CATALOG, TokenType.NUMBER, TokenType.REAL,
            TokenType.MUCHOTEXTO, TokenType.MAYBE, TokenType.LETTER, TokenType.SIXSEVEN,
            TokenType.ID, TokenType.PLUS, TokenType.MINUS,
            TokenType.INT_LITERAL, TokenType.FLOAT_LITERAL, TokenType.STRING_LITERAL,
            TokenType.CHAR_LITERAL, TokenType.TRUE, TokenType.FALSE, TokenType.LPAREN
    );

    public Parser(Lexer lexer, ErrorHandler errorHandler, SemanticListener listener) {
        this.lexer = lexer;
        this.currentToken = lexer.peek();
        this.previousToken = null;
        this.errorHandler = errorHandler;
        this.listener = listener;
    }


    private Token advance() {
        previousToken = currentToken;
        lexer.next();
        currentToken = lexer.peek();
        return previousToken;
    }

    private Token previous() {
        return previousToken;
    }

    private Token match(TokenType expected) {
        if (currentToken.type == expected) {
            return advance();
        }

        String suggestion = getSuggestionForMissingToken(expected);
        errorHandler.error(
                CompilerError.Type.MISSING_TOKEN,
                "Expected " + expected + " but found " + currentToken.type + " (\"" + currentToken.value + "\")",
                currentToken.line,
                1,
                suggestion
        );

        return new Token(expected, "", currentToken.line);
    }

    private String getSuggestionForMissingToken(TokenType expected) {
        switch (expected) {
            case SEMICOLON: return "Add a semicolon ';' at the end of the statement.";
            case RPAREN: return "Add a closing parenthesis ')'.";
            case LPAREN: return "Add an opening parenthesis '('.";
            case RBRACE: return "Add a closing brace '}'.";
            case LBRACE: return "Add an opening brace '{'.";
            case ID: return "An identifier (variable or function name) is expected here.";
            default: return "Add the missing '" + expected + "' token.";
        }
    }

    private boolean check(TokenType type) {
        return currentToken.type == type;
    }

    private boolean isAtEnd() {
        return currentToken.type == TokenType.EOF;
    }

    private void synchronizeTo(Set<TokenType> followSet) {
        panicMode = false;

        while (!isAtEnd()) {

            if (followSet.contains(currentToken.type)) {
                return;
            }

            if (previousToken != null && previousToken.type == TokenType.SEMICOLON) {
                return;
            }

            advance();
        }
    }





    private void synchronize() {
        synchronizeTo(FOLLOW_STATEMENT);
    }

    private void reportError(CompilerError.Type type, String message, String suggestion) {
        if (!panicMode) {
            errorHandler.error(type, message, currentToken.line, 1, suggestion);
            panicMode = true;
        }
    }





    private boolean reportErrorAndSync(CompilerError.Type type, String message,
                                       String suggestion, Set<TokenType> followSet) {
        if (!panicMode) {
            errorHandler.error(type, message, currentToken.line, 1, suggestion);
            panicMode = true;
            synchronizeTo(followSet);
            return true;
        }
        return false;
    }





    public boolean hasErrors() {
        return errorHandler.hasErrors(CompilerError.Phase.PARSER);
    }

    private boolean isTypeSpecifier() {
        switch (currentToken.type) {
            case NUMBER: case REAL: case MUCHOTEXTO:
            case MAYBE: case LETTER: case SIXSEVEN:
            case CONST: case CATALOG:
                return true;
            default: return false;
        }
    }

    private boolean isBaseType() {
        switch (currentToken.type) {
            case NUMBER: case REAL: case MUCHOTEXTO:
            case MAYBE: case LETTER: case SIXSEVEN:
                return true;
            default: return false;
        }
    }

    private boolean isLiteral() {
        switch (currentToken.type) {
            case INT_LITERAL: case FLOAT_LITERAL:
            case STRING_LITERAL: case CHAR_LITERAL:
            case TRUE: case FALSE:
                return true;
            default: return false;
        }
    }


    public ParseTree parse() {
        TreeNode root = parseProgram();
        if (listener != null) {
            listener.onProgramEnd();
        }
        return new ParseTree(root);
    }


    private TreeNode parseProgram() {
        TreeNode node = new TreeNode("program");
        node.addChild(parseDeclarationList());
        node.addChild(parseMainFunction());
        match(TokenType.EOF);
        return node;
    }

    private TreeNode parseDeclarationList() {
        TreeNode node = new TreeNode("declaration_list");
        while (!check(TokenType.MAINCHARACTER) && !check(TokenType.EOF)) {

            if (check(TokenType.ID) && currentToken.hasSuggestion()) {

                if ("maincharacter".equals(currentToken.suggestion)) {


                    Token misspelled = currentToken;
                    errorHandler.error(
                            CompilerError.Type.UNKNOWN_IDENTIFIER,
                            "Unknown identifier '" + misspelled.value + "'",
                            misspelled.line,
                            1,
                            misspelled.value.length(),
                            "Did you mean '" + misspelled.suggestion + "'?"
                    );

                    advance();

                    mainFunctionTypoRecovered = true;
                    break;
                }
            }

            TreeNode decl = parseDeclaration();
            if (decl != null) {
                node.addChild(decl);
                emitDeclarationEvent(decl);
            }


            if (panicMode) {
                synchronizeTo(FOLLOW_DECLARATION);
            }
        }
        return node;
    }


    private void emitDeclarationEvent(TreeNode declarationNode) {
        if (listener == null || declarationNode.children.isEmpty()) {
            return;
        }
        TreeNode child = declarationNode.children.get(0);
        switch (child.label) {
            case "import_statement":
                listener.onInject(child);
                break;
            case "struct_declaration":
                listener.onStruct(child);
                break;
            case "enum_declaration":
                listener.onEnum(child);
                break;
            default:
                break;
        }
    }

    private TreeNode parseDeclaration() {
        TreeNode node = new TreeNode("declaration");
        switch (currentToken.type) {
            case INJECT:
                node.addChild(parseImportStatement());
                break;
            case OUTFIT:
                node.addChild(parseStructDeclaration());
                break;
            case PICKS:
                node.addChild(parseEnumDeclaration());
                break;
            case SIDEQUEST:
                node.addChild(parseFunctionDeclaration());
                break;
            default:

                if (currentToken.hasSuggestion()) {
                    errorHandler.error(
                            CompilerError.Type.UNKNOWN_IDENTIFIER,
                            "Unknown identifier '" + currentToken.value + "'",
                            currentToken.line,
                            1,
                            currentToken.value.length(),
                            "Did you mean '" + currentToken.suggestion + "'?"
                    );
                } else {
                    reportError(
                            CompilerError.Type.UNEXPECTED_TOKEN,
                            "Expected declaration (inject, outfit, picks, or sidequest) but found " + currentToken.type,
                            "Top-level code must be: inject (import), outfit (struct), picks (enum), or sidequest (function)."
                    );
                }

                synchronizeTo(FOLLOW_DECLARATION);
                return null;
        }
        return node;
    }


    private TreeNode parseVariableDeclaration() {
        TreeNode node = new TreeNode("variable_declaration");
        node.addChild(parseTypeSpecifier());
        node.addChild(new TreeNode(match(TokenType.ID)));
        node.addChild(new TreeNode(match(TokenType.ASSIGN)));
        node.addChild(parseExpression());
        node.addChild(new TreeNode(match(TokenType.SEMICOLON)));
        return node;
    }

    private TreeNode parseConstantDeclaration() {
        TreeNode node = new TreeNode("constant_declaration");
        node.addChild(new TreeNode(match(TokenType.CONST)));
        node.addChild(parseTypeSpecifier());
        node.addChild(new TreeNode(match(TokenType.ID)));
        node.addChild(new TreeNode(match(TokenType.ASSIGN)));
        node.addChild(parseExpression());
        node.addChild(new TreeNode(match(TokenType.SEMICOLON)));
        return node;
    }


    private TreeNode parseFunctionDeclaration() {
        TreeNode functionNode = new TreeNode("function_declaration");






        TreeNode signature = new TreeNode("function_signature");
        signature.addChild(new TreeNode(match(TokenType.SIDEQUEST)));
        signature.addChild(parseTypeSpecifier());
        signature.addChild(new TreeNode(match(TokenType.ID)));
        signature.addChild(new TreeNode(match(TokenType.LPAREN)));
        signature.addChild(parseParamListOpt());
        signature.addChild(new TreeNode(match(TokenType.RPAREN)));

        for (TreeNode signatureChild : signature.children) {
            functionNode.addChild(signatureChild);
        }
        if (listener != null) {
            listener.onFunctionSignature(signature);
        }

        TreeNode bodyBlock = parseStreamedBodyBlock(false );
        functionNode.addChild(bodyBlock);

        if (listener != null) {
            listener.onFunctionEnd();
        }
        return functionNode;
    }

    private TreeNode parseMainFunction() {
        TreeNode mainNode = new TreeNode("main_function");

        TreeNode header = new TreeNode("main_header");

        if (mainFunctionTypoRecovered) {

            header.addChild(new TreeNode(new Token(TokenType.MAINCHARACTER, "maincharacter", currentToken.line)));
        } else {
            header.addChild(new TreeNode(match(TokenType.MAINCHARACTER)));
        }
        header.addChild(new TreeNode(match(TokenType.LPAREN)));
        header.addChild(new TreeNode(match(TokenType.RPAREN)));

        for (TreeNode headerChild : header.children) {
            mainNode.addChild(headerChild);
        }
        if (listener != null) {
            listener.onMainBegin(header);
        }

        TreeNode bodyBlock = parseStreamedBodyBlock(true );
        mainNode.addChild(bodyBlock);

        if (listener != null) {
            listener.onMainEnd();
        }
        return mainNode;
    }








    private TreeNode parseStreamedBodyBlock(boolean isMain) {
        TreeNode block = new TreeNode("block");
        block.addChild(new TreeNode(match(TokenType.LBRACE)));
        while (!check(TokenType.RBRACE) && !check(TokenType.EOF)) {
            TreeNode statement = parseStatement();
            block.addChild(statement);
            if (listener != null) {
                if (isMain) {
                    listener.onMainStatement(statement);
                } else {
                    listener.onFunctionBodyStatement(statement);
                }
            }
            if (panicMode) {
                synchronizeTo(FOLLOW_STATEMENT);
            }
        }
        block.addChild(new TreeNode(match(TokenType.RBRACE)));
        return block;
    }

    private TreeNode parseParamListOpt() {
        TreeNode node = new TreeNode("param_list_opt");
        if (!check(TokenType.RPAREN)) {
            node.addChild(parseParamList());
        }
        return node;
    }

    private TreeNode parseParamList() {
        TreeNode node = new TreeNode("param_list");
        node.addChild(parseParam());
        while (check(TokenType.COMMA)) {
            node.addChild(new TreeNode(match(TokenType.COMMA)));
            node.addChild(parseParam());
        }
        return node;
    }

    private TreeNode parseParam() {
        TreeNode node = new TreeNode("param");
        node.addChild(parseTypeSpecifier());
        node.addChild(new TreeNode(match(TokenType.ID)));
        return node;
    }





    private TreeNode parseTypeSpecifier() {
        TreeNode node = new TreeNode("type_specifier");
        if (check(TokenType.CONST)) {
            node.addChild(new TreeNode(match(TokenType.CONST)));
            node.addChild(parseBaseType());
        } else if (check(TokenType.CATALOG)) {
            node.addChild(new TreeNode(match(TokenType.CATALOG)));
            node.addChild(new TreeNode(match(TokenType.LANGLE)));
            node.addChild(parseBaseType());
            node.addChild(new TreeNode(match(TokenType.RANGLE)));
        } else {
            node.addChild(parseBaseType());
        }
        return node;
    }

    private TreeNode parseBaseType() {
        TreeNode node = new TreeNode("base_type");
        switch (currentToken.type) {
            case NUMBER: case REAL: case MUCHOTEXTO:
            case MAYBE: case LETTER: case SIXSEVEN:
                node.addChild(new TreeNode(advance()));
                break;
            default:
                reportError(
                        CompilerError.Type.UNEXPECTED_TOKEN,
                        "Expected base type but found " + currentToken.type,
                        "Valid types are: number, real, muchotexto, maybe, letter, sixseven."
                );

                node.addChild(new TreeNode(new Token(TokenType.NUMBER, "number", currentToken.line)));

        }
        return node;
    }





    private TreeNode parseBlock() {
        TreeNode node = new TreeNode("block");
        node.addChild(new TreeNode(match(TokenType.LBRACE)));
        while (!check(TokenType.RBRACE) && !check(TokenType.EOF)) {
            node.addChild(parseStatement());
            if (panicMode) {

                synchronizeTo(FOLLOW_STATEMENT);
            }
        }
        node.addChild(new TreeNode(match(TokenType.RBRACE)));
        return node;
    }

    private TreeNode parseStatement() {
        TreeNode node = new TreeNode("statement");
        switch (currentToken.type) {
            case HEARMEOUT:   node.addChild(parseIfStatement());       break;
            case CONSIDER:    node.addChild(parseSwitchStatement());   break;
            case FAR:         node.addChild(parseForStatement());      break;
            case TAKEAS:      node.addChild(parseWhileStatement());    break;
            case POST_ONCE:   node.addChild(parseDoWhileStatement());  break;
            case MICDROP:     node.addChild(parseReturnStatement());   break;
            case RAGEQUIT:    node.addChild(parseBreakStatement());    break;
            case SAYLESS:     node.addChild(parseContinueStatement()); break;
            case YAP:         node.addChild(parsePrintStatement());    break;
            case LISTENCLOSELY: node.addChild(parseInputStatement());  break;
            case LBRACE:      node.addChild(parseBlock());             break;
            default:
                node.addChild(parseOpenStatement());
        }
        return node;
    }

    private TreeNode parseOpenStatement() {
        TreeNode node = new TreeNode("open_statement");

        if (check(TokenType.CONST)) {
            node.addChild(new TreeNode(match(TokenType.CONST)));
            node.addChild(parseBaseType());
            node.addChild(new TreeNode(match(TokenType.ID)));
            node.addChild(new TreeNode(match(TokenType.ASSIGN)));
            node.addChild(parseExpression());
            node.addChild(new TreeNode(match(TokenType.SEMICOLON)));
        } else if (check(TokenType.CATALOG) || isBaseType()) {
            node.addChild(parseTypeSpecifierNoConst());
            node.addChild(parseDeclOrExprTail());
        } else if (check(TokenType.ID)) {
            node.addChild(new TreeNode(match(TokenType.ID)));
            node.addChild(parseIdLedTail());
        } else if (check(TokenType.PLUS) && currentToken.value.equals("++")) {
            node.addChild(new TreeNode(advance()));
            node.addChild(new TreeNode(match(TokenType.ID)));
            node.addChild(new TreeNode(match(TokenType.SEMICOLON)));
        } else if (check(TokenType.MINUS) && currentToken.value.equals("--")) {
            node.addChild(new TreeNode(advance()));
            node.addChild(new TreeNode(match(TokenType.ID)));
            node.addChild(new TreeNode(match(TokenType.SEMICOLON)));
        } else if (check(TokenType.LPAREN)) {
            node.addChild(new TreeNode(match(TokenType.LPAREN)));
            node.addChild(parseExpression());
            node.addChild(new TreeNode(match(TokenType.RPAREN)));
            node.addChild(new TreeNode(match(TokenType.SEMICOLON)));
        } else if (isLiteral()) {
            node.addChild(parseLiteral());
            node.addChild(new TreeNode(match(TokenType.SEMICOLON)));
        } else {
            reportError(
                    CompilerError.Type.UNEXPECTED_TOKEN,
                    "Unexpected token " + currentToken.type + " (\"" + currentToken.value + "\") in statement",
                    "Expected a statement: variable declaration, assignment, function call, or control flow."
            );

            synchronizeTo(FOLLOW_STATEMENT);
        }
        return node;
    }

    private TreeNode parseTypeSpecifierNoConst() {
        TreeNode node = new TreeNode("type_specifier_no_const");
        if (check(TokenType.CATALOG)) {
            node.addChild(new TreeNode(match(TokenType.CATALOG)));
            node.addChild(new TreeNode(match(TokenType.LANGLE)));
            node.addChild(parseBaseType());
            node.addChild(new TreeNode(match(TokenType.RANGLE)));
        } else {
            node.addChild(parseBaseType());
        }
        return node;
    }

    private TreeNode parseDeclOrExprTail() {
        TreeNode node = new TreeNode("decl_or_expr_tail");
        if (check(TokenType.ID)) {
            node.addChild(new TreeNode(match(TokenType.ID)));
            node.addChild(new TreeNode(match(TokenType.ASSIGN)));
            node.addChild(parseExpression());
        }
        node.addChild(new TreeNode(match(TokenType.SEMICOLON)));
        return node;
    }

    private TreeNode parseIdLedTail() {
        TreeNode node = new TreeNode("id_led_tail");
        if (check(TokenType.ASSIGN)) {
            node.addChild(new TreeNode(match(TokenType.ASSIGN)));
            node.addChild(parseExpression());
            node.addChild(new TreeNode(match(TokenType.SEMICOLON)));
        } else if (check(TokenType.LPAREN)) {
            node.addChild(new TreeNode(match(TokenType.LPAREN)));
            node.addChild(parseArgumentListOpt());
            node.addChild(new TreeNode(match(TokenType.RPAREN)));
            node.addChild(new TreeNode(match(TokenType.SEMICOLON)));
        } else if (check(TokenType.LBRACKET)) {
            node.addChild(new TreeNode(match(TokenType.LBRACKET)));
            node.addChild(parseExpression());
            node.addChild(new TreeNode(match(TokenType.RBRACKET)));
            node.addChild(parseIdLedTailPrime());
        } else if (check(TokenType.PLUS) && currentToken.value.equals("++")) {
            node.addChild(new TreeNode(advance()));
            node.addChild(new TreeNode(match(TokenType.SEMICOLON)));
        } else if (check(TokenType.MINUS) && currentToken.value.equals("--")) {
            node.addChild(new TreeNode(advance()));
            node.addChild(new TreeNode(match(TokenType.SEMICOLON)));
        } else if (check(TokenType.DOT)) {
            node.addChild(new TreeNode(match(TokenType.DOT)));
            node.addChild(new TreeNode(match(TokenType.ID)));
            node.addChild(parseIdLedTail());
        } else {
            node.addChild(new TreeNode(match(TokenType.SEMICOLON)));
        }
        return node;
    }

    private TreeNode parseIdLedTailPrime() {
        TreeNode node = new TreeNode("id_led_tail_prime");
        if (check(TokenType.ASSIGN)) {
            node.addChild(new TreeNode(match(TokenType.ASSIGN)));
            node.addChild(parseExpression());
        }
        node.addChild(new TreeNode(match(TokenType.SEMICOLON)));
        return node;
    }


    private TreeNode parseIfStatement() {
        TreeNode node = new TreeNode("if_statement");
        node.addChild(new TreeNode(match(TokenType.HEARMEOUT)));
        node.addChild(new TreeNode(match(TokenType.LPAREN)));
        node.addChild(parseExpression());
        node.addChild(new TreeNode(match(TokenType.RPAREN)));
        node.addChild(parseBlock());
        if (check(TokenType.NVM) || check(TokenType.PERHAPS)) {
            node.addChild(parseElseClause());
        }
        return node;
    }

    private TreeNode parseElseClause() {
        TreeNode node = new TreeNode("else_clause");
        if (check(TokenType.NVM)) {
            node.addChild(new TreeNode(match(TokenType.NVM)));
            node.addChild(parseBlock());
        } else {
            node.addChild(new TreeNode(match(TokenType.PERHAPS)));
            node.addChild(new TreeNode(match(TokenType.LPAREN)));
            node.addChild(parseExpression());
            node.addChild(new TreeNode(match(TokenType.RPAREN)));
            node.addChild(parseBlock());
            if (check(TokenType.NVM) || check(TokenType.PERHAPS)) {
                node.addChild(parseElseClause());
            }
        }
        return node;
    }

    private TreeNode parseSwitchStatement() {
        TreeNode node = new TreeNode("switch_statement");
        node.addChild(new TreeNode(match(TokenType.CONSIDER)));
        node.addChild(new TreeNode(match(TokenType.LPAREN)));
        node.addChild(parseExpression());
        node.addChild(new TreeNode(match(TokenType.RPAREN)));
        node.addChild(new TreeNode(match(TokenType.LBRACE)));
        while (check(TokenType.CHECKMEOUT) || check(TokenType.IDC)) {
            node.addChild(parseCaseClause());
        }
        node.addChild(new TreeNode(match(TokenType.RBRACE)));
        return node;
    }

    private TreeNode parseCaseClause() {
        TreeNode node = new TreeNode("case_clause");
        if (check(TokenType.CHECKMEOUT)) {
            node.addChild(new TreeNode(match(TokenType.CHECKMEOUT)));
            node.addChild(parseLiteral());
        } else {
            node.addChild(new TreeNode(match(TokenType.IDC)));
        }
        node.addChild(new TreeNode(match(TokenType.COLON)));
        while (!check(TokenType.RAGEQUIT) && !check(TokenType.RBRACE) &&
               !check(TokenType.CHECKMEOUT) && !check(TokenType.IDC) && !check(TokenType.EOF)) {
            node.addChild(parseStatement());
            if (panicMode) {

                synchronizeTo(EnumSet.of(
                        TokenType.RAGEQUIT, TokenType.RBRACE,
                        TokenType.CHECKMEOUT, TokenType.IDC, TokenType.EOF
                ));
            }
        }
        node.addChild(new TreeNode(match(TokenType.RAGEQUIT)));
        node.addChild(new TreeNode(match(TokenType.SEMICOLON)));
        return node;
    }





    private TreeNode parseForStatement() {
        TreeNode node = new TreeNode("for_statement");
        node.addChild(new TreeNode(match(TokenType.FAR)));
        node.addChild(new TreeNode(match(TokenType.LPAREN)));
        node.addChild(parseForInit());
        node.addChild(new TreeNode(match(TokenType.COMMA)));
        node.addChild(parseExpression());
        node.addChild(new TreeNode(match(TokenType.COMMA)));
        node.addChild(parseForUpdate());
        node.addChild(new TreeNode(match(TokenType.RPAREN)));
        node.addChild(parseBlock());
        return node;
    }

    private TreeNode parseForInit() {
        TreeNode node = new TreeNode("for_init");
        if (isTypeSpecifier()) {
            node.addChild(parseTypeSpecifier());
            node.addChild(new TreeNode(match(TokenType.ID)));
            node.addChild(new TreeNode(match(TokenType.ASSIGN)));
            node.addChild(parseExpression());
        } else if (check(TokenType.ID)) {
            node.addChild(new TreeNode(match(TokenType.ID)));
            node.addChild(new TreeNode(match(TokenType.ASSIGN)));
            node.addChild(parseExpression());
        }
        return node;
    }

    private TreeNode parseForUpdate() {
        TreeNode node = new TreeNode("for_update");
        if (!check(TokenType.RPAREN)) {
            node.addChild(parseExpression());
        }
        return node;
    }

    private TreeNode parseWhileStatement() {
        TreeNode node = new TreeNode("while_statement");
        node.addChild(new TreeNode(match(TokenType.TAKEAS)));
        node.addChild(new TreeNode(match(TokenType.LPAREN)));
        node.addChild(parseExpression());
        node.addChild(new TreeNode(match(TokenType.RPAREN)));
        node.addChild(parseBlock());
        return node;
    }

    private TreeNode parseDoWhileStatement() {
        TreeNode node = new TreeNode("do_while_statement");
        node.addChild(new TreeNode(match(TokenType.POST_ONCE)));
        node.addChild(parseBlock());
        node.addChild(new TreeNode(match(TokenType.KEEP_IT_GOING)));
        node.addChild(new TreeNode(match(TokenType.LPAREN)));
        node.addChild(parseExpression());
        node.addChild(new TreeNode(match(TokenType.RPAREN)));
        node.addChild(new TreeNode(match(TokenType.SEMICOLON)));
        return node;
    }





    private TreeNode parseReturnStatement() {
        TreeNode node = new TreeNode("return_statement");
        node.addChild(new TreeNode(match(TokenType.MICDROP)));
        if (!check(TokenType.SEMICOLON)) node.addChild(parseExpression());
        node.addChild(new TreeNode(match(TokenType.SEMICOLON)));
        return node;
    }

    private TreeNode parseBreakStatement() {
        TreeNode node = new TreeNode("break_statement");
        node.addChild(new TreeNode(match(TokenType.RAGEQUIT)));
        node.addChild(new TreeNode(match(TokenType.SEMICOLON)));
        return node;
    }

    private TreeNode parseContinueStatement() {
        TreeNode node = new TreeNode("continue_statement");
        node.addChild(new TreeNode(match(TokenType.SAYLESS)));
        node.addChild(new TreeNode(match(TokenType.SEMICOLON)));
        return node;
    }

    private TreeNode parsePrintStatement() {
        TreeNode node = new TreeNode("print_statement");
        node.addChild(new TreeNode(match(TokenType.YAP)));
        node.addChild(new TreeNode(match(TokenType.LPAREN)));
        if (check(TokenType.STRING_LITERAL)) {
            Token strToken = advance();
            node.addChild(new TreeNode(strToken));
            if (check(TokenType.COMMA)) {
                node.addChild(new TreeNode(match(TokenType.COMMA)));
                node.addChild(parseArgumentList());
            }
        } else {
            node.addChild(parseExpression());
        }
        node.addChild(new TreeNode(match(TokenType.RPAREN)));
        node.addChild(new TreeNode(match(TokenType.SEMICOLON)));
        return node;
    }

    private TreeNode parseInputStatement() {
        TreeNode node = new TreeNode("input_statement");
        node.addChild(new TreeNode(match(TokenType.LISTENCLOSELY)));
        node.addChild(new TreeNode(match(TokenType.LPAREN)));
        node.addChild(new TreeNode(match(TokenType.ID)));
        node.addChild(new TreeNode(match(TokenType.RPAREN)));
        node.addChild(new TreeNode(match(TokenType.SEMICOLON)));
        return node;
    }





    private TreeNode parseExpression() {
        return parseAssignmentExpression();
    }

    private TreeNode parseAssignmentExpression() {
        TreeNode left = parseLogicalOr();
        if (check(TokenType.ASSIGN) && left.label.equals("primary_expression")
                && left.children.size() == 1
                && left.children.get(0).token != null
                && left.children.get(0).token.type == TokenType.ID) {
            TreeNode node = new TreeNode("assignment_expression");
            node.addChild(left);
            node.addChild(new TreeNode(match(TokenType.ASSIGN)));
            node.addChild(parseAssignmentExpression());
            return node;
        }
        return left;
    }

    private TreeNode parseLogicalOr() {
        TreeNode left = parseLogicalAnd();
        while (check(TokenType.OR)) {
            TreeNode node = new TreeNode("logical_or_expression");
            node.addChild(left);
            node.addChild(new TreeNode(match(TokenType.OR)));
            node.addChild(parseLogicalAnd());
            left = node;
        }
        return left;
    }

    private TreeNode parseLogicalAnd() {
        TreeNode left = parseEquality();
        while (check(TokenType.AND)) {
            TreeNode node = new TreeNode("logical_and_expression");
            node.addChild(left);
            node.addChild(new TreeNode(match(TokenType.AND)));
            node.addChild(parseEquality());
            left = node;
        }
        return left;
    }

    private TreeNode parseEquality() {
        TreeNode left = parseRelational();
        while (check(TokenType.EQUALEQUAL) || check(TokenType.NOTEQUAL)) {
            TreeNode node = new TreeNode("equality_expression");
            node.addChild(left);
            node.addChild(new TreeNode(advance()));
            node.addChild(parseRelational());
            left = node;
        }
        return left;
    }

    private TreeNode parseRelational() {
        TreeNode left = parseAdditive();
        while (check(TokenType.GREATER) || check(TokenType.LESS)
                || check(TokenType.GREATEREQUAL) || check(TokenType.LESSEQUAL)) {
            TreeNode node = new TreeNode("relational_expression");
            node.addChild(left);
            node.addChild(new TreeNode(advance()));
            node.addChild(parseAdditive());
            left = node;
        }
        return left;
    }

    private TreeNode parseAdditive() {
        TreeNode left = parseMultiplicative();
        while (check(TokenType.PLUS) || check(TokenType.MINUS)) {
            TreeNode node = new TreeNode("additive_expression");
            node.addChild(left);
            node.addChild(new TreeNode(advance()));
            node.addChild(parseMultiplicative());
            left = node;
        }
        return left;
    }

    private TreeNode parseMultiplicative() {
        TreeNode left = parseUnary();
        while (check(TokenType.STAR) || check(TokenType.SLASH) || check(TokenType.MOD)) {
            TreeNode node = new TreeNode("multiplicative_expression");
            node.addChild(left);
            node.addChild(new TreeNode(advance()));
            node.addChild(parseUnary());
            left = node;
        }
        return left;
    }

    private TreeNode parseUnary() {
        TreeNode node = new TreeNode("unary_expression");
        if (check(TokenType.NOT)) {
            node.addChild(new TreeNode(match(TokenType.NOT)));
            node.addChild(parseUnary());
        } else if (check(TokenType.PLUS) && currentToken.value.equals("++")) {
            node.addChild(new TreeNode(advance()));
            node.addChild(new TreeNode(match(TokenType.ID)));
        } else if (check(TokenType.MINUS) && currentToken.value.equals("--")) {
            node.addChild(new TreeNode(advance()));
            node.addChild(new TreeNode(match(TokenType.ID)));
        } else if (check(TokenType.MINUS)) {
            node.addChild(new TreeNode(match(TokenType.MINUS)));
            node.addChild(parseUnary());
        } else if (check(TokenType.AMPERSAND)) {
            node.addChild(new TreeNode(match(TokenType.AMPERSAND)));
            node.addChild(new TreeNode(match(TokenType.ID)));
        } else {
            node.addChild(parsePostfix());
        }
        return node;
    }

    private TreeNode parsePostfix() {
        TreeNode node = new TreeNode("postfix_expression");
        node.addChild(parsePrimary());
        parsePostfixPrime(node);
        return node;
    }

    private void parsePostfixPrime(TreeNode node) {
        if (check(TokenType.PLUS) && currentToken.value.equals("++")) {
            node.addChild(new TreeNode(advance()));
            parsePostfixPrime(node);
        } else if (check(TokenType.MINUS) && currentToken.value.equals("--")) {
            node.addChild(new TreeNode(advance()));
            parsePostfixPrime(node);
        } else if (check(TokenType.LBRACKET)) {
            node.addChild(new TreeNode(match(TokenType.LBRACKET)));
            node.addChild(parseExpression());
            node.addChild(new TreeNode(match(TokenType.RBRACKET)));
            parsePostfixPrime(node);
        } else if (check(TokenType.LPAREN)) {
            node.addChild(new TreeNode(match(TokenType.LPAREN)));
            node.addChild(parseArgumentListOpt());
            node.addChild(new TreeNode(match(TokenType.RPAREN)));
            parsePostfixPrime(node);
        } else if (check(TokenType.DOT)) {
            node.addChild(new TreeNode(match(TokenType.DOT)));
            node.addChild(new TreeNode(match(TokenType.ID)));
            parsePostfixPrime(node);
        }
    }

    private TreeNode parsePrimary() {
        TreeNode node = new TreeNode("primary_expression");
        if (check(TokenType.LPAREN)) {
            node.addChild(new TreeNode(match(TokenType.LPAREN)));
            node.addChild(parseExpression());
            node.addChild(new TreeNode(match(TokenType.RPAREN)));
        } else if (check(TokenType.ID)) {
            node.addChild(new TreeNode(match(TokenType.ID)));
        } else {
            node.addChild(parseLiteral());
        }
        return node;
    }

    private TreeNode parseLiteral() {
        TreeNode node = new TreeNode("literal");
        switch (currentToken.type) {
            case INT_LITERAL: case FLOAT_LITERAL:
            case STRING_LITERAL: case CHAR_LITERAL:
            case TRUE: case FALSE:
                node.addChild(new TreeNode(advance()));
                break;
            default:
                reportError(
                        CompilerError.Type.UNEXPECTED_TOKEN,
                        "Expected literal but found " + currentToken.type,
                        "Valid literals: numbers (42, 3.14), strings (\"text\"), chars ('a'), booleans (nocap, cap)."
                );

                node.addChild(new TreeNode(new Token(TokenType.INT_LITERAL, "0", currentToken.line)));
                synchronizeTo(FOLLOW_EXPRESSION);
        }
        return node;
    }





    private TreeNode parseArgumentListOpt() {
        TreeNode node = new TreeNode("argument_list_opt");
        if (!check(TokenType.RPAREN)) {
            node.addChild(parseArgumentList());
        }
        return node;
    }

    private TreeNode parseArgumentList() {
        TreeNode node = new TreeNode("argument_list");
        node.addChild(parseExpression());
        while (check(TokenType.COMMA)) {
            node.addChild(new TreeNode(match(TokenType.COMMA)));
            node.addChild(parseExpression());
        }
        return node;
    }


    private TreeNode parseStructDeclaration() {
        TreeNode node = new TreeNode("struct_declaration");
        node.addChild(new TreeNode(match(TokenType.OUTFIT)));
        node.addChild(new TreeNode(match(TokenType.ID)));
        node.addChild(new TreeNode(match(TokenType.LBRACE)));
        while (!check(TokenType.RBRACE) && !check(TokenType.EOF)) {
            node.addChild(parseVariableDeclaration());
            if (panicMode) {

                synchronizeTo(EnumSet.of(
                        TokenType.NUMBER, TokenType.REAL, TokenType.MUCHOTEXTO,
                        TokenType.MAYBE, TokenType.LETTER, TokenType.SIXSEVEN,
                        TokenType.CONST, TokenType.CATALOG, TokenType.RBRACE, TokenType.EOF
                ));
            }
        }
        node.addChild(new TreeNode(match(TokenType.RBRACE)));
        node.addChild(new TreeNode(match(TokenType.SEMICOLON)));
        return node;
    }

    private TreeNode parseEnumDeclaration() {
        TreeNode node = new TreeNode("enum_declaration");
        node.addChild(new TreeNode(match(TokenType.PICKS)));
        node.addChild(new TreeNode(match(TokenType.ID)));
        node.addChild(new TreeNode(match(TokenType.LBRACE)));
        node.addChild(new TreeNode(match(TokenType.ID)));
        while (check(TokenType.COMMA)) {
            node.addChild(new TreeNode(match(TokenType.COMMA)));
            node.addChild(new TreeNode(match(TokenType.ID)));
        }
        node.addChild(new TreeNode(match(TokenType.RBRACE)));
        node.addChild(new TreeNode(match(TokenType.SEMICOLON)));
        return node;
    }

    private TreeNode parseImportStatement() {
        TreeNode node = new TreeNode("import_statement");
        node.addChild(new TreeNode(match(TokenType.INJECT)));
        node.addChild(new TreeNode(match(TokenType.STRING_LITERAL)));
        node.addChild(new TreeNode(match(TokenType.SEMICOLON)));
        return node;
    }
}
