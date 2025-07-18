/*
 * Copyright 2024, Seqera Labs
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package nextflow.script.control;

import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

import nextflow.script.ast.AssignmentExpression;
import nextflow.script.ast.FeatureFlagNode;
import nextflow.script.ast.FunctionNode;
import nextflow.script.ast.IncludeNode;
import nextflow.script.ast.OutputBlockNode;
import nextflow.script.ast.ParamNode;
import nextflow.script.ast.ProcessNode;
import nextflow.script.ast.ScriptNode;
import nextflow.script.ast.ScriptVisitorSupport;
import nextflow.script.ast.WorkflowNode;
import org.codehaus.groovy.ast.ASTNode;
import org.codehaus.groovy.ast.CodeVisitorSupport;
import org.codehaus.groovy.ast.VariableScope;
import org.codehaus.groovy.ast.expr.ArgumentListExpression;
import org.codehaus.groovy.ast.expr.BinaryExpression;
import org.codehaus.groovy.ast.expr.ClosureExpression;
import org.codehaus.groovy.ast.expr.ConstructorCallExpression;
import org.codehaus.groovy.ast.expr.EmptyExpression;
import org.codehaus.groovy.ast.expr.Expression;
import org.codehaus.groovy.ast.expr.MethodCallExpression;
import org.codehaus.groovy.ast.expr.PropertyExpression;
import org.codehaus.groovy.ast.expr.TupleExpression;
import org.codehaus.groovy.ast.expr.VariableExpression;
import org.codehaus.groovy.ast.stmt.BlockStatement;
import org.codehaus.groovy.ast.stmt.EmptyStatement;
import org.codehaus.groovy.ast.stmt.ExpressionStatement;
import org.codehaus.groovy.ast.stmt.ReturnStatement;
import org.codehaus.groovy.ast.stmt.Statement;
import org.codehaus.groovy.control.SourceUnit;
import org.codehaus.groovy.runtime.DefaultGroovyMethods;
import org.codehaus.groovy.syntax.SyntaxException;
import org.codehaus.groovy.syntax.Types;

import static nextflow.script.ast.ASTUtils.*;
import static org.codehaus.groovy.ast.tools.GeneralUtils.*;

/**
 * Transform a Nextflow script AST into a Groovy AST.
 *
 * @see nextflow.script.BaseScript
 *
 * @author Ben Sherman <bentshermann@gmail.com>
 */
public class ScriptToGroovyVisitor extends ScriptVisitorSupport {

    private static Set<String> RESERVED_NAMES = Set.of("main", "run", "runScript");

    private SourceUnit sourceUnit;

    private ScriptNode moduleNode;

    public ScriptToGroovyVisitor(SourceUnit sourceUnit) {
        this.sourceUnit = sourceUnit;
        this.moduleNode = (ScriptNode) sourceUnit.getAST();
    }

    @Override
    protected SourceUnit getSourceUnit() {
        return sourceUnit;
    }

    public void visit() {
        if( moduleNode == null )
            return;
        super.visit(moduleNode);
        if( moduleNode.isEmpty() )
            moduleNode.addStatement(ReturnStatement.RETURN_NULL_OR_VOID);
    }

    @Override
    public void visitFeatureFlag(FeatureFlagNode node) {
        var names = node.name.split("\\.");
        Expression target = varX(DefaultGroovyMethods.head(names));
        for( var name : DefaultGroovyMethods.tail(names) )
            target = propX(target, name);

        var result = stmt(assignX(target, node.value));
        moduleNode.addStatement(result);
    }

    @Override
    public void visitInclude(IncludeNode node) {
        var entries = (List<Expression>) node.entries.stream()
            .map((entry) -> {
                var name = constX(entry.name);
                return entry.alias != null
                    ? createX("nextflow.script.IncludeDef.Module", args(name, constX(entry.alias)))
                    : createX("nextflow.script.IncludeDef.Module", args(name));
            })
            .collect(Collectors.toList());

        var include = callThisX("include", args(createX("nextflow.script.IncludeDef", args(listX(entries)))));
        var from = callX(include, "from", args(node.source));
        var result = stmt(callX(from, "load0", args(varX("params"))));
        moduleNode.addStatement(result);
    }

    @Override
    public void visitParam(ParamNode node) {
        var result = stmt(assignX(node.target, node.value));
        moduleNode.addStatement(result);
    }

