import compiler.error.*;
import java.util.*;

public class Semantic implements SemanticListener {

    private final ErrorHandler errors;
    private final SymbolTable  symbols;

    private Symbol currentFunction = null;
    private int loopDepth   = 0;
    private int switchDepth = 0;

    private final List<PendingFunctionBody> pendingFunctions = new ArrayList<>();
    private PendingFunctionBody currentPendingFunction = null;
    private PendingMainBody pendingMain = null;
    private PendingMainBody currentPendingMain = null;

    private static class PendingFunctionBody {
        final TreeNode signatureNode;
        final Symbol   functionSymbol;
        final List<TreeNode> statements = new ArrayList<>();

        PendingFunctionBody(TreeNode signatureNode, Symbol functionSymbol) {
            this.signatureNode  = signatureNode;
            this.functionSymbol = functionSymbol;
        }
    }

    private static class PendingMainBody {
        final List<TreeNode> statements = new ArrayList<>();
    }

    public Semantic(ErrorHandler errors) {
        this.errors  = errors;
        this.symbols = new SymbolTable();
    }

    public boolean hasErrors() {
        return errors.hasErrors(CompilerError.Phase.SEMANTIC);
    }

    public SymbolTable getSymbolTable() {
        return symbols;
    }


    @Override
    public void onInject(TreeNode injectNode) {
    }

    @Override
    public void onStruct(TreeNode structNode) {
        collectStruct(structNode);
    }

    @Override
    public void onEnum(TreeNode enumNode) {
        collectEnum(enumNode);
    }

    @Override
    public void onFunctionSignature(TreeNode signatureNode) {
        Symbol functionSymbol = collectFunction(signatureNode);
        currentPendingFunction = new PendingFunctionBody(signatureNode, functionSymbol);
        pendingFunctions.add(currentPendingFunction);
    }

    @Override
    public void onFunctionBodyStatement(TreeNode statementNode) {
        if (currentPendingFunction != null) {
            currentPendingFunction.statements.add(statementNode);
        }
    }

    @Override
    public void onFunctionEnd() {
        currentPendingFunction = null;
    }

    @Override
    public void onMainBegin(TreeNode mainHeaderNode) {
        currentPendingMain = new PendingMainBody();
        pendingMain = currentPendingMain;
    }

    @Override
    public void onMainStatement(TreeNode statementNode) {
        if (currentPendingMain != null) {
            currentPendingMain.statements.add(statementNode);
        }
    }

    @Override
    public void onMainEnd() {
        currentPendingMain = null;
    }

    @Override
    public void onProgramEnd() {
        for (PendingFunctionBody pendingBody : pendingFunctions) {
            analyzePendingFunctionBody(pendingBody);
        }
        if (pendingMain != null) {
            analyzePendingMainBody(pendingMain);
        }
    }

    private void analyzePendingFunctionBody(PendingFunctionBody pendingBody) {
        Symbol previousFunction = currentFunction;
        currentFunction = pendingBody.functionSymbol;
        symbols.enterScope();

        registerParametersFromSignature(pendingBody.signatureNode);

        for (TreeNode statementNode : pendingBody.statements) {
            analyzeStatement(statementNode);
        }

        symbols.exitScope();
        currentFunction = previousFunction;
    }

    private void analyzePendingMainBody(PendingMainBody pendingBody) {
        symbols.enterScope();
        for (TreeNode statementNode : pendingBody.statements) {
            analyzeStatement(statementNode);
        }
        symbols.exitScope();
    }

    private void registerParametersFromSignature(TreeNode signatureNode) {
        TreeNode parameterListOptionalNode = childByLabel(signatureNode, "param_list_opt");
        if (parameterListOptionalNode == null) {
            return;
        }
        TreeNode parameterListNode = childByLabel(parameterListOptionalNode, "param_list");
        if (parameterListNode == null) {
            return;
        }
        for (TreeNode parameterNode : parameterListNode.children) {
            if (!"param".equals(parameterNode.label)) {
                continue;
            }
            SymbolTable.Type parameterType = resolveTypeSpecifier(childByLabel(parameterNode, "type_specifier"));
            Token parameterNameToken = firstTokenOfType(parameterNode, TokenType.ID);
            if (parameterNameToken == null) {
                continue;
            }
            Symbol parameterSymbol = new Symbol(parameterNameToken.value, Symbol.Kind.PARAMETER, parameterType, parameterNameToken.line);
            parameterSymbol.initialized = true;
            if (symbols.insert(parameterSymbol) != null) {
                duplicate(parameterNameToken, "parameter");
            }
        }
    }

    private Symbol collectFunction(TreeNode signatureNode) {
        SymbolTable.Type returnType = resolveTypeSpecifier(childByLabel(signatureNode, "type_specifier"));
        Token nameToken = firstTokenOfType(signatureNode, TokenType.ID);
        if (nameToken == null) {
            return null;
        }

        Symbol functionSymbol = new Symbol(nameToken.value, Symbol.Kind.FUNCTION, returnType, nameToken.line);

        TreeNode parameterListOptionalNode = childByLabel(signatureNode, "param_list_opt");
        if (parameterListOptionalNode != null) {
            TreeNode parameterListNode = childByLabel(parameterListOptionalNode, "param_list");
            if (parameterListNode != null) {
                for (TreeNode parameterNode : parameterListNode.children) {
                    if ("param".equals(parameterNode.label)) {
                        SymbolTable.Type parameterType = resolveTypeSpecifier(childByLabel(parameterNode, "type_specifier"));
                        Token parameterNameToken = firstTokenOfType(parameterNode, TokenType.ID);
                        functionSymbol.paramTypes.add(parameterType);
                        functionSymbol.paramNames.add(parameterNameToken != null ? parameterNameToken.value : "?");
                    }
                }
            }
        }
        Symbol existingSymbol = symbols.insert(functionSymbol);
        if (existingSymbol != null) {
            duplicate(nameToken, "function");
            return existingSymbol;
        }
        return functionSymbol;
    }

