import compiler.error.*;
import java.util.List;
import java.util.Set;
import java.util.EnumSet;

/**
 * Recursive descent parser for GenZ language.
 * Features: FOLLOW-based error recovery, spell check integration.
 */
public class Parser {

    private final List<Token> tokens;
    private int pos;
    private Token currentToken;
    private Token previousToken;
    private final ErrorHandler errorHandler;
    private boolean panicMode = false;
    private boolean mainFunctionTypoRecovered = false;  // True if "maincharacter" was misspelled but recovered

    // ═══════════════════════════════════════════════════════════════
    // FOLLOW SETS (based on grammar.txt)
    // ═══════════════════════════════════════════════════════════════

    // FOLLOW(declaration) = FIRST(declaration) ∪ { MAINCHARACTER }
    private static final Set<TokenType> FOLLOW_DECLARATION = EnumSet.of(
            TokenType.INJECT, TokenType.OUTFIT, TokenType.PICKS,
            TokenType.SIDEQUEST, TokenType.MAINCHARACTER, TokenType.EOF
    );

    // FOLLOW(statement) = FIRST(statement) ∪ { RBRACE }
    private static final Set<TokenType> FOLLOW_STATEMENT = EnumSet.of(
            // Control flow
            TokenType.HEARMEOUT, TokenType.CONSIDER, TokenType.FAR,
            TokenType.TAKEAS, TokenType.POST_ONCE, TokenType.MICDROP,
            TokenType.RAGEQUIT, TokenType.SAYLESS, TokenType.YAP, TokenType.LISTENCLOSELY,
            // Block
            TokenType.LBRACE, TokenType.RBRACE,
            // Type specifiers (for declarations/expressions)
            TokenType.CONST, TokenType.CATALOG, TokenType.NUMBER, TokenType.REAL,
            TokenType.MUCHOTEXTO, TokenType.MAYBE, TokenType.LETTER,
            // ID (for assignments, calls)
            TokenType.ID,
            // Unary operators
            TokenType.PLUS, TokenType.MINUS,
            // Literals
            TokenType.INT_LITERAL, TokenType.FLOAT_LITERAL, TokenType.STRING_LITERAL,
            TokenType.CHAR_LITERAL, TokenType.TRUE, TokenType.FALSE,
            // Parenthesized expression
            TokenType.LPAREN,
            // EOF
            TokenType.EOF
    );

    // FOLLOW(expression) = tokens that can follow an expression
    private static final Set<TokenType> FOLLOW_EXPRESSION = EnumSet.of(
            TokenType.SEMICOLON, TokenType.RPAREN, TokenType.COMMA,
            TokenType.RBRACKET, TokenType.RBRACE, TokenType.COLON, TokenType.EOF
    );

    // FOLLOW(block) = tokens that can follow a block
    private static final Set<TokenType> FOLLOW_BLOCK = EnumSet.of(
            // After function/control blocks
            TokenType.INJECT, TokenType.OUTFIT, TokenType.PICKS, TokenType.SIDEQUEST,
            TokenType.MAINCHARACTER, TokenType.EOF,
            // else clauses
            TokenType.NVM, TokenType.PERHAPS,
            // keep_it_going after do-while block
            TokenType.KEEP_IT_GOING,
            // next statement
            TokenType.HEARMEOUT, TokenType.CONSIDER, TokenType.FAR,
            TokenType.TAKEAS, TokenType.POST_ONCE, TokenType.MICDROP,
            TokenType.RAGEQUIT, TokenType.SAYLESS, TokenType.YAP, TokenType.LISTENCLOSELY,
            TokenType.LBRACE, TokenType.RBRACE,
            TokenType.CONST, TokenType.CATALOG, TokenType.NUMBER, TokenType.REAL,
            TokenType.MUCHOTEXTO, TokenType.MAYBE, TokenType.LETTER,
            TokenType.ID, TokenType.PLUS, TokenType.MINUS,
            TokenType.INT_LITERAL, TokenType.FLOAT_LITERAL, TokenType.STRING_LITERAL,
            TokenType.CHAR_LITERAL, TokenType.TRUE, TokenType.FALSE, TokenType.LPAREN
    );

    public Parser(List<Token> tokens, ErrorHandler errorHandler) {
        this.tokens = tokens;
        this.pos = 0;
        this.currentToken = tokens.get(0);
        this.previousToken = null;
        this.errorHandler = errorHandler;
    }

    // ═══════════════════════════════════════════════════════════════
    // UTILITIES
    // ═══════════════════════════════════════════════════════════════

