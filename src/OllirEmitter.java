import java.io.File;
import java.io.IOException;
import java.io.PrintWriter;
import java.lang.reflect.Array;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.function.BiFunction;
import java.util.stream.Collectors;

//import com.sun.prism.shader.Solid_TextureYV12_AlphaTest_Loader;
import pt.up.fe.comp.jmm.JmmNode;
import pt.up.fe.comp.jmm.analysis.table.Symbol;
import pt.up.fe.comp.jmm.analysis.table.SymbolTable;
import pt.up.fe.comp.jmm.analysis.table.Type;
import pt.up.fe.comp.jmm.ast.JmmVisitor;
import pt.up.fe.comp.jmm.ast.PostorderJmmVisitor;

public class OllirEmitter implements JmmVisitor {

    private PrintWriter printWriterFile;
    private SymbolTableImp symbolTable;
    private int loopCounter;
    private int localVars;
    private int nParams;
    private int maxStack;
    private int totalStack;
    private StringBuilder stringCode;
    private StringBuilder bodyCode;
    private List<Symbol> localVariables;
    private List<String> localVariablesNames;
    private List<Symbol> methodParameters;
    private List<String> methodParametersNames;
    private String methodName;
    private List<String> methods;
    private List<Symbol> globalVariables;
    private List<String> globalVariablesNames;
    private List<Symbol> tempRegisters;
    private List<Symbol> objects;
    private int tempVarsCount;
    private int objectsCount;
    private StringBuilder aux1;
    private StringBuilder aux2;
    private StringBuilder auxGeral;


    public OllirEmitter(SymbolTable table) {
        this.symbolTable = (SymbolTableImp) table;
        this.loopCounter = 0;
        this.localVars = 0;
        this.nParams = 0;
        this.maxStack = 0;
        this.totalStack = 0;
        this.aux1 = new StringBuilder();
        this.aux2 = new StringBuilder();
        this.auxGeral = new StringBuilder();
        this.stringCode = new StringBuilder();
        this.bodyCode = new StringBuilder();
        this.localVariables = new ArrayList<>();
        this.localVariablesNames = new ArrayList<>();
        this.methodParameters = new ArrayList<>();
        this.methodParametersNames = new ArrayList<>();
        this.methodName = null;
        this.methods = new ArrayList<>();
        this.globalVariables = new ArrayList<>();
        this.globalVariablesNames = new ArrayList<>();
        this.tempRegisters = new ArrayList<>();
        this.tempVarsCount = 1;
        this.objects = new ArrayList<>();
        this.objectsCount = 1;
    }


    public String getMethodCode() {
        return stringCode.toString();
    }

    @Override
    public Object visit(JmmNode node, Object data) {
        /*System.out.println("\n\nNODE: ");
        System.out.println("String: "  +node.toString());
        System.out.println("Type: " + node.getClass().getComponentType());
        System.out.println("Kind: " + node.getKind());
        System.out.println("Class: " + node.getClass());
        System.out.println("Attributes: " + node.getAttributes());
        System.out.println("Children: " + node.getChildren());             */

        switch (node.getKind()) {
            case "Class":
                generateClass(node);
        }

        return defaultVisit(node, "");
    }

    private void generateClass(JmmNode classNode) {
        this.localVars = 0;
        this.methods = symbolTable.getMethods();
        this.globalVariables = symbolTable.getFields();

        if(this.globalVariables != null) {
            for(int i = 0; i < this.globalVariables.size(); i++) {
                this.globalVariablesNames.add(this.globalVariables.get(i).getName());
            }
        }

        stringCode.append(symbolTable.getClassName() + "{\n");
        //stringCode.append( ".construct public " + symbolTable.getClassName() + "().V \n");

        generateClassVariables(classNode);
        generateConstructor();

        List<JmmNode> children = classNode.getChildren();
        for (int i = 0; i < children.size(); i++) {
            JmmNode child = children.get(i);

            switch (child.getKind()) {
                case "Main":
                    generateMain(child);
                    break;
                case "Method":
                    generateMethod(child);
                    break;
            }
        }
        stringCode.append("}");
    }

    private void generateConstructor() {
        stringCode.append("\n\t.construct " + symbolTable.getClassName() + "().V {\n");
        stringCode.append("\t\tinvokespecial(this, \"<init>\").V;");
        stringCode.append("\n\t}\n");
    }

