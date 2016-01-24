package org.eclipse.smarthome.model.rule.runtime.automation;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.eclipse.smarthome.automation.Module;
import org.eclipse.smarthome.automation.Trigger;
import org.eclipse.smarthome.automation.handler.BaseModuleHandlerFactory;
import org.eclipse.smarthome.automation.handler.ModuleHandler;
import org.eclipse.smarthome.automation.handler.ModuleHandlerFactory;
import org.osgi.framework.BundleContext;
import org.osgi.framework.InvalidSyntaxException;
import org.osgi.framework.ServiceRegistration;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Module Handler Factory Sample implementation
 *
 * @author Karel Goderis - Initial Contribution
 */
public class RuleRuntimeHandlerFactory extends BaseModuleHandlerFactory {

    private Logger logger = LoggerFactory.getLogger(RuleRuntimeHandlerFactory.class);

    public static final String SUPPORTED_TRIGGER = "SystemTrigger";
    public static final String MODULE_HANDLER_FACTORY_NAME = "[RuleRuntimeHandlerFactory]";
    private static final Collection<String> types;
    private Map<String, SystemTriggerHandler> triggerHandlers = new HashMap<String, SystemTriggerHandler>();
    private ServiceRegistration<?> factoryRegistration;
    private ServiceRegistration<?> ruleRuntimeFactoryRegistration;

    static {
        List<String> temp = new ArrayList<String>();
        temp.add(SUPPORTED_TRIGGER);
        types = Collections.unmodifiableCollection(temp);
    }

    public RuleRuntimeHandlerFactory(BundleContext bc) throws InvalidSyntaxException {
        super(bc);
    }

    @Override
    public Collection<String> getTypes() {
        return types;
    }

    /**
     * Retrieves created TriggerHandlers from this HandlerFactory.
     *
     * @return list of created TriggerHandlers
     */
    public Collection<SystemTriggerHandler> getCreatedTriggerHandler() {
        return triggerHandlers.values();
    }

    public SystemTriggerHandler getTriggerHandler(String uid) {
        return triggerHandlers.get(uid);
    }

    @Override
    protected ModuleHandler internalCreate(Module module, String ruleUID) {
        ModuleHandler moduleHandler = null;
        if (SUPPORTED_TRIGGER.equals(module.getTypeUID())) {
            moduleHandler = new SystemTriggerHandler((Trigger) module, ruleUID);
            triggerHandlers.put(ruleUID + module.getId(), (SystemTriggerHandler) moduleHandler);
        } else {
            logger.error(MODULE_HANDLER_FACTORY_NAME + "Not supported moduleHandler: {}", module.getTypeUID());
        }
        if (moduleHandler != null) {
            handlers.put(ruleUID + module.getId(), moduleHandler);
        }

        return moduleHandler;
    }

    @Override
    public void ungetHandler(Module module, String ruleUID, ModuleHandler hdlr) {
        triggerHandlers.remove(ruleUID + module.getId());
        super.ungetHandler(module, ruleUID, hdlr);
    }

    @Override
    public void dispose() {
        triggerHandlers.clear();
        super.dispose();
    }

    public void register() {
        factoryRegistration = bundleContext.registerService(ModuleHandlerFactory.class.getName(), this, null);
        ruleRuntimeFactoryRegistration = bundleContext.registerService(RuleRuntimeHandlerFactory.class.getName(), this,
                null);
    }

    public void unregister() {
        factoryRegistration.unregister();
        factoryRegistration = null;
        ruleRuntimeFactoryRegistration.unregister();
        ruleRuntimeFactoryRegistration = null;
    }

}
