public class ParseTree {
    private TreeNode root;
    private int nodeCount;

    public ParseTree(TreeNode root) {
        this.root      = root;
        this.nodeCount = 0;
    }

    public TreeNode getRoot() { return root; }

    public void print() {
        System.out.println("=== PARSE TREE ===");
        root.print("");
    }
}