/**
 * Copyright (c) 1997, 2015 by ProSyst Software GmbH and others.
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v1.0
 * which accompanies this distribution, and is available at
 * http://www.eclipse.org/legal/epl-v10.html
 */
package org.eclipse.smarthome.automation.module.timer.handler;

import java.text.ParseException;

import org.eclipse.smarthome.automation.Trigger;
import org.eclipse.smarthome.automation.handler.BaseModuleHandler;
import org.eclipse.smarthome.automation.handler.RuleEngineCallback;
import org.eclipse.smarthome.automation.handler.TriggerHandler;
import org.eclipse.smarthome.automation.module.timer.factory.TimerModuleHandlerFactory;
import org.eclipse.smarthome.core.common.CronExpression;
import org.eclipse.smarthome.core.common.ThreadPoolManager;
import org.eclipse.smarthome.core.common.ThreadPoolManager.ExpressionThreadPoolExecutor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * This is an ModuleHandler implementation for Triggers which trigger the rule
 * based on a cron expression. The cron expression can be set with the
 * configuration.
 *
 * @author Christoph Knauf - Initial Contribution
 * @author Karel Goderis - Migration to ThreadPoolManager based scheduler
 *
 */
public class CronTriggerHandler extends BaseModuleHandler<Trigger>implements TriggerHandler {

    private final Logger logger = LoggerFactory.getLogger(CronTriggerHandler.class);

    private RuleEngineCallback callback;
    private CallbackJob job;
    private CronExpression cronExpression;

    public static final String MODULE_TYPE_ID = "CronTrigger";
    private static final String CFG_CRON_EXPRESSION = "cronExpression";

    public CronTriggerHandler(Trigger module) {
        super(module);
        String cronExpression = (String) module.getConfiguration().get(CFG_CRON_EXPRESSION);
        try {
            this.cronExpression = new CronExpression(cronExpression);
        } catch (ParseException e) {
            logger.error("An exception occurred while parsing a cron expression: {}", e.getMessage());
        }
    }

    @Override
    public void setRuleEngineCallback(RuleEngineCallback ruleCallback) {
        this.callback = ruleCallback;
        this.job = new CallbackJob();
        try {
            ExpressionThreadPoolExecutor scheduler = ThreadPoolManager.getExpressionScheduledPool("automation");
            job.put(TimerModuleHandlerFactory.CALLBACK_CONTEXT_NAME, this.callback);
            job.put(TimerModuleHandlerFactory.MODULE_CONTEXT_NAME, this.module);
            scheduler.schedule(job, cronExpression);
        } catch (Exception e) {
            logger.error("Error while scheduling Job: {}", e.getMessage());
        }
    }

    @Override
    public void dispose() {
        try {
            ExpressionThreadPoolExecutor scheduler = ThreadPoolManager.getExpressionScheduledPool("automation");
            if (scheduler != null && job != null) {
                scheduler.remove(job);
            }
            job = null;
        } catch (Exception e) {
            logger.error("Error while disposing Job: {}", e.getMessage());
        }

    }
}
