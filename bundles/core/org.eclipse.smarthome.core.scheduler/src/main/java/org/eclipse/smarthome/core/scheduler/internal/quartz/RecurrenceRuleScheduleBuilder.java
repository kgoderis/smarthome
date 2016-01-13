package org.eclipse.smarthome.core.scheduler.internal.quartz;

import java.text.ParseException;
import java.util.Date;
import java.util.TimeZone;

import org.quartz.CalendarIntervalScheduleBuilder;
import org.quartz.CronExpression;
import org.quartz.CronTrigger;
import org.quartz.ScheduleBuilder;
import org.quartz.SimpleScheduleBuilder;
import org.quartz.Trigger;
import org.quartz.TriggerBuilder;
import org.quartz.spi.MutableTrigger;

/**
 * <code>RecurrenceRuleScheduleBuilder</code> is a {@link ScheduleBuilder} that
 * defines {@link RecurrenceRule}-based schedules for <code>Trigger</code>s.
 *
 * <p>
 * Quartz provides a builder-style API for constructing scheduling-related
 * entities via a Domain-Specific Language (DSL). The DSL can best be utilized
 * through the usage of static imports of the methods on the classes
 * <code>TriggerBuilder</code>, <code>JobBuilder</code>,
 * <code>DateBuilder</code>, <code>JobKey</code>, <code>TriggerKey</code> and
 * the various <code>ScheduleBuilder</code> implementations.
 * </p>
 *
 * <p>
 * Client code can then use the DSL to write code such as this:
 * </p>
 *
 * <pre>
 * JobDetail job = newJob(MyJob.class).withIdentity(&quot;myJob&quot;).build();
 *
 * Trigger trigger = newTrigger().withIdentity(triggerKey(&quot;myTrigger&quot;, &quot;myTriggerGroup&quot;))
 *         .withSchedule(RecurrenceRuleScheduleBuilder.recurrenceRuleSchedule("SomeRRuleExpression")).build();
 *
 * scheduler.scheduleJob(job, trigger);
 * </pre>
 *
 * @see RecurrenceRule
 * @see RecurrenceRuleTrigger
 * @see ScheduleBuilder
 * @see SimpleScheduleBuilder
 * @see CalendarIntervalScheduleBuilder
 * @see TriggerBuilder
 *
 * @author Karel Goderis - Initial Contribution
 */
public class RecurrenceRuleScheduleBuilder extends ScheduleBuilder<RecurrenceRuleTrigger> {

    /** The recurrence rule. */
    private RecurrenceRule recurrenceRule;
    /** The policy to handle misfire instructions. */
    private int misfireInstruction = RecurrenceRuleTrigger.MISFIRE_INSTRUCTION_SMART_POLICY;
    private Date startDate;

    /**
     * Constructor.
     *
     * @param rrule
     *            The recurrence rule.
     */
    protected RecurrenceRuleScheduleBuilder(final RecurrenceRule rrule) {
        if (rrule == null) {
            throw new NullPointerException("recurrence rule cannot be null");
        }
        this.recurrenceRule = rrule;
    }

    /**
     * Build the actual Trigger -- NOT intended to be invoked by end users, but
     * will rather be invoked by a TriggerBuilder which this ScheduleBuilder is
     * given to.
     *
     * @return The trigger that has been built.
     *
     * @see TriggerBuilder#withSchedule(ScheduleBuilder)
     */
    @Override
    protected MutableTrigger build() {
        RecurrenceRuleTriggerImpl ct = new RecurrenceRuleTriggerImpl();

        ct.setRecurrenceRule(recurrenceRule);
        ct.setMisfireInstruction(misfireInstruction);
        if (startDate != null) {
            ct.setStartTime(startDate);
        } else {
            ct.setStartTime(new Date());
        }

        return ct;
    }

    /**
     * Create a RecurrenceRuleScheduleBuilder with the given recurrence rule
     * expression string - which is presumed to be valid RFC5545 recurrence rule
     * expression (and hence only a RuntimeException will be thrown if it is
     * not).
     *
     * @param recurrenceRuleExpression
     *            the recurrence rule expression string to base the schedule on.
     * @return the new RecurrenceRuleScheduleBuilder
     *
     * @see RecurrenceRule
     */
    public static RecurrenceRuleScheduleBuilder recurrenceRuleSchedule(final String recurrenceRuleExpression) {
        try {
            return recurrenceRuleSchedule(new RecurrenceRule(recurrenceRuleExpression));
        } catch (ParseException e) {
            // all methods of construction ensure the expression is valid by
            // this point...
            throw new RuntimeException("RecurrenceRuleExpression '" + recurrenceRuleExpression + "' is invalid,.", e);
        }
    }

