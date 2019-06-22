package rs.ac.bg.etf.pp1;

import org.apache.log4j.Logger;

import rs.ac.bg.etf.pp1.ast.*;
import rs.etf.pp1.symboltable.concepts.*;

import java.util.*;

public class SemanticAnalyzer extends VisitorAdaptor {
	
	class FunctionDeclarator {
	    private int requiredParams;
	    private int processedParams;
	    private Collection<Obj> locals;
	    private boolean error;
	    public FunctionDeclarator(int requiredParams, Collection<Obj> locals) {
	        this.requiredParams = requiredParams;
	        this.processedParams = 0;
	        this.locals = locals;
	        this.error = false;
	    }
	    public FunctionDeclarator(Obj functionNode) {
	        this.requiredParams = functionNode.getLevel();
	        this.processedParams = 0;
	        this.locals = functionNode.getLocalSymbols();
	        this.error = false;
	    }
	    int expectedNumberOfArguments() { return requiredParams; }
	    String processActualParam(Expr expr) {
	        String errorMsg = null;
	        if (processedParams == requiredParams) {
	            errorMsg = "Error: number of arguments in function call too high";
	            error = true;
	            return errorMsg;
	        }
	        int currParam = processedParams;
	        processedParams++;
	        for (Obj obj: locals) {
	            if (obj.getFpPos() == currParam) {
	            	if (compatibleWithEnum(obj.getType(), expr)) { return null; }
	                if (!expr.struct.assignableTo(obj.getType())) {
	                    errorMsg = "Error: invalid argument type " + obj.getName();
	                    error = true;
	                    return errorMsg;
	                }
	            }
	        }
	        return null;
	    }
	    boolean allParametersProcessed() { return processedParams == requiredParams; }
	    boolean errorFound() { return error; }
	}

    boolean errorDetected = false;
    int errorCount = 0;
    private Logger log = Logger.getLogger(getClass());

    private int varsCount = 0;
    private int forLoopNestingLevel = 0;
    private int formalParamCount = 0;
    private int arrayInitListLen = 0;
    private boolean returnFound = false;
    private Type lastDeclaredType = null;
    private Struct currEnum = null;
    private Obj currMethod = null;
    private Deque<FunctionDeclarator> functionDeclaratorStack = new ArrayDeque<>();
    
    private List<Integer> enumVals;

    private Deque<Obj> designatorNodes = new ArrayDeque<>();
    private Deque<StringBuilder> designatorNames = new ArrayDeque<>();

    private String buildMessage(String message, SyntaxNode info) {
        StringBuilder sb;
        int line = (info == null) ? 0 : info.getLine();
        if (line != 0) { sb = new StringBuilder(String.format("%1$10s", "Line " + line + ": ")); }
        else { sb = new StringBuilder(String.format("%10s", "")); }
        sb.append(message);
        return sb.toString();
    }

    public void report_error(String message, SyntaxNode info) {
        errorDetected = true;
        errorCount++;
        log.error(buildMessage(message, info));
    }

    public void report_info(String message, SyntaxNode info) {
        log.info(buildMessage(message, info));
    }

    public int getVarsCount() {
        return varsCount;
    }

    private boolean nameExistsInCurrentScope(String name, SyntaxNode node) {
        if (SymbolTable.currentScope.findSymbol(name) != null) {
            report_error("Error: current scope already contains name " + name, node);
            return true;
        }
        return false;
    }

    private String findMethodMain() {
        Obj mainObjNode = SymbolTable.currentScope.findSymbol("main");
        if (mainObjNode == null) { return "Error: main declaration is required"; }
        if (mainObjNode.getType() != SymbolTable.noType) { return "Error: main return type must be void"; }
        if (mainObjNode.getLevel() > 0) { return "Error: main requres 0 parameters"; }
        return null;
    }

    @Override
    public void visit(ProgName progName) {
        String programName = progName.getName();
        if (nameExistsInCurrentScope(programName, progName)) { programName = "__no_name_"; }
        progName.obj = SymbolTable.insert(Obj.Prog, programName, SymbolTable.noType);
        SymbolTable.openScope();
        report_info("Program name declared: " + programName, progName);
    }

