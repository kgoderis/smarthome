package org.eclipse.smarthome.model.script.engine.automation;

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
public class ExecuteScriptHandlerFactory extends BaseModuleHandlerFactory {

    private Logger logger = LoggerFactory.getLogger(ExecuteScriptHandlerFactory.class);

    public static final String EXECUTE_SCRIPT_TRIGGER_TYPE_UID = "ExecuteScriptTrigger";
    public static final String MODULE_HANDLER_FACTORY_NAME = "[ExecuteScriptHandlerFactory]";

    private static final Collection<String> types;
    private Map<String, ExecuteScriptTriggerHandler> triggerHandlers = new HashMap<String, ExecuteScriptTriggerHandler>();
    private ServiceRegistration<?> factoryRegistration;
    private ServiceRegistration<?> executeScriptFactoryRegistration;

    static {
        List<String> temp = new ArrayList<String>();
        temp.add(EXECUTE_SCRIPT_TRIGGER_TYPE_UID);
        types = Collections.unmodifiableCollection(temp);
    }

    public ExecuteScriptHandlerFactory(BundleContext bc) throws InvalidSyntaxException {
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
    public Collection<ExecuteScriptTriggerHandler> getTriggerHandlers() {
        return triggerHandlers.values();
    }

    public ExecuteScriptTriggerHandler getTriggerHandler(String uid) {
        return triggerHandlers.get(uid);
    }

    @Override
    protected ModuleHandler internalCreate(Module module, String ruleUID) {
        ModuleHandler moduleHandler = null;
        if (EXECUTE_SCRIPT_TRIGGER_TYPE_UID.equals(module.getTypeUID())) {
            moduleHandler = new ExecuteScriptTriggerHandler((Trigger) module);
            triggerHandlers.put(ruleUID + module.getId(), (ExecuteScriptTriggerHandler) moduleHandler);
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
        executeScriptFactoryRegistration = bundleContext.registerService(ExecuteScriptHandlerFactory.class.getName(), this, null);
    }

    public void unregister() {
        factoryRegistration.unregister();
        factoryRegistration = null;
        executeScriptFactoryRegistration.unregister();
        executeScriptFactoryRegistration = null;
    }

}
