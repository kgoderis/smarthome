package org.eclipse.smarthome.model.script.engine.automation;

import java.util.Map;

import org.eclipse.smarthome.automation.Trigger;
import org.eclipse.smarthome.automation.handler.BaseModuleHandler;
import org.eclipse.smarthome.automation.handler.RuleEngineCallback;
import org.eclipse.smarthome.automation.handler.TriggerHandler;

/**
 * Trigger Handler that is solely used to trigger the execution of a Rule
 *
 * @author Karel Goderis - Intial Contribution
 */
public class ExecuteScriptTriggerHandler extends BaseModuleHandler<Trigger>implements TriggerHandler {
    private RuleEngineCallback ruleCallback;

    public ExecuteScriptTriggerHandler(Trigger module) {
        super(module);
    }

    public void trigger(Map<String, ?> context) {
        ruleCallback.triggered(module, context);
    }

    String getTriggerID() {
        return module.getId();
    }

    @Override
    public void setRuleEngineCallback(RuleEngineCallback ruleCallback) {
        this.ruleCallback = ruleCallback;
    }

}