    private void collectStruct(TreeNode structNode) {
        Token nameToken = firstTokenOfType(structNode, TokenType.ID);
        if (nameToken == null) {
            return;
        }

        Symbol structSymbol = new Symbol(nameToken.value, Symbol.Kind.STRUCT,
                SymbolTable.Type.struct(nameToken.value), nameToken.line);

        for (TreeNode childNode : structNode.children) {
            if ("variable_declaration".equals(childNode.label)) {
                SymbolTable.Type fieldType = resolveTypeSpecifier(childByLabel(childNode, "type_specifier"));
                Token fieldNameToken = firstTokenOfType(childNode, TokenType.ID);
                if (fieldNameToken != null) {
                    if (structSymbol.fields.containsKey(fieldNameToken.value)) {
                        duplicate(fieldNameToken, "field");
                    } else {
                        structSymbol.fields.put(fieldNameToken.value, fieldType);
                    }
                }
            }
        }
        if (symbols.insert(structSymbol) != null) {
            duplicate(nameToken, "struct");
        }
    }

    private void collectEnum(TreeNode enumNode) {
        List<Token> identifierTokens = collectTokensOfType(enumNode, TokenType.ID);
        if (identifierTokens.isEmpty()) {
            return;
        }
        Token enumNameToken = identifierTokens.get(0);

        Symbol enumSymbol = new Symbol(enumNameToken.value, Symbol.Kind.ENUM,
                SymbolTable.Type.enumType(enumNameToken.value), enumNameToken.line);

        for (int valueIndex = 1; valueIndex < identifierTokens.size(); valueIndex++) {
            Token valueToken = identifierTokens.get(valueIndex);
            if (!enumSymbol.values.add(valueToken.value)) {
                duplicate(valueToken, "enum value");
                continue;
            }
            Symbol valueSymbol = new Symbol(valueToken.value, Symbol.Kind.ENUM_VALUE,
                    SymbolTable.Type.enumType(enumNameToken.value), valueToken.line);
            valueSymbol.parentEnum = enumNameToken.value;
            valueSymbol.initialized = true;
            if (symbols.lookupGlobal(valueToken.value) == null) {
                symbols.insert(valueSymbol);
            }
        }
        if (symbols.insert(enumSymbol) != null) {
            duplicate(enumNameToken, "enum");
        }
    }

    private void analyzeBlock(TreeNode blockNode, boolean createsOwnScope) {
        if (createsOwnScope) {
            symbols.enterScope();
        }
        for (TreeNode childNode : blockNode.children) {
            if ("statement".equals(childNode.label)) {
                analyzeStatement(childNode);
            }
        }
        if (createsOwnScope) {
            symbols.exitScope();
        }
    }

    private void analyzeStatement(TreeNode statementNode) {
        if (statementNode.children.isEmpty()) {
            return;
        }
        TreeNode statementChild = statementNode.children.get(0);
        switch (statementChild.label) {
            case "if_statement":
                analyzeIf(statementChild);
                break;
            case "switch_statement":
                analyzeSwitch(statementChild);
                break;
            case "for_statement":
                analyzeFor(statementChild);
                break;
            case "while_statement":
                analyzeWhile(statementChild);
                break;
            case "do_while_statement":
                analyzeDoWhile(statementChild);
                break;
            case "break_statement":
                if (loopDepth == 0 && switchDepth == 0) {
                    semanticError(firstToken(statementChild), "'ragequit' used outside of a loop or switch.");
                }
                break;
            case "continue_statement":
                if (loopDepth == 0) {
                    semanticError(firstToken(statementChild), "'sayless' used outside of a loop.");
                }
                break;
            case "return_statement":
                analyzeReturn(statementChild);
                break;
            case "print_statement":
                analyzePrint(statementChild);
                break;
            case "input_statement":
                analyzeInput(statementChild);
                break;
            case "block":
                analyzeBlock(statementChild, true);
                break;
            case "open_statement":
                analyzeOpenStatement(statementChild);
                break;
            default:
                break;
        }
    }

    private void analyzeIf(TreeNode ifNode) {
        TreeNode conditionNode = nthExpression(ifNode, 0);
        SymbolTable.Type conditionType = evalExpr(conditionNode);
        requireBoolean(conditionType, firstToken(ifNode), "if (hearmeout)");
        for (TreeNode childNode : ifNode.children) {
            if ("block".equals(childNode.label)) {
                analyzeBlock(childNode, true);
            } else if ("else_clause".equals(childNode.label)) {
                analyzeElse(childNode);
            }
        }
    }

    private void analyzeElse(TreeNode elseNode) {
        boolean hasCondition = nthExpression(elseNode, 0) != null;
        if (hasCondition) {
            SymbolTable.Type conditionType = evalExpr(nthExpression(elseNode, 0));
            requireBoolean(conditionType, firstToken(elseNode), "perhaps");
        }
        for (TreeNode childNode : elseNode.children) {
            if ("block".equals(childNode.label)) {
                analyzeBlock(childNode, true);
            } else if ("else_clause".equals(childNode.label)) {
                analyzeElse(childNode);
            }
        }
    }

