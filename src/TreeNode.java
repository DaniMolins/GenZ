import java.util.ArrayList;
import java.util.List;

public class TreeNode {
    public final String label;           // non-terminal or token name
    public final Token token;            // null when this is a non-terminal
    public final List<TreeNode> children;

    // Constructor for non-terminals
    public TreeNode(String label) {
        this.label    = label;
        this.token    = null;
        this.children = new ArrayList<>();
    }

    // Constructor for terminals (leaves)
    public TreeNode(Token token) {
        this.label    = token.type.name();
        this.token    = token;
        this.children = new ArrayList<>();
    }

    public void addChild(TreeNode child) {
        children.add(child);
    }

    // Prints the tree with indentation
    public void print(String indent) {
        if (token != null) {
            System.out.println(indent + "[" + label + " = \"" + token.value + "\"]");
        } else {
            System.out.println(indent + "<" + label + ">");
            for (TreeNode child : children) {
                child.print(indent + "  ");
            }
        }
    }
}