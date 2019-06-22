package rs.ac.bg.etf.pp1;

import rs.etf.pp1.symboltable.Tab;
import rs.etf.pp1.symboltable.concepts.Obj;
import rs.etf.pp1.symboltable.concepts.Struct;

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

}