    @Override
    public void visitWorkflow(WorkflowNode node) {
        visitWorkflowTakes(node.takes);
        visit(node.main);
        visitWorkflowEmits(node.emits, node.main);
        visitWorkflowPublishers(node.publishers, node.main);

        var bodyDef = stmt(createX(
            "nextflow.script.BodyDef",
            args(
                closureX(null, node.main),
                constX(getSourceText(node)),
                constX("workflow")
            )
        ));
        var closure = closureX(null, block(new VariableScope(), List.of(
            node.takes,
            node.emits,
            bodyDef
        )));
        var arguments = node.isEntry()
            ? args(closure)
            : args(constX(node.getName()), closure);
        var result = stmt(callThisX("workflow", arguments));
        moduleNode.addStatement(result);
    }

    private void visitWorkflowTakes(Statement takes) {
        for( var stmt : asBlockStatements(takes) ) {
            var es = (ExpressionStatement)stmt;
            var take = (VariableExpression)es.getExpression();
            es.setExpression(callThisX("_take_", args(constX(take.getName()))));
        }
    }

    private void visitWorkflowEmits(Statement emits, Statement main) {
        var code = (BlockStatement)main;
        for( var stmt : asBlockStatements(emits) ) {
            var es = (ExpressionStatement)stmt;
            var emit = es.getExpression();
            if( emit instanceof VariableExpression ve ) {
                es.setExpression(callThisX("_emit_", args(constX(ve.getName()))));
            }
            else if( emit instanceof AssignmentExpression ae ) {
                var target = (VariableExpression)ae.getLeftExpression();
                code.addStatement(assignS(target, emit));
                es.setExpression(callThisX("_emit_", args(constX(target.getName()))));
                code.addStatement(es);
            }
            else {
                var target = varX("$out");
                code.addStatement(assignS(target, emit));
                es.setExpression(callThisX("_emit_", args(constX(target.getName()))));
                code.addStatement(es);
            }
        }
    }

    private void visitWorkflowPublishers(Statement publishers, Statement main) {
        var code = (BlockStatement)main;
        for( var stmt : asBlockStatements(publishers) ) {
            var es = (ExpressionStatement)stmt;
            var publish = (BinaryExpression)es.getExpression();
            var target = asVarX(publish.getLeftExpression());
            es.setExpression(callThisX("_publish_", args(constX(target.getName()), publish.getRightExpression())));
            code.addStatement(es);
        }
    }

    @Override
    public void visitProcess(ProcessNode node) {
        visitProcessDirectives(node.directives);
        visitProcessInputs(node.inputs);
        visitProcessOutputs(node.outputs);
        visit(node.exec);
        visit(node.stub);

        if( "script".equals(node.type) )
            node.exec.visit(new TaskCmdXformVisitor(sourceUnit));
        node.stub.visit(new TaskCmdXformVisitor(sourceUnit));

        var when = processWhen(node.when);
        var bodyDef = stmt(createX(
            "nextflow.script.BodyDef",
            args(
                closureX(null, node.exec),
                constX(getSourceText(node.exec)),
                constX(node.type)
            )
        ));
        var stub = processStub(node.stub);
        var closure = closureX(null, block(new VariableScope(), List.of(
            node.directives,
            node.inputs,
            node.outputs,
            when,
            stub,
            bodyDef
        )));
        var result = stmt(callThisX("process", args(constX(node.getName()), closure)));
        moduleNode.addStatement(result);
    }

    private void visitProcessDirectives(Statement directives) {
        asDirectives(directives).forEach((call) -> {
            fixLazyGString(call);
        });
    }

    private void visitProcessInputs(Statement inputs) {
        asDirectives(inputs).forEach((call) -> {
            fixLazyGString(call);

            var name = call.getMethodAsString();
            varToConstX(call.getArguments(), "tuple".equals(name), "each".equals(name));
            call.setMethod( constX("_in_" + name) );
        });
    }

    private void visitProcessOutputs(Statement outputs) {
        asDirectives(outputs).forEach((call) -> {
            fixLazyGString(call);

            var name = call.getMethodAsString();
            varToConstX(call.getArguments(), "tuple".equals(name), "each".equals(name));
            call.setMethod( constX("_out_" + name) );
            visitProcessOutputEmitAndTopic(call);
        });
    }

