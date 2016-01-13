/**
 * Copyright (c) 1997, 2015 by ProSyst Software GmbH and others.
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v1.0
 * which accompanies this distribution, and is available at
 * http://www.eclipse.org/legal/epl-v10.html
 */
package org.eclipse.smarthome.automation.module.timer.handler;

import java.util.Date;
import java.util.UUID;

import org.eclipse.smarthome.automation.Trigger;
import org.eclipse.smarthome.automation.handler.BaseModuleHandler;
import org.eclipse.smarthome.automation.handler.RuleEngineCallback;
import org.eclipse.smarthome.automation.handler.TriggerHandler;
import org.eclipse.smarthome.automation.module.timer.factory.TimerModuleHandlerFactory;
import org.eclipse.smarthome.core.scheduler.internal.quartz.RecurrenceRuleScheduleBuilder;
import org.eclipse.smarthome.core.scheduler.internal.quartz.RecurrenceRuleTrigger;
import org.quartz.JobBuilder;
import org.quartz.JobDetail;
import org.quartz.Scheduler;
import org.quartz.SchedulerException;
import org.quartz.TriggerBuilder;
import org.quartz.impl.StdSchedulerFactory;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * This is an ModuleHandler implementation for Triggers which trigger the rule
 * based on a cron expression. The cron expression can be set with the
 * configuration.
 *
 * @author Christoph Knauf - Initial Contribution
 *
 */
public class RecurrenceTriggerHandler extends BaseModuleHandler<Trigger>implements TriggerHandler {

    private final Logger logger = LoggerFactory.getLogger(RecurrenceTriggerHandler.class);

    private RuleEngineCallback callback;
    private JobDetail job;
    private RecurrenceRuleTrigger trigger;
    private Scheduler scheduler;

    public static final String MODULE_TYPE_ID = "RecurrenceTrigger";
    private static final String CFG_RRULE_EXPRESSION = "RRuleExpression";
    private static final String CFG_RRULE_CALENDAR = "RRuleCalendar";
    private static final String CFG_START_DATE = "StartDate";

    public RecurrenceTriggerHandler(Trigger module) {
        super(module);
        String recurrenceRuleExpression = (String) module.getConfiguration().get(CFG_RRULE_EXPRESSION);
        String calendarName = (String) module.getConfiguration().get(CFG_RRULE_CALENDAR);
        Date date = (Date) module.getConfiguration().get(CFG_START_DATE);

        TriggerBuilder builder = TriggerBuilder.newTrigger().withIdentity(MODULE_TYPE_ID + UUID.randomUUID().toString())
                .withSchedule(RecurrenceRuleScheduleBuilder.recurrenceRuleSchedule(recurrenceRuleExpression)
                        .withMisfireHandlingInstructionDoNothing().withStartTime(date));
        if (calendarName != null) {
            builder.modifiedByCalendar(calendarName);
        }
        this.trigger = (RecurrenceRuleTrigger) builder.build();
    }

    @Override
    public void setRuleEngineCallback(RuleEngineCallback ruleCallback) {
        this.callback = ruleCallback;
        this.job = JobBuilder.newJob(CallbackJob.class).withIdentity(MODULE_TYPE_ID + UUID.randomUUID().toString())
                .build();
        try {
            this.scheduler = new StdSchedulerFactory().getScheduler();
            scheduler.start();
            scheduler.getContext().put(TimerModuleHandlerFactory.CALLBACK_CONTEXT_NAME, this.callback);
            scheduler.getContext().put(TimerModuleHandlerFactory.MODULE_CONTEXT_NAME, this.module);
            scheduler.scheduleJob(job, trigger);
        } catch (SchedulerException e) {
            logger.error("Error while scheduling Job: {}", e.getMessage());
        }
    }

    @Override
    public void dispose() {
        try {
            if (scheduler != null && job != null) {
                scheduler.deleteJob(job.getKey());
            }
            scheduler = null;
            trigger = null;
            job = null;
        } catch (SchedulerException e) {
            logger.error("Error while disposing Job: {}", e.getMessage());
        }

    }
}
