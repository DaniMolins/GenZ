import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;

public class Symbol {

    public enum Kind { VARIABLE, CONSTANT, PARAMETER, FUNCTION, STRUCT, ENUM, ENUM_VALUE }

    public final String name;
    public final Kind kind;
    public final SymbolTable.Type type;
    public final int line;

    public boolean initialized = false;

    public final List<SymbolTable.Type> paramTypes = new ArrayList<>();
    public final List<String>           paramNames = new ArrayList<>();

    public final LinkedHashMap<String, SymbolTable.Type> fields = new LinkedHashMap<>();

    public final LinkedHashSet<String> values = new LinkedHashSet<>();

    public String parentEnum;

    public Symbol(String name, Kind kind, SymbolTable.Type type, int line) {
        this.name = name;
        this.kind = kind;
        this.type = type;
        this.line = line;
    }

    public boolean isConst() {
        return kind == Kind.CONSTANT;
    }

    public boolean isAssignable() {
        return kind == Kind.VARIABLE || kind == Kind.PARAMETER;
    }

    @Override
    public String toString() {
        return String.format("%-9s %-15s : %-20s (line %d)", kind, name, type, line);
    }
}