    @Override
    public void visit(Program program) {
        String errMsg = findMethodMain();
        if (errMsg != null) { report_error(errMsg, null); }
        varsCount = SymbolTable.currentScope().getnVars();
        if (varsCount > 65536) {
            report_error("Error: too many global declarations (" + varsCount + "), up to 65536 is allowed", null);
        }
        SymbolTable.chainLocalSymbols(program.getProgName().obj);
        SymbolTable.closeScope();
    }

    @Override
    public void visit(Type type) {
        Obj typeNode = SymbolTable.find(type.getName());
        if (typeNode == SymbolTable.noObj) {
            report_error("Error: type " + type.getName() + " not defined", null);
            type.struct = SymbolTable.noType;
        } else {
            if (typeNode.getKind() == Obj.Type) {
                type.struct = typeNode.getType();
            } else {
                report_error("Error: invalid type name " + type.getName(), type);
                type.struct = SymbolTable.noType;
            }
        }
        lastDeclaredType = type;
    }

    @Override
    public void visit(NumConstInit numConstInit) {
        if (lastDeclaredType.struct == SymbolTable.noType) { return; }
        if (lastDeclaredType.struct != SymbolTable.intType) {
            report_error("Error: incompatible types", numConstInit);
            return;
        }
        String name = numConstInit.getName();
        if (nameExistsInCurrentScope(name, numConstInit)) { return; }
        Obj newConst = SymbolTable.insert(Obj.Con, name, SymbolTable.intType);
        int val = numConstInit.getVal();
        newConst.setAdr(val);
        report_info("Constant declared: name: " + name + ", type: int, value: " + val, numConstInit);
    }

    @Override
    public void visit(BoolConstInit boolConstInit) {
        if (lastDeclaredType.struct == SymbolTable.noType) { return; }
        if (lastDeclaredType.struct != SymbolTable.boolType) {
            report_error("Error: incompatible types", boolConstInit);
            return;
        }
        String name = boolConstInit.getName();
        if (nameExistsInCurrentScope(name, boolConstInit)) { return; }
        Obj newConst = SymbolTable.insert(Obj.Con, name, SymbolTable.boolType);
        boolean val = boolConstInit.getVal();
        if (val) { newConst.setAdr(1); }
        else { newConst.setAdr(0); }
        report_info("Constant declared: name: " + name + ", type: bool, value: " + val, boolConstInit);
    }

    @Override
    public void visit(CharConstInit charConstInit) {
        if (lastDeclaredType.struct == SymbolTable.noType) { return; }
        if (lastDeclaredType.struct != SymbolTable.charType) {
            report_error("Error: incompatible types", charConstInit);
            return;
        }
        String name = charConstInit.getName();
        if (nameExistsInCurrentScope(name, charConstInit)) { return; }
        Obj newConst = SymbolTable.insert(Obj.Con, name, SymbolTable.charType);
        char val = charConstInit.getVal();
        newConst.setAdr(val);
        report_info("Constant declared: name: " + name + ", type: char, value: " + val, charConstInit);
    }

    @Override
    public void visit(EnumName EnumName) {
        String name = EnumName.getName();
        if (nameExistsInCurrentScope(name, EnumName)) { return; }
        currEnum = new Struct(SymbolTable.Enum);
        enumVals = new ArrayList<>();
        EnumName.obj = SymbolTable.insert(Obj.Type, name, currEnum);
        report_info("Type declared: name: " + name + " kind: enum", EnumName);
        SymbolTable.insert(Obj.Type, name + "[]", new Struct(Struct.Array, currEnum));
        report_info("Type declared: name: " + name + "[]" + " kind: array", EnumName);
        SymbolTable.openScope();
    }

    @Override
    public void visit(EnumMemDef enumMember) {
        if (currEnum == null) { return; }
        String name = enumMember.getName();
        if (nameExistsInCurrentScope(name, enumMember)) { return; }
        int val = enumMember.getVal();
        for (Integer existingVal: enumVals) {
        	if (val == existingVal) {
                report_error("Error: enum already contains value " + val, enumMember);
                return;
            }
        }
        enumVals.add(val);
        Obj member = SymbolTable.insert(Obj.Con, name, SymbolTable.intType);
        member.setAdr(val);
        report_info("Enum constant declared: name: " + name + ", value: " + val, enumMember);
    }

