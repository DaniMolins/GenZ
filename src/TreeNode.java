import java.util.ArrayList;
import java.util.List;

public class TreeNode {
    public final String label;           // nombre del no-terminal o token
    public final Token token;            // null si es no-terminal
    public final List<TreeNode> children;

    // Constructor para no-terminales
    public TreeNode(String label) {
        this.label    = label;
        this.token    = null;
        this.children = new ArrayList<>();
    }

    // Constructor para terminales (hojas)
    public TreeNode(Token token) {
        this.label    = token.type.name();
        this.token    = token;
        this.children = new ArrayList<>();
    }

    public void addChild(TreeNode child) {
        children.add(child);
    }

    // Imprime el árbol con indentación
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