    private void analyzeSwitch(TreeNode switchNode) {
        SymbolTable.Type discriminatorType = evalExpr(nthExpression(switchNode, 0));
        switchDepth++;
        for (TreeNode childNode : switchNode.children) {
            if ("case_clause".equals(childNode.label)) {
                TreeNode literalNode = childByLabel(childNode, "literal");
                if (literalNode != null) {
                    SymbolTable.Type literalType = evalLiteral(literalNode);
                    if (!discriminatorType.isError() && !literalType.isError()
                            && !SymbolTable.Type.assignable(discriminatorType, literalType)) {
                        semanticError(firstToken(childNode),
                                "Case type (" + literalType + ") is not compatible with switch type (" + discriminatorType + ").");
                    }
                }
                for (TreeNode caseStatement : childNode.children) {
                    if ("statement".equals(caseStatement.label)) {
                        analyzeStatement(caseStatement);
                    }
                }
            }
        }
        switchDepth--;
    }

    private void analyzeFor(TreeNode forNode) {
        symbols.enterScope();
        TreeNode forInitNode   = childByLabel(forNode, "for_init");
        TreeNode forUpdateNode = childByLabel(forNode, "for_update");
        TreeNode conditionNode = nthExpression(forNode, 0);

        if (forInitNode != null) {
            analyzeForInit(forInitNode);
        }
        if (conditionNode != null) {
            requireBoolean(evalExpr(conditionNode), firstToken(forNode), "for");
        }
        if (forUpdateNode != null) {
            analyzeForUpdate(forUpdateNode);
        }

        loopDepth++;
        TreeNode bodyBlock = childByLabel(forNode, "block");
        if (bodyBlock != null) {
            analyzeBlock(bodyBlock, false);
        }
        loopDepth--;

        symbols.exitScope();
    }

    private void analyzeForInit(TreeNode forInitNode) {
        if (forInitNode.children.isEmpty()) {
            return;
        }
        TreeNode firstChild = forInitNode.children.get(0);
        if ("type_specifier".equals(firstChild.label)) {
            SymbolTable.Type declaredType = resolveTypeSpecifier(firstChild);
            Token variableToken = firstTokenOfType(forInitNode, TokenType.ID);
            SymbolTable.Type rightHandSideType = evalExpr(nthExpression(forInitNode, 0));
            declareVariable(variableToken, declaredType, rightHandSideType, false);
        } else if (firstChild.token != null && firstChild.token.type == TokenType.ID) {
            Symbol existingSymbol = symbols.lookup(firstChild.token.value);
            if (existingSymbol == null) {
                undeclared(firstChild.token);
            } else if (existingSymbol.isConst()) {
                semanticError(firstChild.token, "Cannot assign to constant '" + existingSymbol.name + "'.");
            }
            SymbolTable.Type rightHandSideType = evalExpr(nthExpression(forInitNode, 0));
            if (existingSymbol != null && !existingSymbol.type.isError()
                    && !SymbolTable.Type.assignable(existingSymbol.type, rightHandSideType)) {
                typeMismatch(firstChild.token, existingSymbol.type, rightHandSideType);
            }
        }
    }

    private void analyzeForUpdate(TreeNode forUpdateNode) {
        TreeNode expressionNode = nthExpression(forUpdateNode, 0);
        if (expressionNode != null) {
            evalExpr(expressionNode);
        }
    }

    private void analyzeWhile(TreeNode whileNode) {
        requireBoolean(evalExpr(nthExpression(whileNode, 0)), firstToken(whileNode), "while (takeas)");
        loopDepth++;
        TreeNode bodyBlock = childByLabel(whileNode, "block");
        if (bodyBlock != null) {
            analyzeBlock(bodyBlock, true);
        }
        loopDepth--;
    }

    private void analyzeDoWhile(TreeNode doWhileNode) {
        loopDepth++;
        TreeNode bodyBlock = childByLabel(doWhileNode, "block");
        if (bodyBlock != null) {
            analyzeBlock(bodyBlock, true);
        }
        loopDepth--;
        requireBoolean(evalExpr(nthExpression(doWhileNode, 0)), firstToken(doWhileNode), "do-while (keep_it_going)");
    }

    private void analyzeReturn(TreeNode returnNode) {
        TreeNode expressionNode = nthExpression(returnNode, 0);
        SymbolTable.Type actualReturnType = (expressionNode == null) ? SymbolTable.Type.VOID : evalExpr(expressionNode);
        SymbolTable.Type expectedReturnType = (currentFunction != null) ? currentFunction.type : SymbolTable.Type.VOID;
        if (currentFunction == null) {
            if (expressionNode != null && !actualReturnType.isError()) {
                semanticError(firstToken(returnNode), "main (maincharacter) cannot return a value.");
            }
            return;
        }
        if (!SymbolTable.Type.assignable(expectedReturnType, actualReturnType)) {
            semanticError(firstToken(returnNode),
                    "Return type (" + actualReturnType + ") is not compatible with the one declared in '"
                            + currentFunction.name + "' (" + expectedReturnType + ").");
        }
    }

    private void analyzePrint(TreeNode printNode) {
        for (TreeNode childNode : printNode.children) {
            if ("argument_list".equals(childNode.label)) {
                for (TreeNode argumentNode : childNode.children) {
                    if (isExprNode(argumentNode)) {
                        evalExpr(argumentNode);
                    }
                }
            } else if (isExprNode(childNode)) {
                evalExpr(childNode);
            }
        }
    }

    private void analyzeInput(TreeNode inputNode) {
        Token variableToken = firstTokenOfType(inputNode, TokenType.ID);
        if (variableToken == null) {
            return;
        }
        Symbol variableSymbol = symbols.lookup(variableToken.value);
        if (variableSymbol == null) {
            undeclared(variableToken);
            return;
        }
        if (!variableSymbol.isAssignable()) {
            semanticError(variableToken, "Cannot read input into '" + variableToken.value + "' (not a variable).");
            return;
        }
        variableSymbol.initialized = true;
    }

