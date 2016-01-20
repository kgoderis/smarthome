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
import org.eclipse.smarthome.model.script.internal.actions.TimerExecutionJob;
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

    public static Map<String, TimerExecutionJob> timerExecutionJobs = new HashMap<String, TimerExecutionJob>();

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
        ModelRepository repo = ScriptActivator.modelRepositoryTracker.getService();
        if (repo != null) {
            String scriptNameWithExt = scriptName;
            if (!StringUtils.endsWith(scriptName, Script.SCRIPT_FILEEXT)) {
                scriptNameWithExt = scriptName + "." + Script.SCRIPT_FILEEXT;
            }
            XExpression expr = (XExpression) repo.getModel(scriptNameWithExt);
            if (expr != null) {

                ExecuteScriptHandlerFactory factory = ScriptActivator.getExecuteScriptHandlerFactory();
                ExecuteScriptRuleProvider provider = ScriptActivator.getExecuteScriptRuleProvider();
                Rule rule = provider.createScriptRule(scriptName, "eclipse/scriptfile", scriptNameWithExt);

                Map<String, Object> context = new HashMap<String, Object>();

                // causes the execution of the rule WelcomeHomeRulesProvider.LRL_UID
                ExecuteScriptTriggerHandler handler = factory
                        .getTriggerHandler(ExecuteScriptRuleProvider.EXECUTE_SCRIPT_RULE_UID + "." + scriptName);
                if (handler != null) {
                    handler.trigger(context);
                } else {
                    throw new ScriptExecutionException("The rule to execute the script is not available.");
                }

                // ScriptEngine scriptEngine = ScriptActivator.scriptEngineTracker.getService();
                // if (scriptEngine != null) {
                // Script script = scriptEngine.newScriptFromXExpression(expr);
                // return script.execute();
                // } else {
                // throw new ScriptExecutionException("Script engine is not available.");
                // }
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
        // Trigger trigger = newTrigger().startAt(instant.toDate()).build();

        ExpressionThreadPoolExecutor scheduler = ThreadPoolManager.getExpressionScheduledPool("automation");
        DateExpression dateExpression = null;
        try {
            dateExpression = new DateExpression(instant.toDate());
        } catch (ParseException e1) {
            logger.error("An exception occurred while creating a time for execution at {}", instant.toDate());
        }

        try {
            // JobDataMap dataMap = new JobDataMap();
            // dataMap.put("procedure", closure);
            // dataMap.put("timer", timer);
            TimerExecutionJob job = new TimerExecutionJob(instant, closure);
            // Timer timer = new TimerImpl(job, instant, closure);

            // job.put("procedure", closure);
            // job.put("timer", timer);
            if (timerExecutionJobs.containsKey(jobKey)) {
                scheduler.remove(timerExecutionJobs.get(jobKey));
                logger.debug("Deleted existing Job {}", jobKey);
            }
            // if (TimerImpl.scheduler.checkExists(job.getKey())) {
            // TimerImpl.scheduler.deleteJob(job.getKey());
            // logger.debug("Deleted existing Job {}", job.getKey().toString());
            // }
            timerExecutionJobs.put(jobKey, job);
            scheduler.schedule(job, dateExpression);
            logger.debug("Scheduled code for execution at {}", instant.toString());
            return job;
        } catch (Exception e) {
            logger.error("Failed to schedule code for execution.", e);
            return null;
        }
    }
}
