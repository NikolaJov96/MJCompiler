package rs.ac.bg.etf.pp1;

import java.util.Collection;

import rs.etf.pp1.symboltable.Tab;
import rs.etf.pp1.symboltable.concepts.Obj;
import rs.etf.pp1.symboltable.concepts.Scope;
import rs.etf.pp1.symboltable.concepts.Struct;
import rs.etf.pp1.symboltable.visitors.SymbolTableVisitor;

class SymbolTablePrintVisitor extends SymbolTableVisitor {

    private StringBuilder output = new StringBuilder();
    private final String indent = ">>> ";
    private StringBuilder currentIndent = new StringBuilder();

    private void nextIndentationLevel() {
        currentIndent.append(indent);
    }

    private void previousIndentationLevel() {
        if (currentIndent.length() > 0)
            currentIndent.setLength(currentIndent.length() - indent.length());
    }

    public String getOutput() {
        return output.toString();
    }

    @Override
    public void visitObjNode(Obj objToVisit) {
        switch (objToVisit.getKind()) {
            case Obj.Con:  output.append("Constant "); break;
            case Obj.Var:  output.append("Variable "); break;
            case Obj.Type: output.append("Type     "); break;
            case Obj.Meth: output.append("Method   "); break;
            case Obj.Fld:  output.append("Field    "); break;
            case Obj.Prog: output.append("Program  "); break;
        }

        output.append("'" + objToVisit.getName() + "' (");

        if (!((Obj.Var == objToVisit.getKind()) && "this".equalsIgnoreCase(objToVisit.getName()))) {
            output.append("type=");
            objToVisit.getType().accept(this);
        }

        switch (objToVisit.getKind()) {
            case Obj.Var:
                output.append(", address=").append(objToVisit.getAdr());
                output.append(", fppos=").append(objToVisit.getFpPos());
                output.append(", level=").append(objToVisit.getLevel());
                break;
            case Obj.Fld:
                output.append(", address=").append(objToVisit.getAdr());
                break;
            case Obj.Meth:
                output.append(", address=").append(objToVisit.getAdr());
                output.append(", nparams=").append(objToVisit.getLevel());
                break;
            case Obj.Con:
                output.append(", value=").append(objToVisit.getAdr());
                output.append(", level=").append(objToVisit.getLevel());
                break;
            case Obj.Type:
                if (objToVisit.getType().getKind() == Struct.Class)
                    output.append(", nfields=").append(objToVisit.getLevel());
                break;
        }
        output.append(")");

        int kind = objToVisit.getKind();
        Collection<Obj> localSymbols = objToVisit.getLocalSymbols();
        if (kind == Obj.Prog || kind == Obj.Meth) {
            nextIndentationLevel();
        }

        for (Obj o : localSymbols) {
            output.append("\n");
            output.append(currentIndent.toString());
            o.accept(this);
        }

        if (kind == Obj.Prog || kind == Obj.Meth)
            previousIndentationLevel();
    }

    @Override
    public void visitScopeNode(Scope scope) {
        for (Obj o : scope.values()) {
            o.accept(this);
            output.append("\n");
        }
    }

    @Override
    public void visitStructNode(Struct structToVisit) {
        switch (structToVisit.getKind()) {
            case Struct.None:
                output.append("NOTYPE");
                break;
            case Struct.Int:
                output.append("int");
                break;
            case Struct.Char:
                output.append("char");
                break;
            case SymbolTable.Bool:
                output.append("bool");
                break;
            case Struct.Array:
                output.append("array of ");

                switch (structToVisit.getElemType().getKind()) {
                    case Struct.None:
                        output.append("NOTYPE");
                        break;
                    case Struct.Int:
                        output.append("int");
                        break;
                    case Struct.Char:
                        output.append("char");
                        break;
                    case Struct.Class:
                        output.append("class");
                        break;
                    case SymbolTable.Bool:
                        output.append("bool");
                        break;
                    case SymbolTable.Enum:
                        output.append("enum");
                        break;
                }
                break;
            case Struct.Class:
                output.append("class {\n");
                nextIndentationLevel();
                for (Obj obj : structToVisit.getMembers().symbols()) {
                    output.append(currentIndent);
                    obj.accept(this);
                    output.append("\n");
                }
                previousIndentationLevel();
                output.append(currentIndent);
                output.append("} /* class */");
                break;
            case SymbolTable.Enum:
                output.append("enum {\n");
                nextIndentationLevel();
                for (Obj obj : structToVisit.getMembers().symbols()) {
                    output.append(currentIndent);
                    obj.accept(this);
                    output.append("\n");
                }
                previousIndentationLevel();
                output.append(currentIndent);
                output.append("} /* enum */");
                break;
        }
    }

}

public class SymbolTable extends Tab {

    public static final int Bool = 5;
    public static final int Enum = 6;

    public static final Struct boolType = new Struct(SymbolTable.Bool);

    public static void init() {
        Tab.init();
        Tab.currentScope.addToLocals(new Obj(Obj.Type, "bool", boolType));
        Tab.currentScope.addToLocals(new Obj(Obj.Type, "int[]", new Struct(Struct.Array, intType)));
        Tab.currentScope.addToLocals(new Obj(Obj.Type, "char[]", new Struct(Struct.Array, charType)));
        Tab.currentScope.addToLocals(new Obj(Obj.Type, "bool[]", new Struct(Struct.Array, boolType)));
    }

    public static void dump() {
        System.out.println("===================== SYMBOL TABLE DUMP =========================");
        SymbolTableVisitor stv = new SymbolTablePrintVisitor();
        for (Scope s = currentScope; s != null; s = s.getOuter()) {
            s.accept(stv);
        }
        System.out.println(stv.getOutput());
        System.out.println("=================================================================");
    }

}