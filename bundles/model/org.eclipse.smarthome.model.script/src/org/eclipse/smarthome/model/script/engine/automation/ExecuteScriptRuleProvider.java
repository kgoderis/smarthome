package org.eclipse.smarthome.model.script.engine.automation;

import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.Map;

import org.eclipse.smarthome.automation.Rule;
import org.eclipse.smarthome.automation.RuleProvider;
import org.eclipse.smarthome.core.common.registry.ProviderChangeListener;
import org.osgi.framework.BundleContext;
import org.osgi.framework.ServiceRegistration;

public class ExecuteScriptRuleProvider implements RuleProvider {

    public static final String EXECUTE_SCRIPT_RULE_UID = "ExecuteScriptRule";

    /** Constant defining the configuration parameter of modules that specifies the mime type of a script */
    public static final String CONFIG_SCRIPT_TYPE = "type";
    /** Constant defining the configuration parameter of modules that specifies the script itself */
    public static final String CONFIG_SCRIPT = "script";

    Map<String, Rule> rules;

    @SuppressWarnings("rawtypes")
    private ServiceRegistration providerReg;
    private Collection<ProviderChangeListener<Rule>> listeners;

    /**
     * This method is used to initialize the provided rules by using templates and from scratch.
     * The configuration of the rule created by template should contain as keys all required parameter names of the
     * configuration of the template and their values.
     * In this example the UIDs of the rules is given by the provider, but can be <code>null</code>.
     * Then the RuleEngine will generate the UID for each provided rule.
     */
    public ExecuteScriptRuleProvider() {
        rules = new HashMap<String, Rule>();
    }

    @Override
    public void addProviderChangeListener(ProviderChangeListener<Rule> listener) {
        if (listeners == null) {
            listeners = new ArrayList<ProviderChangeListener<Rule>>();
        }
        listeners.add(listener);
    }

    @Override
    public Collection<Rule> getAll() {
        return rules.values();
    }

    @Override
    public void removeProviderChangeListener(ProviderChangeListener<Rule> listener) {
        if (listeners != null) {
            listeners.remove(listener);
        }
    }

    /**
     * This method is used to update the provided rules configuration.
     *
     * @param uid
     *            specifies the rule for updating by UID
     * @param template
     *            specifies the rule template by UID
     * @param config
     *            gives the new configuration of the rule
     */
    public void update(String uid, String template, Map<String, Object> config) {

        // specific for this application
        Rule oldelement = rules.get(uid);
        Rule element = new Rule(uid, template, config);
        rules.put(uid, element);

        // inform all listeners, interested about changing of the rules
        for (ProviderChangeListener<Rule> listener : listeners) {
            listener.updated(this, oldelement, element);
        }
    }

    /**
     * This method is used for registration of the ScriptRulesProvider as a {@link RuleProvider} service.
     *
     * @param bc
     *            is a bundle's execution context within the Framework.
     */
    public void register(BundleContext bc) {
        providerReg = bc.registerService(RuleProvider.class.getName(), this, null);
    }

    /**
     * This method is used to unregister the ScriptRulesProvider service.
     */
    public void unregister() {
        providerReg.unregister();
        providerReg = null;
        rules = null;
    }

