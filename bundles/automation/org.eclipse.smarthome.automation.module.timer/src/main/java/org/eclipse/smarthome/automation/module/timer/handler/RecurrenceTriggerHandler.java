/**
 * Copyright (c) 1997, 2015 by ProSyst Software GmbH and others.
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v1.0
 * which accompanies this distribution, and is available at
 * http://www.eclipse.org/legal/epl-v10.html
 */
package org.eclipse.smarthome.automation.module.timer.handler;

import java.text.ParseException;
import java.util.Date;

import org.eclipse.smarthome.automation.Trigger;
import org.eclipse.smarthome.automation.handler.BaseModuleHandler;
import org.eclipse.smarthome.automation.handler.RuleEngineCallback;
import org.eclipse.smarthome.automation.handler.TriggerHandler;
import org.eclipse.smarthome.automation.module.timer.factory.TimerModuleHandlerFactory;
import org.eclipse.smarthome.core.common.RecurrenceExpression;
import org.eclipse.smarthome.core.common.ThreadPoolManager;
import org.eclipse.smarthome.core.common.ThreadPoolManager.ExpressionThreadPoolExecutor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * This is an ModuleHandler implementation for Triggers which trigger the rule
 * based on a RFC5545 expression. The expression can be set with the
 * configuration.
 *
 * @author Karel Goderis - Initial Contribution
 *
 */
public class RecurrenceTriggerHandler extends BaseModuleHandler<Trigger>implements TriggerHandler {

    private final Logger logger = LoggerFactory.getLogger(RecurrenceTriggerHandler.class);

    private RuleEngineCallback callback;
    private CallbackJob job;
    private RecurrenceExpression recurrenceExpression;

    public static final String MODULE_TYPE_ID = "RecurrenceTrigger";
    private static final String CFG_RRULE_EXPRESSION = "RRuleExpression";
    private static final String CFG_START_DATE = "StartDate";

    public RecurrenceTriggerHandler(Trigger module) {
        super(module);
        String recurrenceRuleExpression = (String) module.getConfiguration().get(CFG_RRULE_EXPRESSION);
        Date date = (Date) module.getConfiguration().get(CFG_START_DATE);

        try {
            this.recurrenceExpression = new RecurrenceExpression(recurrenceRuleExpression, date);
        } catch (ParseException e) {
            logger.error("An exception occurred while parsing a recurrence expression: {}", e.getMessage());
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
            scheduler.schedule(job, recurrenceExpression);
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
