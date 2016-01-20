/**
 * Copyright (c) 2014-2015 openHAB UG (haftungsbeschraenkt) and others.
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v1.0
 * which accompanies this distribution, and is available at
 * http://www.eclipse.org/legal/epl-v10.html
 */
package org.eclipse.smarthome.model.script.internal.actions;

import org.eclipse.smarthome.core.common.ThreadPoolManager;
import org.eclipse.smarthome.core.common.ThreadPoolManager.ExpressionThreadPoolExecutor;
import org.eclipse.smarthome.model.script.actions.ScriptExecution;
import org.eclipse.smarthome.model.script.actions.Timer;
import org.eclipse.xtext.xbase.lib.Procedures.Procedure0;
import org.joda.time.DateTime;
import org.joda.time.base.AbstractInstant;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * This is an implementation of the {@link Timer} interface using the Quartz
 * library for scheduling.
 *
 * @author Kai Kreuzer - Initial contribution and API
 *
 */
public class TimerImpl implements Timer {

    private final Logger logger = LoggerFactory.getLogger(TimerImpl.class);

    // the scheduler used for timer events
    public static ExpressionThreadPoolExecutor scheduler;

    static {
        scheduler = ThreadPoolManager.getExpressionScheduledPool("automation");
    }

    private TimerExecutionJob job;
    // private TriggerKey triggerKey;
    private AbstractInstant startTime;
    private Procedure0 closure;

    private boolean cancelled = false;
    private boolean terminated = false;

    public TimerImpl(TimerExecutionJob job, AbstractInstant startTime, Procedure0 closure) {
        this.job = job;
        // this.triggerKey = triggerKey;
        this.startTime = startTime;
        this.closure = closure;
    }

    @Override
    public boolean cancel() {
        try {
            boolean result = scheduler.remove(job);
            if (result) {
                cancelled = true;
            }
        } catch (Exception e) {
            logger.warn("An error occured while cancelling the job '{}': {}",
                    new Object[] { job.toString(), e.getMessage() });
        }
        return cancelled;
    }

    @Override
    public boolean reschedule(AbstractInstant newTime) {
        try {
            // Trigger trigger = newTrigger().startAt(newTime.toDate()).build();
            // DateExpression exprssion = new DateExpression(newTime.toDate());
            // scheduler.rescheduleJob(triggerKey, trigger);
            // this.triggerKey = trigger.getKey();
            ScriptExecution.createTimer(newTime, closure);
            this.cancelled = false;
            this.terminated = false;
            return true;
        } catch (Exception e) {
            logger.warn("An error occured while rescheduling the job '{}': {}",
                    new Object[] { job.toString(), e.getMessage() });
            return false;
        }
    }

    @Override
    public boolean isRunning() {

        try {
            for (Runnable runnable : scheduler.getRunning()) {
                if (runnable.equals(job)) {
                    return true;
                }
            }
            return false;
        } catch (Exception e) {
            // fallback implementation
            logger.debug("An error occured getting currently running jobs: {}", e.getMessage());
            return DateTime.now().isAfter(startTime) && !terminated;
        }
    }

    @Override
    public boolean hasTerminated() {
        return terminated;
    }

    public void setTerminated(boolean terminated) {
        this.terminated = terminated;
    }
}
