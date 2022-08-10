/*
 *  Copyright (c) 2022, WSO2 Inc. (http://www.wso2.org) All Rights Reserved.
 *
 *  WSO2 Inc. licenses this file to you under the Apache License,
 *  Version 2.0 (the "License"); you may not use this file except
 *  in compliance with the License.
 *  You may obtain a copy of the License at
 *
 *    http://www.apache.org/licenses/LICENSE-2.0
 *
 *  Unless required by applicable law or agreed to in writing,
 *  software distributed under the License is distributed on an
 *  "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY
 *  KIND, either express or implied.  See the License for the
 *  specific language governing permissions and limitations
 *  under the License.
 */

package io.ballerina.stdlib.grpc.builder.syntaxtree.utils;

import io.ballerina.compiler.syntax.tree.AbstractNodeFactory;
import io.ballerina.compiler.syntax.tree.AnonymousFunctionExpressionNode;
import io.ballerina.compiler.syntax.tree.ExpressionNode;
import io.ballerina.compiler.syntax.tree.ExpressionStatementNode;
import io.ballerina.compiler.syntax.tree.FunctionSignatureNode;
import io.ballerina.compiler.syntax.tree.ImportDeclarationNode;
import io.ballerina.compiler.syntax.tree.MethodCallExpressionNode;
import io.ballerina.compiler.syntax.tree.ModuleMemberDeclarationNode;
import io.ballerina.compiler.syntax.tree.ModulePartNode;
import io.ballerina.compiler.syntax.tree.Node;
import io.ballerina.compiler.syntax.tree.NodeFactory;
import io.ballerina.compiler.syntax.tree.NodeList;
import io.ballerina.compiler.syntax.tree.SyntaxKind;
import io.ballerina.compiler.syntax.tree.SyntaxTree;
import io.ballerina.compiler.syntax.tree.Token;
import io.ballerina.compiler.syntax.tree.TypedBindingPatternNode;
import io.ballerina.compiler.syntax.tree.VariableDeclarationNode;
import io.ballerina.stdlib.grpc.builder.stub.Field;
import io.ballerina.stdlib.grpc.builder.stub.Message;
import io.ballerina.stdlib.grpc.builder.stub.Method;
import io.ballerina.stdlib.grpc.builder.stub.ServiceStub;
import io.ballerina.stdlib.grpc.builder.syntaxtree.components.Function;
import io.ballerina.stdlib.grpc.builder.syntaxtree.components.Imports;
import io.ballerina.stdlib.grpc.builder.syntaxtree.components.ModuleVariable;
import io.ballerina.stdlib.grpc.builder.syntaxtree.components.VariableDeclaration;
import io.ballerina.stdlib.grpc.builder.syntaxtree.constants.SyntaxTreeConstants;
import io.ballerina.tools.text.TextDocument;
import io.ballerina.tools.text.TextDocuments;

import java.util.ArrayList;
import java.util.Map;

import static io.ballerina.stdlib.grpc.GrpcConstants.ORG_NAME;
import static io.ballerina.stdlib.grpc.builder.syntaxtree.components.Expression.getCheckExpressionNode;
import static io.ballerina.stdlib.grpc.builder.syntaxtree.components.Expression.getImplicitNewExpressionNode;
import static io.ballerina.stdlib.grpc.builder.syntaxtree.components.Expression.getRemoteMethodCallActionNode;
import static io.ballerina.stdlib.grpc.builder.syntaxtree.components.Literal.getBooleanLiteralNode;
import static io.ballerina.stdlib.grpc.builder.syntaxtree.components.Literal.getNumericLiteralNode;
import static io.ballerina.stdlib.grpc.builder.syntaxtree.components.Literal.getStringLiteralNode;
import static io.ballerina.stdlib.grpc.builder.syntaxtree.components.Statement.getCallStatementNode;
import static io.ballerina.stdlib.grpc.builder.syntaxtree.components.Statement.getFunctionCallExpressionNode;
import static io.ballerina.stdlib.grpc.builder.syntaxtree.components.TypeDescriptor.getBuiltinSimpleNameReferenceNode;
import static io.ballerina.stdlib.grpc.builder.syntaxtree.components.TypeDescriptor.getCaptureBindingPatternNode;
import static io.ballerina.stdlib.grpc.builder.syntaxtree.components.TypeDescriptor.getSimpleNameReferenceNode;
import static io.ballerina.stdlib.grpc.builder.syntaxtree.components.TypeDescriptor.getStreamTypeDescriptorNode;
import static io.ballerina.stdlib.grpc.builder.syntaxtree.components.TypeDescriptor.getTypedBindingPatternNode;
import static io.ballerina.stdlib.grpc.builder.syntaxtree.constants.SyntaxTreeConstants.STREAMING_CLIENT;
import static io.ballerina.stdlib.grpc.builder.syntaxtree.utils.CommonUtils.capitalize;

