
public interface SemanticListener {

    void onInject(TreeNode injectNode);

    void onStruct(TreeNode structNode);

    void onEnum(TreeNode enumNode);

    void onFunctionSignature(TreeNode signatureNode);

    void onFunctionBodyStatement(TreeNode statementNode);

    void onFunctionEnd();

    void onMainBegin(TreeNode mainHeaderNode);

    void onMainStatement(TreeNode statementNode);

    void onMainEnd();

    void onProgramEnd();
}
