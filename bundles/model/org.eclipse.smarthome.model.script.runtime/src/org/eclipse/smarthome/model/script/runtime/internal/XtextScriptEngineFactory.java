package org.eclipse.smarthome.model.script.runtime.internal;

import java.util.ArrayList;
import java.util.List;

import javax.script.ScriptEngine;
import javax.script.ScriptEngineFactory;

import org.eclipse.smarthome.model.script.runtime.internal.engine.XtextScriptEngine;

public class XtextScriptEngineFactory implements ScriptEngineFactory {

    @Override
    public String getEngineName() {
        return "Xtext Script Engine";
    }

    @Override
    public String getEngineVersion() {
        return "1.0.0";
    }

    @Override
    public List<String> getExtensions() {
        ArrayList<String> extList = new ArrayList<String>();
        extList.add("script");
        return extList;
    }

    @Override
    public List<String> getMimeTypes() {
        ArrayList<String> extList = new ArrayList<String>();
        extList.add("eclipse/script");
        extList.add("eclipse/scriptfile");
        extList.add("eclipse/rule");
        return extList;
    }

    @Override
    public List<String> getNames() {
        ArrayList<String> extList = new ArrayList<String>();
        extList.add("xtext");
        return extList;
    }

    @Override
    public String getLanguageName() {
        return "Eclipse Smarthome Script Language";
    }

    @Override
    public String getLanguageVersion() {
        return "1.0.0";
    }

    @Override
    public Object getParameter(String key) {
        if (key == ScriptEngine.ENGINE) {
            return this.getEngineName();
        } else if (key == ScriptEngine.ENGINE_VERSION) {
            return this.getEngineVersion();
        } else if (key == ScriptEngine.NAME) {
            return this.getNames();
        } else if (key == ScriptEngine.LANGUAGE) {
            return this.getLanguageName();
        } else if (key == ScriptEngine.LANGUAGE_VERSION) {
            return this.getLanguageVersion();
        } else {
            return null;
        }
    }

    @Override
    public String getMethodCallSyntax(String obj, String m, String... args) {
        String ret = obj;
        ret += "." + m + "(";
        for (int i = 0; i < args.length; i++) {
            ret += args[i];
            if (i < args.length - 1) {
                ret += ",";
            }
        }
        ret += ")";
        return ret;
    }

    @Override
    public String getOutputStatement(String toDisplay) {
        return "logInfo(\"eclipse\"," + toDisplay + ")";
    }

    @Override
    public String getProgram(String... statements) {
        return "";
    }

    @Override
    public ScriptEngine getScriptEngine() {
        return new XtextScriptEngine();
    }

}