/**
 * Syntax tree generation class for the client sample.
 */
public class ClientSampleSyntaxTreeUtils {

    public static SyntaxTree generateSyntaxTreeForClientSample(ServiceStub serviceStub, String filename,
                                                               Map<String, Message> msgMap) {
        NodeList<ModuleMemberDeclarationNode> moduleMembers = AbstractNodeFactory.createEmptyNodeList();
        NodeList<ImportDeclarationNode> imports = AbstractNodeFactory.createNodeList(
                Imports.getImportDeclarationNode(ORG_NAME, "grpc"),
                Imports.getImportDeclarationNode(ORG_NAME, "io"));

        Function main = new Function("main");
        main.addQualifiers(new String[]{"public"});
        main.addReturns(SyntaxTreeConstants.SYNTAX_TREE_GRPC_ERROR_OPTIONAL);
        ModuleVariable clientEp = new ModuleVariable(
                getTypedBindingPatternNode(
                        getSimpleNameReferenceNode(serviceStub.getServiceName() + "Client"),
                        getCaptureBindingPatternNode("ep")
                ),
                getCheckExpressionNode(
                        getImplicitNewExpressionNode("\"http://localhost:9090\"")
                )
        );

        if (serviceStub.getUnaryFunctions().size() > 0) {
            addUnaryCallMethodBody(main, serviceStub.getUnaryFunctions().get(0), filename, msgMap);
        } else if (serviceStub.getServerStreamingFunctions().size() > 0) {
            addServerStreamingCallMethodBody(main, serviceStub.getServerStreamingFunctions().get(0), filename, msgMap);
        } else if (serviceStub.getClientStreamingFunctions().size() > 0 ||
                serviceStub.getBidiStreamingFunctions().size() > 0) {
            addStreamingCallMethodBody(main, serviceStub.getClientStreamingFunctions().get(0), filename, msgMap);
        }

        moduleMembers = moduleMembers.add(clientEp.getModuleVariableDeclarationNode());
        moduleMembers = moduleMembers.add(main.getFunctionDefinitionNode());

        Token eofToken = AbstractNodeFactory.createIdentifierToken("");
        ModulePartNode modulePartNode = NodeFactory.createModulePartNode(imports, moduleMembers, eofToken);
        TextDocument textDocument = TextDocuments.from("");
        SyntaxTree syntaxTree = SyntaxTree.from(textDocument);
        return syntaxTree.modifyWith(modulePartNode);
    }

    private static void addUnaryCallMethodBody(Function main, Method method, String filename,
                                               Map<String, Message> msgMap) {
        main.addVariableStatement(getInputDeclarationStatement(method, filename, msgMap));
        main.addVariableStatement(getUnaryCallDeclarationNode(method, filename));
        main.addExpressionStatement(getPrintlnStatement("response"));
    }

    private static void addServerStreamingCallMethodBody(Function main, Method method, String filename,
                                                         Map<String, Message> msgMap) {
        main.addVariableStatement(getInputDeclarationStatement(method, filename, msgMap));
        main.addVariableStatement(getServerStreamingCallDeclarationNode(method, filename));
        main.addExpressionStatement(getForEachExpressionNode(method, filename));
    }

    private static void addStreamingCallMethodBody(Function main, Method method, String filename,
                                                   Map<String, Message> msgMap) {
        main.addVariableStatement(getInputDeclarationStatement(method, filename, msgMap));
        main.addVariableStatement(getClientStreamingCallDeclarationNode(method));
        main.addExpressionStatement(getStreamSendValueStatementNode(method));
        main.addExpressionStatement(getStreamCompleteStatementNode());
        main.addVariableStatement(getStreamReceiveValueStatementNode(method, filename));
        main.addExpressionStatement(getPrintlnStatement("response"));
    }

    private static VariableDeclarationNode getUnaryCallDeclarationNode(Method method, String filename) {
        TypedBindingPatternNode bindingPatternNode = getTypedBindingPatternNode(
                getBuiltinSimpleNameReferenceNode(method.getOutputPackageType(filename) + method.getOutputType() + " "),
                getCaptureBindingPatternNode("response"));
        ExpressionNode node = getCheckExpressionNode(getRemoteMethodCallActionNode(
                getBuiltinSimpleNameReferenceNode("ep"), method.getMethodName(), "request"));
        VariableDeclaration unaryCallVariable = new VariableDeclaration(bindingPatternNode, node);
        return unaryCallVariable.getVariableDeclarationNode();
    }

