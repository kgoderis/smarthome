package org.eclipse.smarthome.model.script.runtime.internal.engine;

import static com.google.common.collect.Iterables.filter;

import java.io.IOException;
import java.io.Reader;
import java.util.List;

import javax.script.AbstractScriptEngine;
import javax.script.Bindings;
import javax.script.ScriptContext;
import javax.script.ScriptEngineFactory;
import javax.script.ScriptException;

import org.apache.commons.lang.StringUtils;
import org.eclipse.emf.common.notify.Adapter;
import org.eclipse.emf.common.util.EList;
import org.eclipse.emf.common.util.URI;
import org.eclipse.emf.ecore.EObject;
import org.eclipse.emf.ecore.resource.Resource;
import org.eclipse.emf.ecore.resource.Resource.Diagnostic;
import org.eclipse.emf.ecore.resource.ResourceSet;
import org.eclipse.emf.ecore.util.EContentAdapter;
import org.eclipse.smarthome.core.items.events.ItemCommandEvent;
import org.eclipse.smarthome.core.items.events.ItemStateChangedEvent;
import org.eclipse.smarthome.model.rule.RulesStandaloneSetup;
import org.eclipse.smarthome.model.rule.jvmmodel.RulesJvmModelInferrer;
import org.eclipse.smarthome.model.rule.rules.Rule;
import org.eclipse.smarthome.model.rule.rules.RuleModel;
import org.eclipse.smarthome.model.rule.rules.VariableDeclaration;
import org.eclipse.smarthome.model.rule.runtime.internal.engine.RuleEvaluationContext;
import org.eclipse.smarthome.model.script.engine.Script;
import org.eclipse.smarthome.model.script.engine.ScriptExecutionException;
import org.eclipse.smarthome.model.script.engine.ScriptParsingException;
import org.eclipse.smarthome.model.script.runtime.internal.ScriptRuntimeActivator;
import org.eclipse.smarthome.model.script.runtime.internal.ScriptRuntimeStandaloneSetup;
import org.eclipse.smarthome.model.script.runtime.internal.XtextScriptEngineFactory;
import org.eclipse.xtext.diagnostics.Severity;
import org.eclipse.xtext.naming.QualifiedName;
import org.eclipse.xtext.resource.XtextResource;
import org.eclipse.xtext.resource.XtextResourceSet;
import org.eclipse.xtext.util.CancelIndicator;
import org.eclipse.xtext.util.StringInputStream;
import org.eclipse.xtext.validation.CheckMode;
import org.eclipse.xtext.validation.IResourceValidator;
import org.eclipse.xtext.validation.Issue;
import org.eclipse.xtext.xbase.XExpression;
import org.eclipse.xtext.xbase.interpreter.IEvaluationContext;
import org.eclipse.xtext.xbase.interpreter.impl.DefaultEvaluationContext;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.google.common.base.Predicate;
import com.google.inject.Provider;

@SuppressWarnings("restriction")
public class XtextScriptEngine extends AbstractScriptEngine {

    private final Logger logger = LoggerFactory.getLogger(XtextScriptEngine.class);

    protected XtextResourceSet resourceSet;

    private XtextResourceSet getResourceSet() {
        if (resourceSet == null) {
            resourceSet = ScriptRuntimeStandaloneSetup.getInjector().getInstance(XtextResourceSet.class);
            resourceSet.addLoadOption(XtextResource.OPTION_RESOLVE_ALL, Boolean.TRUE);
        }
        return resourceSet;
    }