    private void analyzeOpenStatement(TreeNode openStatementNode) {
        if (openStatementNode.children.isEmpty()) {
            return;
        }
        TreeNode firstChild = openStatementNode.children.get(0);

        if (firstChild.token != null && firstChild.token.type == TokenType.CONST) {
            SymbolTable.Type declaredType = resolveBaseType(childByLabel(openStatementNode, "base_type"));
            Token variableToken = firstTokenOfType(openStatementNode, TokenType.ID);
            SymbolTable.Type rightHandSideType = evalExpr(nthExpression(openStatementNode, 0));
            declareVariable(variableToken, declaredType, rightHandSideType, true);
            return;
        }

        if ("type_specifier_no_const".equals(firstChild.label)) {
            SymbolTable.Type declaredType = resolveTypeSpecifierNoConst(firstChild);
            TreeNode tailNode = childByLabel(openStatementNode, "decl_or_expr_tail");
            if (tailNode != null) {
                Token variableToken = firstTokenOfType(tailNode, TokenType.ID);
                if (variableToken != null) {
                    SymbolTable.Type rightHandSideType = evalExpr(nthExpression(tailNode, 0));
                    declareVariable(variableToken, declaredType, rightHandSideType, false);
                }
            }
            return;
        }

        if (firstChild.token != null && firstChild.token.type == TokenType.ID) {
            Token identifierToken = firstChild.token;
            TreeNode tailNode = childByLabel(openStatementNode, "id_led_tail");
            analyzeIdLedTail(identifierToken, tailNode);
            return;
        }

        if (firstChild.token != null
                && (firstChild.token.type == TokenType.PLUS || firstChild.token.type == TokenType.MINUS)) {
            Token variableToken = firstTokenOfType(openStatementNode, TokenType.ID);
            if (variableToken != null) {
                Symbol existingSymbol = symbols.lookup(variableToken.value);
                if (existingSymbol == null) {
                    undeclared(variableToken);
                } else if (!existingSymbol.type.isNumeric()) {
                    semanticError(variableToken, "The ++/-- operator requires a numeric type (found " + existingSymbol.type + ").");
                } else if (existingSymbol.isConst()) {
                    semanticError(variableToken, "Cannot modify constant '" + variableToken.value + "'.");
                }
            }
            return;
        }

        TreeNode expressionNode = nthExpression(openStatementNode, 0);
        if (expressionNode != null) {
            evalExpr(expressionNode);
        }
    }

    private void analyzeIdLedTail(Token identifierToken, TreeNode tailNode) {
        if (tailNode == null || tailNode.children.isEmpty()) {
            return;
        }
        TreeNode firstChild = tailNode.children.get(0);

        if (firstChild.token != null && firstChild.token.type == TokenType.ASSIGN) {
            Symbol existingSymbol = symbols.lookup(identifierToken.value);
            SymbolTable.Type rightHandSideType = evalExpr(nthExpression(tailNode, 0));
            if (existingSymbol == null) {
                undeclared(identifierToken);
                return;
            }
            if (existingSymbol.isConst()) {
                semanticError(identifierToken, "Cannot assign to constant '" + identifierToken.value + "'.");
                return;
            }
            if (!existingSymbol.isAssignable()) {
                semanticError(identifierToken, "'" + identifierToken.value + "' is not assignable.");
                return;
            }
            if (!SymbolTable.Type.assignable(existingSymbol.type, rightHandSideType)) {
                typeMismatch(identifierToken, existingSymbol.type, rightHandSideType);
            } else {
                existingSymbol.initialized = true;
            }
            return;
        }

        if (firstChild.token != null && firstChild.token.type == TokenType.LPAREN) {
            TreeNode argumentsOptionalNode = childByLabel(tailNode, "argument_list_opt");
            checkCall(identifierToken, argumentsOptionalNode);
            return;
        }

        if (firstChild.token != null && firstChild.token.type == TokenType.LBRACKET) {
            Symbol existingSymbol = symbols.lookup(identifierToken.value);
            if (existingSymbol == null) {
                undeclared(identifierToken);
                return;
            }
            if (existingSymbol.type.kind != SymbolTable.Type.Kind.CATALOG) {
                semanticError(identifierToken, "'" + identifierToken.value + "' is not a catalog and does not support indexing.");
                return;
            }
            SymbolTable.Type indexType = evalExpr(nthExpression(tailNode, 0));
            if (!indexType.isError() && !indexType.isInteger()) {
                semanticError(identifierToken, "Index must be an integer, found " + indexType + ".");
            }

            TreeNode primeNode = childByLabel(tailNode, "id_led_tail_prime");
            if (primeNode != null) {
                TreeNode rightHandSideNode = nthExpression(primeNode, 0);
                if (rightHandSideNode != null) {
                    SymbolTable.Type rightHandSideType = evalExpr(rightHandSideNode);
                    if (!SymbolTable.Type.assignable(existingSymbol.type.elementType, rightHandSideType)) {
                        typeMismatch(identifierToken, existingSymbol.type.elementType, rightHandSideType);
                    }
                }
            }
            return;
        }

        if (firstChild.token != null
                && (firstChild.token.type == TokenType.PLUS || firstChild.token.type == TokenType.MINUS)) {
            Symbol existingSymbol = symbols.lookup(identifierToken.value);
            if (existingSymbol == null) {
                undeclared(identifierToken);
                return;
            }
            if (existingSymbol.isConst()) {
                semanticError(identifierToken, "Cannot modify constant '" + identifierToken.value + "'.");
            } else if (!existingSymbol.type.isNumeric()) {
                semanticError(identifierToken, "The ++/-- operator requires a numeric type (found " + existingSymbol.type + ").");
            }
            return;
        }

        if (firstChild.token != null && firstChild.token.type == TokenType.DOT) {
            Symbol existingSymbol = symbols.lookup(identifierToken.value);
            if (existingSymbol == null) {
                undeclared(identifierToken);
                return;
            }
            if (existingSymbol.type.kind != SymbolTable.Type.Kind.STRUCT) {
                semanticError(identifierToken, "'" + identifierToken.value + "' is not a struct, does not support '.'.");
                return;
            }
            return;
        }

        Symbol existingSymbol = symbols.lookup(identifierToken.value);
        if (existingSymbol == null) {
            undeclared(identifierToken);
        }
    }