    @Override
    public void visit(EnumMemUnDef enumMember) {
        if (currEnum == null) { return; }
        String name = enumMember.getName();
        if (nameExistsInCurrentScope(name, enumMember)) { return; }
        Integer nextVal = (enumVals.size() == 0 ? 0 : enumVals.get(enumVals.size() - 1) + 1);
        for (Integer existingVal: enumVals) {
        	if (nextVal == existingVal) {
                report_error("Error: enum already contains needed value " + nextVal, enumMember);
                return;
            }
        }
        enumVals.add(nextVal);
        Obj member = SymbolTable.insert(Obj.Con, name, SymbolTable.intType);
        member.setAdr(nextVal);
        report_info("Enum constant declared: name: " + name + ", value: " + nextVal, enumMember);
    }

    @Override
    public void visit(EnumDecl enumDecl) {
        SymbolTable.chainLocalSymbols(currEnum);
        SymbolTable.closeScope();
        currEnum = null;
    }

    @Override
    public void visit(BasicVar var) {
        if (lastDeclaredType.struct == SymbolTable.noType) { return; }
        String name = var.getName();
        if (nameExistsInCurrentScope(name, var)) { return; }
        Obj objNode = SymbolTable.insert(Obj.Var, name, lastDeclaredType.struct);
        objNode.setFpPos(-1);
        report_info("Variable declared: name: " + name + ", type: " + lastDeclaredType.getName(), var);
    }

    @Override
    public void visit(ArrayVar var) {
        if (lastDeclaredType.struct == SymbolTable.noType) { return; }
        String name = var.getName();
        if (nameExistsInCurrentScope(name, var)) { return; }
        String arrayType = lastDeclaredType.getName() + "[]";
        Obj arrayTypeNode = SymbolTable.find(arrayType);
        Obj node = SymbolTable.insert(Obj.Var, name, arrayTypeNode.getType());
        node.setFpPos(-1);
        report_info("Variable declared: name: " + name + ", type: " + arrayType, var);
    }

    @Override
    public void visit(MethodType methodType) {
        String name = methodType.getName();
        if (nameExistsInCurrentScope(name, methodType)) { return; }
        ReturnType retType = methodType.getReturnType();
        if (retType instanceof VoidReturnType) { retType.struct = SymbolTable.noType; }
        else { retType.struct = ((OtherReturnType)retType).getType().struct; }
        currMethod = SymbolTable.insert(Obj.Meth, name, retType.struct);
        SymbolTable.openScope();
        methodType.obj = currMethod;
        report_info("Inside method " + name, methodType);
        if (retType.struct == SymbolTable.noType) { report_info("Return type: void", null); }
        else { report_info("Return type: " + lastDeclaredType.getName(), null); }
    }

    @Override
    public void visit(MethodDecl methodDecl) {
        if (currMethod == null) { return; }
        int nlocals = SymbolTable.currentScope().getnVars();
        if (nlocals > 256) {
            report_error("Error: too many local variables (" + nlocals + ") in method method " +
            		currMethod.getName() + ", up to 256 is allowed", null);
        }
        if (currMethod.getType() != SymbolTable.noType && !returnFound) {
            report_error("Error: return statement required for method " + currMethod.getName(), null);
        }
        SymbolTable.chainLocalSymbols(currMethod);
        SymbolTable.closeScope();
        currMethod.setLevel(formalParamCount);
        report_info("Outside method " + currMethod.getName(), null);
        returnFound = false;
        currMethod = null;
        formalParamCount = 0;
    }

    @Override
    public void visit(ExprRetStat exprRet) {
        if (currMethod == null) {
            report_error("Error: return statement has to be inside function body", exprRet);
            return;
        }
        returnFound = true;
        Struct returnType = currMethod.getType();
        if (compatibleWithEnum(returnType, exprRet.getExpr())) { return; }
        if (!returnType.equals(exprRet.getExpr().struct)) {
            report_error("Error: incompatible return type", exprRet);
        }
    }

    @Override
    public void visit(VoidRetStat voidRet) {
        if (currMethod == null) {
            report_error("Error: return statement has to be inside function body", voidRet);
        }
        else if (currMethod.getType() != SymbolTable.noType) {
            report_error("Error: return type must be void", voidRet);
        }
    }