    @Override
    public Object eval(String scriptAsString, ScriptContext context) throws ScriptException {
        try {

            IEvaluationContext scriptContext = new DefaultEvaluationContext();

            XExpression expr = null;
            if (StringUtils.endsWith(scriptAsString, Script.SCRIPT_FILEEXT)) {
                expr = (XExpression) ScriptRuntimeActivator.getModelRepository().getModel(scriptAsString);
            } else if (StringUtils.contains(scriptAsString, ".rules.")) {
                String modelName = StringUtils.substringBefore(scriptAsString, ".rules.") + ".rules";
                String ruleName = StringUtils.substringAfter(scriptAsString, ".rules.");
                RuleModel model = (RuleModel) ScriptRuntimeActivator.getModelRepository().getModel(modelName);
                for (Rule aRule : model.getRules()) {
                    if (aRule.getName().equals(ruleName)) {
                        expr = aRule.getScript();
                        scriptContext = new RuleEvaluationContext();
                        ((RuleEvaluationContext) scriptContext).setGlobalContext(getContext(aRule));
                        break;
                    }
                }
            } else {
                expr = parseScriptIntoXTextEObject(scriptAsString);
            }

            // copy over the context
            for (Integer scope : context.getScopes()) {
                for (String binding : context.getBindings(scope).keySet()) {
                    String[] segments = StringUtils.split(binding, ".");
                    if (binding.contains(".event")) {
                        Object value = context.getBindings(scope).get(binding);
                        if (value instanceof ItemCommandEvent) {
                            ItemCommandEvent event = (ItemCommandEvent) value;
                            if (scriptContext.getValue(
                                    QualifiedName.create(RulesJvmModelInferrer.VAR_RECEIVED_COMMAND)) != null) {
                                scriptContext.assignValue(
                                        QualifiedName.create(RulesJvmModelInferrer.VAR_RECEIVED_COMMAND),
                                        event.getItemCommand());
                            } else {
                                scriptContext.newValue(QualifiedName.create(RulesJvmModelInferrer.VAR_RECEIVED_COMMAND),
                                        event.getItemCommand());
                            }
                        } else if (value instanceof ItemStateChangedEvent) {
                            ItemStateChangedEvent event = (ItemStateChangedEvent) value;
                            if (scriptContext
                                    .getValue(QualifiedName.create(RulesJvmModelInferrer.VAR_PREVIOUS_STATE)) != null) {
                                scriptContext.assignValue(
                                        QualifiedName.create(RulesJvmModelInferrer.VAR_PREVIOUS_STATE),
                                        event.getOldItemState());

                            } else {
                                scriptContext.newValue(QualifiedName.create(RulesJvmModelInferrer.VAR_PREVIOUS_STATE),
                                        event.getOldItemState());
                            }
                        } else {
                            logger.warn(
                                    "Received a context variable of type {} that can not be mapped into the context of the script",
                                    value.getClass().getSimpleName());
                        }

                    } else {
                        scriptContext.newValue(QualifiedName.create(segments), context.getBindings(scope).get(binding));
                    }
                }
            }

            if (expr != null) {
                ScriptImpl script = ScriptRuntimeStandaloneSetup.getInjector().getInstance(ScriptImpl.class);
                script.setXExpression(expr);
                try {
                    return script.execute(scriptContext);
                } catch (ScriptExecutionException e) {
                    String msg = e.getCause().getMessage();
                    if (msg == null) {
                        logger.error("Error during the execution of scripted code : '{}'", e.getCause());
                    } else {
                        logger.error("Error during the execution of scripted code : '{}'", msg);
                    }
                }
            } else {
                return null;
            }
        } catch (ScriptParsingException ex) {
            throw new ScriptException(ex);
        }
        return null;
    }

    @Override
    public Object eval(Reader reader, ScriptContext context) throws ScriptException {
        return null;
    }

    @Override
    public Bindings createBindings() {
        // TODO Auto-generated method stub
        return null;
    }

    @Override
    public ScriptEngineFactory getFactory() {
        return new XtextScriptEngineFactory();
    }

