import java.util.*;

public class SymbolTable {

    public static class Type {
        public enum Kind { PRIMITIVE, CATALOG, STRUCT, ENUM, VOID, ERROR }

        public final Kind kind;
        public final String name;
        public final Type elementType;

        private Type(Kind kind, String name, Type elementType) {
            this.kind = kind;
            this.name = name;
            this.elementType = elementType;
        }

        public static final Type NUMBER     = new Type(Kind.PRIMITIVE, "number",     null);
        public static final Type REAL       = new Type(Kind.PRIMITIVE, "real",       null);
        public static final Type MUCHOTEXTO = new Type(Kind.PRIMITIVE, "muchotexto", null);
        public static final Type MAYBE      = new Type(Kind.PRIMITIVE, "maybe",      null);
        public static final Type LETTER     = new Type(Kind.PRIMITIVE, "letter",     null);
        public static final Type SIXSEVEN   = new Type(Kind.PRIMITIVE, "sixseven",   null);
        public static final Type VOID       = new Type(Kind.VOID,      "void",       null);
        public static final Type ERROR      = new Type(Kind.ERROR,     "<error>",    null);

        public static Type catalog(Type elementType) {
            return new Type(Kind.CATALOG, "catalog", elementType);
        }

        public static Type struct(String name) {
            return new Type(Kind.STRUCT, name, null);
        }

        public static Type enumType(String name) {
            return new Type(Kind.ENUM, name, null);
        }

        public boolean isError() {
            return kind == Kind.ERROR;
        }

        public boolean isVoid() {
            return kind == Kind.VOID;
        }

        public boolean isInteger() {
            return kind == Kind.PRIMITIVE && (name.equals("number") || name.equals("sixseven"));
        }

        public boolean isNumeric() {
            return kind == Kind.PRIMITIVE
                    && (name.equals("number") || name.equals("real") || name.equals("sixseven"));
        }

        public boolean isBoolean() {
            return kind == Kind.PRIMITIVE && name.equals("maybe");
        }

        public boolean isString() {
            return kind == Kind.PRIMITIVE && name.equals("muchotexto");
        }

        public boolean isChar() {
            return kind == Kind.PRIMITIVE && name.equals("letter");
        }

        public boolean equalsType(Type other) {
            if (this == other) {
                return true;
            }
            if (other == null || kind != other.kind) {
                return false;
            }
            if (kind == Kind.CATALOG) {
                return elementType.equalsType(other.elementType);
            }
            return Objects.equals(name, other.name);
        }

        public static boolean assignable(Type targetType, Type sourceType) {
            if (targetType == null || sourceType == null) {
                return false;
            }
            if (targetType.isError() || sourceType.isError()) {
                return true;
            }
            if (targetType.equalsType(sourceType)) {
                return true;
            }
            if (targetType.isNumeric() && sourceType.isNumeric()) {
                return true;
            }
            return false;
        }

        @Override
        public String toString() {
            if (kind == Kind.CATALOG) {
                return "catalog<" + elementType + ">";
            }
            return name;
        }
    }

    private final Deque<Map<String, Symbol>> scopes  = new ArrayDeque<>();
    private final List<Map<String, Symbol>>  history = new ArrayList<>();

    public SymbolTable() {
        enterScope();
    }

    public void enterScope() {
        Map<String, Symbol> newScope = new LinkedHashMap<>();
        scopes.push(newScope);
        history.add(newScope);
    }

    public void exitScope() {
        if (scopes.size() > 1) {
            scopes.pop();
        }
    }

    public int depth() {
        return scopes.size();
    }

    public boolean isGlobal() {
        return scopes.size() == 1;
    }

    public Symbol insert(Symbol newSymbol) {
        Map<String, Symbol> currentScope = scopes.peek();
        if (currentScope.containsKey(newSymbol.name)) {
            return currentScope.get(newSymbol.name);
        }
        currentScope.put(newSymbol.name, newSymbol);
        return null;
    }

    public Symbol lookup(String name) {
        for (Map<String, Symbol> scope : scopes) {
            Symbol foundSymbol = scope.get(name);
            if (foundSymbol != null) {
                return foundSymbol;
            }
        }
        return null;
    }

    public Symbol lookupCurrentScope(String name) {
        return scopes.peek().get(name);
    }

    public Symbol lookupGlobal(String name) {
        Iterator<Map<String, Symbol>> scopeIterator = scopes.descendingIterator();
        if (!scopeIterator.hasNext()) {
            return null;
        }
        return scopeIterator.next().get(name);
    }

    public void print() {
        System.out.println("=== SYMBOL TABLE ===");
        int level = 0;
        for (Map<String, Symbol> scope : history) {
            System.out.println("-- Scope #" + (level++) + (level == 1 ? " (global)" : "") + " --");
            if (scope.isEmpty()) {
                System.out.println("  (empty)");
            } else {
                for (Symbol scopeSymbol : scope.values()) {
                    System.out.println("  " + scopeSymbol);
                }
            }
        }
    }
}