    private void generateClassVariables(JmmNode node) {
        for (int i = 0; i < node.getNumChildren(); i++) {
            JmmNode child = node.getChildren().get(i);
            // stringCode.append(node.toString() + "\n");
            if (child.getKind().equals("VarDeclaration")) {
                generateGlobalVar(child);
            }
        }
    }

    private void generateGlobalVar(JmmNode node) {
        JmmNode typeNode = node.getChildren().get(0);
        JmmNode nameNode = node.getChildren().get(1);
        String type = null;
        String name = null;

        if (typeNode.getKind().equals("Identifier")) {
            type = typeNode.get("name");
        } else {
            type = typeNode.getKind();
        }

        name = nameNode.get("name");

        stringCode.append("\n\t.field private " + name + "." + getType(type) + ";\n");

    }

    private void generateMain(JmmNode node) {
        if(this.localVariables != null) {
            this.localVariables.clear();
            this.localVariablesNames.clear();
        }

        if(this.methodParameters != null) {
            this.methodParameters.clear();
            this.methodParametersNames.clear();
        }

        this.methodName = "main";
        this.tempVarsCount = 1;
        this.tempRegisters.clear();
        this.objects.clear();
        this.objectsCount = 1;
        generateMainMethodHeader(node);
        generateMainMethodBody(node);
        stringCode.append("\t}\n");
    }

    private void generateMainMethodHeader(JmmNode node) {
        StringBuilder methodArgs = new StringBuilder();
        StringBuilder methodType = new StringBuilder();

        this.methodParameters = symbolTable.getParameters(methodName);

        for (int i = 0; i < this.methodParameters.size(); i++) {
            this.methodParametersNames.add(this.methodParameters.get(i).getName());
        }


        if (symbolTable.getLocalVariables(this.methodName) != null) {
            this.localVariables = symbolTable.getLocalVariables(this.methodName);
        }

        if (this.localVariables != null) {
            for (int i = 0; i < this.localVariables.size(); i++) {
                this.localVariablesNames.add(this.localVariables.get(i).getName());
            }
        }


        for (int i = 0; i < this.methodParameters.size(); i++) {
            if (i > 0) {
                methodArgs.append(", ");
            }
            String type = this.methodParameters.get(i).getType().getName();
            if (this.methodParameters.get(i).getType().isArray()) {
                type += "[]";
            }
            methodArgs.append(this.methodParametersNames.get(i) + "." + getType(type));
            this.nParams++;
        }

        methodType.append(".V");

        stringCode.append("\n\t.method public static main (" + methodArgs + ")" + methodType + " {\n");
    }

    public void generateMainMethodBody(JmmNode node) {
        for (int i = 1; i < node.getNumChildren(); i++) {
            JmmNode child = node.getChildren().get(i);

            switch (child.getKind()) {
                case "Statement":
                    generateStatement(child, "main");
                    break;
            }

            //Already processed
            //retirar se for argumento ou declaração de variaveis e return

            //If is not any of the others, it is a statement
            //generateStatement(node, false);

        }
    }

    private void generateMethod(JmmNode node) {
        if(localVariablesNames != null) {
            this.localVariablesNames.clear();
            this.localVariables.clear();
        }

        if(methodParameters != null) {
            this.methodParameters.clear();
            this.methodParametersNames.clear();
        }

        this.tempVarsCount = 1;
        this.tempRegisters.clear();
        this.objects.clear();
        this.objectsCount = 1;
        generateMethodHeader(node);
        generateMethodBody(node, this.methodName);
        stringCode.append("\t}\n");
    }

    private void generateMethodHeader(JmmNode methodNode) {
        StringBuilder methodArgs = new StringBuilder();
        StringBuilder methodType = new StringBuilder();

        this.methodName = methodNode.getChildren().get(1).get("name");

        this.methodParameters = symbolTable.getParameters(this.methodName);

        if (methodParameters != null) {
            for (int i = 0; i < this.methodParameters.size(); i++) {
                this.methodParametersNames.add(this.methodParameters.get(i).getName());
            }
        }

        if(symbolTable.getLocalVariables(this.methodName) != null) {
            this.localVariables = symbolTable.getLocalVariables(this.methodName);
        }

        if (localVariables != null) {
            for (int i = 0; i < this.localVariables.size(); i++) {
                this.localVariablesNames.add(this.localVariables.get(i).getName());
            }
        }


        if (this.methodParameters != null) {
            for (int i = 0; i < this.methodParameters.size(); i++) {
                if (i > 0) {
                    methodArgs.append(", ");
                }
                String type = this.methodParameters.get(i).getType().getName();
                if (this.methodParameters.get(i).getType().isArray()) {
                    type += "[]";
                }
                methodArgs.append(this.methodParameters.get(i).getName() + "." + getType(type));
                this.nParams++;
            }
        }

        Type type = symbolTable.getReturnType(this.methodName);
        String typeS = type.getName();
        if (type.isArray()) {
            typeS += "[]";
        }

        methodType.append("." + getType(typeS));

        stringCode.append("\n\t.method public " + this.methodName + "(" + methodArgs + ")" + methodType + " {\n");
    }

