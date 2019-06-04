package rs.ac.bg.etf.pp1;

import java.util.Stack;

import rs.ac.bg.etf.pp1.ast.*;
import rs.etf.pp1.mj.runtime.Code;
import rs.etf.pp1.symboltable.concepts.Obj;
import rs.etf.pp1.symboltable.concepts.Struct;

public class CodeGenerator extends VisitorAdaptor {
	
    private Stack<Integer> backpathcingStack = new Stack<>();
    private Stack<Integer> forCondStack = new Stack<>();
    private Stack<Integer> forTermStack = new Stack<>();
    private Stack<Integer> breakAddressStack = new Stack<>();
    private Stack<Integer> breakCountStack = new Stack<>();
	
	private int mainPc;
	private int arrayInitListLast;
	
	public int getMainPc() { return mainPc; }
	
	@Override
    public void visit(ProgName progName) {
        SymbolTable.find("chr").setAdr(Code.pc);
        Code.put(Code.return_);

        SymbolTable.find("ord").setAdr(Code.pc);
        Code.put(Code.return_);

        SymbolTable.find("len").setAdr(Code.pc);
        Code.put(Code.arraylength);
        Code.put(Code.return_);
	}
	
	@Override
    public void visit(Program Program) {
        super.visit(Program);
        if (Code.pc >= 8192) {
            System.out.println("Program is too big, the upper limit is 8192 bytes!");
        }
	}

	@Override
    public void visit(MethodType methodType) {
        if (methodType.getName().equals("main")) { mainPc = Code.pc; }
        methodType.obj.setAdr(Code.pc);
        Code.put(Code.enter);
        Code.put(methodType.obj.getLevel());
        Code.put(methodType.obj.getLocalSymbols().size());
    }

    @Override
    public void visit(FunCall funCall) {
        Obj functionObj = funCall.getDesignator().obj;
        int offset = functionObj.getAdr() - Code.pc;
        Code.put(Code.call);
        Code.put2(offset);
        if (funCall.getDesignator().obj.getType() != SymbolTable.noType &&
        		FuncCallExprStatement.class == funCall.getParent().getClass()) {
            Code.put(Code.pop);
        }
    }

    @Override
    public void visit(MethodDecl methodDecl) {
        Code.put(Code.exit);
        Code.put(Code.return_);
    }

    @Override
    public void visit(ExprRetStat stat) {
        Code.put(Code.exit);
        Code.put(Code.return_);
    }

    @Override
    public void visit(VoidRetStat stat) {
        Code.put(Code.exit);
        Code.put(Code.return_);
    }

    @Override
    public void visit(NumConstFact numConst) {
        Obj cnst = SymbolTable.insert(Obj.Con, "$", numConst.struct);
        cnst.setLevel(0);
        cnst.setAdr(numConst.getVal());
        Code.load(cnst);
    }

    @Override
    public void visit(CharConstFact charConst) {
        Obj cnst = SymbolTable.insert(Obj.Con, "$", charConst.struct);
        cnst.setLevel(0);
        cnst.setAdr(charConst.getVal());
        Code.load(cnst);
    }

    @Override
    public void visit(BoolConstFact boolConst) {
        Obj cnst = SymbolTable.insert(Obj.Con, "$", boolConst.struct);
        cnst.setLevel(0);
        cnst.setAdr(boolConst.getVal() ? 1 : 0);
        Code.load(cnst);
    }

    @Override
    public void visit(PrintSingleStat stat) {
        if (stat.getExpr().struct == SymbolTable.charType) {
            Code.loadConst(1);
            Code.put(Code.bprint);
        } else {
            Code.loadConst(5);
            Code.put(Code.print);
        }
    }

    @Override
    public void visit(PrintMultiStat stat) {
        if (stat.getExpr().struct == SymbolTable.charType) {
            Code.loadConst(stat.getVal());
            Code.put(Code.bprint);
        } else {
            Code.loadConst(stat.getVal());
            Code.put(Code.print);
        }
    }

    @Override
    public void visit(ReadStat stat) {
        if (stat.getDesignator().obj.getType() == SymbolTable.charType) {
            Code.put(Code.bread);
        } else {
            Code.put(Code.read);
        }
        Code.store(stat.getDesignator().obj);
    }

    @Override
    public void visit(DesignatorName name) {
        Obj objNode = name.obj;
        if (objNode.getType().getKind() == Struct.Array) { Code.load(objNode); }
    }

    @Override
    public void visit(Designator designator) {
        SyntaxNode parent = designator.getParent();
        if (designator.obj.getType().getKind() == Struct.Array) {
            Code.put(Code.pop);
        }
        if (IncExprStatement.class == parent.getClass() || DecExprStatement.class == parent.getClass()) {
            if (designator.obj.getKind() == Obj.Elem) { Code.put(Code.dup2); }
        }
        if (AssignStat.class != parent.getClass() && FunCall.class != parent.getClass() &&
                ReadStat.class != parent.getClass()) {
            Code.load(designator.obj);
        }
    }

    @Override
    public void visit(AssignStat statement) { Code.store(statement.getDesignator().obj); }

    @Override
    public void visit(NegatedExpression expr) { Code.put(Code.neg); }

    @Override
    public void visit(AddopExpression expr) {
        if (expr.getAddop() instanceof OpPlus) { Code.put(Code.add); }
        else { Code.put(Code.sub); }
    }

    @Override
    public void visit(AddopTerm term) {
        if (term.getMulop() instanceof OpMul) { Code.put(Code.mul); } 
        else if (term.getMulop() instanceof OpDiv) { Code.put(Code.div); }
        else { Code.put(Code.rem); }
    }