    private static final List<String> EMIT_AND_TOPIC = List.of("emit", "topic");

    private void visitProcessOutputEmitAndTopic(MethodCallExpression output) {
        var namedArgs = asNamedArgs(output);
        for( int i = 0; i < namedArgs.size(); i++ ) {
            var entry = namedArgs.get(i);
            var key = asConstX(entry.getKeyExpression());
            var value = asVarX(entry.getValueExpression());
            if( value != null && key != null && EMIT_AND_TOPIC.contains(key.getText()) ) {
                namedArgs.set(i, entryX(key, constX(value.getText())));
            }
        }
    }

    private void fixLazyGString(Expression node) {
        new GStringToLazyVisitor(sourceUnit).visit(node);
    }

    private Expression varToConstX(Expression node, boolean withinTuple, boolean withinEach) {
        if( node instanceof TupleExpression te ) {
            var arguments = te.getExpressions();
            for( int i = 0; i < arguments.size(); i++ )
                arguments.set(i, varToConstX(arguments.get(i), withinTuple, withinEach));
            return te;
        }

        if( node instanceof VariableExpression ve ) {
            var name = ve.getName();

            if( "stdin".equals(name) && withinTuple )
                return createX( "nextflow.script.TokenStdinCall" );

            if ( "stdout".equals(name) && withinTuple )
                return createX( "nextflow.script.TokenStdoutCall" );

            return createX( "nextflow.script.TokenVar", constX(name) );
        }

        if( node instanceof MethodCallExpression mce ) {
            var name = mce.getMethodAsString();
            var arguments = mce.getArguments();

            if( "env".equals(name) && withinTuple )
                return createX( "nextflow.script.TokenEnvCall", (TupleExpression) varToStrX(arguments) );

            if( "eval".equals(name) && withinTuple )
                return createX( "nextflow.script.TokenEvalCall", (TupleExpression) varToStrX(arguments) );

            if( "file".equals(name) && (withinTuple || withinEach) )
                return createX( "nextflow.script.TokenFileCall", (TupleExpression) varToConstX(arguments, withinTuple, withinEach) );

            if( "path".equals(name) && (withinTuple || withinEach) )
                return createX( "nextflow.script.TokenPathCall", (TupleExpression) varToConstX(arguments, withinTuple, withinEach) );

            if( "val".equals(name) && withinTuple )
                return createX( "nextflow.script.TokenValCall", (TupleExpression) varToStrX(arguments) );
        }

        if( node instanceof PropertyExpression ) {
            // before:
            //   val( x.foo )
            // after:
            //   val({ x.foo })
            return wrapExpressionInClosure(node);
        }

        return node;
    }

    private Expression varToStrX(Expression node) {
        if( node instanceof TupleExpression te ) {
            var arguments = te.getExpressions();
            for( int i = 0; i < arguments.size(); i++ )
                arguments.set(i, varToStrX(arguments.get(i)));
            return te;
        }

        if( node instanceof VariableExpression ve ) {
            // before:
            //   val(x)
            // after:
            //   val(TokenVar('x'))
            var name = ve.getName();
            return createX( "nextflow.script.TokenVar", constX(name) );
        }

        if( node instanceof PropertyExpression ) {
            // before:
            //   tuple val( x.foo )
            // after:
            //   tuple val({ x.foo })
            return wrapExpressionInClosure(node);
        }

        return node;
    }

    protected ClosureExpression wrapExpressionInClosure(Expression node)  {
        return closureX(null, block(stmt(node)));
    }

    private Statement processWhen(Expression when) {
        if( when instanceof EmptyExpression )
            return EmptyStatement.INSTANCE;
        return stmt(callThisX("when", createX(
            "nextflow.script.TaskClosure",
            args(
                wrapExpressionInClosure(when),
                constX(getSourceText(when))
            )
        )));
    }

    private Statement processStub(Statement stub) {
        if( stub instanceof EmptyStatement )
            return EmptyStatement.INSTANCE;
        return stmt(callThisX("stub", createX(
            "nextflow.script.TaskClosure",
            args(
                closureX(null, stub),
                constX(getSourceText(stub))
            )
        )));
    }