    private SymbolTable.Type evalExpr(TreeNode expressionNode) {
        if (expressionNode == null) {
            return SymbolTable.Type.ERROR;
        }
        if (expressionNode.token != null) {
            return evalTokenLeaf(expressionNode.token);
        }

        switch (expressionNode.label) {
            case "assignment_expression":
                return evalAssignment(expressionNode);
            case "logical_or_expression":
            case "logical_and_expression":
                return evalLogical(expressionNode);
            case "equality_expression":
                return evalEquality(expressionNode);
            case "relational_expression":
                return evalRelational(expressionNode);
            case "additive_expression":
            case "multiplicative_expression":
                return evalArithmetic(expressionNode);
            case "unary_expression":
                return evalUnary(expressionNode);
            case "postfix_expression":
                return evalPostfix(expressionNode);
            case "primary_expression":
                return evalPrimary(expressionNode);
            case "literal":
                return evalLiteral(expressionNode);
            default:
                for (TreeNode childNode : expressionNode.children) {
                    if (isExprNode(childNode) || childNode.token != null) {
                        return evalExpr(childNode);
                    }
                }
                return SymbolTable.Type.ERROR;
        }
    }

    private SymbolTable.Type evalAssignment(TreeNode assignmentNode) {
        TreeNode leftHandSideNode = assignmentNode.children.get(0);
        Token identifierToken = (leftHandSideNode.children.size() == 1
                && leftHandSideNode.children.get(0).token != null)
                ? leftHandSideNode.children.get(0).token : null;
        SymbolTable.Type rightHandSideType = evalExpr(assignmentNode.children.get(2));
        if (identifierToken == null || identifierToken.type != TokenType.ID) {
            semanticError(firstToken(assignmentNode), "Invalid left-hand side in assignment.");
            return SymbolTable.Type.ERROR;
        }
        Symbol variableSymbol = symbols.lookup(identifierToken.value);
        if (variableSymbol == null) {
            undeclared(identifierToken);
            return SymbolTable.Type.ERROR;
        }
        if (variableSymbol.isConst()) {
            semanticError(identifierToken, "Cannot assign to constant '" + identifierToken.value + "'.");
            return variableSymbol.type;
        }
        if (!SymbolTable.Type.assignable(variableSymbol.type, rightHandSideType)) {
            typeMismatch(identifierToken, variableSymbol.type, rightHandSideType);
        } else {
            variableSymbol.initialized = true;
        }
        return variableSymbol.type;
    }

    private SymbolTable.Type evalLogical(TreeNode logicalNode) {
        SymbolTable.Type leftType  = evalExpr(logicalNode.children.get(0));
        SymbolTable.Type rightType = evalExpr(logicalNode.children.get(2));
        if (!leftType.isError() && !leftType.isBoolean()) {
            semanticError(firstToken(logicalNode), "The left operand of a logical operator must be maybe (boolean).");
        }
        if (!rightType.isError() && !rightType.isBoolean()) {
            semanticError(firstToken(logicalNode), "The right operand of a logical operator must be maybe (boolean).");
        }
        return SymbolTable.Type.MAYBE;
    }

    private SymbolTable.Type evalEquality(TreeNode equalityNode) {
        SymbolTable.Type leftType  = evalExpr(equalityNode.children.get(0));
        SymbolTable.Type rightType = evalExpr(equalityNode.children.get(2));
        if (!leftType.isError() && !rightType.isError()
                && !leftType.equalsType(rightType)
                && !(leftType.isNumeric() && rightType.isNumeric())) {
            semanticError(firstToken(equalityNode),
                    "Cannot compare incompatible types: " + leftType + " and " + rightType + ".");
        }
        return SymbolTable.Type.MAYBE;
    }

    private SymbolTable.Type evalRelational(TreeNode relationalNode) {
        SymbolTable.Type leftType  = evalExpr(relationalNode.children.get(0));
        SymbolTable.Type rightType = evalExpr(relationalNode.children.get(2));
        if (!leftType.isError() && !leftType.isNumeric()) {
            semanticError(firstToken(relationalNode),
                    "Relational comparison requires a numeric type (left: " + leftType + ").");
        }
        if (!rightType.isError() && !rightType.isNumeric()) {
            semanticError(firstToken(relationalNode),
                    "Relational comparison requires a numeric type (right: " + rightType + ").");
        }
        return SymbolTable.Type.MAYBE;
    }

    private SymbolTable.Type evalArithmetic(TreeNode arithmeticNode) {
        SymbolTable.Type leftType  = evalExpr(arithmeticNode.children.get(0));
        Token operatorToken = arithmeticNode.children.get(1).token;
        SymbolTable.Type rightType = evalExpr(arithmeticNode.children.get(2));

        if (operatorToken != null && operatorToken.type == TokenType.PLUS
                && (leftType.isString() || rightType.isString())) {
            return SymbolTable.Type.MUCHOTEXTO;
        }
        if (leftType.isError() || rightType.isError()) {
            return SymbolTable.Type.ERROR;
        }
        if (!leftType.isNumeric() || !rightType.isNumeric()) {
            semanticError(operatorToken != null ? operatorToken : firstToken(arithmeticNode),
                    "Operator requires numeric operands (found " + leftType + " and " + rightType + ").");
            return SymbolTable.Type.ERROR;
        }
        if (leftType.equalsType(SymbolTable.Type.REAL) || rightType.equalsType(SymbolTable.Type.REAL)) {
            return SymbolTable.Type.REAL;
        }
        return SymbolTable.Type.NUMBER;
    }