    @Override
    public void visit(SimpleMethodParameter param) {
        if (lastDeclaredType.struct == SymbolTable.noType) { return; }
        String name = param.getName();
        if (nameExistsInCurrentScope(name, param))
            return;
        Obj objNode = SymbolTable.insert(Obj.Var, name, lastDeclaredType.struct);
        objNode.setFpPos(formalParamCount++);
        report_info("Formal parameter declared: name: " + name + ", type: " + param.getType().getName(), param);
    }

    @Override
    public void visit(ArrayMethodParameter param) {
        if (lastDeclaredType.struct == SymbolTable.noType) { return; }
        String name = param.getName();
        if (nameExistsInCurrentScope(name, param)) { return; }
        Type type = param.getType();
        String typeName = type.getName() + "[]";
        Obj arrayTypeNode = SymbolTable.find(typeName);
        Obj node = SymbolTable.insert(Obj.Var, name, arrayTypeNode.getType());
        node.setFpPos(formalParamCount++);
        report_info("Formal parameter declared: name: " + name + ", type: " + typeName, param);
    }

    @Override
    public void visit(Expression expr) {
        expr.struct = expr.getAddopExpr().struct;
    }

    @Override
    public void visit(ComplexFact nestedExpr) {
        nestedExpr.struct = nestedExpr.getExpr().struct;
    }

    @Override
    public void visit(NegatedExpression expr) {
        expr.struct = expr.getAddopExpr().struct;
        if (expr.struct != SymbolTable.intType) {
            report_error("Error: can only negate type int", expr);
            expr.struct = SymbolTable.noType;
            return;
        }
        expr.struct = expr.getAddopExpr().struct;
    }

    @Override
    public void visit(AddopExpression expr) {
        Struct term1 = expr.getAddopExpr().struct;
        Struct term2 = expr.getTerm().struct;
        if ((term1.getKind() == SymbolTable.Enum || term1 == SymbolTable.intType) &&
        		(term2.getKind() == SymbolTable.Enum || term2 == SymbolTable.intType)) {
        expr.struct = SymbolTable.intType;
        } else {
            report_error("Error: incompatible types", expr);
            expr.struct = SymbolTable.noType;
        }
    }

    @Override
    public void visit(TermExpression termExp) {
        termExp.struct = termExp.getTerm().struct;
    }

    @Override
    public void visit(AddopTerm addopTerm) {
        Struct term1 = addopTerm.getTerm().struct;
        Struct term2 = addopTerm.getFactor().struct;
        if ((term1.getKind() == SymbolTable.Enum || term1 == SymbolTable.intType) &&
        		(term2.getKind() == SymbolTable.Enum || term2 == SymbolTable.intType)) {
            addopTerm.struct = term1;
        } else {
            report_error("Error: incompatible types", addopTerm);
            addopTerm.struct = SymbolTable.noType;
        }
    }

    @Override
    public void visit(FactorTerm factorTerm) {
        factorTerm.struct = factorTerm.getFactor().struct;
    }

    @Override
    public void visit(ManyTermCondition termCond) {
        Struct term1 = termCond.getCondition().struct;
        Struct term2 = termCond.getCondTerm().struct;
        if (term1.equals(term2)) {
            termCond.struct = term1;
        } else {
            report_error("Error: incompatible types", termCond);
            termCond.struct = SymbolTable.noType;
        }
    }

    @Override
    public void visit(OneTermCondition termCond) {
        termCond.struct = termCond.getCondTerm().struct;
    }

    @Override
    public void visit(ManyFactorCondTerm condTerm) {
        Struct term1 = condTerm.getCondTerm().struct;
        Struct term2 = condTerm.getCondFact().struct;
        if (term1.equals(term2)) {
            condTerm.struct = term1;
        } else {
            report_error("Error: incompatible types", condTerm);
            condTerm.struct = SymbolTable.noType;
        }
    }

    @Override
    public void visit(OneFactorCondTerm condTerm) {
        condTerm.struct = condTerm.getCondFact().struct;
    }

    @Override
    public void visit(SingleCondFact condFact) {
        Struct type = condFact.getExpr().struct;
        if (type == SymbolTable.boolType) {
            condFact.struct = type;
        } else {
            report_error("Error: expected type bool", condFact);
            condFact.struct = SymbolTable.noType;
        }
    }