    private void generateMethodBody(JmmNode node, String methodName) {
        for (int i = 0; i < node.getNumChildren(); i++) {

            JmmNode child = node.getChildren().get(i);

            //Already processed
            //retirar se for argumento ou declaração de variaveis e return

            //If is not any of the others, it is a statement

            switch (child.getKind()) {
                case "Statement":
                    generateStatement(child, methodName);
                    break;
                case "Return":
                    generateReturn(child, methodName);
                    break;
            }
        }
    }

    private void generateStatement(JmmNode node, String methodName) {
        for (int i = 0; i < node.getNumChildren(); i++) {
            JmmNode child = node.getChildren().get(i);
            String type = null;
            if (child.getKind().equals("Assign")) {
                JmmNode first = child.getChildren().get(0);
                JmmNode second = child.getChildren().get(1);

                if (first.getKind().equals("Identifier")) {
                    type = getNodeType(first);
                    stringCode.append("\t\t").append(first.get("name")).append(".").append(getType(type)).append(" :=").append(getType(type)).append(" ");
                    if (!second.getKind().equals("Identifier") && !second.getKind().equals("int")) {
                        stringCode.append(generateExpression(second, methodName));
                    }
                    else if (second.getKind().equals("Identifier")){
                        type = getNodeType(second);
                        stringCode.append(second.get("name")).append(".").append(getType(type)).append(";\n");
                    }
                    else if (second.getKind().equals("int")) {
                        type = "int";
                        stringCode.append(second.get("value")).append(".").append(getType(type)).append(";\n");
                    }
                } else {
                    stringCode.append(generateExpression(first, methodName));
                    stringCode.append(" := ");
                    if (!second.getKind().equals("Identifier") && !second.getKind().equals("int")) {
                        stringCode.append(generateExpression(second, methodName));
                    }
                    else if (second.getKind().equals("Identifier")){
                        type = getNodeType(second);
                        stringCode.append(second.get("name")).append(".").append(getType(type)).append(";\n");
                    }
                    else if (second.getKind().equals("int")) {
                        type = "int";
                        stringCode.append(second.get("value")).append(".").append(getType(type)).append(";\n");
                    }
                }
            } 
            else if (child.getKind().equals("TwoPartExpression")) { //InsideArray or DotExpression
                generateTwoPartExpression(child);
            }
        }
    }

    private String newAuxiliarVar(String type, String methodName, JmmNode node){
        String value;
        value= generateExpression(node, methodName);
        tempVarsCount++;
        return "\t\tt" + tempVarsCount + "." + type + " :=." + type + " " + value + "." + type + ";\n";
    }

    private String generateExpression(JmmNode node, String methodName) {
        StringBuilder st = new StringBuilder();
        String leftValue ="";
        String rightValue ="";
        switch (node.getKind()) {
            case "Identifier": {
                StringBuilder a= new StringBuilder();
                a.append(node.get("name")).append(".").append(getType(getNodeType(node)));
                return a.toString();
            }
            case "AdditiveExpression":
            case "SubtractiveExpression":
            case "MultiplicativeExpression":
            case "DivisionExpression": {

                JmmNode left = node.getChildren().get(0);
                if(left.getNumChildren()>0 ){
                    st.append(newAuxiliarVar(".i32",methodName,left));
                    leftValue= "t" + tempVarsCount ;
                }
                else{
                    leftValue = generateExpression(left, methodName);
                }
                JmmNode right = node.getChildren().get(1);
                if(right.getNumChildren()>0 ){
                    st.append(newAuxiliarVar(".i32",methodName,right));
                    rightValue= "t" + tempVarsCount ;
                }
                else{
                    rightValue = generateExpression(right, methodName);
                }
                return st + leftValue + " " +node.get("operation") +".i32 "+ rightValue + ";\n";
            }
            case "Less": {
                JmmNode first = node.getChildren().get(0);
                JmmNode second = node.getChildren().get(1);
                lessExp(first, second, methodName);
                break;
            }
            case "And":

                break;
            case "Not":

                break;
        }
        return "";
    }