    private SymbolTable.Type evalUnary(TreeNode unaryNode) {
        TreeNode firstChild = unaryNode.children.get(0);
        if (firstChild.token != null && firstChild.token.type == TokenType.NOT) {
            SymbolTable.Type operandType = evalExpr(unaryNode.children.get(1));
            if (!operandType.isError() && !operandType.isBoolean()) {
                semanticError(firstChild.token, "The 'nahfam' (!) operator requires a maybe (boolean).");
            }
            return SymbolTable.Type.MAYBE;
        }
        if (firstChild.token != null
                && (firstChild.token.type == TokenType.PLUS || firstChild.token.type == TokenType.MINUS)
                && unaryNode.children.size() == 2
                && unaryNode.children.get(1).token != null
                && unaryNode.children.get(1).token.type == TokenType.ID) {
            Token identifierToken = unaryNode.children.get(1).token;
            Symbol variableSymbol = symbols.lookup(identifierToken.value);
            if (variableSymbol == null) {
                undeclared(identifierToken);
                return SymbolTable.Type.ERROR;
            }
            if (variableSymbol.isConst()) {
                semanticError(identifierToken, "Cannot modify constant '" + identifierToken.value + "'.");
            } else if (!variableSymbol.type.isNumeric()) {
                semanticError(identifierToken, "++/-- requires a numeric type (found " + variableSymbol.type + ").");
            }
            return variableSymbol.type;
        }
        if (firstChild.token != null && firstChild.token.type == TokenType.MINUS) {
            SymbolTable.Type operandType = evalExpr(unaryNode.children.get(1));
            if (!operandType.isError() && !operandType.isNumeric()) {
                semanticError(firstChild.token, "Unary '-' requires a numeric type.");
            }
            return operandType;
        }
        if (firstChild.token != null && firstChild.token.type == TokenType.AMPERSAND) {
            Token identifierToken = unaryNode.children.get(1).token;
            Symbol variableSymbol = (identifierToken == null) ? null : symbols.lookup(identifierToken.value);
            if (variableSymbol == null && identifierToken != null) {
                undeclared(identifierToken);
            }
            return variableSymbol == null ? SymbolTable.Type.ERROR : variableSymbol.type;
        }
        return evalExpr(firstChild);
    }

    private SymbolTable.Type evalPostfix(TreeNode postfixNode) {
        SymbolTable.Type currentType = evalExpr(postfixNode.children.get(0));
        Token baseIdentifierToken = null;
        TreeNode primaryNode = postfixNode.children.get(0);
        if (primaryNode.children.size() == 1
                && primaryNode.children.get(0).token != null
                && primaryNode.children.get(0).token.type == TokenType.ID) {
            baseIdentifierToken = primaryNode.children.get(0).token;
        }

        int childIndex = 1;
        while (childIndex < postfixNode.children.size()) {
            TreeNode childNode = postfixNode.children.get(childIndex);
            if (childNode.token != null
                    && (childNode.token.type == TokenType.PLUS || childNode.token.type == TokenType.MINUS)) {
                if (!currentType.isError() && !currentType.isNumeric()) {
                    semanticError(childNode.token, "++/-- requires a numeric type.");
                }
                childIndex++;
                continue;
            }
            if (childNode.token != null && childNode.token.type == TokenType.LBRACKET) {
                SymbolTable.Type indexType = evalExpr(postfixNode.children.get(childIndex + 1));
                if (!indexType.isError() && !indexType.isInteger()) {
                    semanticError(childNode.token, "Index must be an integer (found " + indexType + ").");
                }
                if (!currentType.isError() && currentType.kind != SymbolTable.Type.Kind.CATALOG) {
                    semanticError(childNode.token, "Indexing is only allowed on catalog (type " + currentType + ").");
                    currentType = SymbolTable.Type.ERROR;
                } else if (currentType.kind == SymbolTable.Type.Kind.CATALOG) {
                    currentType = currentType.elementType;
                }
                baseIdentifierToken = null;
                childIndex += 3;
                continue;
            }
            if (childNode.token != null && childNode.token.type == TokenType.LPAREN) {
                TreeNode argumentsOptionalNode = postfixNode.children.get(childIndex + 1);
                if (baseIdentifierToken != null) {
                    currentType = checkCall(baseIdentifierToken, argumentsOptionalNode);
                } else {
                    semanticError(childNode.token, "Only function identifiers can be called.");
                    currentType = SymbolTable.Type.ERROR;
                }
                baseIdentifierToken = null;
                childIndex += 3;
                continue;
            }
            if (childNode.token != null && childNode.token.type == TokenType.DOT) {
                Token fieldToken = (childIndex + 1 < postfixNode.children.size())
                        ? postfixNode.children.get(childIndex + 1).token : null;
                if (fieldToken != null) {
                    if (currentType.kind == SymbolTable.Type.Kind.STRUCT) {
                        Symbol structSymbol = symbols.lookupGlobal(currentType.name);
                        SymbolTable.Type fieldType = structSymbol == null ? null : structSymbol.fields.get(fieldToken.value);
                        if (fieldType == null) {
                            semanticError(fieldToken, "Struct '" + currentType.name + "' has no field '" + fieldToken.value + "'.");
                            currentType = SymbolTable.Type.ERROR;
                        } else {
                            currentType = fieldType;
                        }
                    } else if (currentType.kind == SymbolTable.Type.Kind.ENUM) {
                        Symbol enumSymbol = symbols.lookupGlobal(currentType.name);
                        if (enumSymbol == null || !enumSymbol.values.contains(fieldToken.value)) {
                            semanticError(fieldToken, "Enum '" + currentType.name + "' has no value '" + fieldToken.value + "'.");
                            currentType = SymbolTable.Type.ERROR;
                        }
                    } else if (!currentType.isError()) {
                        semanticError(fieldToken, "'.' can only be used on struct or enum (type " + currentType + ").");
                        currentType = SymbolTable.Type.ERROR;
                    }
                }
                baseIdentifierToken = null;
                childIndex += 2;
                continue;
            }
            childIndex++;
        }
        return currentType;
    }