    @Override
    public void visit(ComplexCondFact condFact) {
        Relop relop = condFact.getRelop();
        Struct term1 = condFact.getExpr().struct;
        Struct term2 = condFact.getExpr1().struct;
        if (term1.compatibleWith(term2)) {
            if ((term1.getKind() == Struct.Array) &&
                    (!(relop instanceof RelopEQ || relop instanceof RelopNEQ))) {
                report_error("Error: illegal operator", condFact);
                condFact.struct = SymbolTable.noType;
                return;
            }
            condFact.struct = SymbolTable.boolType;
        } else {
            report_error("Error: incompatible types", condFact);
            condFact.struct = SymbolTable.noType;
        }
    }

    @Override
    public void visit(DesignatorName designatorName) {
        String name = designatorName.getName();
        Obj node =  SymbolTable.find(name);
        designatorNodes.addFirst(node);
        designatorNames.addFirst(new StringBuilder(name));
        designatorName.obj = node;
        if (node == SymbolTable.noObj) {
            report_error("Error: " + name + " not yet declared", designatorName);
            designatorNodes.removeFirst();
            designatorNodes.addFirst(SymbolTable.noObj);
        }
    }

    @Override
    public void visit(DesignatorArrayMember member) {
        if (designatorNodes.peekFirst() == SymbolTable.noObj) { return; }
        if (designatorNodes.peekFirst().getType().getKind() != Struct.Array) {
            designatorNodes.removeFirst();
            designatorNodes.addFirst(SymbolTable.noObj);
            report_info(Struct.None + " " + designatorNodes.peekFirst().getType().getKind(), member);
            report_error("Error: indexing a non array", member);
            return;
        }
        if (member.getExpr().struct != SymbolTable.intType) {
            designatorNodes.removeFirst();
            designatorNodes.addFirst(SymbolTable.noObj);
            report_error("Error: non numeric array index", member);
            return;
        }
        Obj obj = designatorNodes.removeFirst();
        designatorNodes.addFirst(new Obj(Obj.Elem, designatorNames.peekFirst().toString(), obj.getType().getElemType()));
        designatorNames.peekFirst().append("[]");
    }

    @Override
    public void visit(DesignatorEnumMember member) {
        if (designatorNodes.peekFirst() == SymbolTable.noObj) { return; }
        int kind = designatorNodes.peekFirst().getType().getKind();
        if (kind != SymbolTable.Enum) {
            designatorNodes.removeFirst();
            designatorNodes.addFirst(SymbolTable.noObj);
            report_error("Error: enum required", member);
            return;
        }
        Obj memberNode = designatorNodes.peekFirst().getType().getMembers().searchKey(member.getName());
        if (memberNode == null) {
            designatorNodes.removeFirst();
            designatorNodes.addFirst(SymbolTable.noObj);
            report_error("Error: " + member.getName() + " not yet defined", member);
            return;
        }
        designatorNodes.removeFirst();
        designatorNodes.addFirst(memberNode);
        designatorNames.peekFirst().append(".").append( member.getName());
    }

    @Override
    public void visit(Designator designator) {
        String designatorName = designatorNames.peekFirst().toString();
        designator.obj = designatorNodes.removeFirst();
        designatorNames.removeFirst();
        if (FunCall.class == designator.getParent().getClass()) {
            if (currMethod != null && currMethod.getName() == designator.obj.getName()) {
                SymbolTable.chainLocalSymbols(currMethod);
                functionDeclaratorStack.addFirst(new FunctionDeclarator(formalParamCount, currMethod.getLocalSymbols()));
            } else {
                functionDeclaratorStack.addFirst(new FunctionDeclarator(designator.getDesignatorName().obj));
            }
        }
        report_info("Designator declared: " + designatorName, designator);
    }

    @Override
    public void visit(DesignatorFact fact) {
        if (fact.getDesignator().obj.getType().getKind() == SymbolTable.Enum) { fact.struct = SymbolTable.intType; }
        else { fact.struct = fact.getDesignator().obj.getType(); }
    }

    @Override
    public void visit(FuncCallFact fact) {
        DesignatorName funDesignator = fact.getFunCall().getDesignator().getDesignatorName();
        if (funDesignator.obj.getKind() != Obj.Meth) {
            report_error("Error: " + funDesignator.getName() + " is not a callable", fact);
        }
        if (fact.getFunCall().getDesignator().obj.getType() == SymbolTable.noType) {
            report_error("Error: invalid return type for " + funDesignator.getName(), fact);
        }
        fact.struct = fact.getFunCall().getDesignator().obj.getType();
    }

