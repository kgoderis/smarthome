package org.eclipse.smarthome.model.script.engine.automation;

import java.util.Collection;
import java.util.Dictionary;
import java.util.HashMap;
import java.util.Hashtable;
import java.util.Locale;
import java.util.Map;

import org.eclipse.smarthome.automation.template.RuleTemplate;
import org.eclipse.smarthome.automation.template.Template;
import org.eclipse.smarthome.automation.template.TemplateProvider;
import org.osgi.framework.BundleContext;
import org.osgi.framework.ServiceRegistration;

public class ExecuteScriptTemplateProvider implements TemplateProvider {

    private Map<String, RuleTemplate> providedRuleTemplates;
    @SuppressWarnings("rawtypes")
    private ServiceRegistration providerReg;

    public ExecuteScriptTemplateProvider() {
        providedRuleTemplates = new HashMap<String, RuleTemplate>();
        providedRuleTemplates.put(ExecuteScriptRuleTemplate.UID, ExecuteScriptRuleTemplate.initialize());
    }

    @Override
    public <T extends Template> T getTemplate(String UID, Locale locale) {
        return (T) providedRuleTemplates.get(UID);
    }

    @Override
    public <T extends Template> Collection<T> getTemplates(Locale locale) {
        return (Collection<T>) providedRuleTemplates.values();
    }

    public void register(BundleContext bc) {
        Dictionary<String, Object> properties = new Hashtable<String, Object>();
        properties.put(REG_PROPERTY_RULE_TEMPLATES, providedRuleTemplates.keySet());
        providerReg = bc.registerService(TemplateProvider.class.getName(), this, properties);
    }

    public void unregister() {
        providerReg.unregister();
        providerReg = null;
        providedRuleTemplates = null;
    }

}