    private XExpression parseScriptIntoXTextEObject(String scriptAsString) throws ScriptParsingException {
        XtextResourceSet resourceSet = getResourceSet();
        Resource resource = resourceSet.createResource(computeUnusedUri(resourceSet)); // IS-A XtextResource
        try {
            resource.load(new StringInputStream(scriptAsString), resourceSet.getLoadOptions());
        } catch (IOException e) {
            throw new ScriptParsingException(
                    "Unexpected IOException; from close() of a String-based ByteArrayInputStream, no real I/O; how is that possible???",
                    scriptAsString, e);
        }

        List<Diagnostic> errors = resource.getErrors();
        if (errors.size() != 0) {
            throw new ScriptParsingException("Failed to parse expression (due to managed SyntaxError/s)",
                    scriptAsString).addDiagnosticErrors(errors);
        }

        EList<EObject> contents = resource.getContents();

        if (!contents.isEmpty()) {
            Iterable<Issue> validationErrors = getValidationErrors(contents.get(0));
            if (!validationErrors.iterator().hasNext()) {
                return (XExpression) contents.get(0);
            } else {
                throw new ScriptParsingException("Failed to parse expression (due to managed ValidationError/s)",
                        scriptAsString).addValidationIssues(validationErrors);
            }
        } else {
            return null;
        }
    }

    protected URI computeUnusedUri(ResourceSet resourceSet) {
        String name = "__synthetic";
        final int MAX_TRIES = 1000;
        for (int i = 0; i < MAX_TRIES; i++) {
            // NOTE: The "filename extension" (".script") must match the file.extensions in the *.mwe2
            URI syntheticUri = URI.createURI(name + Math.random() + "." + Script.SCRIPT_FILEEXT);
            if (resourceSet.getResource(syntheticUri, false) == null) {
                return syntheticUri;
            }
        }
        throw new IllegalStateException();
    }

    protected List<Issue> validate(EObject model) {
        IResourceValidator validator = ((XtextResource) model.eResource()).getResourceServiceProvider()
                .getResourceValidator();
        return validator.validate(model.eResource(), CheckMode.ALL, CancelIndicator.NullImpl);
    }

    protected Iterable<Issue> getValidationErrors(final EObject model) {
        final List<Issue> validate = validate(model);
        Iterable<Issue> issues = filter(validate, new Predicate<Issue>() {
            @Override
            public boolean apply(Issue input) {
                return Severity.ERROR == input.getSeverity();
            }
        });
        return issues;
    }

    /**
     * Retrieves the evaluation context (= set of variables) for a rule. The context is shared with all rules in the
     * same model (= rule file).
     *
     * @param rule the rule to get the context for
     * @return the evaluation context
     */
    public synchronized IEvaluationContext getContext(Rule rule) {
        RuleModel ruleModel = (RuleModel) rule.eContainer();

        // check if a context already exists on the resource
        for (Adapter adapter : ruleModel.eAdapters()) {
            if (adapter instanceof RuleContextAdapter) {
                return ((RuleContextAdapter) adapter).getContext();
            }
        }
        Provider<IEvaluationContext> contextProvider = RulesStandaloneSetup.getInjector()
                .getProvider(IEvaluationContext.class);
        IEvaluationContext evaluationContext = contextProvider.get();
        for (VariableDeclaration var : ruleModel.getVariables()) {
            try {
                Object initialValue = null;
                if (var.getRight() != null) {
                    ScriptImpl script = ScriptRuntimeStandaloneSetup.getInjector().getInstance(ScriptImpl.class);
                    script.setXExpression(var.getRight());
                    initialValue = script.execute();
                }
                evaluationContext.newValue(QualifiedName.create(var.getName()), initialValue);
            } catch (ScriptExecutionException e) {
                logger.warn("Variable '{}' on rule file '{}' cannot be initialized with value '{}': {}",
                        new Object[] { var.getName(), ruleModel.eResource().getURI().path(), var.getRight().toString(),
                                e.getMessage() });
            }
        }
        ruleModel.eAdapters().add(new RuleContextAdapter(evaluationContext));
        return evaluationContext;
    }

    /**
     * Inner class that wraps an evaluation context into an EMF adapters
     */
    private static class RuleContextAdapter extends EContentAdapter {

        private IEvaluationContext context;

        public RuleContextAdapter(IEvaluationContext context) {
            this.context = context;
        }

        public IEvaluationContext getContext() {
            return context;
        }

    }

}