    private SymbolTable.Type evalPrimary(TreeNode primaryNode) {
        if (primaryNode.children.isEmpty()) {
            return SymbolTable.Type.ERROR;
        }
        TreeNode childNode = primaryNode.children.get(0);
        if (childNode.token != null && childNode.token.type == TokenType.LPAREN) {
            return evalExpr(primaryNode.children.get(1));
        }
        if (childNode.token != null && childNode.token.type == TokenType.ID) {
            Symbol variableSymbol = symbols.lookup(childNode.token.value);
            if (variableSymbol == null) {
                undeclared(childNode.token);
                return SymbolTable.Type.ERROR;
            }
            return variableSymbol.type;
        }
        return evalExpr(childNode);
    }

    private SymbolTable.Type evalLiteral(TreeNode literalNode) {
        if (literalNode.children.isEmpty() || literalNode.children.get(0).token == null) {
            return SymbolTable.Type.ERROR;
        }
        return evalTokenLeaf(literalNode.children.get(0).token);
    }

    private SymbolTable.Type evalTokenLeaf(Token tokenLeaf) {
        switch (tokenLeaf.type) {
            case INT_LITERAL:
                return SymbolTable.Type.NUMBER;
            case FLOAT_LITERAL:
                return SymbolTable.Type.REAL;
            case STRING_LITERAL:
                return SymbolTable.Type.MUCHOTEXTO;
            case CHAR_LITERAL:
                return SymbolTable.Type.LETTER;
            case TRUE:
            case FALSE:
                return SymbolTable.Type.MAYBE;
            case ID: {
                Symbol variableSymbol = symbols.lookup(tokenLeaf.value);
                if (variableSymbol == null) {
                    undeclared(tokenLeaf);
                    return SymbolTable.Type.ERROR;
                }
                return variableSymbol.type;
            }
            default:
                return SymbolTable.Type.ERROR;
        }
    }

    private SymbolTable.Type checkCall(Token functionNameToken, TreeNode argumentsOptionalNode) {
        Symbol functionSymbol = symbols.lookup(functionNameToken.value);
        if (functionSymbol == null) {
            undeclared(functionNameToken);
            return SymbolTable.Type.ERROR;
        }
        if (functionSymbol.kind != Symbol.Kind.FUNCTION) {
            semanticError(functionNameToken, "'" + functionNameToken.value + "' is not a function.");
            return SymbolTable.Type.ERROR;
        }

        List<SymbolTable.Type> argumentTypes = new ArrayList<>();
        if (argumentsOptionalNode != null) {
            TreeNode argumentListNode = childByLabel(argumentsOptionalNode, "argument_list");
            if (argumentListNode != null) {
                for (TreeNode argumentNode : argumentListNode.children) {
                    if (isExprNode(argumentNode)) {
                        argumentTypes.add(evalExpr(argumentNode));
                    }
                }
            }
        }

        if (argumentTypes.size() != functionSymbol.paramTypes.size()) {
            semanticError(functionNameToken, "Function '" + functionNameToken.value + "' expects "
                    + functionSymbol.paramTypes.size() + " arguments but got " + argumentTypes.size() + ".");
        } else {
            for (int argumentIndex = 0; argumentIndex < argumentTypes.size(); argumentIndex++) {
                SymbolTable.Type expectedType = functionSymbol.paramTypes.get(argumentIndex);
                SymbolTable.Type actualType   = argumentTypes.get(argumentIndex);
                if (!SymbolTable.Type.assignable(expectedType, actualType)) {
                    semanticError(functionNameToken, "Argument " + (argumentIndex + 1) + " of '" + functionNameToken.value
                            + "': expected " + expectedType + " but got " + actualType + ".");
                }
            }
        }
        return functionSymbol.type;
    }

    private void declareVariable(Token identifierToken, SymbolTable.Type declaredType,
                                 SymbolTable.Type rightHandSideType, boolean isConstant) {
        if (identifierToken == null) {
            return;
        }
        Symbol.Kind kind = isConstant ? Symbol.Kind.CONSTANT : Symbol.Kind.VARIABLE;
        Symbol newSymbol = new Symbol(identifierToken.value, kind, declaredType, identifierToken.line);
        newSymbol.initialized = (rightHandSideType != null);

        if (symbols.insert(newSymbol) != null) {
            duplicate(identifierToken, isConstant ? "constant" : "variable");
            return;
        }
        if (rightHandSideType != null && !SymbolTable.Type.assignable(declaredType, rightHandSideType)) {
            typeMismatch(identifierToken, declaredType, rightHandSideType);
        }
    }

    private SymbolTable.Type resolveTypeSpecifier(TreeNode typeSpecifierNode) {
        if (typeSpecifierNode == null || typeSpecifierNode.children.isEmpty()) {
            return SymbolTable.Type.ERROR;
        }
        TreeNode firstChild = typeSpecifierNode.children.get(0);
        if (firstChild.token != null && firstChild.token.type == TokenType.CONST) {
            return resolveBaseType(childByLabel(typeSpecifierNode, "base_type"));
        }
        if (firstChild.token != null && firstChild.token.type == TokenType.CATALOG) {
            return SymbolTable.Type.catalog(resolveBaseType(childByLabel(typeSpecifierNode, "base_type")));
        }
        return resolveBaseType(childByLabel(typeSpecifierNode, "base_type"));
    }