    @Override
    public void visit(NumConstFact numConstFact) {
        numConstFact.struct = SymbolTable.intType;
    }

    @Override
    public void visit(CharConstFact charConstFact) {
        charConstFact.struct = SymbolTable.charType;
    }

    @Override
    public void visit(BoolConstFact boolConstFact) {
        boolConstFact.struct = SymbolTable.boolType;
    }

    @Override
    public void visit(NewObjFact newObjFact) {
        if (newObjFact.getType().struct == SymbolTable.noType) { return; }
        newObjFact.struct = newObjFact.getType().struct;
    }

    @Override
    public void visit(NewArrayFact newArrayFact) {
        if (newArrayFact.getArrayType().getType().struct == SymbolTable.noType) { return; }
        if (newArrayFact.getArrayType().getExpr().struct != SymbolTable.intType) {
            report_error("Error: array length is not a number", newArrayFact);
            newArrayFact.struct = SymbolTable.noType;
            return;
        }
        Obj typeNode = SymbolTable.find(newArrayFact.getArrayType().getType().getName() + "[]");
        newArrayFact.struct = typeNode.getType();
        if (newArrayFact.getOptArrayInit() != null && newArrayFact.getOptArrayInit() instanceof ArrayInitializer) {
        	try {
        		NumConstFact numConstFact = ((NumConstFact)((FactorTerm)((TermExpression)((Expression)
        				newArrayFact.getArrayType().getExpr()).getAddopExpr()).getTerm()).getFactor());
        		if (arrayInitListLen != numConstFact.getVal()) {
            		report_error("Error: wrong length of array initialization list, requred " +
            				numConstFact.getVal() + " got " + arrayInitListLen, newArrayFact);
            		return;        			
        		}
        	} catch(Exception e) {
        		report_error("Error: use fixed array len with array initializatoion list", newArrayFact);
        		return;
        	}
        }
    }

    @Override
    public void visit(IncExprStatement incStat) {
        DesignatorName statDesignator = incStat.getDesignator().getDesignatorName();
        if (statDesignator.obj.getKind() != Obj.Var) {
            report_error("Error: cannot increment " + statDesignator.getName(), incStat);
            return;
        }
        if (incStat.getDesignator().obj.getType() != SymbolTable.intType) {
            report_error("Error: cannot increment non numerical", incStat);
        }
    }

    @Override
    public void visit(DecExprStatement decStat) {
        DesignatorName statDesignator = decStat.getDesignator().getDesignatorName();
        if (statDesignator.obj.getKind() != Obj.Var) {
            report_error("Error: cannot decrement " + statDesignator.getName(), decStat);
            return;
        }
        if (decStat.getDesignator().obj.getType() != SymbolTable.intType) {
            report_error("Error: cannot decrement non numerical", decStat);
        }
    }

    @Override
    public void visit(ForLoopTag forLoopTag) {
        forLoopNestingLevel++;
    }

    @Override
    public void visit(ForStat forStat) {
        forLoopNestingLevel--;
    }

    @Override
    public void visit(BreakStat breakStat) {
        if (forLoopNestingLevel == 0) {
            report_error("Error: break statement is outside a for loop", breakStat);
        }
    }

    @Override
    public void visit(ContStat contStat) {
        if (forLoopNestingLevel == 0) {
            report_error("Error: continue statement is outside a for loop", contStat);
        }
    }

    @Override
    public void visit(ReadStat readStat) {
        DesignatorName statDesignator = readStat.getDesignator().getDesignatorName();
        if (statDesignator.obj.getKind() != Obj.Var) {
            report_error("Error: cannot read into " + statDesignator.getName(), readStat);
            return;
        }
        Struct type = readStat.getDesignator().obj.getType();
        if (type != SymbolTable.intType && type != SymbolTable.charType && type != SymbolTable.boolType) {
            report_error("Error: infalid type", readStat);
        }
    }