    private static VariableDeclarationNode getClientStreamingCallDeclarationNode(Method method) {
        TypedBindingPatternNode bindingPatternNode = getTypedBindingPatternNode(
                getBuiltinSimpleNameReferenceNode(
                        capitalize(method.getMethodName()) + STREAMING_CLIENT + " "),
                getCaptureBindingPatternNode("streamingClient"));
        ExpressionNode node = getCheckExpressionNode(getRemoteMethodCallActionNode(
                getBuiltinSimpleNameReferenceNode("ep"), method.getMethodName()));
        VariableDeclaration streamingCallVariable = new VariableDeclaration(bindingPatternNode, node);
        return streamingCallVariable.getVariableDeclarationNode();
    }

    private static ExpressionStatementNode getForEachExpressionNode(Method method, String filename) {
        FunctionSignatureNode functionSignatureNode = NodeFactory.createFunctionSignatureNode(
                SyntaxTreeConstants.SYNTAX_TREE_OPEN_PAREN,
                AbstractNodeFactory.createSeparatedNodeList(getTypedBindingPatternNode(
                        NodeFactory.createSimpleNameReferenceNode(AbstractNodeFactory.createIdentifierToken(
                                method.getOutputPackageType(filename) + method.getOutputType() + " ")),
                        getCaptureBindingPatternNode("value"))), SyntaxTreeConstants.SYNTAX_TREE_CLOSE_PAREN, null);
        AnonymousFunctionExpressionNode functionExpressionNode = NodeFactory
                .createExplicitAnonymousFunctionExpressionNode(AbstractNodeFactory.createEmptyNodeList(),
                        AbstractNodeFactory.createEmptyNodeList(), SyntaxTreeConstants.SYNTAX_TREE_KEYWORD_FUNCTION,
                        functionSignatureNode, NodeFactory.createFunctionBodyBlockNode(
                                SyntaxTreeConstants.SYNTAX_TREE_OPEN_BRACE,
                                null, AbstractNodeFactory.createNodeList(
                                        getPrintlnStatement("value")),
                                SyntaxTreeConstants.SYNTAX_TREE_CLOSE_BRACE));
        MethodCallExpressionNode methodCallExpressionNode = NodeFactory.createMethodCallExpressionNode(
                getSimpleNameReferenceNode("response"),
                SyntaxTreeConstants.SYNTAX_TREE_DOT,
                getSimpleNameReferenceNode("forEach"),
                SyntaxTreeConstants.SYNTAX_TREE_OPEN_PAREN,
                AbstractNodeFactory.createSeparatedNodeList(
                        NodeFactory.createPositionalArgumentNode(functionExpressionNode)),
                SyntaxTreeConstants.SYNTAX_TREE_CLOSE_PAREN);
        return NodeFactory.createExpressionStatementNode(SyntaxKind.CALL_STATEMENT,
                getCheckExpressionNode(methodCallExpressionNode), SyntaxTreeConstants.SYNTAX_TREE_SEMICOLON);
    }

    private static VariableDeclarationNode getServerStreamingCallDeclarationNode(Method method, String filename) {
        TypedBindingPatternNode bindingPatternNode = getTypedBindingPatternNode(
                getStreamTypeDescriptorNode(getSimpleNameReferenceNode(method.getOutputPackageType(filename) +
                        method.getOutputType()), SyntaxTreeConstants.SYNTAX_TREE_GRPC_ERROR_OPTIONAL),
                getCaptureBindingPatternNode("response"));
        ExpressionNode node = getCheckExpressionNode(getRemoteMethodCallActionNode(
                getBuiltinSimpleNameReferenceNode("ep"), method.getMethodName(), "request"));
        VariableDeclaration streamingCallVariable = new VariableDeclaration(bindingPatternNode, node);
        return streamingCallVariable.getVariableDeclarationNode();
    }

    private static ExpressionStatementNode getStreamSendValueStatementNode(Method method) {
        ExpressionNode node = getCheckExpressionNode(getRemoteMethodCallActionNode(
                getBuiltinSimpleNameReferenceNode("ep"),
                "send" + capitalize(method.getInputType()), "request"));
        return NodeFactory.createExpressionStatementNode(SyntaxKind.CALL_STATEMENT, node,
                SyntaxTreeConstants.SYNTAX_TREE_SEMICOLON);
    }

    private static ExpressionStatementNode getStreamCompleteStatementNode() {
        ExpressionNode node = getCheckExpressionNode(getRemoteMethodCallActionNode(
                getBuiltinSimpleNameReferenceNode("ep"), "complete"));
        return NodeFactory.createExpressionStatementNode(SyntaxKind.CALL_STATEMENT, node,
                SyntaxTreeConstants.SYNTAX_TREE_SEMICOLON);
    }

