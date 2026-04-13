import java.util.List;

public class Parser {

    private final List<Token> tokens;
    private int pos;
    private Token currentToken;

    public Parser(List<Token> tokens) {
        this.tokens       = tokens;
        this.pos          = 0;
        this.currentToken = tokens.get(0);
    }

    // ── Utilidades ─────────────────────────────────────────

    private Token advance() {
        Token t = currentToken;
        if (pos < tokens.size() - 1) pos++;
        currentToken = tokens.get(pos);
        return t;
    }

    private Token match(TokenType expected) {
        if (currentToken.type == expected) {
            return advance();
        }
        throw new RuntimeException(
                "Syntax error at line " + currentToken.line +
                        ": expected " + expected +
                        " but found " + currentToken.type +
                        " (\"" + currentToken.value + "\")"
        );
    }

    private boolean check(TokenType type) {
        return currentToken.type == type;
    }

    private boolean isTypeSpecifier() {
        switch (currentToken.type) {
            case NUMBER: case REAL: case MUCHOTEXTO:
            case MAYBE:  case LETTER: case SIXSEVEN:
            case CONST:  case CATALOG:
                return true;
            default: return false;
        }
    }

    // ── Entrada principal ───────────────────────────────────

    public ParseTree parse() {
        TreeNode root = parseProgram();
        return new ParseTree(root);
    }

    // ── PROGRAM ─────────────────────────────────────────────

    // <program> ::= <declaration_list>
    private TreeNode parseProgram() {
        TreeNode node = new TreeNode("program");
        node.addChild(parseDeclarationList());
        match(TokenType.EOF);
        return node;
    }

    // <declaration_list> ::= <declaration> | <declaration_list> <declaration>
    private TreeNode parseDeclarationList() {
        TreeNode node = new TreeNode("declaration_list");
        while (!check(TokenType.EOF)) {
            node.addChild(parseDeclaration());
        }
        return node;
    }

    private TreeNode parseDeclaration() {
        TreeNode node = new TreeNode("declaration");
        switch (currentToken.type) {
            case SIDEQUEST:   node.addChild(parseFunctionDeclaration()); break;
            case MAINCHARACTER: node.addChild(parseMainFunction());      break;
            case OUTFIT:      node.addChild(parseStructDeclaration());   break;
            case PICKS:       node.addChild(parseEnumDeclaration());     break;
            case INJECT:      node.addChild(parseImportStatement());     break;
            case CONST:       node.addChild(parseConstantDeclaration()); break;
            default:
                if (isTypeSpecifier()) node.addChild(parseVariableDeclaration());
                else throw new RuntimeException(
                        "Syntax error at line " + currentToken.line +
                                ": unexpected token " + currentToken.type);
        }
        return node;
    }

    // ── DECLARATIONS ────────────────────────────────────────

    // <variable_declaration> ::= <type_specifier> ID ASSIGN <expression> SEMICOLON
    private TreeNode parseVariableDeclaration() {
        TreeNode node = new TreeNode("variable_declaration");
        node.addChild(parseTypeSpecifier());
        node.addChild(new TreeNode(match(TokenType.ID)));
        node.addChild(new TreeNode(match(TokenType.ASSIGN)));
        node.addChild(parseExpression());
        node.addChild(new TreeNode(match(TokenType.SEMICOLON)));
        return node;
    }

    // <constant_declaration> ::= CONST <type_specifier> ID ASSIGN <expression> SEMICOLON
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

    // ── FUNCTIONS ───────────────────────────────────────────

    // <function_declaration> ::= SIDEQUEST <type_specifier> ID LPAREN <param_list_opt> RPAREN <block>
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

    // <main_function> ::= MAINCHARACTER LPAREN RPAREN <block>
    private TreeNode parseMainFunction() {
        TreeNode node = new TreeNode("main_function");
        node.addChild(new TreeNode(match(TokenType.MAINCHARACTER)));
        node.addChild(new TreeNode(match(TokenType.LPAREN)));
        node.addChild(new TreeNode(match(TokenType.RPAREN)));
        node.addChild(parseBlock());
        return node;
    }

    // <param_list_opt> ::= <param_list> | epsilon
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

    // <param> ::= <type_specifier> ID
    private TreeNode parseParam() {
        TreeNode node = new TreeNode("param");
        node.addChild(parseTypeSpecifier());
        node.addChild(new TreeNode(match(TokenType.ID)));
        return node;
    }

