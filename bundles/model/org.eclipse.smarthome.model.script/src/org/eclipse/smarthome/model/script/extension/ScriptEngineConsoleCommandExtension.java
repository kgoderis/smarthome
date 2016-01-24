/**
 * Copyright (c) 2014-2015 openHAB UG (haftungsbeschraenkt) and others.
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v1.0
 * which accompanies this distribution, and is available at
 * http://www.eclipse.org/legal/epl-v10.html
 */
package org.eclipse.smarthome.model.script.extension;

import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.eclipse.smarthome.automation.Rule;
import org.eclipse.smarthome.io.console.Console;
import org.eclipse.smarthome.io.console.extensions.AbstractConsoleCommandExtension;
import org.eclipse.smarthome.io.console.extensions.ConsoleCommandExtension;
import org.eclipse.smarthome.model.script.engine.automation.ExecuteScriptHandlerFactory;
import org.eclipse.smarthome.model.script.engine.automation.ExecuteScriptRuleProvider;
import org.eclipse.smarthome.model.script.engine.automation.ExecuteScriptTriggerHandler;
import org.osgi.framework.BundleContext;
import org.osgi.framework.ServiceRegistration;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.google.common.base.Joiner;

/**
 * This class provides the script engine as a console command
 *
 * @author Oliver Libutzki - Initial contribution
 * @author Karel Goderis - Migration to Automation
 *
 */
public class ScriptEngineConsoleCommandExtension extends AbstractConsoleCommandExtension {

    private static final Logger logger = LoggerFactory.getLogger(ScriptEngineConsoleCommandExtension.class);

    @SuppressWarnings("rawtypes")
    private ServiceRegistration scriptCommandsServiceReg;
    private ExecuteScriptHandlerFactory factory;
    private ExecuteScriptRuleProvider provider;

    public void setRuleProvider(ExecuteScriptRuleProvider ruleProvider) {
        provider = ruleProvider;
    }

    public void unsetRuleProvider(ExecuteScriptRuleProvider ruleProvider) {
        provider = null;
    }

    public void setHandlerFactory(ExecuteScriptHandlerFactory factory) {
        this.factory = factory;
    }

    public void unsetHandlerFactory(ExecuteScriptHandlerFactory factory) {
        factory = null;
    }

    // public ScriptEngineConsoleCommandExtension(BundleContext bc, ExecuteScriptHandlerFactory ruleHandlerFactory,
    // ExecuteScriptRuleProvider provider) {
    // super(">", "Execute scripts");
    // this.factory = ruleHandlerFactory;
    // this.provider = provider;
    // }

    public ScriptEngineConsoleCommandExtension() {
        super(">", "Execute scripts");
    }

    public void register(BundleContext context) {
        scriptCommandsServiceReg = context.registerService(ConsoleCommandExtension.class.getName(), this, null);
    }

    public void unregister() {
        if (scriptCommandsServiceReg != null) {
            scriptCommandsServiceReg.unregister();
        }
        scriptCommandsServiceReg = null;

    }

    @Override
    public void execute(String[] args, Console console) {

        String scriptString = Joiner.on(" ").join(args);
        Rule rule = provider.createScriptRule(scriptString, "eclipse/script");

        Map<String, Object> context = new HashMap<String, Object>();

        ExecuteScriptTriggerHandler handler = factory
                .getTriggerHandler(rule.getUID() + ExecuteScriptRuleProvider.EXECUTE_SCRIPT_RULE_TRIGGER_TYPE_UID);
        if (handler != null) {
            logger.debug("Executing a console script '{}'", scriptString);
            handler.trigger(context);
        }

        // TODO : process results and handling
        // Automation, for now, does not return the results of Rules executing!!

    }

    @Override
    public List<String> getUsages() {
        return Collections.singletonList(buildCommandUsage("<script to execute>", "Executes a script"));
    }
}