    private Token advance() {
        previousToken = currentToken;
        if (pos < tokens.size() - 1) pos++;
        currentToken = tokens.get(pos);
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

    // ═══════════════════════════════════════════════════════════════
    // FOLLOW-BASED ERROR RECOVERY
    // ═══════════════════════════════════════════════════════════════

    /**
     * Synchronize to the FOLLOW set of the current construct.
     * This is more precise than generic panic mode - we skip exactly
     * until we find a token that can legitimately follow the construct
     * we were trying to parse.
     *
     * @param followSet The FOLLOW set for the current non-terminal
     */
    private void synchronizeTo(Set<TokenType> followSet) {
        panicMode = false;

        while (!isAtEnd()) {
            // If current token is in FOLLOW set, we can resume parsing
            if (followSet.contains(currentToken.type)) {
                return;
            }

            // Semicolon often ends statements - good sync point
            if (previousToken != null && previousToken.type == TokenType.SEMICOLON) {
                return;
            }

            advance();
        }
    }

    /**
     * Legacy synchronize - uses combined sync points for backwards compatibility.
     * Prefer synchronizeTo(followSet) for context-aware recovery.
     */
    private void synchronize() {
        synchronizeTo(FOLLOW_STATEMENT);
    }

    private void reportError(CompilerError.Type type, String message, String suggestion) {
        if (!panicMode) {
            errorHandler.error(type, message, currentToken.line, 1, suggestion);
            panicMode = true;
        }
    }

    /**
     * Report error and synchronize to FOLLOW set in one call.
     * Returns true if recovery was needed (an error occurred).
     */
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

    // ═══════════════════════════════════════════════════════════════
    // TYPE CHECKS
    // ═══════════════════════════════════════════════════════════════

    public boolean hasErrors() {
        return errorHandler.hasErrors(CompilerError.Phase.PARSER);
    }

    private boolean isTypeSpecifier() {
        switch (currentToken.type) {
            case NUMBER: case REAL: case MUCHOTEXTO:
            case MAYBE: case LETTER:
            case CONST: case CATALOG:
                return true;
            default: return false;
        }
    }

    private boolean isBaseType() {
        switch (currentToken.type) {
            case NUMBER: case REAL: case MUCHOTEXTO:
            case MAYBE: case LETTER:
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

    // ═══════════════════════════════════════════════════════════════
    // MAIN ENTRY
    // ═══════════════════════════════════════════════════════════════

    public ParseTree parse() {
        TreeNode root = parseProgram();
        return new ParseTree(root);
    }

    // ═══════════════════════════════════════════════════════════════
    // PROGRAM
    // ═══════════════════════════════════════════════════════════════

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
            // Check if current token is an ID with a suggestion (possible typo)
            if (check(TokenType.ID) && currentToken.hasSuggestion()) {
                // Check if the suggestion is "maincharacter"
                if ("maincharacter".equals(currentToken.suggestion)) {
                    // Report the typo but treat it as maincharacter
                    // Store the misspelled token for error reporting
                    Token misspelled = currentToken;
                    errorHandler.error(
                            CompilerError.Type.UNKNOWN_IDENTIFIER,
                            "Unknown identifier '" + misspelled.value + "'",
                            misspelled.line,
                            1,
                            misspelled.value.length(),
                            "Did you mean '" + misspelled.suggestion + "'?"
                    );
                    // Advance past the misspelled ID - we'll treat it as maincharacter
                    advance();
                    // Mark that we found the "main function start" via typo recovery
                    mainFunctionTypoRecovered = true;
                    break;
                }
            }

            TreeNode decl = parseDeclaration();
            if (decl != null) {
                node.addChild(decl);
            }

            // If we're in panic mode, synchronize to declaration FOLLOW set
            if (panicMode) {
                synchronizeTo(FOLLOW_DECLARATION);
            }
        }
        return node;
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
                // Check for typo
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
                // Sync to FOLLOW(declaration) to find next valid declaration or maincharacter
                synchronizeTo(FOLLOW_DECLARATION);
                return null;
        }
        return node;
    }

    // ═══════════════════════════════════════════════════════════════
    // DECLARATIONS
    // ═══════════════════════════════════════════════════════════════

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

    // ═══════════════════════════════════════════════════════════════
    // FUNCTIONS
    // ═══════════════════════════════════════════════════════════════

    private TreeNode parseFunctionDeclaration() {
        TreeNode node = new TreeNode("function_declaration");
        node.addChild(new TreeNode(match(TokenType.SIDEQUEST)));
        node.addChild(parseTypeSpecifier());
        node.addChild(new TreeNode(match(TokenType.ID)));
        node.addChild(new TreeNode(match(TokenType.LPAREN)));
        node.addChild(parseParamListOpt());
        node.addChild(new TreeNode(match(TokenType.RPAREN)));
        node.addChild(parseBlock());
        return node;
    }