    @Override
    public void visitFunction(FunctionNode node) {
        if( RESERVED_NAMES.contains(node.getName()) ) {
            syntaxError(node, "`${node.getName()}` is not allowed as a function name because it is reserved for internal use");
            return;
        }
        moduleNode.getScriptClassDummy().addMethod(node);
    }

    @Override
    public void visitOutputs(OutputBlockNode node) {
        var statements = node.declarations.stream()
            .map((output) -> {
                new PublishDslVisitor().visit(output.body);
                var name = constX(output.name);
                var body = closureX(null, output.body);
                return stmt(callThisX("declare", args(name, body)));
            })
            .toList();
        var closure = closureX(null, block(new VariableScope(), statements));
        var result = stmt(callThisX("output", args(closure)));
        moduleNode.addStatement(result);
    }

    private String getSourceText(Statement node) {
        var builder = new StringBuilder();
        var colx = node.getColumnNumber();
        var colz = node.getLastColumnNumber();
        var first = node.getLineNumber();
        var last = node.getLastLineNumber();
        for( int i = first; i <= last; i++ ) {
            var line = sourceUnit.getSource().getLine(i, null);

            // prepend first-line indent
            if( i == first ) {
                int k = 0;
                while( k < line.length() && line.charAt(k) == ' ' )
                    k++;
                builder.append( line.substring(0, k) );
            }

            var begin = (i == first) ? colx - 1 : 0;
            var end = (i == last) ? colz - 1 : line.length();
            builder.append( line.substring(begin, end) ).append('\n');
        }
        return builder.toString();
    }

    private String getSourceText(Expression node) {
        var stm = stmt(node);
        stm.setSourcePosition(node);
        return getSourceText(stm);
    }

    private String getSourceText(WorkflowNode node) {
        if( node.isEntry() && node.getLineNumber() == -1 )
            return getSourceText(node.main);

        var builder = new StringBuilder();
        var colx = node.getColumnNumber();
        var colz = node.getLastColumnNumber();
        var first = node.getLineNumber();
        var last = node.getLastLineNumber();
        for( int i = first; i <= last; i++ ) {
            var line = sourceUnit.getSource().getLine(i, null);
            if( i == last ) {
                line = line.substring(0, colz-1).replaceFirst("}.*$", "");
                if( line.trim().isEmpty() )
                    continue;
            }
            if( i == first ) {
                line = line.substring(colx-1).replaceFirst("^.*\\{", "").trim();
                if( line.isEmpty() )
                    continue;
            }
            builder.append(line).append('\n');
        }
        return builder.toString();
    }

    private void syntaxError(ASTNode node, String message) {
        sourceUnit.addError(new SyntaxException(message, node));
    }

    /**
     * Transform publish statements in a workflow output:
     *
     *   path { sample ->
     *     sample.foo >> 'foo/'
     *     sample.bar >> 'bar/'
     *   }
     *
     * becomes:
     *
     *   path { sample ->
     *     publish(sample.foo, 'foo/')
     *     publish(sample.bar, 'bar/')
     *   }
     */
    private class PublishDslVisitor extends CodeVisitorSupport {

        private boolean hasPublishStatements;

        private boolean hasNonPublishStatements;

        @Override
        public void visitMethodCallExpression(MethodCallExpression node) {
            if( "path".equals(node.getMethodAsString()) )
                visitPathDirective(node);
        }

        private void visitPathDirective(MethodCallExpression node) {
            var code = asDslBlock(node, 1);
            if( code == null )
                return;
            for( var stmt : code.getStatements() ) {
                if( visitPublishStatement(stmt) )
                    hasPublishStatements = true;
                else
                    hasNonPublishStatements = true;
            }
            if( hasPublishStatements && hasNonPublishStatements )
                syntaxError(node, "Publish statements cannot be mixed with other statements in a dynamic publish path");
        }

        private boolean visitPublishStatement(Statement node) {
            if( !(node instanceof ExpressionStatement) )
                return false;
            var es = (ExpressionStatement) node;
            if( !(es.getExpression() instanceof BinaryExpression) )
                return false;
            var be = (BinaryExpression) es.getExpression();
            if( be.getOperation().getType() != Types.RIGHT_SHIFT )
                return false;
            var source = be.getLeftExpression();
            var target = be.getRightExpression();
            es.setExpression(callThisX("publish", args(source, target)));
            return true;
        }
    }

}