    private static VariableDeclarationNode getStreamReceiveValueStatementNode(Method method, String filename) {
        TypedBindingPatternNode bindingPatternNode = getTypedBindingPatternNode(
                getSimpleNameReferenceNode(method.getOutputPackageType(filename) + method.getOutputType() + " "),
                getCaptureBindingPatternNode("response"));
        ExpressionNode checkExpressionNode = getCheckExpressionNode(getRemoteMethodCallActionNode(
                getSimpleNameReferenceNode("streamingClient"),"receive" + capitalize(method.getOutputType())));
        VariableDeclaration streamingCallVariable = new VariableDeclaration(bindingPatternNode, checkExpressionNode);
        return streamingCallVariable.getVariableDeclarationNode();
    }

    private static VariableDeclarationNode getInputDeclarationStatement(Method method, String filename,
                                                                        Map<String, Message> msgMap) {
        TypedBindingPatternNode bindingPatternNode = getTypedBindingPatternNode(
                NodeFactory.createSimpleNameReferenceNode(AbstractNodeFactory.createIdentifierToken(
                        method.getInputPackagePrefix(filename) + method.getInputType() + " ")),
                getCaptureBindingPatternNode("request"));
        ExpressionNode node = null;
        switch (method.getInputType()) {
            case "int":
            case "float":
            case "decimal":
                node = getNumericLiteralNode(1);
                break;
            case "boolean":
                node = getBooleanLiteralNode(true);
                break;
            case "string":
                node = getStringLiteralNode("Hello");
                break;
            case "byte[]":
                node = getStringLiteralNode("[72,101,108,108,111]");
                break;
            case "Timestamp":
                node = getStringLiteralNode("[1659688553,0.310073000d]");
                break;
            case "Duration":
                node = getStringLiteralNode("0.310073000d");
                break;
            case "Struct":
                node = getStringLiteralNode("{}");
                break;
            case "'any:Any":
                node = getStringLiteralNode("\"Hello\"");
                break;
            default:
                if (msgMap.containsKey(method.getInputType())) {
                    Message msg = msgMap.get(method.getInputType());
                    node = NodeFactory.createMappingConstructorExpressionNode(
                            SyntaxTreeConstants.SYNTAX_TREE_OPEN_BRACE,
                            NodeFactory.createSeparatedNodeList(getFieldNodes(msg, msgMap)),
                            SyntaxTreeConstants.SYNTAX_TREE_CLOSE_BRACE);
                }
        }
        VariableDeclaration valueVariable = new VariableDeclaration(bindingPatternNode, node);
        return valueVariable.getVariableDeclarationNode();
    }

    private static ArrayList<Node> getFieldNodes(Message message, Map<String, Message> msgMap) {
        ArrayList<Node> nodes = new ArrayList<>();
        for (Field field : message.getFieldList()) {
            nodes.add(NodeFactory.createFieldMatchPatternNode(
                    AbstractNodeFactory.createIdentifierToken(
                            field.getFieldName() + " "), SyntaxTreeConstants.SYNTAX_TREE_COLON,
                    getFieldPatternNode(field, msgMap)));
            nodes.add(NodeFactory.createCaptureBindingPatternNode(SyntaxTreeConstants.SYNTAX_TREE_COMMA));
        }
        nodes.remove(nodes.size() - 1);
        return nodes;
    }

    private static Node getFieldPatternNode(Field field, Map<String, Message> msgMap) {
        switch (field.getFieldType()) {
            case "int":
            case "float":
            case "decimal":
                return getCaptureBindingPatternNode("1");
            case "boolean":
                return getCaptureBindingPatternNode("true");
            case "byte[]":
                return getCaptureBindingPatternNode("[72,101,108,108,111]");
            case "Timestamp":
                return getCaptureBindingPatternNode("[1659688553,0.310073000d]");
            case "Duration":
                return getCaptureBindingPatternNode("0.310073000d");
            case "Struct":
                return getCaptureBindingPatternNode("{}");
            case "string":
            case "'any:Any":
                return getCaptureBindingPatternNode("\"Hello\"");
            default:
                if (msgMap.containsKey(field.getFieldType())) {
                    Message msg = msgMap.get(field.getFieldType());
                    ArrayList<Node> subRecordNodes = getFieldNodes(msg, msgMap);
                    return NodeFactory.createMappingConstructorExpressionNode(
                            SyntaxTreeConstants.SYNTAX_TREE_OPEN_BRACE,
                            NodeFactory.createSeparatedNodeList(subRecordNodes),
                            SyntaxTreeConstants.SYNTAX_TREE_CLOSE_BRACE);
                }
                return getCaptureBindingPatternNode("\"\"");
        }
    }

    private static ExpressionStatementNode getPrintlnStatement(String input) {
        return getCallStatementNode(getFunctionCallExpressionNode("io", "println", input));
    }
}