    @Override
    public void visit(NewArrayFact newArrayFact) { Code.put(Code.pop); }

    @Override
    public void visit(ArrayType arrayType) {
        Code.put(Code.newarray);
        if (arrayType.getType().struct == SymbolTable.charType) { Code.put(0); }
        else { Code.put(1); }

        try {
	        int arrayLen = ((NumConstFact)((FactorTerm)((TermExpression)((Expression)
	        		arrayType.getExpr()).getAddopExpr()).getTerm()).getFactor()).getVal();
	        for (int i = 0; i < arrayLen; i++) { Code.put(Code.dup); }
        } catch (Exception e) {}
    	arrayInitListLast = 0;
    	Code.loadConst(arrayInitListLast++);
    }
    
    @Override
    public void visit(IncExprStatement statement) {
        Code.loadConst(1);
        Code.put(Code.add);
        Code.store(statement.getDesignator().obj);
    }

    @Override
    public void visit(DecExprStatement statement) {
        Code.loadConst(1);
        Code.put(Code.sub);
        Code.store(statement.getDesignator().obj);
    }

    @Override
    public void visit(ComplexCondFact condFact) {
        Relop relop = condFact.getRelop();
        int temp1 = Code.pc + 1;
        if (relop instanceof RelopEQ) { Code.putFalseJump(Code.eq, 0); }
        else if (relop instanceof RelopNEQ) { Code.putFalseJump(Code.ne, 0); }
        else if (relop instanceof RelopGT) { Code.putFalseJump(Code.gt, 0); }
        else if (relop instanceof RelopGE) { Code.putFalseJump(Code.ge, 0); }
        else if (relop instanceof RelopLT) { Code.putFalseJump(Code.lt, 0); }
        else if (relop instanceof RelopLE) { Code.putFalseJump(Code.le, 0); }
        Code.loadConst(1);
        int temp2 = Code.pc + 1;
        Code.putJump(0);
        Code.fixup(temp1);
        Code.loadConst(0);
        Code.fixup(temp2);
    }

    @Override
    public void visit(ManyFactorCondTerm andExpr) {
        Code.put(Code.mul);
    }

    @Override
    public void visit(ManyTermCondition orExpr) {
        Code.put(Code.add);
        Code.loadConst(0);
        int temp1 =  Code.pc + 1;
        Code.putFalseJump(Code.eq, 0);
        Code.loadConst(0);
        int temp2 = Code.pc + 1;
        Code.putJump(0);
        Code.fixup(temp1);
        Code.loadConst(1);
        Code.fixup(temp2);
    }

    @Override
    public void visit(IfCond cond) {
        Code.loadConst(1);
        backpathcingStack.push(Code.pc + 1);
        Code.putFalseJump(Code.eq, 0);
    }

    @Override
    public void visit(IfStat ifStat) { Code.fixup(backpathcingStack.pop()); }

    @Override
    public void visit(Else e) {
        int temp = backpathcingStack.pop();
        backpathcingStack.push(Code.pc + 1);
        Code.putJump(0);
        Code.fixup(temp);
    }

    @Override
    public void visit(IfElseStat ifElseStat) { Code.fixup(backpathcingStack.pop()); }

    @Override
    public void visit(EmptyForInitStat stat) {
        forCondStack.push(Code.pc);
        breakCountStack.push(0);
    }

    @Override
    public void visit(NonEmptyForInitStat statement) {
        forCondStack.push(Code.pc);
        breakCountStack.push(0);
    }

    @Override
    public void visit(EmptyForLoopCond cond) {
        backpathcingStack.push(Code.pc + 1);
        Code.putJump(0);
        forTermStack.push(Code.pc);
    }

    @Override
    public void visit(NonEmptyForLoopCond cond) {
        Code.loadConst(1);
        backpathcingStack.push(Code.pc + 1);
        Code.putFalseJump(Code.eq, 0);
        backpathcingStack.push(Code.pc + 1);
        Code.putJump(0);
        forTermStack.push(Code.pc);
    }

    @Override
    public void visit(EmptyForFinalStat stat) {
        Code.putJump(forCondStack.peek());
        Code.fixup(backpathcingStack.pop());
    }

    @Override
    public void visit(NonEmptyForFinalStat stat) {
        Code.putJump(forCondStack.peek());
        Code.fixup(backpathcingStack.pop());
    }

    @Override
    public void visit(ForStat stat) {
        Code.putJump(forTermStack.peek());
        if (stat.getForLoopCond() instanceof NonEmptyForLoopCond) { Code.fixup(backpathcingStack.pop()); }
        forCondStack.pop();
        forTermStack.pop();
        int breakCount = breakCountStack.pop();
        while (breakCount > 0) {
            Code.fixup(breakAddressStack.pop());
            breakCount--;
        }
    }

    @Override
    public void visit(ContStat statement) { Code.putJump(forTermStack.peek()); }

    @Override
    public void visit(BreakStat statement) {
        breakCountStack.push(breakCountStack.pop() + 1);
        breakAddressStack.push(Code.pc + 1);
        Code.putJump(0);
    }
    
    @Override
	public void visit(ArrayInitializerList arrayInitiList) {
    	if (arrayInitiList.getExpr().struct.getKind() == Struct.Char) Code.put(Code.bastore);
        else Code.put(Code.astore); 
    	Code.loadConst(arrayInitListLast++);
	}

	@Override
	public void visit(ArrayInitializerExpr arrayInitExpr) {
		if (arrayInitExpr.getExpr().struct.getKind() == Struct.Char) Code.put(Code.bastore);
        else Code.put(Code.astore);
		Code.loadConst(arrayInitListLast++);
	}
	
}
