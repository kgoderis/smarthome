package org.eclipse.smarthome.automation.module.script;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

import javax.script.Bindings;
import javax.script.ScriptContext;
import javax.script.SimpleBindings;
import javax.script.SimpleScriptContext;

public class ModuleScriptContext extends SimpleScriptContext {

    public static final int MODULE_SCOPE = 300;

    protected Bindings moduleBindings;
    private static List<Integer> scopes;

    public ModuleScriptContext() {
        super();
        moduleBindings = new SimpleBindings();
    }

    public ModuleScriptContext(ScriptContext context) {
        this();
        setErrorWriter(context.getErrorWriter());
        setWriter(context.getWriter());
        setReader(context.getReader());
        setBindings(context.getBindings(ScriptContext.ENGINE_SCOPE), ScriptContext.ENGINE_SCOPE);
        setBindings(context.getBindings(ScriptContext.GLOBAL_SCOPE), ScriptContext.GLOBAL_SCOPE);
    }

    @Override
    public void setBindings(Bindings bindings, int scope) {
        if (scope == MODULE_SCOPE) {
            moduleBindings = bindings;
        } else {
            super.setBindings(bindings, scope);
        }
    }

    @Override
    public Bindings getBindings(int scope) {
        if (scope == MODULE_SCOPE) {
            return moduleBindings;
        } else {
            return super.getBindings(scope);
        }
    }

    @Override
    public Object getAttribute(java.lang.String name, int scope) {
        if (scope == MODULE_SCOPE) {
            return moduleBindings.get(name);
        } else {
            return super.getAttribute(name, scope);
        }
    }

    @Override
    public Object removeAttribute(java.lang.String name, int scope) {
        if (scope == MODULE_SCOPE) {
            return moduleBindings.remove(name);
        } else {
            return super.removeAttribute(name, scope);
        }
    }

    @Override
    public void setAttribute(java.lang.String name, java.lang.Object value, int scope) {
        if (scope == MODULE_SCOPE) {
            moduleBindings.put(name, value);
        } else {
            super.setAttribute(name, value, scope);
        }
    }

    @Override
    public List<Integer> getScopes() {
        return scopes;
    }

    static {
        scopes = new ArrayList<Integer>(3);
        scopes.add(GLOBAL_SCOPE);
        scopes.add(ENGINE_SCOPE);
        scopes.add(MODULE_SCOPE);
        scopes = Collections.unmodifiableList(scopes);
    }

}