    @Override
    public void visit(PrintSingleStat printStat) {
        Struct type = printStat.getExpr().struct;
        if (type != SymbolTable.intType && type != SymbolTable.charType && type != SymbolTable.boolType) {
            report_error("Error: invalid type", printStat);
        }
    }

    @Override
    public void visit(PrintMultiStat printMultiStat) {
        Struct type = printMultiStat.getExpr().struct;
        if (type != SymbolTable.intType && type != SymbolTable.charType && type != SymbolTable.boolType) {
            report_error("Error: invalid type", printMultiStat);
        }
        if (printMultiStat.getVal() < 1) {
            report_error("Error: non negative integer required as second argument", printMultiStat);
        }
    }

    @Override
    public void visit(AssignStat statement) {
        Designator statDesignator = statement.getDesignator();
        if (statDesignator.getDesignatorName().obj.getKind() != Obj.Var) {
            report_error("Error: cannot assign to " + statDesignator.getDesignatorName().getName(), statement);
            return;
        }
        if (compatibleWithEnum(statDesignator.obj.getType(), statement.getExpr())) { return; }
        if (!statement.getExpr().struct.assignableTo(statDesignator.obj.getType())) {
            report_error("Error: expression not assignable", statement);
        }
    }

    @Override
    public void visit(FuncCallExprStatement statement) {
        DesignatorName statDesignator = statement.getFunCall().getDesignator().getDesignatorName();
        if (statDesignator.obj.getKind() != Obj.Meth) {
            report_error("Error: cannot call " + statDesignator.getName(), statement);
        }
    }

    @Override
    public void visit(EmptyCallParameterList list) {
        int required = functionDeclaratorStack.peekFirst().expectedNumberOfArguments();
        if (required != 0) { report_error("Error: " + required + " parameter(s) needed", list); }
    }

    @Override
    public void visit(CallParameterList list) {
        if (!functionDeclaratorStack.peekFirst().allParametersProcessed()) {
            report_error("Error: too few arguments", list);
        }
    }

    @Override
    public void visit(ActualParameters params) {
        if (functionDeclaratorStack.peekFirst().errorFound()) { return; }
        String errorMsg = functionDeclaratorStack.peekFirst().processActualParam(params.getExpr());
        if (errorMsg != null) { report_error(errorMsg, params); }
    }

    @Override
    public void visit(OneActualParameter actParam) {
        if (functionDeclaratorStack.peekFirst().errorFound()) { return; }
        String errorMsg = functionDeclaratorStack.peekFirst().processActualParam(actParam.getExpr());
        if (errorMsg != null) { report_error(errorMsg, actParam); }
    }

    @Override
    public void visit(FunCall funCall) {
        functionDeclaratorStack.removeFirst();
    }

	@Override
	public void visit(ArrayInitializerList arrayInitiList) {
		arrayInitListLen++;
        if (compatibleWithEnum(lastDeclaredType.struct, arrayInitiList.getExpr())) { return; }
		if (!arrayInitiList.getExpr().struct.assignableTo(lastDeclaredType.struct)) {
            report_error("Error: bad type", arrayInitiList);
        }
	}

	@Override
	public void visit(ArrayInitializerExpr arrayInitExpr) {
		arrayInitListLen = 1;
        if (compatibleWithEnum(lastDeclaredType.struct, arrayInitExpr.getExpr())) { return; }
		if (!arrayInitExpr.getExpr().struct.assignableTo(lastDeclaredType.struct)) {
			report_error("Error: bad type", arrayInitExpr);
		}
	}
	
	static boolean compatibleWithEnum(Struct dest, Expr expr) {
		if (expr.struct.getKind() == SymbolTable.Enum && dest == SymbolTable.intType) {
			return true;
		}
		if (expr.struct.getKind() == dest.getKind()) {
			return true;
		}
		if (dest.getKind() == SymbolTable.Enum) {
			try {
				Designator desig = ((DesignatorFact)((FactorTerm)((TermExpression)((Expression) expr)
						.getAddopExpr()).getTerm()).getFactor()).getDesignator();
				DesignatorName desigName = desig.getDesignatorName();
	            if (desigName.obj.getType().getKind() == Struct.Array) {
	                return desig.obj.getType().equals(dest);
	            } else {
	                return desigName.obj.getType().equals(dest);
	            }
			} catch (Exception e) {
		        return false;
			}
		}
		return false;
    }
	
}