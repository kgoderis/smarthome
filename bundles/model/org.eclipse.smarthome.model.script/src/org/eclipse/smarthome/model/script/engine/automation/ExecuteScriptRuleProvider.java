package org.eclipse.smarthome.model.script.engine.automation;

import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

import org.eclipse.smarthome.automation.Rule;
import org.eclipse.smarthome.automation.RuleProvider;
import org.eclipse.smarthome.core.common.registry.ProviderChangeListener;
import org.osgi.framework.BundleContext;
import org.osgi.framework.ServiceRegistration;

public class ExecuteScriptRuleProvider implements RuleProvider {

    public static final String EXECUTE_SCRIPT_TEMPLATE_UID = "ExecuteScriptRuleTemplate";
    public static final String EXECUTE_SCRIPT_RULE_TRIGGER_TYPE_UID = "ExcecuteScriptRuleTrigger";

    public static final String CONFIG_SCRIPT_TYPE = "type";
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
     * Then the RuleEngine will generate the EXECUTE_SCRIPT_TRIGGER_TYPE_UID for each provided rule.
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
     *            specifies the rule for updating by EXECUTE_SCRIPT_TRIGGER_TYPE_UID
     * @param template
     *            specifies the rule template by EXECUTE_SCRIPT_TRIGGER_TYPE_UID
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
    public Rule createScriptRule(String script, String scriptType) {

        String ruleUID = UUID.randomUUID().toString();

        for (Rule aRule : rules.values()) {
            Map<String, ?> configuration = aRule.getConfiguration();
            if (((String) configuration.get(CONFIG_SCRIPT)).equals(script)
                    && ((String) configuration.get(CONFIG_SCRIPT_TYPE)).equals(scriptType)) {
                return aRule;
            }
        }

        Map<String, Object> config = new HashMap<String, Object>();
        config.put(ExecuteScriptRuleProvider.CONFIG_SCRIPT_TYPE, scriptType);
        config.put(ExecuteScriptRuleProvider.CONFIG_SCRIPT, script);
        Rule newRule = new Rule(ruleUID, EXECUTE_SCRIPT_TEMPLATE_UID, config);
        newRule.setName(script + " execution rule");

        rules.put(ruleUID, newRule);

        for (ProviderChangeListener<Rule> listener : listeners) {
            listener.added(this, newRule);
        }

        return newRule;
    }

}