    /**
     * This method creates a rule from scratch by using trigger, condition, action, configDescriptions and
     * configuration, tags.
     *
     * @return the created rule
     */
    public Rule createScriptRule(String UID, String ScriptType, String Script) {

        Map<String, Object> config = new HashMap<String, Object>();
        config.put(ExecuteScriptRuleProvider.CONFIG_SCRIPT_TYPE, "Air Conditioner");
        config.put(ExecuteScriptRuleProvider.CONFIG_SCRIPT, "The air conditioner is switched on.");
        Rule newRule = new Rule(EXECUTE_SCRIPT_RULE_UID + "." + UID, ExecuteScriptRuleTemplate.UID, config);
        rules.put(EXECUTE_SCRIPT_RULE_UID + "." + UID, newRule);

        for (ProviderChangeListener<Rule> listener : listeners) {
            listener.added(this, newRule);
        }

        return newRule;

        // ArrayList<Trigger> triggers = new ArrayList<Trigger>();
        // triggers.add(new Trigger("ExcecuteScriptRuleTrigger", ExecuteScriptTriggerType.UID, null));
        //
        // ArrayList<Action> actions = new ArrayList<Action>();
        // HashMap<String, String> config = new HashMap<String, String>();
        // config.put("type", "eclipse/script");
        // config.put("script", "ExcecuteScriptRuleTrigger" + "." + ExecuteScriptTriggerType.INPUT_EXPRESSION);
        // actions.add(new Action("ExcecuteScriptRuleAction", "ScriptAction", config, null));
        //
        // return new Rule(EXECUTE_SCRIPT_RULE_UID, triggers, null, actions, new
        // ArrayList<ConfigDescriptionParameter>(),
        // null, Visibility.VISIBLE);

        // Rule theRule = new Rule(uid + "_rule_", triggers, null, actions, new ArrayList<ConfigDescriptionParameter>(),
        // null, Visibility.VISIBLE);
        // ruleRegistry.add(theRule);

        // RuleStartHandlerFactory factory = ScriptActivator.getRuleStartHandlerFactory();
        // RuleStartTriggerHandler handler = (RuleStartTriggerHandler) factory.getHandler(trigger, uid + "_rule");
        // handler.trigger();

        // // initialize the condition - here the tricky part is the referring into the condition input - trigger
        // output.
        // // The syntax is a similar to the JUEL syntax.
        // Map<String, Object> config = new HashMap<String, Object>();
        // config.put(StateConditionType.CONFIG_STATE, "on");
        // List<Condition> conditions = new ArrayList<Condition>();
        // Map<String, String> inputs = new HashMap<String, String>();
        // inputs.put(StateConditionType.INPUT_CURRENT_STATE, triggerId + "." + StateConditionType.INPUT_CURRENT_STATE);
        // conditions.add(new Condition("LightsStateCondition", StateConditionType.UID, config, inputs));

        // initialize the action - here the tricky part is the referring into the action configuration parameter - the
        // template configuration parameter. The syntax is a similar to the JUEL syntax.
        // config = new HashMap<String, Object>();
        // config.put(WelcomeHomeActionType.CONFIG_DEVICE, "Lights");
        // config.put(WelcomeHomeActionType.CONFIG_RESULT, "Lights are switched on");
        // List<Action> actions = new ArrayList<Action>();
        // actions.add(new Action("LightsSwitchOnAction", WelcomeHomeActionType.UID, config, null));

        // // initialize the configDescriptions
        // List<ConfigDescriptionParameter> configDescriptions = new ArrayList<ConfigDescriptionParameter>();
        // ConfigDescriptionParameter device = new ConfigDescriptionParameter(WelcomeHomeRulesProvider.CONFIG_UNIT,
        // Type.TEXT, null, null, null, null, true, true, false, null, null, "Device", "Device description", null,
        // null, null, null, null, null);
        // ConfigDescriptionParameter result = new ConfigDescriptionParameter(
        // WelcomeHomeRulesProvider.CONFIG_EXPECTED_RESULT, Type.TEXT, null, null, null, null, true, true, false,
        // null, null, "Result", "Result description", null, null, null, null, null, null);
        // configDescriptions.add(device);
        // configDescriptions.add(result);
        //
        // // initialize the configuration
        // config = new HashMap<String, Object>();
        // config.put(CONFIG_UNIT, "Lights");
        // config.put(CONFIG_EXPECTED_RESULT, "The lights are switched on.");
        //
        // // create the rule
        // Rule lightsSwitchOn = new Rule(L_UID, triggers, conditions, actions, configDescriptions, config);
        //
        // // initialize the tags
        // Set<String> tags = new HashSet<String>();
        // tags.add("lights");
        //
        // // set the tags
        // lightsSwitchOn.setTags(tags);
        //
        // return lightsSwitchOn;
    }

}
