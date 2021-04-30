import pt.up.fe.comp.jmm.JmmNode;
import pt.up.fe.comp.jmm.analysis.table.Symbol;
import pt.up.fe.comp.jmm.analysis.table.Type;
import pt.up.fe.comp.jmm.ast.JmmVisitor;
import pt.up.fe.comp.jmm.ast.PostorderJmmVisitor;

import java.util.List;
import java.util.Map;

public class TwoPartExpressionVisitor extends PostorderJmmVisitor<StringBuilder, Boolean> {

    private List<String> methods;
    private SymbolTableImp symbolTable;
    private String methodName;
    private List<String> methodParametersNames;
    private String methodType;
    private List<Symbol> globalVariables;
    private List<String> globalVariablesNames;
    private List<Symbol> methodParameters;
    private List<Symbol> localVariables;
    private List<String> localVariablesNames;
    private boolean firstMultLines;
    private List<Symbol> tempRegisters;
    private int tempVarsCount;
    private Symbol lastSymbol;

    public TwoPartExpressionVisitor(List<String> methods, SymbolTableImp symbolTable, String methodName, List<String> methodParametersNames, List<Symbol> globalVariables, List<String> globalVariablesNames, List<Symbol> methodParameters, List<Symbol> localVariables, List<String> localVariablesNames, List<Symbol> tempRegisters, int tempVarsCount) {
        this.methods = methods;
        this.symbolTable = symbolTable;
        this.globalVariables = globalVariables;
        this.globalVariablesNames = globalVariablesNames;
        this.methodName = methodName;
        this.methodParameters = methodParameters;
        this.methodParametersNames = methodParametersNames;
        this.localVariables = localVariables;
        this.localVariablesNames = localVariablesNames;
        this.methodType = "void";
        this.firstMultLines = false;
        this.tempRegisters = tempRegisters;
        this.tempVarsCount = tempVarsCount;
        this.lastSymbol = null;
        addVisit("TwoPartExpression", this::visitTwoPartExpression);
        System.out.println("TEMP VARS: " + this.tempVarsCount);
        System.out.println("LOCAL: " + this.tempRegisters + "; " + this.tempVarsCount);
        System.out.println("PASSED: " + tempRegisters + "; " + tempVarsCount);
    }

    public Boolean visitTwoPartExpression(JmmNode node, StringBuilder stringBuilder) {
        JmmNode firstChild = node.getChildren().get(0); // Identifier, This, New
        JmmNode secondChild = node.getChildren().get(1); // InsideArray, DotExpression
//        stringBuilder.append("\t\tTwo Part Expression: line = " + node.get("line") + ", col = " + node.get("col") + ";\n");
//        stringBuilder.append("\t\t\t First Child: " + firstChild + ";\n");
//        stringBuilder.append("\t\t\t Second Child: " + secondChild + ";\n");
        StringBuilder firstChildString = new StringBuilder();

        switch (firstChild.getKind()) {
            case "Identifier":
                generateIdentifier(firstChild, firstChildString);
                break;

            case "This":
                generateThis(firstChildString);
                break;

            case "New":
                generateNew(firstChild, firstChildString);
                break;
        }

        switch (secondChild.getKind()) {
            case "InsideArray":
                break;

            case "DotExpression": // MethodCall or Length
                if(secondChild.getChildren().get(0).getKind().equals("MethodCall")) {
                    generateMethodCall(secondChild.getChildren().get(0), stringBuilder, firstChildString);
                }

                break;
        }

        return true;
    }

    public void generateIdentifier(JmmNode node, StringBuilder stringBuilder) {
        stringBuilder.append(node.get("name"));
    }

    public void generateThis(StringBuilder stringBuilder) {
        stringBuilder.append("this");
    }

    public void generateNew(JmmNode node, StringBuilder stringBuilder) {
//            aux1.Fac :=.Fac new(Fac).Fac;
//            invokespecial(aux1.Fac,"<init>").V;
//            aux2.i32 :=.i32 invokevirtual(aux1.Fac,"compFac",10.i32).i32;
//            invokestatic(io, "println", aux2.i32).V;

        JmmNode child = node.getChildren().get(0);
        if(child.getKind().equals("ClassCall")) {
            String className = child.getChildren().get(0).get("name");
            Symbol s = addTempVar(className, false);
            stringBuilder.append("\t\t" + s.getName() + "." + className + " :=." + className + " new(" + className + ")." + className + ";\n");
            stringBuilder.append("\t\tinvokespecial(" + s.getName() + "." + className + ", \"<init>\").V;\n");
            this.lastSymbol = s;
        }
        this.firstMultLines = true;
    }