    // ── TYPES ───────────────────────────────────────────────

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
            case MAYBE:  case LETTER: case SIXSEVEN:
                node.addChild(new TreeNode(advance()));
                break;
            default:
                throw new RuntimeException(
                        "Syntax error at line " + currentToken.line +
                                ": expected base type but found " + currentToken.type);
        }
        return node;
    }

    // ── BLOCK & STATEMENTS ──────────────────────────────────

    private TreeNode parseBlock() {
        TreeNode node = new TreeNode("block");
        node.addChild(new TreeNode(match(TokenType.LBRACE)));
        while (!check(TokenType.RBRACE) && !check(TokenType.EOF)) {
            node.addChild(parseStatement());
        }
        node.addChild(new TreeNode(match(TokenType.RBRACE)));
        return node;
    }

    private TreeNode parseStatement() {
        TreeNode node = new TreeNode("statement");
        switch (currentToken.type) {
            case CONST:       node.addChild(parseConstantDeclaration()); break;
            case HEARMEOUT:   node.addChild(parseIfStatement());         break;
            case CONSIDER:    node.addChild(parseSwitchStatement());     break;
            case FAR:         node.addChild(parseForStatement());        break;
            case TAKEAS:      node.addChild(parseWhileStatement());      break;
            case POST_ONCE:   node.addChild(parseDoWhileStatement());    break;
            case MICDROP:     node.addChild(parseReturnStatement());     break;
            case RAGEQUIT:    node.addChild(parseBreakStatement());      break;
            case SAYLESS:     node.addChild(parseContinueStatement());   break;
            case YAP:         node.addChild(parsePrintStatement());      break;
            case LISTENCLOSELY: node.addChild(parseInputStatement());    break;
            case LBRACE:      node.addChild(parseBlock());               break;
            case ID:          node.addChild(parseAssignmentStatement()); break;
            default:
                if (isTypeSpecifier()) node.addChild(parseVariableDeclaration());
                else node.addChild(parseExpressionStatement());
        }
        return node;
    }

    // ── CONTROL FLOW ────────────────────────────────────────

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
        while (!check(TokenType.CHECKMEOUT) && !check(TokenType.IDC)
                && !check(TokenType.RBRACE) && !check(TokenType.EOF)) {
            node.addChild(parseStatement());
        }
        return node;
    }

    // ── LOOPS ───────────────────────────────────────────────

    private TreeNode parseForStatement() {
        TreeNode node = new TreeNode("for_statement");
        node.addChild(new TreeNode(match(TokenType.FAR)));
        node.addChild(new TreeNode(match(TokenType.LPAREN)));
        node.addChild(parseForInit());
        node.addChild(new TreeNode(match(TokenType.PIPE)));
        node.addChild(parseExpression());
        node.addChild(new TreeNode(match(TokenType.PIPE)));
        node.addChild(parseForUpdate());
        node.addChild(new TreeNode(match(TokenType.RPAREN)));
        node.addChild(parseBlock());
        return node;
    }

    private TreeNode parseForInit() {
        TreeNode node = new TreeNode("for_init");
        if (isTypeSpecifier()) {
            node.addChild(parseTypeSpecifier());
            node.addChild(new TreeNode(match(TokenType.ASSIGN)));
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

    // ── SIMPLE STATEMENTS ───────────────────────────────────

    private TreeNode parseAssignmentStatement() {
        TreeNode node = new TreeNode("assignment_statement");
        node.addChild(new TreeNode(match(TokenType.ID)));
        node.addChild(new TreeNode(match(TokenType.ASSIGN)));
        node.addChild(parseExpression());
        node.addChild(new TreeNode(match(TokenType.SEMICOLON)));
        return node;
    }

    private TreeNode parseExpressionStatement() {
        TreeNode node = new TreeNode("expression_statement");
        node.addChild(parseExpression());
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

    // ── EXPRESSIONS ─────────────────────────────────────────

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
        } else {
            node.addChild(parsePostfix());
        }
        return node;
    }

    private TreeNode parsePostfix() {
        TreeNode node = new TreeNode("postfix_expression");
        // function call or array access
        if (check(TokenType.ID)) {
            Token id = advance();
            if (check(TokenType.LPAREN)) {
                // function call
                TreeNode call = new TreeNode("function_call");
                call.addChild(new TreeNode(id));
                call.addChild(new TreeNode(match(TokenType.LPAREN)));
                call.addChild(parseArgumentListOpt());
                call.addChild(new TreeNode(match(TokenType.RPAREN)));
                node.addChild(call);
            } else if (check(TokenType.LBRACKET)) {
                // array access
                TreeNode arr = new TreeNode("array_access");
                arr.addChild(new TreeNode(id));
                arr.addChild(new TreeNode(match(TokenType.LBRACKET)));
                arr.addChild(parseExpression());
                arr.addChild(new TreeNode(match(TokenType.RBRACKET)));
                node.addChild(arr);
            } else if (check(TokenType.PLUS) && tokens.get(pos).value.equals("++")) {
                node.addChild(new TreeNode(id));
                node.addChild(new TreeNode(advance())); // ++
            } else if (check(TokenType.MINUS) && tokens.get(pos).value.equals("--")) {
                node.addChild(new TreeNode(id));
                node.addChild(new TreeNode(advance())); // --
            } else {
                TreeNode primary = new TreeNode("primary_expression");
                primary.addChild(new TreeNode(id));
                node.addChild(primary);
            }
        } else {
            node.addChild(parsePrimary());
        }
        return node;
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
                throw new RuntimeException(
                        "Syntax error at line " + currentToken.line +
                                ": expected literal but found " + currentToken.type);
        }
        return node;
    }

    // ── FUNCTION CALL ARGS ──────────────────────────────────

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

    // ── STRUCTS / ENUMS / IMPORTS ───────────────────────────

    private TreeNode parseStructDeclaration() {
        TreeNode node = new TreeNode("struct_declaration");
        node.addChild(new TreeNode(match(TokenType.OUTFIT)));
        node.addChild(new TreeNode(match(TokenType.ID)));
        node.addChild(new TreeNode(match(TokenType.LBRACE)));
        while (!check(TokenType.RBRACE) && !check(TokenType.EOF)) {
            node.addChild(parseVariableDeclaration());
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