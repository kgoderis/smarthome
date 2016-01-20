package org.eclipse.smarthome.model.script.engine.automation;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import org.eclipse.smarthome.automation.Action;
import org.eclipse.smarthome.automation.Condition;
import org.eclipse.smarthome.automation.Trigger;
import org.eclipse.smarthome.automation.Visibility;
import org.eclipse.smarthome.automation.template.RuleTemplate;
import org.eclipse.smarthome.config.core.ConfigDescriptionParameter;
import org.eclipse.smarthome.config.core.ConfigDescriptionParameter.Type;

public class ExecuteScriptRuleTemplate extends RuleTemplate {

    public static final String UID = "TRIGGER_ID";
    public static final String TRIGGER_ID = "ExcecuteScriptRuleTrigger";
    public static final String ACTION_ID = "ExcecuteScriptRuleAction";

    public static ExecuteScriptRuleTemplate initialize() {

        ArrayList<Trigger> triggers = new ArrayList<Trigger>();
        triggers.add(new Trigger(TRIGGER_ID, ExecuteScriptTriggerType.UID, null));

        Map<String, String> actionConfig = new HashMap<String, String>();
        actionConfig.put(ExecuteScriptRuleProvider.CONFIG_SCRIPT_TYPE, "$" + ExecuteScriptRuleProvider.CONFIG_SCRIPT_TYPE);
        actionConfig.put(ExecuteScriptRuleProvider.CONFIG_SCRIPT, "$" + ExecuteScriptRuleProvider.CONFIG_SCRIPT);

        ArrayList<Action> actions = new ArrayList<Action>();
        actions.add(new Action(ACTION_ID, "ScriptAction", actionConfig, null));

        List<ConfigDescriptionParameter> configDescriptions = new ArrayList<ConfigDescriptionParameter>();
        ConfigDescriptionParameter type = new ConfigDescriptionParameter(ExecuteScriptRuleProvider.CONFIG_SCRIPT_TYPE,
                Type.TEXT, null, null, null, null, true, true, false, null, null, "Type", "Script type", null, null,
                null, null, null, null);
        ConfigDescriptionParameter script = new ConfigDescriptionParameter(ExecuteScriptRuleProvider.CONFIG_SCRIPT, Type.TEXT,
                null, null, null, null, true, true, false, null, null, "Script", "Script expression", null, null, null,
                null, null, null);
        configDescriptions.add(type);
        configDescriptions.add(script);

        Set<String> tags = new HashSet<String>();
        tags.add("Script");

        // create the template
        return new ExecuteScriptRuleTemplate(tags, triggers, null, actions, configDescriptions);

        // initialize triggers
        // List<Trigger> triggers = new ArrayList<Trigger>();
        // triggers.add(new Trigger(TRIGGER_ID, AirConditionerTriggerType.UID, null));

        // initialize conditions
        // here the tricky part is the giving a value to the condition configuration parameter.
        // Map<String, Object> conditionConfig = new HashMap<String, Object>();
        // conditionConfig.put(StateConditionType.CONFIG_STATE, "on");

        // here the tricky part is the referring into the condition input - trigger output.
        // The syntax is a similar to the JUEL syntax.
        // Map<String, String> conditionInputs = new HashMap<String, String>();
        // conditionInputs.put(StateConditionType.INPUT_CURRENT_STATE,
        // TRIGGER_ID + "." + StateConditionType.INPUT_CURRENT_STATE);

        // Condition stateCondition = new Condition("AirConditionerStateCondition", StateConditionType.UID,
        // conditionConfig, conditionInputs);

        // here the tricky part is the referring into the condition configuration parameter - the
        // // template configuration parameter. The syntax is a similar to the JUEL syntax.
        // conditionConfig = new HashMap<String, Object>();
        // conditionConfig.put(TemperatureConditionType.CONFIG_TEMPERATURE, "$" + CONFIG_TARGET_TEMPERATURE);
        // conditionConfig.put(TemperatureConditionType.CONFIG_OPERATOR, "$" + CONFIG_OPERATION);

        // here the tricky part is the referring into the condition input - trigger output.
        // The syntax is a similar to the JUEL syntax.
        // conditionInputs = new HashMap<String, String>();
        // conditionInputs.put(TemperatureConditionType.INPUT_CURRENT_TEMPERATURE,
        // TRIGGER_ID + "." + TemperatureConditionType.INPUT_CURRENT_TEMPERATURE);
        //
        // Condition temperatuteCondition = new Condition("AirConditionerTemperatureCondition",
        // TemperatureConditionType.UID, conditionConfig, conditionInputs);

        // List<Condition> conditions = new ArrayList<Condition>();
        // conditions.add(stateCondition);
        // conditions.add(temperatuteCondition);

        // initialize actions - here the tricky part is the referring into the action configuration parameter - the
        // template configuration parameter. The syntax is a similar to the JUEL syntax.
        // Map<String, String> actionConfig = new HashMap<String, String>();
        // actionConfig.put(WelcomeHomeActionType.CONFIG_DEVICE, "$" + WelcomeHomeRulesProvider.CONFIG_UNIT);
        // actionConfig.put(WelcomeHomeActionType.CONFIG_RESULT, "$" + WelcomeHomeRulesProvider.CONFIG_EXPECTED_RESULT);
        //
        // List<Action> actions = new ArrayList<Action>();
        // actions.add(new Action("AirConditionerSwitchOnAction", WelcomeHomeActionType.UID, actionConfig, null));

        // initialize configDescriptions
        // List<ConfigDescriptionParameter> configDescriptions = new ArrayList<ConfigDescriptionParameter>();
        // ConfigDescriptionParameter device = new ConfigDescriptionParameter(WelcomeHomeRulesProvider.CONFIG_UNIT,
        // Type.TEXT, null, null, null, null, true, true, false, null, null, "Device", "Device description", null,
        // null, null, null, null, null);
        // ConfigDescriptionParameter result = new ConfigDescriptionParameter(
        // WelcomeHomeRulesProvider.CONFIG_EXPECTED_RESULT, Type.TEXT, null, null, null, null, true, true, false,
        // null, null, "Result", "Result description", null, null, null, null, null, null);
        // ConfigDescriptionParameter temperature = new ConfigDescriptionParameter(CONFIG_TARGET_TEMPERATURE,
        // Type.INTEGER,
        // null, null, null, null, true, true, false, null, null, "Target temperature",
        // "Indicates the target temperature.", null, null, null, null, null, null);
        // ConfigDescriptionParameter operation = new ConfigDescriptionParameter(CONFIG_OPERATION, Type.TEXT, null,
        // null,
        // null, null, true, true, false, null, null, "Heating/Cooling", "Indicates Heating or Cooling is set.",
        // null, null, null, null, null, null);
        // configDescriptions.add(device);
        // configDescriptions.add(result);
        // configDescriptions.add(temperature);
        // configDescriptions.add(operation);

        // initialize tags
        // Set<String> tags = new HashSet<String>();
        // tags.add("AirConditioner");
        // tags.add("LivingRoom");
        //
        // // create the template
        // return new AirConditionerRuleTemplate(tags, triggers, conditions, actions, configDescriptions);
    }

    public ExecuteScriptRuleTemplate(Set<String> tags, List<Trigger> triggers, List<Condition> conditions,
            List<Action> actions, List<ConfigDescriptionParameter> configDescriptions) {
        super(UID, "Execute Script Rule Template", "Template for executing Scripts", tags, triggers, conditions,
                actions, configDescriptions, Visibility.VISIBLE);

    }

}