    public void generateMethodCall(JmmNode node, StringBuilder stringBuilder, StringBuilder firstChildBuilder) {
        JmmNode callMethodNameNode = node.getChildren().get(0);
        String callMethodName = callMethodNameNode.get("name");
        Boolean isArray = false;
        String callType = "void";

        if(symbolTable.getMethods().contains(callMethodName)) {
            this.methodType = symbolTable.getReturnType(callMethodName).getName();
            callType = symbolTable.getReturnType(callMethodName).getName();
            if(symbolTable.getReturnType(callMethodName).isArray()) {
                this.methodType += "[]";
                isArray = true;
            }
        }

        if(this.firstMultLines) {
            stringBuilder.append(firstChildBuilder);
            Symbol s = addTempVar(callType, isArray);
            stringBuilder.append("\t\t" + s.getName() + "." + getType(methodType) + " :=." + getType(methodType) + " invokevirtual(" + lastSymbol.getName() + "." + lastSymbol.getType().getName() + ", \"" + callMethodName + "\"");
            this.firstMultLines = false;
            this.lastSymbol = s;
        }
        else {
            if(!this.methods.contains(callMethodName)) {
                stringBuilder.append("\t\tinvokestatic(");
            }
            else {
                stringBuilder.append("\t\tinvokespecial(");
                Type mType = symbolTable.getReturnType(callMethodName);
                this.methodType = mType.getName();
                if(mType.isArray()) {
                    this.methodType += "[]";
                }
            }

            stringBuilder.append(firstChildBuilder + ", \"" + callMethodName + "\"");
        }


        if(node.getNumChildren() == 1) {
            stringBuilder.append(")." + getType(this.methodType) + ";\n"); //sera sempre .V aqui?
        }
        else {
            for(int i = 1; i < node.getNumChildren(); i++) {
                JmmNode child = node.getChildren().get(i);

                if(child.getKind().equals("Identifier")) {
                    String type = getNodeType(child);

                    if(this.methodParametersNames.contains(child.get("name"))) {
                        int idx = this.methodParametersNames.indexOf(child.get("name"));
                        stringBuilder.append(", " + "$" + idx + "." + child.get("name") + "." + getType(type));
                    }
                    else {
                        stringBuilder.append(", " + child.get("name") + "." + getType(type));
                    }
                }
                else if(child.getKind().equals("int") || child.getKind().equals("int[]")) {
                    String type = child.getKind();
                    stringBuilder.append(", " + child.get("value") + "." + getType(type));
                }
                else if(child.getKind().equals("boolean")) {
                    String type = "boolean";
                    String value;
                    if(child.get("value").equals("true")) {
                        value = "1";
                    }
                    else {
                        value = "0";
                    }
                    stringBuilder.append(", " + value + "." + getType(type));
                }
                else if(child.getKind().equals("TwoPartExpression")) {
                    String exprType = this.lastSymbol.getType().getName();
                    if(this.lastSymbol.getType().isArray()) {
                        exprType += "[]";
                    }
                    stringBuilder.append(", " + this.lastSymbol.getName() + "." + getType(exprType));
                }
            }
            stringBuilder.append(")." + getType(this.methodType) + ";\n");
        }
    }

    private String getType(String type) {
        switch(type) {
            case "int":
                return "i32";
            case "boolean":
                return "bool";
            case "void":
                return "V";
            case "int[]":
                return "array.i32";
            case "String[]":
                return "array.String";
            case "String":
                return "String";
            default:
                return type;
        }
    }

    public String getNodeType(JmmNode node) {
        String type;
        if(this.methodParametersNames.contains(node.get("name"))) {
            int idx = this.methodParametersNames.indexOf(node.get("name"));
            type = this.methodParameters.get(idx).getType().getName();
            if(this.methodParameters.get(idx).getType().isArray()) {
                type += "[]";
            }
        }
        else if(this.localVariablesNames.contains(node.get("name"))) {
            int idx = this.localVariablesNames.indexOf(node.get("name"));
            type = this.localVariables.get(idx).getType().getName();
            if(this.localVariables.get(idx).getType().isArray()) {
                type += "[]";
            }
        }
        else {
            int idx = this.globalVariablesNames.indexOf(node.get("name"));
            type = this.globalVariables.get(idx).getType().getName();
            if(this.globalVariables.get(idx).getType().isArray()) {
                type += "[]";
            }
        }
        return type;
    }

    private Symbol addTempVar(String type, Boolean isArray) {
        Symbol s = new Symbol(new Type("int", false), "t"+this.tempVarsCount);
        this.tempRegisters.add(s);
        this.tempVarsCount++;
        return s;
    }
}