    /*private void addExp(JmmNode node1, JmmNode node2, String op, String methodname, StringBuilder a){
        if(!node1.getKind().equals("Identifier")){
            Symbol tempVar = addTempVar("int", false);
            a.append(tempVar.getName()).append(".i32").append(" :=.i32 ");
            if(node2.getKind().equals("Identifier")){
                aux2.insert(0,node1.get("operation")+".i32 "+node2.get("name")+"."+getType(getNodeType(node2))+" ");
            }
            addExp(node1.getChildren().get(0), node1.getChildren().get(1), node1.get("operation"), methodname, aux1);
        }
        else{
            a.append(node1.get("name")).append(".").append(getType(getNodeType(node1))).append(" ").append(op).append(".i32 ");
            if(node2.getKind().equals("Identifier")){
                a.append(node2.get("name")).append(".").append(getType(getNodeType(node2))).append(" ");
               // stringCode.append("\t\t").append(a).append(";\n");
            }
            else{
                Symbol tempVar = addTempVar("int", false);
                aux2.append(tempVar.getName()).append(".i32").append(" :=.i32 ");
                addExp(node2.getChildren().get(0), node2.getChildren().get(1), node2.get("operation"), methodname, aux2);
            }
        }
    }
    */

   /* private String parseExp(JmmNode node, String methodName) {
        JmmNode first = node.getChildren().get(0);
        JmmNode second = node.getChildren().get(1);
        if (first.getKind().equals("Identifier") && second.getKind().equals("Identifier")) {
            String op = node.get("operation");
            auxGeral.append(first.get("name")).append(".").append(getType(getNodeType(first))).append(" ").append(op).append(".i32 ");
            auxGeral.append(second.get("name")).append(".").append(getType(getNodeType(second))).append(" ;\n");
        }
        if (!first.getKind().equals("Identifier") && second.getKind().equals("Identifier")) {
            Symbol tempVar = addTempVar("int", false);
            String here = parseExp(first, methodName);
            aux1.append("\t\t").append(tempVar.getName()).append(".i32").append(" :=.i32 ").append(here);
            String op = node.get("operation");
            Symbol tempVar2 = addTempVar("int", false);
            auxGeral.append("\t\t" + tempVar2.getName()).append(op).append(".i32 ").append(second.get("name"));
        }
        if (first.getKind().equals("Identifier") && !second.getKind().equals("Identifier")) {
            String op = node.get("operation");
            //stringCode.append(first.get("name")).append(".").append(getType(getNodeType(first))).append(" ").append(op).append(".i32 ");
            Symbol tempVar = addTempVar("int", false);
            String here = parseExp(first, methodName);
            aux2.append("\t\t").append(tempVar.getName()).append(".i32").append(" :=.i32 ").append(here);
            auxGeral.append(first.get("name")).append(".").append(getType(getNodeType(first))).append(" ").append(op).append(".i32 ").append(" t1; \n");
        }
        String ret = auxGeral.toString();
        return ret;
    }*/


    private void lessExp(JmmNode node1, JmmNode node2,  String methodname){
        Symbol tempVar = addTempVar("int", false);
        if(node1.getKind().equals("Identifier")){
            String type = getNodeType(node1);
            stringCode.append(node1.get("name") + "." + getType(type) + " < "+ ".i32 ");
        }
        else{
            generateExpression(node1, methodname);
        }
        if(node2.getKind().equals("Identifier")){
            String type = getNodeType(node2);
            stringCode.append(node2.get("name") + "." + getType(type) + ";\n" )  ;
        }
        else{
            stringCode.append("\n\t\t" + tempVar.getName() + ".i32" + " :=.i32 ");
            generateExpression(node2, methodname);
        }
    }

    public void generateTwoPartExpression(JmmNode node) { //InsideArray or DotExpression
        TwoPartExpressionVisitor twoPartExpressionVisitor = new TwoPartExpressionVisitor(this.methods, this.symbolTable, this.methodName, this.methodParametersNames, this.globalVariables, this.globalVariablesNames, this.methodParameters, this.localVariables, this.localVariablesNames, this.tempRegisters, this.tempVarsCount, this.objects, this.objectsCount);
        twoPartExpressionVisitor.visit(node, stringCode);
    }