    private SymbolTable.Type resolveTypeSpecifierNoConst(TreeNode typeSpecifierNode) {
        if (typeSpecifierNode == null || typeSpecifierNode.children.isEmpty()) {
            return SymbolTable.Type.ERROR;
        }
        TreeNode firstChild = typeSpecifierNode.children.get(0);
        if (firstChild.token != null && firstChild.token.type == TokenType.CATALOG) {
            return SymbolTable.Type.catalog(resolveBaseType(childByLabel(typeSpecifierNode, "base_type")));
        }
        return resolveBaseType(childByLabel(typeSpecifierNode, "base_type"));
    }

    private SymbolTable.Type resolveBaseType(TreeNode baseTypeNode) {
        if (baseTypeNode == null || baseTypeNode.children.isEmpty()
                || baseTypeNode.children.get(0).token == null) {
            return SymbolTable.Type.ERROR;
        }
        switch (baseTypeNode.children.get(0).token.type) {
            case NUMBER:
                return SymbolTable.Type.NUMBER;
            case REAL:
                return SymbolTable.Type.REAL;
            case MUCHOTEXTO:
                return SymbolTable.Type.MUCHOTEXTO;
            case MAYBE:
                return SymbolTable.Type.MAYBE;
            case LETTER:
                return SymbolTable.Type.LETTER;
            case SIXSEVEN:
                return SymbolTable.Type.SIXSEVEN;
            default:
                return SymbolTable.Type.ERROR;
        }
    }

    private TreeNode childByLabel(TreeNode parentNode, String label) {
        if (parentNode == null) {
            return null;
        }
        for (TreeNode childNode : parentNode.children) {
            if (label.equals(childNode.label)) {
                return childNode;
            }
        }
        return null;
    }

    private boolean isExprNode(TreeNode candidateNode) {
        if (candidateNode == null || candidateNode.token != null) {
            return false;
        }
        switch (candidateNode.label) {
            case "primary_expression":
            case "postfix_expression":
            case "unary_expression":
            case "multiplicative_expression":
            case "additive_expression":
            case "relational_expression":
            case "equality_expression":
            case "logical_and_expression":
            case "logical_or_expression":
            case "assignment_expression":
            case "literal":
                return true;
            default:
                return false;
        }
    }

    private TreeNode nthExpression(TreeNode parentNode, int desiredIndex) {
        if (parentNode == null) {
            return null;
        }
        int matchedCount = 0;
        for (TreeNode childNode : parentNode.children) {
            if (isExprNode(childNode)) {
                if (matchedCount == desiredIndex) {
                    return childNode;
                }
                matchedCount++;
            }
        }
        return null;
    }

    private Token firstToken(TreeNode searchNode) {
        if (searchNode == null) {
            return null;
        }
        if (searchNode.token != null) {
            return searchNode.token;
        }
        for (TreeNode childNode : searchNode.children) {
            Token foundToken = firstToken(childNode);
            if (foundToken != null) {
                return foundToken;
            }
        }
        return null;
    }

    private Token firstTokenOfType(TreeNode searchNode, TokenType tokenType) {
        if (searchNode == null) {
            return null;
        }
        if (searchNode.token != null && searchNode.token.type == tokenType) {
            return searchNode.token;
        }
        for (TreeNode childNode : searchNode.children) {
            Token foundToken = firstTokenOfType(childNode, tokenType);
            if (foundToken != null) {
                return foundToken;
            }
        }
        return null;
    }

    private List<Token> collectTokensOfType(TreeNode searchNode, TokenType tokenType) {
        List<Token> output = new ArrayList<>();
        collectTokensOfType(searchNode, tokenType, output);
        return output;
    }

    private void collectTokensOfType(TreeNode searchNode, TokenType tokenType, List<Token> output) {
        if (searchNode == null) {
            return;
        }
        if (searchNode.token != null && searchNode.token.type == tokenType) {
            output.add(searchNode.token);
        }
        for (TreeNode childNode : searchNode.children) {
            collectTokensOfType(childNode, tokenType, output);
        }
    }

    private void semanticError(Token locationToken, String message) {
        int line = locationToken != null ? locationToken.line : 1;
        errors.error(CompilerError.Type.INVALID_OPERATION, message, line, 1, null);
    }

    private void undeclared(Token identifierToken) {
        errors.error(CompilerError.Type.UNDEFINED_VARIABLE,
                "Undeclared identifier: '" + identifierToken.value + "'.",
                identifierToken.line, 1, identifierToken.value.length(),
                "Declare '" + identifierToken.value + "' before using it.");
    }

    private void typeMismatch(Token locationToken, SymbolTable.Type expectedType, SymbolTable.Type actualType) {
        errors.error(CompilerError.Type.TYPE_MISMATCH,
                "Type mismatch: expected " + expectedType + " but got " + actualType + ".",
                locationToken.line, 1, locationToken.value == null ? 1 : locationToken.value.length(),
                "Convert the value or adjust the declared type.");
    }

    private void duplicate(Token identifierToken, String declarationKind) {
        errors.error(CompilerError.Type.DUPLICATE_DECLARATION,
                "Duplicate declaration of " + declarationKind + " '" + identifierToken.value + "'.",
                identifierToken.line, 1, identifierToken.value.length(),
                "Rename the " + declarationKind + " or remove the duplicate declaration.");
    }

    private void requireBoolean(SymbolTable.Type conditionType, Token locationToken, String contextDescription) {
        if (conditionType.isError()) {
            return;
        }
        if (!conditionType.isBoolean()) {
            semanticError(locationToken,
                    "Condition of '" + contextDescription + "' must be maybe (boolean), found " + conditionType + ".");
        }
    }
}
