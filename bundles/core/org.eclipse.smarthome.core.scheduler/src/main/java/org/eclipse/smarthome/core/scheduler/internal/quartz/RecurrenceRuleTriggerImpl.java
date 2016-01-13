/**
 *
 */
package org.eclipse.smarthome.core.scheduler.internal.quartz;

import java.text.ParseException;
import java.util.Calendar;
import java.util.Date;

import org.quartz.ScheduleBuilder;
import org.quartz.Trigger;
import org.quartz.impl.triggers.AbstractTrigger;
import org.quartz.impl.triggers.CoreTrigger;

/**
 * <p>
 * A concrete <code>{@link Trigger}</code> that is used to fire a
 * <code>{@link org.quartz.JobDetail}</code> at given moments in time, defined
 * with recurrence rules as defined in RFC 5545.
 * </p>
 *
 * @author Karel Goderis - Initial contribution
 */
public class RecurrenceRuleTriggerImpl extends AbstractTrigger<RecurrenceRuleTrigger>
        implements RecurrenceRuleTrigger, CoreTrigger {

    /** Serial Version UID. */
    private static final long serialVersionUID = 1233337060012692693L;

    private static final int YEAR_TO_GIVEUP_SCHEDULING_AT = java.util.Calendar.getInstance()
            .get(java.util.Calendar.YEAR) + 100;

    /** The recurrence rule that defines the schedule of the trigger. */
    private RecurrenceRule recurrenceRule = null;
    /** The next fire date of the trigger. */
    private Date nextFireTime = null;
    /** The previous fire date of the trigger. */
    private Date previousFireTime = null;

    /**
     * <p>
     * Create a <code>RecurRuleTrigger</code> with no settings.
     * </p>
     *
     * <p>
     * The start-time will also be set to the current time, and the time zone
     * will be set the the system's default time zone.
     * </p>
     */
    public RecurrenceRuleTriggerImpl() {
        super();
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public Object clone() {
        RecurrenceRuleTriggerImpl copy = (RecurrenceRuleTriggerImpl) super.clone();
        if (recurrenceRule != null) {
            copy.setRecurrenceRule((RecurrenceRule) recurrenceRule.clone());
        }
        return copy;
    }

    /**
     * Sets the recurrence rule defined by a string compliant with RFC5545.
     *
     * @param rruleExpression
     *            The string that represents the recurrence rule.
     * @throws ParseException
     *             Thrown if the string does not meet requirements of the
     *             RFC5545.
     */
    public void setRecurrenceRuleExpression(String rruleExpression) throws ParseException {
        recurrenceRule = new RecurrenceRule(rruleExpression);
    }

    /**
     * Sets the recurrence rule to the given one.
     *
     * @param rrule
     *            The recurrence rule to set up.
     */
    public void setRecurrenceRule(RecurrenceRule rrule) {
        this.recurrenceRule = (RecurrenceRule) rrule.clone();
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public String getRecurrenceRuleExpression() {
        if (recurrenceRule != null) {
            return recurrenceRule.toString();
        } else {
            return null;
        }
    }

    /**
     * <p>
     * Get the time at which the <code>RecurrenceRuleTrigger</code> should
     * occur.
     * </p>
     *
     * @return the start time of the recurrence rule.
     */
    @Override
    public Date getStartTime() {
        return recurrenceRule.getStartDate();
    }

    /**
     * <p>
     * Set the time at which the <code>RecurrenceRuleTrigger</code> should
     * occur.
     * </p>
     *
     * @param startTime
     *            the start time of the recurrence rule.
     */
    @Override
    public void setStartTime(Date startTime) {
        if (startTime == null) {
            throw new IllegalArgumentException("Start time cannot be null");
        }

        Date eTime = getEndTime();
        if (eTime != null && startTime != null && eTime.before(startTime)) {
            throw new IllegalArgumentException("End time cannot be before start time");
        }

        Calendar cl = Calendar.getInstance();
        cl.setTime(startTime);
        cl.set(Calendar.MILLISECOND, 0);

        this.recurrenceRule.setStartDate(cl.getTime());
    }

    /**
     * <p>
     * Get the time at which the <code>RecurrenceRuleTrigger</code> should quit
     * repeating.
     * </p>
     *
     * @return the end time of the recurrence rule
     * @see #getFinalFireTime()
     */
    @Override
    public Date getEndTime() {
        if (recurrenceRule == null) {
            return null;
        } else {
            return recurrenceRule.getUntil();
        }
    }

    /**
     * <p>
     * Set the time at which the <code>RecurrenceRuleTrigger</code> should quit
     * repeating.
     * </p>
     *
     * @param endTime
     *            the end time of the recurrence rule
     */
    @Override
    public void setEndTime(Date endTime) {
        Date sTime = getStartTime();
        if (sTime != null && endTime != null && sTime.after(endTime)) {
            throw new IllegalArgumentException("End time cannot be before start time");
        }

        if (recurrenceRule != null && endTime != null) {
            recurrenceRule.setUntil(endTime);
        }
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public int getRepeatCount() {
        if (recurrenceRule == null) {
            return REPEAT_INDEFINITELY;
        } else {
            return recurrenceRule.getCount();
        }
    }

    /**
     * <p>
     * Set the number of times the <code>RecurrenceRuleTrigger</code> should
     * occur.
     * </p>
     *
     * @param repeatCount
     *            The number of occurrences.
     */
    public void setRepeatCount(int repeatCount) {
        if (repeatCount < 0 && repeatCount != REPEAT_INDEFINITELY) {
            throw new IllegalArgumentException(
                    "Repeat count must be >= 0, use the " + "constant REPEAT_INDEFINITELY for infinite.");
        }

        if (recurrenceRule != null) {
            recurrenceRule.setCount(repeatCount);
        }
    }

    /**
     * <p>
     * Returns the next time at which the <code>Trigger</code> is scheduled to
     * fire. If the trigger will not fire again, <code>null</code> will be
     * returned. Note that the time returned can possibly be in the past, if the
     * time that was computed for the trigger to next fire has already arrived,
     * but the scheduler has not yet been able to fire the trigger (which would
     * likely be due to lack of resources e.g. threads).
     * </p>
     *
     * <p>
     * The value returned is not guaranteed to be valid until after the
     * <code>Trigger</code> has been added to the scheduler.
     * </p>
     *
     * @return the next fire time.
     *
     * @see org.quartz.TriggerUtils#computeFireTimesBetween(Trigger, org.quartz.Calendar ,
     *      Date, Date)
     */
    @Override
    public Date getNextFireTime() {
        return this.nextFireTime;
    }

    /**
     * <p>
     * Returns the previous time at which the <code>RecurrenceRuleTrigger</code>
     * fired. If the trigger has not yet fired, <code>null</code> will be
     * returned.
     *
     * @return the previous fire time.
     */
    @Override
    public Date getPreviousFireTime() {
        return this.previousFireTime;
    }

    /**
     * <p>
     * Sets the next time at which the <code>RecurrenceRuleTrigger</code> will
     * fire.
     * </p>
     *
     * <p>
     * <b>This method should not be invoked by client code.</b>
     * </p>
     *
     * @param fireTime
     *            the next fire time.
     */
    @Override
    public void setNextFireTime(Date fireTime) {
        this.nextFireTime = fireTime;
    }

    /**
     * <p>
     * Set the previous time at which the <code>RecurrenceRuleTrigger</code>
     * fired.
     * </p>
     *
     * <p>
     * <b>This method should not be invoked by client code.</b>
     * </p>
     *
     * @param fireTime
     *            the next fire time.
     */
    @Override
    public void setPreviousFireTime(Date fireTime) {
        this.previousFireTime = fireTime;
    }

    /**
     * <p>
     * Returns the next time at which the <code>RecurrenceRuleTrigger</code>
     * will fire, after the given time. If the trigger will not fire after the
     * given time, <code>null</code> will be returned.
     * </p>
     *
     * <p>
     * Note that the date returned is NOT validated against the related
     * org.quartz.Calendar (if any)
     * </p>
     *
     * @param afterTime
     *            The time to start the search.
     * @return the next fire time after <code>afterTime</code>.
     */
    @Override
    public Date getFireTimeAfter(Date afterTime) {
        Date after = null;
        if (afterTime == null) {
            after = new Date();
        } else {
            after = (Date) afterTime.clone();
        }

        if (getStartTime().after(after)) {
            after = new Date(getStartTime().getTime() - 1000l);
        }

        if (getEndTime() != null && (after.compareTo(getEndTime()) >= 0)) {
            return null;
        }

        Date pot = getTimeAfter(after);
        if (getEndTime() != null && pot != null && pot.after(getEndTime())) {
            return null;
        }

        return pot;
    }

    /**
     * <p>
     * Returns the final time at which the <code>RecurrenceRuleTrigger</code>
     * will fire.
     * </p>
     *
     * <p>
     * Note that the return time *may* be in the past. and the date returned is
     * not validated against org.quartz.calendar
     * </p>
     *
     * @return the final fire time.
     */
    @Override
    public Date getFinalFireTime() {
        Date resultTime;
        if (recurrenceRule != null) {
            resultTime = recurrenceRule.getFinalFireTime();
        } else {
            resultTime = null;
        }

        if ((resultTime != null) && (getStartTime() != null) && (resultTime.before(getStartTime()))) {
            return null;
        }

        return resultTime;
    }

    /**
     * <p>
     * Determines whether or not the <code>RecurrenceRuleTrigger</code> will
     * occur again.
     * </p>
     *
     * @return true if the trigger may occur again
     */
    @Override
    public boolean mayFireAgain() {
        return (getNextFireTime() != null);
    }

    /**
     * {@inheritDoc}
     */
    @Override
    protected boolean validateMisfireInstruction(int misfireInstruction) {
        if (misfireInstruction < MISFIRE_INSTRUCTION_IGNORE_MISFIRE_POLICY) {
            return false;
        }

        if (misfireInstruction > MISFIRE_INSTRUCTION_DO_NOTHING) {
            return false;
        }

        return true;
    }

    /**
     * <p>
     * Updates the <code>RecurrenceRuleTrigger</code>'s state based on the
     * MISFIRE_INSTRUCTION_XXX that was selected when the
     * <code>RecurrenceRuleTrigger</code> was created.
     * </p>
     *
     * <p>
     * If the misfire instruction is set to MISFIRE_INSTRUCTION_SMART_POLICY,
     * then the following scheme will be used: <br>
     * <ul>
     * <li>The instruction will be interpreted as
     * <code>MISFIRE_INSTRUCTION_FIRE_ONCE_NOW</code>
     * </ul>
     * </p>
     */
    @Override
    public void updateAfterMisfire(org.quartz.Calendar cal) {
        int instr = getMisfireInstruction();

        if (instr == Trigger.MISFIRE_INSTRUCTION_IGNORE_MISFIRE_POLICY) {
            return;
        }

        if (instr == MISFIRE_INSTRUCTION_SMART_POLICY) {
            instr = MISFIRE_INSTRUCTION_FIRE_ONCE_NOW;
        }

        if (instr == MISFIRE_INSTRUCTION_DO_NOTHING) {
            Date newFireTime = getFireTimeAfter(new Date());
            while (newFireTime != null && cal != null && !cal.isTimeIncluded(newFireTime.getTime())) {
                newFireTime = getFireTimeAfter(newFireTime);
            }
            setNextFireTime(newFireTime);
        } else if (instr == MISFIRE_INSTRUCTION_FIRE_ONCE_NOW) {
            setNextFireTime(new Date());
        }
    }

    /**
     * <p>
     * Determines whether the date and (optionally) time of the given Calendar
     * instance falls on a scheduled fire-time of this trigger.
     * </p>
     *
     * <p>
     * Equivalent to calling <code>willFireOn(cal, false)</code>.
     * </p>
     *
     * @param test
     *            the date to compare
     * @return true if the trigger fires at the given date.
     *
     * @see #willFireOn(Calendar, boolean)
     */
    public boolean willFireOn(Calendar test) {
        return willFireOn(test, false);
    }

    /**
     * <p>
     * Determines whether the date and (optionally) time of the given Calendar
     * instance falls on a scheduled fire-time of this trigger.
     * </p>
     *
     * <p>
     * Note that the value returned is NOT validated against the related
     * org.quartz.Calendar (if any)
     * </p>
     *
     * @param cal
     *            the date to compare
     * @param dayOnly
     *            if set to true, the method will only determine if the trigger
     *            will fire during the day represented by the given Calendar
     *            (hours, minutes and seconds will be ignored).
     * @see #willFireOn(Calendar)
     */
    public boolean willFireOn(Calendar cal, boolean dayOnly) {
        if (recurrenceRule == null) {
            return false;
        } else {
            return recurrenceRule.contains(cal.getTime(), dayOnly);
        }
    }

    /**
     * <p>
     * Called when the <code>{@link org.quartz.Scheduler}</code> has decided to 'fire' the
     * trigger (execute the associated <code>Job</code>), in order to give the
     * <code>Trigger</code> a chance to update itself for its next triggering
     * (if any).
     * </p>
     *
     * @see #executionComplete(org.quartz.JobExecutionContext, org.quartz.JobExecutionException)
     */
    @Override
    public void triggered(org.quartz.Calendar calendar) {
        previousFireTime = nextFireTime;
        nextFireTime = getFireTimeAfter(nextFireTime);

        while (nextFireTime != null && calendar != null && !calendar.isTimeIncluded(nextFireTime.getTime())) {
            nextFireTime = getFireTimeAfter(nextFireTime);
        }
    }

    @Override
    public void updateWithNewCalendar(org.quartz.Calendar calendar, long misfireThreshold) {
        nextFireTime = getFireTimeAfter(previousFireTime);

        if (nextFireTime == null || calendar == null) {
            return;
        }

        Date now = new Date();
        while (nextFireTime != null && !calendar.isTimeIncluded(nextFireTime.getTime())) {

            nextFireTime = getFireTimeAfter(nextFireTime);

            if (nextFireTime == null) {
                break;
            }

            // avoid infinite loop
            // Use gregorian only because the constant is based on Gregorian
            java.util.Calendar c = new java.util.GregorianCalendar();
            c.setTime(nextFireTime);
            if (c.get(java.util.Calendar.YEAR) > YEAR_TO_GIVEUP_SCHEDULING_AT) {
                nextFireTime = null;
            }

            if (nextFireTime != null && nextFireTime.before(now)) {
                long diff = now.getTime() - nextFireTime.getTime();
                if (diff >= misfireThreshold) {
                    nextFireTime = getFireTimeAfter(nextFireTime);
                    continue;
                }
            }
        }
    }

    /**
     * <p>
     * Called by the scheduler at the time a <code>Trigger</code> is first added
     * to the scheduler, in order to have the <code>Trigger</code> compute its
     * first fire time, based on any associated calendar.
     * </p>
     *
     * <p>
     * After this method has been called, <code>getNextFireTime()</code> should
     * return a valid answer.
     * </p>
     *
     * @return the first time at which the <code>Trigger</code> will be fired by
     *         the scheduler, which is also the same value
     *         <code>getNextFireTime()</code> will return (until after the first
     *         firing of the <code>Trigger</code>).
     *         </p>
     */
    @Override
    public Date computeFirstFireTime(org.quartz.Calendar calendar) {
        nextFireTime = getFireTimeAfter(new Date(getStartTime().getTime() - 1000l));

        while (nextFireTime != null && calendar != null && !calendar.isTimeIncluded(nextFireTime.getTime())) {
            nextFireTime = getFireTimeAfter(nextFireTime);
        }

        return nextFireTime;
    }

    /**
     * Used by extensions of CronTrigger to imply that there are additional
     * properties, specifically so that extensions can choose whether to be
     * stored as a serialized blob, or as a flattened RecurrenceRuleTrigger
     * table.
     */
    @Override
    public boolean hasAdditionalProperties() {
        return false;
    }

    /**
     * Get a {@link ScheduleBuilder} that is configured to produce a schedule
     * identical to this trigger's schedule.
     *
     * @see #getTriggerBuilder()
     */
    @Override
    public ScheduleBuilder<RecurrenceRuleTrigger> getScheduleBuilder() {

        RecurrenceRuleScheduleBuilder cb = RecurrenceRuleScheduleBuilder
                .recurrenceRuleSchedule(getRecurrenceRuleExpression());

        if (MISFIRE_INSTRUCTION_DO_NOTHING == getMisfireInstruction()) {
            cb.withMisfireHandlingInstructionDoNothing();
        } else if (MISFIRE_INSTRUCTION_FIRE_ONCE_NOW == getMisfireInstruction()) {
            cb.withMisfireHandlingInstructionFireAndProceed();
        }

        return cb;
    }

    protected Date getTimeAfter(Date afterTime) {
        return (recurrenceRule == null) ? null : recurrenceRule.getTimeAfter(afterTime);
    }

    protected Date getTimeBefore(Date eTime) {
        return (recurrenceRule == null) ? null : recurrenceRule.getTimeBefore(eTime);
    }
}
