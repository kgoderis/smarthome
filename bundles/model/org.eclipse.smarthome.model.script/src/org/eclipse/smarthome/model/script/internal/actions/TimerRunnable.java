/**
 * Copyright (c) 2014-2015 openHAB UG (haftungsbeschraenkt) and others.
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v1.0
 * which accompanies this distribution, and is available at
 * http://www.eclipse.org/legal/epl-v10.html
 */
package org.eclipse.smarthome.model.script.internal.actions;

import java.text.ParseException;

import org.eclipse.smarthome.core.common.ContextRunnable;
import org.eclipse.smarthome.core.common.DateExpression;
import org.eclipse.smarthome.core.common.ThreadPoolManager.ExpressionThreadPoolExecutor;
import org.eclipse.smarthome.model.script.actions.Timer;
import org.eclipse.xtext.xbase.lib.Procedures.Procedure0;
import org.joda.time.DateTime;
import org.joda.time.base.AbstractInstant;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * This is a Runnable {@link ContextRunnable} which executes the code of a closure that is passed
 * to the createTimer() extension method.
 *
 * @author Kai Kreuzer - Initial contribution and API
 * @author Karel Goderis - Migration to internal scheduler infrastructure
 *
 */
public class TimerRunnable extends ContextRunnable implements Timer {

    private final Logger logger = LoggerFactory.getLogger(TimerRunnable.class);

    private AbstractInstant startTime;
    private Procedure0 closure;
    private ExpressionThreadPoolExecutor scheduler;

    private boolean cancelled = false;
    private boolean terminated = false;

    public TimerRunnable(AbstractInstant instant, Procedure0 closure, ExpressionThreadPoolExecutor scheduler) {
        this.startTime = instant;
        this.closure = closure;
        this.scheduler = scheduler;
    }

    /**
     * Runs the configured closure of this job
     *
     * @param context the execution context of the job
     */
    @Override
    public void run() {
        closure.apply();
        setTerminated(true);
    }

    @Override
    public boolean cancel() {
        try {
            boolean result = scheduler.remove(this);
            if (result) {
                cancelled = true;
            }
        } catch (Exception e) {
            logger.warn("An error occured while cancelling the job '{}': {}",
                    new Object[] { this.toString(), e.getMessage() });
        }
        return cancelled;
    }

    @Override
    public boolean isRunning() {

        try {
            for (Runnable runnable : scheduler.getRunning()) {
                if (runnable.equals(this)) {
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
        // TODO Auto-generated method stub
        return false;
    }

    public void setTerminated(boolean terminated) {
        this.terminated = terminated;
    }

    @Override
    public boolean reschedule(AbstractInstant newTime) {
        try {
            cancel();

            DateExpression dateExpression = null;
            try {
                dateExpression = new DateExpression(newTime.toDate());
            } catch (ParseException e1) {
                logger.error("An exception occurred while creating a time for execution at {}", newTime.toDate());
            }

            scheduler.schedule(this, dateExpression);

            this.cancelled = false;
            this.terminated = false;
            return true;
        } catch (Exception e) {
            logger.warn("An error occured while rescheduling the job '{}': {}",
                    new Object[] { this.toString(), e.getMessage() });
            return false;
        }
    }

}