    /**
     * Create a RecurrenceRuleScheduleBuilder with the given recurrence rule
     * expression string - which may not be a valid RFC5545 recurrence rule
     * expression (and hence a ParseException will be thrown if it is not).
     *
     * @param recurrenceRuleExpression
     *            the recurrence rule expression string to base the schedule on.
     * @return the new RecurrenceRuleScheduleBuilder
     * @throws ParseException
     *             if the expression is invalid
     * @see RecurrenceRule
     */
    public static RecurrenceRuleScheduleBuilder recurrenceRuleScheduleNonvalidatedExpression(
            final String recurrenceRuleExpression) throws ParseException {
        return recurrenceRuleSchedule(new RecurrenceRule(recurrenceRuleExpression));
    }

    /**
     * Create a RecurrenceRuleScheduleBuilder with the given recurrence rule
     * expression string - which is presumed to be a valid RFC5545 recurrence
     * rule expression.
     *
     * @param presumedValidRecurrenceRuleExpression
     *            the recurrence rule expression string to base the schedule on.
     * @return the new RecurrenceRuleScheduleBuilder
     *
     * @see RecurrenceRule
     */
    private static RecurrenceRuleScheduleBuilder recurrenceRuleScheduleNoParseException(
            final String presumedValidRecurrenceRuleExpression) {
        try {
            return recurrenceRuleSchedule(new RecurrenceRule(presumedValidRecurrenceRuleExpression));
        } catch (ParseException e) {
            // all methods of construction ensure the expression is valid by
            // this point...
            throw new RuntimeException("RecurrenceRuleExpression '" + presumedValidRecurrenceRuleExpression
                    + "' is invalid, which should not be possible, please report bug to Quartz developers.", e);
        }
    }

    /**
     * Create a RecurrenceRuleScheduleBuilder with the given recurrence rule
     * expression.
     *
     * @param rrule
     *            the recurrence rule expression to base the schedule on.
     * @return the new RecurrenceRuleScheduleBuilder
     * @see RecurrenceRule
     */
    public static RecurrenceRuleScheduleBuilder recurrenceRuleSchedule(final RecurrenceRule rrule) {
        return new RecurrenceRuleScheduleBuilder(rrule);
    }

    /**
     * The <code>TimeZone</code> in which to base the schedule.
     *
     * @param timezone
     *            the time-zone for the schedule.
     * @return the updated RecurrenceRuleScheduleBuilder
     * @see CronExpression#getTimeZone()
     */
    public RecurrenceRuleScheduleBuilder inTimeZone(final TimeZone timezone) {
        recurrenceRule.setTimeZone(timezone);
        return this;
    }

    /**
     * If the Trigger misfires, use the
     * {@link Trigger#MISFIRE_INSTRUCTION_IGNORE_MISFIRE_POLICY} instruction.
     *
     * @return the updated RecurrenceRuleScheduleBuilder
     * @see Trigger#MISFIRE_INSTRUCTION_IGNORE_MISFIRE_POLICY
     */
    public RecurrenceRuleScheduleBuilder withMisfireHandlingInstructionIgnoreMisfires() {
        misfireInstruction = Trigger.MISFIRE_INSTRUCTION_IGNORE_MISFIRE_POLICY;
        return this;
    }

    /**
     * If the Trigger misfires, use the
     * {@link CronTrigger#MISFIRE_INSTRUCTION_DO_NOTHING} instruction.
     *
     * @return the updated RecurrenceRuleScheduleBuilder
     * @see CronTrigger#MISFIRE_INSTRUCTION_DO_NOTHING
     */
    public RecurrenceRuleScheduleBuilder withMisfireHandlingInstructionDoNothing() {
        misfireInstruction = CronTrigger.MISFIRE_INSTRUCTION_DO_NOTHING;
        return this;
    }

    /**
     * If the Trigger misfires, use the
     * {@link CronTrigger#MISFIRE_INSTRUCTION_FIRE_ONCE_NOW} instruction.
     *
     * @return the updated RecurrenceRuleScheduleBuilder
     * @see CronTrigger#MISFIRE_INSTRUCTION_FIRE_ONCE_NOW
     */
    public RecurrenceRuleScheduleBuilder withMisfireHandlingInstructionFireAndProceed() {
        misfireInstruction = CronTrigger.MISFIRE_INSTRUCTION_FIRE_ONCE_NOW;
        return this;
    }

    public RecurrenceRuleScheduleBuilder withStartTime(Date date) {
        if (date != null) {
            this.startDate = date;
        }
        return this;
    }
}