    private TreeNode parseMainFunction() {
        TreeNode node = new TreeNode("main_function");
        // If we recovered from a "maincharacter" typo, we already consumed the ID
        if (mainFunctionTypoRecovered) {
            // Create a synthetic MAINCHARACTER token for the tree
            node.addChild(new TreeNode(new Token(TokenType.MAINCHARACTER, "maincharacter", currentToken.line)));
        } else {
            node.addChild(new TreeNode(match(TokenType.MAINCHARACTER)));
        }
        node.addChild(new TreeNode(match(TokenType.LPAREN)));
        node.addChild(new TreeNode(match(TokenType.RPAREN)));
        node.addChild(parseBlock());
        return node;
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

    // ═══════════════════════════════════════════════════════════════
    // TYPES
    // ═══════════════════════════════════════════════════════════════

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
            case MAYBE: case LETTER:
                node.addChild(new TreeNode(advance()));
                break;
            default:
                reportError(
                        CompilerError.Type.UNEXPECTED_TOKEN,
                        "Expected base type but found " + currentToken.type,
                        "Valid types are: number, real, muchotexto, maybe, letter."
                );
                // Insert a placeholder type to allow parsing to continue
                node.addChild(new TreeNode(new Token(TokenType.NUMBER, "number", currentToken.line)));
                // Don't sync here - let the caller handle recovery based on their FOLLOW set
        }
        return node;
    }

    // ═══════════════════════════════════════════════════════════════
    // BLOCK & STATEMENTS
    // ═══════════════════════════════════════════════════════════════

    private TreeNode parseBlock() {
        TreeNode node = new TreeNode("block");
        node.addChild(new TreeNode(match(TokenType.LBRACE)));
        while (!check(TokenType.RBRACE) && !check(TokenType.EOF)) {
            node.addChild(parseStatement());
            if (panicMode) {
                // Sync to FOLLOW(statement) to find next valid statement or RBRACE
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
            case SAYLESS:     node.addChild(parseContinueStatement()); break;
            case YAP:         node.addChild(parsePrintStatement());    break;
            case LISTENCLOSELY: node.addChild(parseInputStatement());  break;
            case LBRACE:      node.addChild(parseBlock());             break;
            default:
                if (check(TokenType.CONST) || check(TokenType.CATALOG) || isBaseType()) {
                    node.addChild(parseDeclarationStatement());
                } else {
                    node.addChild(parseExpressionStatement());
                }
        }
        return node;
    }

    private TreeNode parseDeclarationStatement() {
        TreeNode node = new TreeNode("declaration_statement");
        if (check(TokenType.CONST)) {
            node.addChild(new TreeNode(match(TokenType.CONST)));
            node.addChild(parseBaseType());
            node.addChild(new TreeNode(match(TokenType.ID)));
            node.addChild(new TreeNode(match(TokenType.ASSIGN)));
            node.addChild(parseExpression());
            node.addChild(new TreeNode(match(TokenType.SEMICOLON)));
        } else {
            node.addChild(parseTypeSpecifierNoConst());
            node.addChild(parseDeclOrExprTail());
        }
        return node;
    }

    private TreeNode parseExpressionStatement() {
        TreeNode node = new TreeNode("expression_statement");
        if (check(TokenType.ID)) {
            node.addChild(new TreeNode(match(TokenType.ID)));
            node.addChild(parseIdLedTail());
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

    // ═══════════════════════════════════════════════════════════════
    // CONTROL FLOW
    // ═══════════════════════════════════════════════════════════════

    private TreeNode parseIfStatement() {
        TreeNode node = new TreeNode("if_statement");
        node.addChild(new TreeNode(match(TokenType.HEARMEOUT)));
        node.addChild(new TreeNode(match(TokenType.LPAREN)));
        node.addChild(parseExpression());
        node.addChild(new TreeNode(match(TokenType.RPAREN)));
        node.addChild(parseBlock());
        node.addChild(parseElseIfChain());
        return node;
    }

    private TreeNode parseElseIfChain() {
        TreeNode node = new TreeNode("else_if_chain");
        if (check(TokenType.PERHAPS)) {
            node.addChild(new TreeNode(match(TokenType.PERHAPS)));
            node.addChild(new TreeNode(match(TokenType.LPAREN)));
            node.addChild(parseExpression());
            node.addChild(new TreeNode(match(TokenType.RPAREN)));
            node.addChild(parseBlock());
            node.addChild(parseElseIfChain());
        } else {
            node.addChild(parseElseFinal());
        }
        return node;
    }

    private TreeNode parseElseFinal() {
        TreeNode node = new TreeNode("else_final");
        if (check(TokenType.NVM)) {
            node.addChild(new TreeNode(match(TokenType.NVM)));
            node.addChild(parseBlock());
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
        node.addChild(parseSwitchBody());
        node.addChild(new TreeNode(match(TokenType.RBRACE)));
        return node;
    }

    private TreeNode parseSwitchBody() {
        TreeNode node = new TreeNode("switch_body");
        while (check(TokenType.CHECKMEOUT) || check(TokenType.IDC)) {
            node.addChild(parseSwitchItem());
        }
        return node;
    }

    private TreeNode parseSwitchItem() {
        TreeNode node = new TreeNode("switch_item");
        if (check(TokenType.CHECKMEOUT)) {
            node.addChild(parseCaseClause());
        } else {
            node.addChild(parseDefaultClause());
        }
        return node;
    }

    private TreeNode parseCaseClause() {
        TreeNode node = new TreeNode("case_clause");
        node.addChild(new TreeNode(match(TokenType.CHECKMEOUT)));
        node.addChild(parseLiteral());
        node.addChild(new TreeNode(match(TokenType.COLON)));
        node.addChild(parseCaseBody());
        return node;
    }

    private TreeNode parseDefaultClause() {
        TreeNode node = new TreeNode("default_clause");
        node.addChild(new TreeNode(match(TokenType.IDC)));
        node.addChild(new TreeNode(match(TokenType.COLON)));
        node.addChild(parseCaseBody());
        return node;
    }

    private TreeNode parseCaseBody() {
        TreeNode node = new TreeNode("case_body");
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
        if (check(TokenType.RAGEQUIT)) {
            node.addChild(new TreeNode(match(TokenType.RAGEQUIT)));
            node.addChild(new TreeNode(match(TokenType.SEMICOLON)));
        }
        return node;
    }

    // ═══════════════════════════════════════════════════════════════
    // LOOPS
    // ═══════════════════════════════════════════════════════════════

    private TreeNode parseForStatement() {
        TreeNode node = new TreeNode("for_statement");
        node.addChild(new TreeNode(match(TokenType.FAR)));
        node.addChild(new TreeNode(match(TokenType.LPAREN)));
        node.addChild(parseForInit());
        node.addChild(new TreeNode(match(TokenType.SEMICOLON)));
        node.addChild(parseExpression());
        node.addChild(new TreeNode(match(TokenType.SEMICOLON)));
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

    // ═══════════════════════════════════════════════════════════════
    // SIMPLE STATEMENTS
    // ═══════════════════════════════════════════════════════════════

    private TreeNode parseReturnStatement() {
        TreeNode node = new TreeNode("return_statement");
        node.addChild(new TreeNode(match(TokenType.MICDROP)));
        node.addChild(parseReturnTail());
        return node;
    }

    private TreeNode parseReturnTail() {
        TreeNode node = new TreeNode("return_tail");
        if (!check(TokenType.SEMICOLON)) {
            node.addChild(parseExpression());
        }
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
        node.addChild(parseArgumentListOpt());
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

    // ═══════════════════════════════════════════════════════════════
    // EXPRESSIONS
    // ═══════════════════════════════════════════════════════════════

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
        } else if (check(TokenType.MINUS)) {
            node.addChild(new TreeNode(match(TokenType.MINUS)));
            node.addChild(parseUnary());
        } else if (check(TokenType.PLUS)) {
            node.addChild(new TreeNode(match(TokenType.PLUS)));
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
        if (check(TokenType.LBRACKET)) {
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
                // Insert placeholder and sync to FOLLOW(expression) since literals appear in expressions
                node.addChild(new TreeNode(new Token(TokenType.INT_LITERAL, "0", currentToken.line)));
                synchronizeTo(FOLLOW_EXPRESSION);
        }
        return node;
    }

    // ═══════════════════════════════════════════════════════════════
    // FUNCTION CALL ARGS
    // ═══════════════════════════════════════════════════════════════

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

    // ═══════════════════════════════════════════════════════════════
    // STRUCTS / ENUMS / IMPORTS
    // ═══════════════════════════════════════════════════════════════

    private TreeNode parseStructDeclaration() {
        TreeNode node = new TreeNode("struct_declaration");
        node.addChild(new TreeNode(match(TokenType.OUTFIT)));
        node.addChild(new TreeNode(match(TokenType.ID)));
        node.addChild(new TreeNode(match(TokenType.LBRACE)));
        while (!check(TokenType.RBRACE) && !check(TokenType.EOF)) {
            node.addChild(parseVariableDeclaration());
            if (panicMode) {
                // Sync to next type specifier (start of var decl) or RBRACE
                synchronizeTo(EnumSet.of(
                        TokenType.NUMBER, TokenType.REAL, TokenType.MUCHOTEXTO,
                        TokenType.MAYBE, TokenType.LETTER,
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
