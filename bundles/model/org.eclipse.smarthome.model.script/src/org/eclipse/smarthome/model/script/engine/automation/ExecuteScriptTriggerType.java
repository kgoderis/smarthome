package org.eclipse.smarthome.model.script.engine.automation;

import java.util.ArrayList;
import java.util.List;

import org.eclipse.smarthome.automation.Visibility;
import org.eclipse.smarthome.automation.type.Output;
import org.eclipse.smarthome.automation.type.TriggerType;

public class ExecuteScriptTriggerType extends TriggerType {

    public static final String UID = "ExecuteScriptTriggerType";
    public static final String INPUT_EXPRESSION = "Expression";

    public static ExecuteScriptTriggerType initialize() {
        // Output state = new Output(INPUT_EXPRESSION, String.class.getName(), "Expression",
        // "Indicates the expression to parsed as a script", null, null, null);
        List<Output> output = new ArrayList<Output>();
        // output.add(state);
        return new ExecuteScriptTriggerType(output);
    }

    public ExecuteScriptTriggerType(List<Output> output) {
        super(UID, null, "Exececute Script Rule Trigger", "Template for creation of an Execeute Script Rule Trigger.",
                null, Visibility.VISIBLE, output);
    }
}
