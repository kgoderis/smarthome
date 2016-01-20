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
import java.util.UUID;

import org.eclipse.smarthome.automation.Rule;
import org.eclipse.smarthome.io.console.Console;
import org.eclipse.smarthome.io.console.extensions.AbstractConsoleCommandExtension;
import org.eclipse.smarthome.model.script.engine.automation.ExecuteScriptHandlerFactory;
import org.eclipse.smarthome.model.script.engine.automation.ExecuteScriptTriggerHandler;
import org.eclipse.smarthome.model.script.engine.automation.ExecuteScriptRuleProvider;
import org.osgi.framework.BundleContext;
import org.osgi.framework.ServiceRegistration;

import com.google.common.base.Joiner;

/**
 * This class provides the script engine as a console command
 *
 * @author Oliver Libutzki - Initial contribution
 * @param <ExecuteScriptRuleProvider>
 *
 */
public class ScriptEngineConsoleCommandExtension<ExecuteScriptRuleProvider> extends AbstractConsoleCommandExtension {

    @SuppressWarnings("rawtypes")
    private ServiceRegistration scriptCommandsServiceReg;
    private ExecuteScriptHandlerFactory factory;
    private ExecuteScriptRuleProvider provider;

    public ScriptEngineConsoleCommandExtension(BundleContext bc, ExecuteScriptHandlerFactory ruleHandlerFactory,
            ExecuteScriptRuleProvider provider) {
        super(">", "Execute scripts");
        this.factory = ruleHandlerFactory;
        this.provider = provider;
    }

    public void register(BundleContext context) {
        scriptCommandsServiceReg = context.registerService(ScriptEngineConsoleCommandExtension.class.getName(), this,
                null);
    }

    public void unregister() {
        if (scriptCommandsServiceReg != null) {
            scriptCommandsServiceReg.unregister();
        }
        scriptCommandsServiceReg = null;

    }

    @Override
    public void execute(String[] args, Console console) {

        String uid = UUID.randomUUID().toString();
        //
        // ArrayList<org.eclipse.smarthome.automation.Trigger> triggers = new
        // ArrayList<org.eclipse.smarthome.automation.Trigger>();
        // HashMap<String, Object> triggerConfig = new HashMap<String, Object>();
        // Trigger trigger = new Trigger(uid + "_trigger", "RuleStartTrigger", triggerConfig);
        // triggers.add(trigger);
        //
        // ArrayList<Action> actions = new ArrayList<Action>();
        // HashMap<String, String> actionConfig = new HashMap<String, String>();
        // actions.add(new Action(UUID.randomUUID().toString(), "ScriptAction", actionConfig, null));
        //
        // Rule theRule = new Rule(uid + "_rule_", triggers, null, actions,
        // new ArrayList<ConfigDescriptionParameter>(), null, Visibility.VISIBLE);
        // ruleRegistry.add(theRule);
        //
        // RuleStartHandlerFactory factory = ScriptActivator.getRuleStartHandlerFactory();
        // RuleStartTriggerHandler handler = (RuleStartTriggerHandler) factory.getHandler(trigger, uid + "_rule");
        // handler.trigger();

        // initialize the output of the trigger of the rule WelcomeHomeRulesProvider.AC_UID

        String scriptString = Joiner.on(" ").join(args);

        Rule rule = provider.createScriptRule(uid, "eclipse/script", scriptString);

        Map<String, Object> context = new HashMap<String, Object>();

        // causes the execution of the rule WelcomeHomeRulesProvider.LRL_UID
        ExecuteScriptTriggerHandler handler = factory
                .getTriggerHandler(ExecuteScriptRuleProvider.EXECUTE_SCRIPT_RULE_UID + "." + uid);
        if (handler != null) {
            handler.trigger(context);
        }

        // TODO : process results and handling

        // if (scriptEngine != null) {
        // String scriptString = Joiner.on(" ").join(args);
        // Script script;
        // try {
        // script = scriptEngine.newScriptFromString(scriptString);
        // Object result = script.execute();
        //
        // if (result != null) {
        // console.println(result.toString());
        // } else {
        // console.println("OK");
        // }
        // } catch (ScriptParsingException e) {
        // console.println(e.getMessage());
        // } catch (ScriptExecutionException e) {
        // console.println(e.getMessage());
        // }
        // } else {
        // console.println("Script engine is not available.");
        // }
    }

    @Override
    public List<String> getUsages() {
        return Collections.singletonList(buildCommandUsage("<script to execute>", "Executes a script"));
    }
}
