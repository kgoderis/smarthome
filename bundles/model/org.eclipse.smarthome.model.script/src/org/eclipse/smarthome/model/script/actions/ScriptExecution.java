/**
 * Copyright (c) 2014-2015 openHAB UG (haftungsbeschraenkt) and others.
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v1.0
 * which accompanies this distribution, and is available at
 * http://www.eclipse.org/legal/epl-v10.html
 */
package org.eclipse.smarthome.model.script.actions;

import java.text.ParseException;
import java.util.HashMap;
import java.util.Map;

import org.apache.commons.lang.StringUtils;
import org.eclipse.smarthome.automation.Rule;
import org.eclipse.smarthome.core.common.DateExpression;
import org.eclipse.smarthome.core.common.ThreadPoolManager;
import org.eclipse.smarthome.core.common.ThreadPoolManager.ExpressionThreadPoolExecutor;
import org.eclipse.smarthome.model.core.ModelRepository;
import org.eclipse.smarthome.model.script.engine.Script;
import org.eclipse.smarthome.model.script.engine.ScriptExecutionException;
import org.eclipse.smarthome.model.script.engine.automation.ExecuteScriptHandlerFactory;
import org.eclipse.smarthome.model.script.engine.automation.ExecuteScriptRuleProvider;
import org.eclipse.smarthome.model.script.engine.automation.ExecuteScriptTriggerHandler;
import org.eclipse.smarthome.model.script.internal.ScriptActivator;
import org.eclipse.smarthome.model.script.internal.actions.TimerRunnable;
import org.eclipse.xtext.xbase.XExpression;
import org.eclipse.xtext.xbase.lib.Procedures.Procedure0;
import org.joda.time.base.AbstractInstant;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * The static methods of this class are made available as functions in the scripts.
 * This allows a script to call another script, which is available as a file.
 *
 * @author Kai Kreuzer - Initial contribution and API
 *
 */
@SuppressWarnings("restriction")
public class ScriptExecution {

    private static final Logger logger = LoggerFactory.getLogger(ScriptExecution.class);

    public static Map<String, TimerRunnable> timerRunnables = new HashMap<String, TimerRunnable>();

    private static ExecuteScriptRuleProvider ruleProvider;
    private static ModelRepository modelRepository;

    public void setRuleProvider(ExecuteScriptRuleProvider ruleProvider) {
        ScriptExecution.ruleProvider = ruleProvider;
    }

    public void unsetRuleProvider(ExecuteScriptRuleProvider ruleProvider) {
        ScriptExecution.ruleProvider = null;
    }

    public void setModelRepository(ModelRepository modelRepository) {
        ScriptExecution.modelRepository = modelRepository;
    }

    public void unsetModelRepository(ModelRepository modelRepository) {
        modelRepository = null;
    }

    /**
     * Calls a script which must be located in the configurations/scripts folder.
     *
     * @param scriptName the name of the script (if the name does not end with
     *            the .script file extension it is added)
     *
     * @return the return value of the script
     * @throws ScriptExecutionException if an error occurs during the execution
     */
    public static Object callScript(String scriptName) throws ScriptExecutionException {
        if (modelRepository != null) {
            String scriptNameWithExt = scriptName;
            if (!StringUtils.endsWith(scriptName, Script.SCRIPT_FILEEXT)) {
                scriptNameWithExt = scriptName + "." + Script.SCRIPT_FILEEXT;
            }
            XExpression expr = (XExpression) modelRepository.getModel(scriptNameWithExt);
            if (expr != null) {

                Rule rule = ruleProvider.createScriptRule(scriptNameWithExt, "eclipse/scriptfile");

                Map<String, Object> context = new HashMap<String, Object>();

                ExecuteScriptHandlerFactory factory = ScriptActivator.getHandlerFactory();
                ExecuteScriptTriggerHandler handler = factory.getTriggerHandler(
                        rule.getUID() + ExecuteScriptRuleProvider.EXECUTE_SCRIPT_RULE_TRIGGER_TYPE_UID);

                logger.debug("Calling script '{}'", scriptNameWithExt);
                if (handler != null) {
                    handler.trigger(context);
                } else {
                    throw new ScriptExecutionException("The rule to execute the script is not available.");
                }

            } else {
                throw new ScriptExecutionException("Script '" + scriptName + "' cannot be found.");
            }
        } else {
            throw new ScriptExecutionException("Model repository is not available.");
        }

        // TODO: This is bad, but the Rules engine does not provide results of the script execution
        return true;
    }

    /**
     * Schedules a block of code for later execution.
     *
     * @param instant the point in time when the code should be executed
     * @param closure the code block to execute
     *
     * @return a handle to the created timer, so that it can be canceled or rescheduled
     * @throws ScriptExecutionException if an error occurs during the execution
     */
    public static Timer createTimer(AbstractInstant instant, Procedure0 closure) {
        Logger logger = LoggerFactory.getLogger(ScriptExecution.class);
        String jobKey = instant.toString() + ": " + closure.toString();

        ExpressionThreadPoolExecutor scheduler = ThreadPoolManager.getExpressionScheduledPool("automation");
        DateExpression dateExpression = null;
        try {
            dateExpression = new DateExpression(instant.toDate());
        } catch (ParseException e1) {
            logger.error("An exception occurred while creating a time for execution at {}", instant.toDate());
        }

        try {
            TimerRunnable job = new TimerRunnable(instant, closure, scheduler);

            if (timerRunnables.containsKey(jobKey)) {
                scheduler.remove(timerRunnables.get(jobKey));
                logger.debug("Deleted existing Job {}", jobKey);
            }

            timerRunnables.put(jobKey, job);
            scheduler.schedule(job, dateExpression);
            logger.debug("Scheduled code for execution at {}", instant.toString());
            return job;
        } catch (Exception e) {
            logger.error("Failed to schedule code for execution.", e);
            return null;
        }
    }
}