    private void generateReturn(JmmNode node, String methodName) {
        String returnType = symbolTable.getReturnType(this.methodName).getName();
        if(symbolTable.getReturnType(this.methodName).isArray()) {
            returnType += "[]";
        }

        JmmNode returnVarNode = node.getChildren().get(0);
        String varKind = returnVarNode.getKind();
        String var = null;

        if(varKind.equals("Identifier")) {
            var = returnVarNode.get("name");

            List<Symbol> vars = new ArrayList<>();

            if(symbolTable.getLocalVariables(this.methodName) != null) {
                for(int i = 0; i < symbolTable.getLocalVariables(this.methodName).size(); i++) {
                    vars.add(symbolTable.getLocalVariables(this.methodName).get(i));
                }
            }

            Boolean found = false;

            for(int i = 0; i < vars.size(); i++) {
                if(vars.get(i).getName().equals(var)) {
                    varKind = vars.get(i).getType().getName();
                    if(vars.get(i).getType().isArray()) {
                        varKind += "[]";
                    }
                    found = true;
                }
            }

            if(!found) {
                vars = symbolTable.getFields();
                Boolean isArray = false;
                String t = null;

                for(int i = 0; i < vars.size(); i++) {
                    if(vars.get(i).getName().equals(var)) {
                        varKind = vars.get(i).getType().getName();
                        t = varKind;
                        if(vars.get(i).getType().isArray()) {
                            varKind += "[]";
                            isArray = true;
                        }
                    }
                }

                String type = getType(varKind);
                Symbol s = addTempVar(t, isArray);

                if(checkIfObject(varKind)) {
                    Symbol o = addObject(t, isArray);
                    stringCode.append("\t\t" + s.getName() + "." + type + " :=." + type + " getfield(" + o.getName() + "." + type + ", " + var + "." + type + ")." + type + ";\n");
                    var = s.getName();
                }
                else {
                    stringCode.append("\t\t" + s.getName() + "." + type + " :=." + type + " getfield(this" + ", " + var + "." + type + ")." + type + ";\n");
                    var = s.getName();
                }
            }
        }
        else if(varKind.equals("TwoPartExpression")) { // como fazer return this.test(a); ??
            generateTwoPartExpression(returnVarNode);
            varKind = this.tempRegisters.get(this.tempRegisters.size() - 1).getType().getName();
            if(this.tempRegisters.get(this.tempRegisters.size() - 1).getType().isArray()) {
                varKind += "[]";
            }
            var = this.tempRegisters.get(this.tempRegisters.size() - 1).getName();
        }
        else {
            var = returnVarNode.get("value");
        }

        stringCode.append("\n\t\tret." + getType(returnType) + " " + var + "." + getType(varKind) + ";\n");
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


    private String defaultVisit(JmmNode node, String space) {
        String content = space + node.getKind();
        String attrs = node.getAttributes()
                .stream()
                .filter(a -> !a.equals("line"))
                .map(a -> a + "=" + node.get(a))
                .collect(Collectors.joining(", ", "[", "]"));

        content += ((attrs.length() > 2) ? attrs : "") + "\n";
        for (JmmNode child : node.getChildren()) {
            content += visit(child, space + " ");
        }
        return content;
    }

    private Symbol addTempVar(String type, Boolean isArray) {
        Symbol s = new Symbol(new Type(type, isArray), "t" + this.tempVarsCount);
        this.tempRegisters.add(s);
        this.tempVarsCount++;
        return s;
    }
    
    private Symbol addTempVarAux(String type, Boolean isArray, String content){
        Symbol s = new Symbol(new Type(type, isArray), "t" + this.tempVarsCount + content);
        this.tempRegisters.add(s);
        this.tempVarsCount++;
        return s;
    }
    
    
    private Symbol addObject(String type, Boolean isArray) {
        Symbol s = new Symbol(new Type(type, isArray), "o"+this.objectsCount);
        this.objects.add(s);
        this.objectsCount++;
        return s;
    }
    private Boolean checkIfObject(String type) {
        if(!(type.equals("int") && type.equals("int[]") && type.equals("boolean") && type.equals("String[]") && type.equals("String"))) {
            return false;
        }
        return true;
    }
    @Override
    public void setDefaultVisit(BiFunction method) {

    }

    @Override
    public void addVisit(String kind, BiFunction method) {

    }

}
