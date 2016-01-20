package org.eclipse.smarthome.model.script.engine.automation;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.List;
import java.util.Map;

import org.eclipse.smarthome.automation.Module;
import org.eclipse.smarthome.automation.Trigger;
import org.eclipse.smarthome.automation.handler.BaseModuleHandlerFactory;
import org.eclipse.smarthome.automation.handler.ModuleHandler;
import org.osgi.framework.BundleContext;
import org.osgi.framework.InvalidSyntaxException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Module Handler Factory Sample implementation
 *
 * @author Karel Goderis - Initial Contribution
 */
public class ExecuteScriptHandlerFactory extends BaseModuleHandlerFactory {
    public static final String SUPPORTED_TRIGGER = "ExecuteScriptTrigger";
    public static final String MODULE_HANDLER_FACTORY_NAME = "[ExecuteScriptHandlerFactory]";
    private Logger logger = LoggerFactory.getLogger(ExecuteScriptHandlerFactory.class);
    private static final Collection<String> types;
    private Map<String, ExecuteScriptTriggerHandler> triggerHandlers;

    static {
        List<String> temp = new ArrayList<String>();
        temp.add(SUPPORTED_TRIGGER);
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
    public Collection<ExecuteScriptTriggerHandler> getCreatedTriggerHandler() {
        return triggerHandlers.values();
    }

    public ExecuteScriptTriggerHandler getTriggerHandler(String uid) {
        return triggerHandlers.get(uid);
    }

    @Override
    protected ModuleHandler internalCreate(Module module, String ruleUID) {
        ModuleHandler moduleHandler = null;
        if (SUPPORTED_TRIGGER.equals(module.getTypeUID())) {
            moduleHandler = new ExecuteScriptTriggerHandler((Trigger) module);
            triggerHandlers.put(ruleUID, (ExecuteScriptTriggerHandler) moduleHandler);
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
        triggerHandlers.remove(ruleUID);
        super.ungetHandler(module, ruleUID, hdlr);
    }

    @Override
    public void dispose() {
        triggerHandlers.clear();
        super.dispose();
    }

}
