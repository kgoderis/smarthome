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
import org.eclipse.emf.common.util.EList;
import org.eclipse.emf.common.util.URI;
import org.eclipse.emf.ecore.EObject;
import org.eclipse.emf.ecore.resource.Resource;
import org.eclipse.emf.ecore.resource.Resource.Diagnostic;
import org.eclipse.emf.ecore.resource.ResourceSet;
import org.eclipse.smarthome.model.core.ModelRepository;
import org.eclipse.smarthome.model.script.engine.Script;
import org.eclipse.smarthome.model.script.engine.ScriptExecutionException;
import org.eclipse.smarthome.model.script.engine.ScriptParsingException;
import org.eclipse.smarthome.model.script.runtime.internal.ScriptRuntimeActivator;
import org.eclipse.smarthome.model.script.runtime.internal.ScriptRuntimeStandaloneSetup;
import org.eclipse.smarthome.model.script.runtime.internal.XtextScriptEngineFactory;
import org.eclipse.xtext.diagnostics.Severity;
import org.eclipse.xtext.resource.XtextResource;
import org.eclipse.xtext.resource.XtextResourceSet;
import org.eclipse.xtext.util.CancelIndicator;
import org.eclipse.xtext.util.StringInputStream;
import org.eclipse.xtext.validation.CheckMode;
import org.eclipse.xtext.validation.IResourceValidator;
import org.eclipse.xtext.validation.Issue;
import org.eclipse.xtext.xbase.XExpression;

import com.google.common.base.Predicate;

@SuppressWarnings("restriction")
public class XtextScriptEngine extends AbstractScriptEngine {

    protected XtextResourceSet resourceSet;
    // private ScriptRuntime scriptRuntime;

    // public void activate() {
    //
    // }

    private XtextResourceSet getResourceSet() {
        if (resourceSet == null) {
            resourceSet = ScriptRuntimeStandaloneSetup.getInjector().getInstance(XtextResourceSet.class);
            resourceSet.addLoadOption(XtextResource.OPTION_RESOLVE_ALL, Boolean.TRUE);
        }
        return resourceSet;
    }

    // public void deactivate() {
    // this.resourceSet = null;
    // }

    // protected void setScriptEngineManager(final ScriptRuntime scriptRuntime) {
    // this.scriptRuntime = scriptRuntime;
    // }
    //
    // protected void unsetScriptRuntime(final ScriptRuntime scriptRuntime) {
    // this.scriptRuntime = null;
    // }

    @Override
    public Object eval(String scriptAsString, ScriptContext context) throws ScriptException {
        try {
            ModelRepository repo = ScriptRuntimeActivator.modelRepositoryTracker.getService();
            XExpression expr;
            String scriptNameWithExt = scriptAsString;
            if (!StringUtils.endsWith(scriptAsString, Script.SCRIPT_FILEEXT)) {
                scriptNameWithExt = scriptAsString + "." + Script.SCRIPT_FILEEXT;
                expr = (XExpression) repo.getModel(scriptNameWithExt);
            } else {
                expr = parseScriptIntoXTextEObject(scriptAsString);
            }

            if (expr != null) {
                ScriptImpl script = ScriptRuntimeStandaloneSetup.getInjector().getInstance(ScriptImpl.class);
                script.setXExpression(expr);
                return script.execute();
            } else {
                return null;
            }
        } catch (ScriptParsingException | ScriptExecutionException ex) {
            throw new ScriptException(ex);
        }
    }

    @Override
    public Object eval(Reader reader, ScriptContext context) throws ScriptException {
        return null;
    }

    // /**
    // * {@inheritDoc}
    // */
    // @Override
    // public Script newScriptFromString(String scriptAsString) throws ScriptParsingException {
    // return newScriptFromXExpression(parseScriptIntoXTextEObject(scriptAsString));
    // }

    // /**
    // * {@inheritDoc}
    // */
    // @Override
    // public Script newScriptFromXExpression(XExpression expression) {
    // ScriptImpl script = ScriptRuntimeStandaloneSetup.getInjector().getInstance(ScriptImpl.class);
    // script.setXExpression(expression);
    // return script;
    // }
    //
    // /**
    // * {@inheritDoc}
    // */
    // @Override
    // public Object executeScript(String scriptAsString) throws ScriptParsingException, ScriptExecutionException {
    // return newScriptFromString(scriptAsString).execute();
    // }

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

}
