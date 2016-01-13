package org.eclipse.smarthome.core.scheduler.internal.quartz;

import java.io.IOException;
import java.io.ObjectInputStream;
import java.io.ObjectOutputStream;
import java.io.Serializable;
import java.text.DateFormat;
import java.text.ParseException;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Calendar;
import java.util.Collection;
import java.util.Collections;
import java.util.Date;
import java.util.Iterator;
import java.util.List;
import java.util.TimeZone;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Provides a parser and evaluator for iCalendar recurrence rule expressions as
 * defined in the <a
 * href="http://tools.ietf.org/html/rfc5545#section-3.3.10">RFC 5545</a>.
 * <p>
 * Recurrence rules provide the ability to specify complex time combinations
 * based on the standard format for defining calendar and scheduling information
 * (iCalendar). For instance : "every Sunday in January at 8:30 AM and 9:30 AM,
 * every other year". More examples can be found <a
 * href="http://http://tools.ietf.org/html/rfc5545#section-3.8.5.3">here</a>.
 * <p>
 * A recurrence rule is composed of 1 required part that defines the frequency
 * and 13 optional ones separated by a semi-colon.
 *
 * <p>
 * Here is an extract of the recur definition of the RFC 5545.
 *
 * <pre>
 * <code>
 *     recur           = recur-rule-part *( ";" recur-rule-part )
 *                     ;
 *                     ; The rule parts are not ordered in any
 *                     ; particular sequence.
 *                     ;
 *                     ; The FREQ rule part is REQUIRED,
 *                     ; but MUST NOT occur more than once.
 *                     ;
 *                     ; The UNTIL or COUNT rule parts are OPTIONAL,
 *                     ; but they MUST NOT occur in the same 'recur'.
 *                     ;
 *                     ; The other rule parts are OPTIONAL,
 *                     ; but MUST NOT occur more than once.
 *
 *     recur-rule-part = ( "FREQ" "=" freq )
 *                     / ( "UNTIL" "=" enddate )
 *                     / ( "COUNT" "=" 1*DIGIT )
 *                     / ( "INTERVAL" "=" 1*DIGIT )
 *                     / ( "BYSECOND" "=" byseclist )
 *                     / ( "BYMINUTE" "=" byminlist )
 *                     / ( "BYHOUR" "=" byhrlist )
 *                     / ( "BYDAY" "=" bywdaylist )
 *                     / ( "BYMONTHDAY" "=" bymodaylist )
 *                     / ( "BYYEARDAY" "=" byyrdaylist )
 *                     / ( "BYWEEKNO" "=" bywknolist )
 *                     / ( "BYMONTH" "=" bymolist )
 *                     / ( "BYSETPOS" "=" bysplist )
 *                     / ( "WKST" "=" weekday )
 *     freq        = "SECONDLY" / "MINUTELY" / "HOURLY" / "DAILY"
 *                 / "WEEKLY" / "MONTHLY" / "YEARLY"
 *     enddate     = date / date-time
 *     byseclist   = ( seconds *("," seconds) )
 *     seconds     = 1*2DIGIT       ;0 to 60
 *     byminlist   = ( minutes *("," minutes) )
 *     minutes     = 1*2DIGIT       ;0 to 59
 *     byhrlist    = ( hour *("," hour) )
 *     hour        = 1*2DIGIT       ;0 to 23
 *     bywdaylist  = ( weekdaynum *("," weekdaynum) )
 *     weekdaynum  = [[plus / minus] ordwk] weekday
 *     plus        = "+"
 *     minus       = "-"
 *     ordwk       = 1*2DIGIT       ;1 to 53
 *     weekday     = "SU" / "MO" / "TU" / "WE" / "TH" / "FR" / "SA"
 *                 ; Corresponding to SUNDAY, MONDAY, TUESDAY,
 *                 ; WEDNESDAY, THURSDAY, FRIDAY, and SATURDAY days of the week.
 *     bymodaylist = ( monthdaynum *("," monthdaynum) )
 *     monthdaynum = [plus / minus] ordmoday
 *     ordmoday    = 1*2DIGIT       ;1 to 31
 *     byyrdaylist = ( yeardaynum *("," yeardaynum) )
 *     yeardaynum  = [plus / minus] ordyrday
 *     ordyrday    = 1*3DIGIT      ;1 to 366
 *     bywknolist  = ( weeknum *("," weeknum) )
 *     weeknum     = [plus / minus] ordwk
 *     bymolist    = ( monthnum *("," monthnum) )
 *     monthnum    = 1*2DIGIT       ;1 to 12
 *     bysplist    = ( setposday *("," setposday) )
 *     setposday   = yeardaynum
 * </code>
 * </pre>
 *
 *
 * @author Karel Goderis - Initial Contribution
 */
public class RecurrenceRule implements Serializable, Cloneable {

    /** Serial version UID. */
    private static final long serialVersionUID = -5677232646050220938L;

    /**
     * Enum that defines the frequency of the recurrence rule: SECONDLY,
     * MINUTELY, HOURLY, DAILY, WEEKLY, MONTHLY, YEARLY.
     */
    public static enum Frequency {
        /** Frequency : secondly. */
        secondly("SECONDLY", Calendar.SECOND),
        /** Frequency : minutely. */
        minutely("MINUTELY", Calendar.MINUTE),
        /** Frequency : hourly. */
        hourly("HOURLY", Calendar.HOUR_OF_DAY),
        /** Frequency : daily. */
        daily("DAILY", Calendar.DAY_OF_YEAR),
        /** Frequency : weekly. */
        weekly("WEEKLY", Calendar.WEEK_OF_YEAR),
        /** Frequency : monthly. */
        monthly("MONTHLY", Calendar.MONTH),
        /** Frequency : yearly. */
        yearly("YEARLY", Calendar.YEAR);

        /** String identifier of the frequency as defined in RFC 5545. */
        private final String identifier;
        /** The corresponding field in {@link java.util.Calendar}. */
        private final int calendarField;

        /**
         * Constructor.
         *
         * @param id
         *            String that identifies the frequency.
         * @param field
         *            Corresponding field number in {@link java.util.Calendar}
         */
        private Frequency(final String id, final int field) {
            this.identifier = id;
            this.calendarField = field;
        }

        /**
         * Gets the field number in {@link java.util.Calendar} that corresponds
         * to the frequency.
         *
         * @return The field number in {@link java.util.Calendar}. Example :
         *         Calendar.SECOND
         */
        public int getCalendarField() {
            return calendarField;
        }

        /**
         * {@inheritDoc}
         */
        @Override
        public String toString() {
            return identifier;
        }
    }

    /**
     * Enum to define the day of the week. SU indicates Sunday; MO indicates
     * Monday; TU indicates Tuesday; WE indicates Wednesday; TH indicates
     * Thursday; FR indicates Friday; and SA indicates Saturday.
     */
    public static enum WeekDay {
        /** WeekDay : Sunday. */
        sunday("SU", Calendar.SUNDAY),
        /** WeekDay : Monday. */
        monday("MO", Calendar.MONDAY),
        /** WeekDay : Tuesday. */
        tuesday("TU", Calendar.TUESDAY),
        /** WeekDay : Wednesday. */
        wednesday("WE", Calendar.WEDNESDAY),
        /** WeekDay : Thursday. */
        thursday("TH", Calendar.THURSDAY),
        /** WeekDay : Friday. */
        friday("FR", Calendar.FRIDAY),
        /** WeekDay : Saturday. */
        saturday("SA", Calendar.SATURDAY);

        /** Code that identifies the week day as defined in RFC 5545. */
        private final String identifier;
        /** Corresponding week day in {@link java.util.Calendar}. */
        private final int calendarDay;

        /**
         * Gets the WeekDay corresponding to the calendar day.
         *
         * @param calendar
         *            The week day as defined in {@link java.util.Calendar}.
         * @return The WeekDay instance.
         */
        public static WeekDay getWeekDay(final int calendar) {
            switch (calendar) {
                case Calendar.SUNDAY:
                    return sunday;
                case Calendar.MONDAY:
                    return monday;
                case Calendar.TUESDAY:
                    return tuesday;
                case Calendar.WEDNESDAY:
                    return wednesday;
                case Calendar.THURSDAY:
                    return thursday;
                case Calendar.FRIDAY:
                    return friday;
                case Calendar.SATURDAY:
                    return saturday;
                default:
                    throw new IllegalArgumentException("invalid calendar value " + calendar);
            }
        }

        /**
         * Constructor.
         *
         * @param code
         *            Code identifying the day of the week in RFC 5545.
         * @param day
         *            Day of the week as defined in {@link java.util.Calendar}
         */
        private WeekDay(final String code, final int day) {
            this.identifier = code;
            this.calendarDay = day;
        }

        /**
         * Return the value of the day of the week defined in
         * {@link java.util.Calendar}.
         *
         * @return the calendar representation of the day of the week.
         */
        public int getCalendarDay() {
            return calendarDay;
        }

        /**
         * {@inheritDoc}
         */
        @Override
        public String toString() {
            return identifier;
        }
    }

    /**
     * Defines a day of the week with a possible occurrence definition related
     * to a MONTHLY or YEARLY recurrence rule.
     * <p>
     * RFC5545: For example, within a MONTHLY rule, +1MO (or simply 1MO)
     * represents the first Monday within the month, whereas -1MO represents the
     * last Monday of the month. The numeric value in a BYDAY rule part with the
     * FREQ rule part set to YEARLY corresponds to an offset within the month
     * when the BYMONTH rule part is present, and corresponds to an offset
     * within the year when the BYWEEKNO or BYMONTH rule parts are present. If
     * an integer modifier is not present, it means all days of this type within
     * the specified frequency. For example, within a MONTHLY rule, MO
     * represents all Mondays within the month. The BYDAY rule part MUST NOT be
     * specified with a numeric value when the FREQ rule part is not set to
     * MONTHLY or YEARLY. Furthermore, the BYDAY rule part MUST NOT be specified
     * with a numeric value with the FREQ rule part set to YEARLY when the
     * BYWEEKNO rule part is specified.
     */
    public static class ByDay {

        /** The day of the week defined in the BYDAY rule part. */
        private WeekDay weekDay;
        /** The occurrence value defined in the BYDAY rule part. */
        private int occurrence;

        /**
         * Constructor.
         *
         * @param day
         *            The day of the week of the BYDAY rule part.
         */
        public ByDay(final WeekDay day) {
            this(day, 0);
        }

        /**
         * Constructor.
         *
         * @param day
         *            The day of the week of the BYDAY rule part.
         * @param occurrenceValue
         *            The occurrence value of the BYDAY rule part.
         */
        public ByDay(final WeekDay day, final int occurrenceValue) {
            this.weekDay = day;
            this.occurrence = occurrenceValue;
        }

        /**
         * Returns the day of the week defined in the BYDAY rule part.
         *
         * @return the day of the week.
         */
        public final WeekDay getWeekDay() {
            return weekDay;
        }

        /**
         * Returns the occurrence value defined in the BYDAY rule part.
         *
         * @return the occurrence value.
         */
        public final int getOccurrence() {
            return occurrence;
        }

        /**
         * {@inheritDoc}
         */
        @Override
        public final int hashCode() {
            return weekDay.hashCode() + occurrence;
        }

        /**
         * {@inheritDoc}
         */
        @Override
        public final boolean equals(final Object obj) {
            if (obj == null) {
                return false;
            }
            if (obj instanceof ByDay) {
                ByDay other = (ByDay) obj;
                return weekDay.equals(other.getWeekDay()) && occurrence == other.getOccurrence();
            } else {
                return false;
            }
        }

        /**
         * {@inheritDoc}
         */
        @Override
        public final String toString() {
            final StringBuffer sb = new StringBuffer();
            if (occurrence != 0) {
                sb.append(occurrence);
            }
            sb.append(weekDay.toString());
            return sb.toString();
        }
    }

    /**
     * A list of integers in a range.
     */
    private static final class BoundedIntegerList extends ArrayList<Integer> {
        /** Serial UID. */
        private static final long serialVersionUID = 4487130864553902674L;
        /** Absolute max value. */
        private final int max;
        /** Absolute min value. */
        private final int min;
        /** Flag indicating if negative values are allowed. */
        private final boolean negative;

        /**
         * Constructor.
         *
         * @param absMax
         *            absolute max value.
         * @param absMin
         *            absolute min value.
         * @param negativeValuesAllowed
         *            True if negative values are allowed.
         */
        private BoundedIntegerList(final int absMin, final int absMax, final boolean negativeValuesAllowed) {
            this.min = absMin;
            this.max = absMax;
            this.negative = negativeValuesAllowed;
        }

        /**
         * Checks if the input is in the specified range.
         *
         * @param integer
         *            The input
         */
        private void checkInput(final Integer integer) {
            if (!negative) {
                if (integer < min || integer > max) {
                    throw new IllegalArgumentException(
                            "Invalid integer value (value not in range [" + min + ", " + max + "])");

                }
            } else {
                final int abs = Math.abs(integer);
                if (abs < min || abs > max) {
                    throw new IllegalArgumentException("Invalid integer value (value not in range [" + (-max) + ", "
                            + -min + "] U [" + min + ", " + max + "])");
                }
            }
        }

        /**
         * {@inheritDoc}
         */
        @Override
        public Integer set(final int index, final Integer element) {
            checkInput(element);
            return super.set(index, element);
        }

        /**
         * {@inheritDoc}
         */
        @Override
        public boolean add(final Integer e) {
            checkInput(e);
            return super.add(e);
        }

        /**
         * {@inheritDoc}
         */
        @Override
        public void add(final int index, final Integer element) {
            checkInput(element);
            super.add(index, element);
        }

        /**
         * {@inheritDoc}
         */
        @Override
        public boolean addAll(final Collection<? extends Integer> c) {
            for (Integer integer : c) {
                checkInput(integer);
            }
            return super.addAll(c);
        }

        /**
         * {@inheritDoc}
         */
        @Override
        public boolean addAll(final int index, final Collection<? extends Integer> c) {
            for (Integer integer : c) {
                checkInput(integer);
            }
            return super.addAll(index, c);
        }
    }

    /** The logger. */
    private final transient Logger log = LoggerFactory.getLogger(getClass());

    /** Keyword for recur rule part : FREQ. */
    private static final String FREQ = "FREQ";
    /** Keyword for recur rule part : UNTIL. */
    private static final String UNTIL = "UNTIL";
    /** Keyword for recur rule part : COUNT. */
    private static final String COUNT = "COUNT";
    /** Keyword for recur rule part : INTERVAL. */
    private static final String INTERVAL = "INTERVAL";
    /** Keyword for recur rule part : BYSECOND. */
    private static final String BYSECOND = "BYSECOND";
    /** Keyword for recur rule part : BYMINUTE. */
    private static final String BYMINUTE = "BYMINUTE";
    /** Keyword for recur rule part : BYHOUR. */
    private static final String BYHOUR = "BYHOUR";
    /** Keyword for recur rule part : BYDAY. */
    private static final String BYDAY = "BYDAY";
    /** Keyword for recur rule part : BYMONTHDAY. */
    private static final String BYMONTHDAY = "BYMONTHDAY";
    /** Keyword for recur rule part : BYYEARDAY. */
    private static final String BYYEARDAY = "BYYEARDAY";
    /** Keyword for recur rule part : BYWEEKNO. */
    private static final String BYWEEKNO = "BYWEEKNO";
    /** Keyword for recur rule part : BYMONTH. */
    private static final String BYMONTH = "BYMONTH";
    /** Keyword for recur rule part : BYSETPOS. */
    private static final String BYSETPOS = "BYSETPOS";
    /** Keyword for recur rule part : WKST. */
    private static final String WKST = "WKST";

    /** Date format for date defined in local timezone : yyyyMMdd'T'HHmmss. */
    private static final String LOCALTIME_FORMAT = "yyyyMMdd'T'HHmmss";
    /** Date format for date defined in UTC timezone : yyyyMMdd'T'HHmmss'Z'. */
    private static final String UTCTIME_FORMAT = "yyyyMMdd'T'HHmmss'Z'";
    /** Date format for date defined in day resolution : yyyyMMdd. */
    private static final String DEFAULT_FORMAT = "yyyyMMdd";

    /** Min of the BYSETPOS values. */
    private static final int MIN_SETPOS = 1;
    /** Max of the BYSETPOS values. */
    private static final int MAX_SETPOS = 366;
    /** Min of the BYMONTH values. */
    private static final int MIN_MONTH = 1;
    /** Max of the BYMONTH values. */
    private static final int MAX_MONTH = 12;
    /** Min of the BYWEEKNO values. */
    private static final int MIN_WEEKNO = 1;
    /** Max of the BYWEEKNO values. */
    private static final int MAX_WEEKNO = 53;
    /** Min of the BYYEARDAY values. */
    private static final int MIN_YEARDAY = 1;
    /** Max of the BYYEARDAY values. */
    private static final int MAX_YEARDAY = 366;
    /** Min of the BYMONTHDAY values. */
    private static final int MIN_MONTHDAY = 1;
    /** Max of the BYMONTHDAY values. */
    private static final int MAX_MONTHDAY = 31;
    /** Min of the BYHOUR values. */
    private static final int MIN_HOUR = 0;
    /** Max of the BYHOUR values. */
    private static final int MAX_HOUR = 23;
    /** Min of the BYMINUTE values. */
    private static final int MIN_MINUTE = 0;
    /** Max of the BYMINUTE values. */
    private static final int MAX_MINUTE = 59;
    /** Min of the BYSECOND values. */
    private static final int MIN_SECOND = 0;
    /** Max of the BYSECOND values. */
    private static final int MAX_SECOND = 59;
    /**
     * Default maximum number of attempts to find a date matching the recurrence
     * rule.
     */
    private static final int DEFAULT_MAX_NB_ATTEMPTS = 100;

    /**
     * The value of the FREQ rule part which identifies the type of recurrence
     * rule. This rule part is mandatory.
     */
    private transient Frequency frequency;
    /**
     * The value of the UNTIL rule part which defines a date that bounds the
     * recurrence rule in an inclusive manner. If the value specified by UNTIL
     * is synchronized with the specified recurrence, this date becomes the last
     * instance of the recurrence.
     */
    private transient Date until;
    /**
     * The value of the COUNT rule part which defines the number of occurrences
     * at which to range-bound the recurrence.
     */
    private transient int count;
    /**
     * The value of INTERVAL rule part : a positive integer representing at
     * which intervals the recurrence rule repeats. The default value is 1
     */
    private transient int interval;
    /**
     * The values of the BYSECOND rule part : list of seconds within a minute.
     * Valid values are 0 to 60.
     */
    private transient List<Integer> secondList;
    /**
     * The values of the BYMINUTE rule part : list of minutes within an hour.
     * Valid values are 0 to 59.
     */
    private transient List<Integer> minuteList;
    /**
     * The values of the BYHOUR rule part : list of hours of the day. Valid
     * values are 0 to 23.
     */
    private transient List<Integer> hourList;
    /**
     * The values of the BYDAY rule part : list of days of the week. Each BYDAY
     * value can also be preceded by a positive (+n) or negative (-n) integer.
     * If present, this indicates the nth occurrence of a specific day within
     * the MONTHLY or YEARLY "RRULE".
     */
    private transient List<ByDay> dayList;
    /**
     * The values for BYMONTHDAY rule part : list of days of the month. Valid
     * values are 1 to 31 or -31 to -1.
     */
    private transient List<Integer> monthDayList;
    /**
     * The values for BYYEARDAY rule part : list of days of the year. Valid
     * values are 1 to 366 or -366 to -1.
     */
    private transient List<Integer> yearDayList;
    /**
     * The values for BYWEEKNO rule part : list of ordinals specifying weeks of
     * the year. Valid values are 1 to 53 or -53 to -1.
     */
    private transient List<Integer> weekNoList;
    /**
     * The values for BYMONTH rule part : list of months of the year. Valid
     * values are 1 to 12.
     */
    private transient List<Integer> monthList;
    /**
     * The values for BYSETPOS rule part : list of values that corresponds to
     * the nth occurrence within the set of recurrence instances specified by
     * the rule.
     */
    private transient List<Integer> setPosList;
    /**
     * The value for WKST rule part which specifies the day on which the
     * workweek starts. By default : monday.
     */
    private transient WeekDay weekStart;
    /**
     * The string representation of the recurrence rule.
     */
    private String recurrenceRuleExpr;
    /**
     * The timezone to use.
     */
    private TimeZone timeZone = null;
    /**
     * The start date to consider for the recurrence.
     */
    private Date startDate = null;
    /**
     * The current reference date for the iteration.
     */
    private Date referenceDate = null;
    /**
     * The computed results of the recurrence.
     */
    private List<Date> recurrenceSet = new ArrayList<Date>();
    /**
     * The step of the recurrence rule evaluation (number of times the frequency
     * / interval rule part was applied).
     */
    private int iteration = 0;
    /**
     * Flag to know if the recurrence set computation has identified all
     * occurrences.
     */
    private boolean searchComplete = false;
    /**
     * The maximum number of attempts to find a date that matches the recurrence
     * rule.
     */
    private int maxAttempts = DEFAULT_MAX_NB_ATTEMPTS;

    /**
     * Constructs a new <CODE>RecurrenceRule</CODE> based on the specified
     * parameter.
     *
     * @param recurrenceRule
     *            String representation of the RFC 5545 recurrence rule the new
     *            object should represent.
     * @throws java.text.ParseException
     *             Thrown if the string expression cannot be parsed into a valid
     *             <code>RecurrenceRule</code>.
     */
    public RecurrenceRule(final String recurrenceRule) throws ParseException {
        this(recurrenceRule, Calendar.getInstance().getTime(), TimeZone.getDefault());
    }

    /**
     * Constructs a new <CODE>RecurrenceRule</CODE> based on the specified
     * parameter.
     *
     * @param recurrenceRule
     *            String representation of the RFC 5545 recurrence rule the new
     *            object should represent.
     * @param startTime
     *            The start time to consider for the recurrence rule.
     * @throws java.text.ParseException
     *             Thrown if the string expression cannot be parsed into a valid
     *             <code>RecurrenceRule</code>.
     */
    public RecurrenceRule(final String recurrenceRule, final Date startTime) throws ParseException {
        this(recurrenceRule, startTime, TimeZone.getDefault());
    }

    /**
     * Constructs a new <CODE>RecurrenceRule</CODE> based on the specified
     * parameter.
     *
     * @param recurrenceRule
     *            String representation of the RFC 5545 recurrence rule the new
     *            object should represent.
     * @param startTime
     *            The start time to consider for the recurrence rule.
     * @param zone
     *            The timezone for which this recurrence rule will be resolved.
     * @throws java.text.ParseException
     *             Thrown if the string expression cannot be parsed into a valid
     *             <code>RecurrenceRule</code>.
     */
    public RecurrenceRule(final String recurrenceRule, final Date startTime, final TimeZone zone)
            throws ParseException {
        if (recurrenceRule == null) {
            throw new IllegalArgumentException("recurrence rule cannot be null");
        }
        setStartDate(startTime, false);
        setTimeZone(zone);
        buildRecurrenceRule(recurrenceRule);
    }

    /**
     * Returns the recurrence rule expression as a RFC 5545 string.
     *
     * @return the recurrence rule expression.
     */
    public final String getRecurrenceRule() {
        // recompute recurrence rule string because setters may have change the
        // rule.
        recurrenceRuleExpr = computeRecurrenceRuleString();
        return recurrenceRuleExpr;
    }

    /**
     * Returns the frequency defined by the FREQ recurrence rule part.
     *
     * @return the frequency.
     */
    public final Frequency getFrequency() {
        return frequency;
    }

    /**
     * Returns the until date defined by the UNTIL recurrence rule part.
     *
     * @return the until date.
     */
    public final Date getUntil() {
        return until;
    }

    /**
     * Returns the occurrence count defined by the COUNT recurrence rule part.
     *
     * @return the occurrence count.
     */
    public final int getCount() {
        return count;
    }

    /**
     * Returns the interval defined by the INTERVAL recurrence rule part.
     *
     * @return the interval.
     */
    public final int getInterval() {
        return interval;
    }

    /**
     * Returns the list of seconds defined by the BYSECOND recurrence rule part.
     *
     * @return the list of seconds.
     */
    public final List<Integer> getSecondList() {
        return secondList;
    }

    /**
     * Returns the list of minutes defined by the BYMINUTE recurrence rule part.
     *
     * @return the list of minutes.
     */
    public final List<Integer> getMinuteList() {
        return minuteList;
    }

    /**
     * Returns the list of hours defined by the BYHOUR recurrence rule part.
     *
     * @return the list of hours.
     */
    public final List<Integer> getHourList() {
        return hourList;
    }

    /**
     * Returns the list of days of the week defined by the BYDAY recurrence rule
     * part.
     *
     * @return the list of days of the week.
     */
    public final List<ByDay> getDayList() {
        return dayList;
    }

    /**
     * Returns the list of days of the month defined by the BYMONTHDAY
     * recurrence rule part.
     *
     * @return the list of days of the month.
     */
    public final List<Integer> getMonthDayList() {
        return monthDayList;
    }

    /**
     * Returns the list of days of the year defined by the BYYEARDAY recurrence
     * rule part.
     *
     * @return the list of days of the year.
     */
    public final List<Integer> getYearDayList() {
        return yearDayList;
    }

    /**
     * Returns the list of the week numbers defined by the BYWEEKNO recurrence
     * rule part.
     *
     * @return the list of week numbers.
     */
    public final List<Integer> getWeekNoList() {
        return weekNoList;
    }

    /**
     * Returns the list of months defined by the BYMONTH recurrence rule part.
     * Caution, a month in the RFC 5545 is in the range [1 .. 12] while in Java,
     * it is in the range [0 .. 11].
     *
     * @return the list of month.
     */
    public final List<Integer> getMonthList() {
        return monthList;
    }

    /**
     * Returns the list of occurrence positions defined by the BYSETPOS
     * recurrence rule part.
     *
     * @return the list of occurrence positions.
     */
    public final List<Integer> getSetPosList() {
        return setPosList;
    }

    /**
     * Returns the day of the week corresponding to the week start.
     *
     * @return the week start day.
     */
    public final WeekDay getWeekStart() {
        return weekStart;
    }

    /**
     * Returns the start date of the recurrence rule.
     *
     * @return the startDate The start date.
     */
    public final Date getStartDate() {
        if (startDate == null) {
            startDate = Calendar.getInstance(getTimeZone()).getTime();
        }
        return startDate;
    }

    /**
     * Returns the time zone for which this <code>RecurrenceRule</code> will be
     * resolved.
     *
     * @return the time zone.
     */
    public final TimeZone getTimeZone() {
        if (timeZone == null) {
            timeZone = TimeZone.getDefault();
        }
        return timeZone;
    }

    /**
     * Returns the maximum number of attempts to find a date that matches the
     * recurrence rule.
     *
     * @return the maximum number of attempts.
     */
    public final int getMaxAttempts() {
        return maxAttempts;
    }

    /**
     * Sets the frequency of the recurrence rule.
     *
     * @param freq
     *            the frequency to set.
     */
    public final void setFrequency(final Frequency freq) {
        this.frequency = freq;
        validateRecurrenceRule();
        clearRecurrenceSet();
    }

    /**
     * Sets the interval of the recurrence rule.
     *
     * @param freqInterval
     *            the interval to set.
     */
    public final void setInterval(final int freqInterval) {
        if (freqInterval <= 0) {
            throw new IllegalArgumentException("The INTERVAL rule part MUST contain a positive integer");
        }
        this.interval = freqInterval;
        clearRecurrenceSet();
    }

    /**
     * Sets the until date of the recurrence rule.
     *
     * @param untilDate
     *            the until date to set.
     */
    public final void setUntil(final Date untilDate) {
        if (untilDate == null) {
            throw new IllegalArgumentException("Until date can not be null");
        }
        this.until = (Date) untilDate.clone();
        this.count = -1;
        clearRecurrenceSet();
    }

    /**
     * Sets the max number of occurrences for the recurrence rule.
     *
     * @param maxOccurrenceNumber
     *            the max number of occurrences to set.
     */
    public final void setCount(final int maxOccurrenceNumber) {
        this.count = maxOccurrenceNumber;
        this.until = null;
        clearRecurrenceSet();
    }

    /**
     * Sets the start date of the recurrence rule.
     *
     * @param startTime
     *            the startDate to set
     */
    public final void setStartDate(final Date startTime) {
        setStartDate(startTime, false);
    }

    /**
     * Sets the start date of the recurrence rule.
     *
     * @param startTime
     *            the startDate to set
     * @param check
     *            True if the start date will be verified against the recurrence
     *            rule.
     */
    public final void setStartDate(final Date startTime, final boolean check) {
        if (startTime == null) {
            throw new IllegalArgumentException("startTime can not be null");
        }
        if (check) {
            validateStartDate(startTime);
        }
        this.startDate = startTime;
        clearRecurrenceSet();
    }

    /**
     * Sets the time zone for which this <code>RecurrenceRule</code> will be
     * resolved. This method does not change the until date.
     *
     * @param zone
     *            The time zone.
     */
    public final void setTimeZone(final TimeZone zone) {
        setTimeZone(zone, false);
    }

    /**
     * Sets the time zone for which this <code>RecurrenceRule</code> will be
     * resolved.
     *
     * @param zone
     *            The time zone.
     * @param updateUntil
     *            True if the until date shall be shifted by the offset between
     *            current time zone and new time zone. If set to true, an until
     *            date defined as 2013/06/01 12:00:00 EST (timeZone =
     *            America/New_York) will be 2013/06/01 12:00:00 CST after
     *            setting the time zone to Europe/Paris.
     */
    public final void setTimeZone(final TimeZone zone, final boolean updateUntil) {
        if (zone == null) {
            throw new IllegalArgumentException("timeZone can not be null");
        }
        TimeZone oldZone = getTimeZone();
        this.timeZone = zone;

        if (!oldZone.equals(zone)) {
            if (updateUntil && getUntil() != null) {
                long oldOffset = oldZone.getOffset(getUntil().getTime());
                long offset = zone.getOffset(getUntil().getTime());
                long timeInMillis = getUntil().getTime();
                Calendar c = Calendar.getInstance(zone);
                c.setTimeInMillis(timeInMillis + oldOffset - offset);
                until = c.getTime();
            }
            clearRecurrenceSet();
        }
    }

    /**
     * Sets the day of the week corresponding to the week start.
     *
     * @param day
     *            the week start day.
     */
    public final void setWeekStart(final WeekDay day) {
        weekStart = day;
        clearRecurrenceSet();
    }

    /**
     * Sets the maximum number of attempts to find a date that matches the
     * recurrence rule.
     *
     * @param attempts
     *            the maximum number of attempts.
     */
    public final void setMaxAttempts(final int attempts) {
        this.maxAttempts = attempts;
    }

    /**
     * Clears the recurrence set.
     */
    private void clearRecurrenceSet() {
        recurrenceSet.clear();
        iteration = 0;
        searchComplete = false;
        referenceDate = null;
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public final String toString() {
        return getRecurrenceRule();
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public final Object clone() {
        RecurrenceRule copy = null;
        try {
            copy = new RecurrenceRule(getRecurrenceRule(), getStartDate(), getTimeZone());
        } catch (ParseException ex) {
            // never happens since the source is valid...
            throw new RuntimeException("Not Cloneable.");
        }
        return copy;
    }

    /**
     * Overrides standard serialization to store the most up-to-date recurrence
     * rule.
     *
     * @param out
     *            The output stream.
     * @throws IOException
     *             Thrown if an error occurs when serializing object.
     */
    private void writeObject(final ObjectOutputStream out) throws IOException {
        // recompute recurrence rule string because setters may have change the
        // rule.
        recurrenceRuleExpr = computeRecurrenceRuleString();
        out.defaultWriteObject();
    }

    /**
     * Overrides standard deserialization to parse the recurrence rule.
     *
     * @param in
     *            The input stream to deserialize.
     * @throws IOException
     *             Thrown if an error occurs when deserializing object.
     * @throws ClassNotFoundException
     *             Thrown if the stream contains an unknown class.
     */
    private void readObject(final ObjectInputStream in) throws IOException, ClassNotFoundException {
        in.defaultReadObject();
        try {
            buildRecurrenceRule(recurrenceRuleExpr);
        } catch (ParseException ignore) {
            // never happens
        }
    }

    /**
     * Recomputes the string representing the recurrence rule as specified in
     * RFC 5545.
     *
     * @return The string representing the recurrence rule.
     */
    private String computeRecurrenceRuleString() {
        final StringBuffer b = new StringBuffer();
        b.append(FREQ).append('=').append(frequency.toString());
        if (until != null) {
            DateFormat format = new SimpleDateFormat(UTCTIME_FORMAT);
            format.setTimeZone(TimeZone.getTimeZone("GMT"));
            b.append(';').append(UNTIL).append('=').append(format.format(getUntil()));
        }
        if (count >= 1) {
            b.append(';').append(COUNT).append('=').append(count);
        }
        if (interval > 1) {
            b.append(';').append(INTERVAL).append('=').append(interval);
        }
        if (!secondList.isEmpty()) {
            b.append(';').append(BYSECOND).append('=').append(numberListToString(secondList));
        }
        if (!minuteList.isEmpty()) {
            b.append(';').append(BYMINUTE).append('=').append(numberListToString(minuteList));
        }
        if (!hourList.isEmpty()) {
            b.append(';').append(BYHOUR).append('=').append(numberListToString(hourList));
        }
        if (!dayList.isEmpty()) {
            b.append(';').append(BYDAY).append('=').append(bydayListToString(dayList));
        }
        if (!monthDayList.isEmpty()) {
            b.append(';').append(BYMONTHDAY).append('=').append(numberListToString(monthDayList));
        }
        if (!yearDayList.isEmpty()) {
            b.append(';').append(BYYEARDAY).append('=').append(numberListToString(yearDayList));
        }
        if (!weekNoList.isEmpty()) {
            b.append(';').append(BYWEEKNO).append('=').append(numberListToString(weekNoList));
        }
        if (!monthList.isEmpty()) {
            b.append(';').append(BYMONTH).append('=').append(numberListToString(monthList));
        }
        if (!setPosList.isEmpty()) {
            b.append(';').append(BYSETPOS).append('=').append(numberListToString(setPosList));
        }
        if (weekStart != null && !WeekDay.monday.equals(weekStart)) {
            b.append(';').append(WKST).append('=').append(weekStart);
        }
        return b.toString();
    }

    // ////////////////////////////////////////////////////////////////////////
    //
    // Recurrence Rule Parsing Functions
    //
    // ////////////////////////////////////////////////////////////////////////

    /**
     * Build the recurrence rule object using the RFC 5545 string expression.
     *
     * @param recurrenceRule
     *            String representation of the RFC 5545 recurrence rule
     * @throws ParseException
     *             Thrown if the string expression cannot be parsed into a valid
     *             <code>RecurrenceRule</code>.
     */
    protected void buildRecurrenceRule(final String recurrenceRule) throws ParseException {

        // initializes attributes
        count = -1;
        interval = 1;
        weekStart = WeekDay.monday;
        if (secondList == null) {
            secondList = new BoundedIntegerList(MIN_SECOND, MAX_SECOND, false);
        }
        if (minuteList == null) {
            minuteList = new BoundedIntegerList(MIN_MINUTE, MAX_MINUTE, false);
        }
        if (hourList == null) {
            hourList = new BoundedIntegerList(MIN_HOUR, MAX_HOUR, false);
        }
        if (dayList == null) {
            dayList = new ArrayList<ByDay>();
        }
        if (monthDayList == null) {
            monthDayList = new BoundedIntegerList(MIN_MONTHDAY, MAX_MONTHDAY, true);
        }
        if (yearDayList == null) {
            yearDayList = new BoundedIntegerList(MIN_YEARDAY, MAX_YEARDAY, true);
        }
        if (weekNoList == null) {
            weekNoList = new BoundedIntegerList(MIN_WEEKNO, MAX_WEEKNO, true);
        }
        if (monthList == null) {
            monthList = new BoundedIntegerList(MIN_MONTH, MAX_MONTH, false);
        }
        if (setPosList == null) {
            setPosList = new BoundedIntegerList(MIN_SETPOS, MAX_SETPOS, true);
        }

        // fill-in attributes according to the string representation of the
        // recurrence rule
        final String[] parts = recurrenceRule.split(";");
        String part = null;
        for (int i = 0; i < parts.length; i++) {
            part = parts[i].replaceAll(" ", "");
            final String[] keyValuePair = part.split("=");
            if (keyValuePair.length == 2) {
                parseRecurrenceRulePart(keyValuePair[0], keyValuePair[1]);
            }
        }
        validateRecurrenceRule();
        this.recurrenceRuleExpr = recurrenceRule;
    }

    /**
     * Parses a recurrence rule part.
     *
     * @param key
     *            Key of the recurrence rule part.
     * @param value
     *            Value of the recurrence rule part.
     * @throws ParseException
     *             Thrown if the value expression cannot be parsed into a valid
     *             recurrence rule part.
     */
    private void parseRecurrenceRulePart(final String key, final String value) throws ParseException {
        if (FREQ.equals(key)) {
            frequency = parseFrequency(key, value);
        } else if (UNTIL.equals(key)) {
            until = parseDate(key, value);
        } else if (COUNT.equals(key)) {
            try {
                count = Integer.parseInt(value);
                if (count <= 0) {
                    throw new IllegalArgumentException("The COUNT rule part MUST contain a positive integer");
                }
            } catch (NumberFormatException pe) {
                throw new ParseException("Invalid integer value for " + key + " : " + value, -1);
            }
        } else if (INTERVAL.equals(key)) {
            try {
                interval = Integer.parseInt(value);
                if (interval <= 0) {
                    throw new IllegalArgumentException("The INTERVAL rule part MUST contain a positive integer");
                }
            } catch (NumberFormatException pe) {
                throw new ParseException("Invalid integer value for " + key + " : " + value, -1);
            }
        } else if (BYSECOND.equals(key)) {
            secondList = parseNumberList(key, value, MIN_SECOND, MAX_SECOND, false);
        } else if (BYMINUTE.equals(key)) {
            minuteList = parseNumberList(key, value, MIN_MINUTE, MAX_MINUTE, false);
        } else if (BYHOUR.equals(key)) {
            hourList = parseNumberList(key, value, MIN_HOUR, MAX_HOUR, false);
        } else if (BYDAY.equals(key)) {
            dayList = parseByDayList(key, value);
        } else if (BYMONTHDAY.equals(key)) {
            monthDayList = parseNumberList(key, value, MIN_MONTHDAY, MAX_MONTHDAY, true);
        } else if (BYYEARDAY.equals(key)) {
            yearDayList = parseNumberList(key, value, MIN_YEARDAY, MAX_YEARDAY, true);
        } else if (BYWEEKNO.equals(key)) {
            weekNoList = parseNumberList(key, value, MIN_WEEKNO, MAX_WEEKNO, true);
        } else if (BYMONTH.equals(key)) {
            monthList = parseNumberList(key, value, MIN_MONTH, MAX_MONTH, false);
        } else if (BYSETPOS.equals(key)) {
            setPosList = parseNumberList(key, value, MIN_SETPOS, MAX_SETPOS, true);
        } else if (WKST.equals(key)) {
            weekStart = parseWeekDay(key, value);
        }
        // unknown recur rule part, just ignore it
    }

    /**
     * Parses the list of week days from the recurrence rule part.
     *
     * @param key
     *            Keyword of the recur rule part.
     * @param value
     *            Value of the recur rule part. It is a COMMA-separated list of
     *            days of the week.
     * @return List of days of the week extracted from the recur rule part.
     * @throws ParseException
     *             Thrown if the value expression cannot be parsed into a valid
     *             recurrence rule part.
     */
    private List<ByDay> parseByDayList(final String key, final String value) throws ParseException {
        final String[] values = value.split(",");
        List<ByDay> bydays = new ArrayList<ByDay>();
        for (int i = 0; i < values.length; i++) {
            WeekDay weekDay = parseWeekDay(key, values[i].substring(values[i].length() - 2));
            if (values[i].length() > 2) {
                // occurrence defined
                try {
                    int occurrence = Integer.parseInt(values[i].substring(0, values[i].length() - 2));
                    bydays.add(new ByDay(weekDay, occurrence));
                } catch (NumberFormatException nfe) {
                    throw new ParseException("Invalid integer value for " + key + " : " + values[i], -1);
                }
            } else {
                bydays.add(new ByDay(weekDay));
            }
        }
        return bydays;
    }

    /**
     * Parses the list of numbers from the recurrence rule part. This method
     * also checks if the parsed values are in the expected range of values
     * defined by the min, max and negativeValuesAllowed arguments.<br>
     * If negativeValuesAllowed is true, the parsed values shall be in the range
     * [-max, -min] or [min, max]. If negativeValuesAllowed is false, the parsed
     * values shall be in the range [min, max].
     *
     * @param key
     *            Keyword of the recur rule part.
     * @param value
     *            Value of the recur rule part. It is a COMMA-separated list of
     *            numbers.
     * @param min
     *            The absolute min value allowed.
     * @param max
     *            The absolute max value allowed.
     * @param negativeValuesAllowed
     *            True if negative values are allowed.
     * @return The list of numbers that is parsed from the recur rule part.
     * @throws ParseException
     *             Thrown if the value expression cannot be parsed into a valid
     *             recurrence rule part.
     */
    private List<Integer> parseNumberList(final String key, final String value, final int min, final int max,
            final boolean negativeValuesAllowed) throws ParseException {
        final String[] values = value.split(",");
        List<Integer> integers = new BoundedIntegerList(min, max, negativeValuesAllowed);
        for (int i = 0; i < values.length; i++) {
            try {
                final int integer = Integer.parseInt(values[i]);
                integers.add(integer);
            } catch (NumberFormatException nfe) {
                throw new ParseException("Invalid integer value for " + key + " : " + value, -1);
            }
        }
        Collections.sort(integers);
        return integers;
    }

    /**
     * Parses a date from the recurrence rule part. The date can be a simple
     * date (day resolution) or a date-time (second resolution).
     *
     * @param key
     *            Keyword of the recur rule part.
     * @param value
     *            Value of the recur rule part.
     * @return The Java date parsed from the recur rule part.
     * @throws ParseException
     *             Thrown if the value expression cannot be parsed into a valid
     *             recurrence rule part.
     */
    private Date parseDate(final String key, final String value) throws ParseException {
        // 2 cases : date or date-time
        if (value != null && value.indexOf("T") >= 0) {
            if (value.indexOf("Z") >= 0) {
                // FORM 1 : date with utc time
                try {
                    DateFormat format = new SimpleDateFormat(UTCTIME_FORMAT);
                    format.setLenient(false);
                    format.setTimeZone(TimeZone.getTimeZone("GMT"));
                    return format.parse(value);
                } catch (ParseException pe) {
                    // datetime does not match form #2
                    if (log.isDebugEnabled()) {
                        log.debug("value does not match UTC time pattern");
                    }
                }
            } else {
                // case date-time
                // 3 forms are possible
                // FORM 1 : date with local time
                try {
                    DateFormat format = new SimpleDateFormat(LOCALTIME_FORMAT);
                    format.setLenient(false);
                    format.setTimeZone(getTimeZone());
                    return format.parse(value);
                } catch (ParseException pe) {
                    // datetime does not match form #1
                    if (log.isDebugEnabled()) {
                        log.debug("value does not match local time pattern");
                    }
                }
            }

            // FORM 3 : date with local time and timezone reference
            // This case is not allowed for "Until" recur rule part
            throw new ParseException("Invalid date format for " + key + " : " + value, -1);

        } else {
            // case date
            try {
                DateFormat format = new SimpleDateFormat(DEFAULT_FORMAT);
                format.setLenient(false);
                format.setTimeZone(getTimeZone());
                return format.parse(value);
            } catch (ParseException pe) {
                throw new ParseException("Invalid date format for " + key + " : " + value, -1);
            }
        }
    }

    /**
     * Parses from the recurrence rule part, the day of the week represented by
     * a string : SU indicates Sunday; MO indicates Monday; TU indicates
     * Tuesday; WE indicates Wednesday; TH indicates Thursday; FR indicates
     * Friday; and SA indicates Saturday.
     *
     * @param key
     *            Keyword of the recur rule part.
     * @param value
     *            Value of the recur rule part.
     * @return Day of the week parsed from the recur rule part.
     * @throws ParseException
     *             Thrown if the value expression cannot be parsed into a valid
     *             recurrence rule part.
     */
    private WeekDay parseWeekDay(final String key, final String value) throws ParseException {
        for (WeekDay weekDay : WeekDay.values()) {
            if (weekDay.toString().equals(value)) {
                return weekDay;
            }
        }
        throw new ParseException("Invalid week day for " + key + " : " + value, -1);
    }

    /**
     * Parses from the recurrence rule part, the frequency represented by a
     * string : SECONDLY, MINUTELY, HOURLY, DAILY, WEEKLY, MONTHLY, YEARLY.
     *
     * @param key
     *            Keyword of the recur rule part.
     * @param value
     *            Value of the recur rule part.
     * @return The frequency parsed from the recur rule part.
     * @throws ParseException
     *             Thrown if the value expression cannot be parsed into a valid
     *             recurrence rule part.
     */
    private Frequency parseFrequency(final String key, final String value) throws ParseException {
        for (Frequency freq : Frequency.values()) {
            if (freq.toString().equals(value)) {
                return freq;
            }
        }
        throw new ParseException("Invalid value for " + key + " : " + value, -1);
    }

    /**
     * Checks if the start date is synchronized with the recurrence rule. The
     * method throws an IllegalArgumentException if the start date is not
     * synchronized with the recurrence rule.
     *
     * @param startTime
     *            The start date to check
     */
    public void validateStartDate(final Date startTime) {
        if (startTime == null) {
            throw new IllegalArgumentException("Start time cannot be null");
        }
        if (getUntil() != null && startTime != null && getUntil().before(startTime)) {
            throw new IllegalArgumentException("Start date cannot be after until");
        }

        // checks that the startdate is compliant with the BYxxx rules.

        Calendar cal = Calendar.getInstance(getTimeZone());
        cal.setTime(startTime);

        // check bysecond
        int second = cal.get(Calendar.SECOND);
        if (!getSecondList().contains(second)) {
            throw new IllegalArgumentException(
                    "start date is not compliant with the recurrence rule (second not in the BYSECOND list)");
        }

        // check byminute
        int minute = cal.get(Calendar.MINUTE);
        if (!getMinuteList().contains(minute)) {
            throw new IllegalArgumentException(
                    "start date is not compliant with the recurrence rule (minute not in the BYMINUTE list)");
        }

        // check byhour
        int hour = cal.get(Calendar.HOUR_OF_DAY);
        if (!getHourList().contains(hour)) {
            throw new IllegalArgumentException(
                    "start date is not compliant with the recurrence rule (hour not in the BYHOUR list)");
        }

        // check byday
        if (!getDayList().isEmpty()) {
            boolean found = false;
            ByDay byDay = null;
            for (int i = 0; i < getDayList().size() && !found; i++) {
                byDay = getDayList().get(i);
                if (byDay.getOccurrence() == 0) {
                    if (cal.get(Calendar.DAY_OF_WEEK) == byDay.getWeekDay().getCalendarDay()) {
                        found = true;
                        break;
                    }
                } else {
                    List<Date> candidates = null;
                    if (Frequency.yearly.equals(getFrequency())) {
                        candidates = getDaysInYear(byDay.getWeekDay().getCalendarDay(), startTime);
                    } else {
                        candidates = getDaysInMonth(byDay.getWeekDay().getCalendarDay(), startTime);
                    }
                    int occurrence = byDay.getOccurrence();
                    int size = candidates.size();
                    if (occurrence > 0 && occurrence <= size) {
                        found = candidates.get(occurrence - 1).equals(startTime);
                    } else if (occurrence < 0 && occurrence >= -size) {
                        // count backward
                        found = candidates.get(size + occurrence).equals(startTime);
                    }
                }
            }
            if (!found) {
                throw new IllegalArgumentException(
                        "start date is not compliant with the recurrence rule (day not in the BYDAY list)");
            }
        }

        // check byyearday
        int dayOfYear = cal.get(Calendar.DAY_OF_YEAR);
        if (!getYearDayList().contains(dayOfYear)) {
            throw new IllegalArgumentException(
                    "start date is not compliant with the recurrence rule (day not in the BYYEARDAY list)");
        }

        // check bymonthday
        int dayOfMonth = cal.get(Calendar.DAY_OF_MONTH);
        if (!getMonthDayList().contains(dayOfMonth)) {
            throw new IllegalArgumentException(
                    "start date is not compliant with the recurrence rule (day not in the BYMONTHDAY list)");
        }

        // check bymonth
        int month = cal.get(Calendar.MONTH);
        if (!getMonthList().contains(month)) {
            throw new IllegalArgumentException(
                    "start date is not compliant with the recurrence rule (month not in the BYMONTH list)");
        }
    }

    /**
     * Checks if the recur rule parts are coherent and meet the RFC 5545
     * requirements. This method throws an <code>IllegalArgumentException</code>
     * when the recurrence rule is invalid.
     */
    public void validateRecurrenceRule() {
        if (frequency == null) {
            throw new IllegalArgumentException("A recurrence rule MUST contain a FREQ rule part.");
        }

        if (getUntil() != null && count >= 1) {
            throw new IllegalArgumentException(
                    "The UNTIL and COUNT rule parts MUST NOT occur in the same recurrence rule.");
        }

        if (isNumericValueInByDay() && !(Frequency.monthly.equals(frequency) || Frequency.yearly.equals(frequency))) {
            throw new IllegalArgumentException("The BYDAY rule part MUST NOT be specified with a numeric value "
                    + "when the FREQ rule part is not set to MONTHLY or YEARLY.");
        }

        if (isNumericValueInByDay() && Frequency.yearly.equals(frequency) && !weekNoList.isEmpty()) {
            throw new IllegalArgumentException("The BYDAY rule part MUST NOT be specified with a numeric value "
                    + "with the FREQ rule part set to YEARLY " + "when the BYWEEKNO rule part is specified.");
        }

        if (!monthDayList.isEmpty() && Frequency.weekly.equals(frequency)) {
            throw new IllegalArgumentException(
                    "The BYMONTHDAY rule part MUST NOT be specified " + "when the FREQ rule part is set to WEEKLY.");
        }

        if (!yearDayList.isEmpty() && (Frequency.daily.equals(frequency) || Frequency.weekly.equals(frequency)
                || Frequency.monthly.equals(frequency))) {
            throw new IllegalArgumentException("The BYYEARDAY rule part MUST NOT be specified when the FREQ rule part "
                    + "is set to DAILY, WEEKLY, or MONTHLY.");
        }

        if (!weekNoList.isEmpty() && !Frequency.yearly.equals(frequency)) {
            throw new IllegalArgumentException("The BYWEEKNO rule part MUST NOT be used when the FREQ rule part "
                    + "is set to anything other than YEARLY.");
        }

        if (!setPosList.isEmpty() && !isByxxxDefined()) {
            throw new IllegalArgumentException(
                    "The BYSETPOS rule part MUST only be used in conjunction " + "with another BYxxx rule part.");
        }
    }

    /**
     * Checks if the BYDAY contains numeric values.
     *
     * @return True if the BYDAY contains numeric values.
     */
    private boolean isNumericValueInByDay() {
        for (ByDay byday : getDayList()) {
            if (byday.getOccurrence() != 0) {
                return true;
            }
        }
        return false;
    }

    /**
     * Checks if a BYxxx is defined.
     *
     * @return True if a BYxxx is defined.
     */
    private boolean isByxxxDefined() {
        return !(getSecondList().isEmpty() && getMinuteList().isEmpty() && getHourList().isEmpty()
                && getDayList().isEmpty() && getMonthDayList().isEmpty() && getYearDayList().isEmpty()
                && getWeekNoList().isEmpty() && getMonthList().isEmpty());
    }

    // ////////////////////////////////////////////////////////////////////////
    //
    // Recurrence Rule Evaluation Functions
    //
    // ////////////////////////////////////////////////////////////////////////

    /**
     * Returns the final time that the <code>RecurrenceRule</code> will match.
     * If neither count nor until is defined, return null.
     *
     * @return the final fire time.
     */
    public final Date getFinalFireTime() {
        Date result = null;
        if (getUntil() != null || getCount() != -1) {
            Calendar cal = Calendar.getInstance(getTimeZone());
            cal.setTimeInMillis(getStartDate().getTime() - 1000);
            Date temp = getTimeAfter(cal.getTime());
            while (temp != null) {
                result = temp;
                temp = getTimeAfter(temp);
            }
        } else {
            result = null;
        }
        return result;
    }

    /**
     * Returns true if the input date matches the recurrence rule.
     *
     * @param test
     *            The date to test.
     * @param dayOnly
     *            If the test should ignore hour / minute / second
     * @return True if the date is in the recurrence set.
     */
    public final boolean contains(final Date test, final boolean dayOnly) {
        // make the recurrence rule compute until test
        getTimeAfter(test);

        // go through recurrence set
        boolean found = false;
        Calendar item = Calendar.getInstance(getTimeZone());
        Calendar testCal = Calendar.getInstance(getTimeZone());
        testCal.setTime(test);
        for (int i = recurrenceSet.size() - 1; i >= 0 && !found; i--) {
            item.setTime(recurrenceSet.get(i));
            if (dayOnly) {
                found = item.get(Calendar.YEAR) == testCal.get(Calendar.YEAR)
                        && item.get(Calendar.DAY_OF_YEAR) == testCal.get(Calendar.DAY_OF_YEAR);
            } else {
                found = item.getTimeInMillis() == testCal.getTimeInMillis();
            }
            if (test.after(recurrenceSet.get(i))) {
                break;
            }
        }
        return found;
    }

    /**
     * Returns the occurrence of the recurrence rule just before a date or null
     * if there is no occurrence date found.
     *
     * @param endTime
     *            the reference date that is just after the occurrence to
     *            return.
     * @return the date of the occurrence just before the date in the recurrence
     *         set .
     */
    public final Date getTimeBefore(final Date endTime) {
        if (endTime == null) {
            throw new IllegalArgumentException("endTime can not be null");
        }
        // check if endTime is after start date
        if (getStartDate() != null && !endTime.after(getStartDate())) {
            // no previous occurrence possible, return null
            return null;
        }

        int failedSearchCount = 0;
        boolean stop = false;
        while (!stop) {
            // check if the recurrence set contains the date we are interested
            // in
            if (!recurrenceSet.isEmpty()) {
                // check if endTime is in the time period of the recurrenceSet
                if (!endTime.after(recurrenceSet.get(recurrenceSet.size() - 1))) {
                    // search the last date in recurrence set that is before
                    // endTime
                    Date item = null;
                    for (int i = recurrenceSet.size() - 1; i >= 0; i--) {
                        item = recurrenceSet.get(i);
                        if (item.before(endTime)) {
                            return item;
                        }
                    }
                } else if (endTime.after(recurrenceSet.get(recurrenceSet.size() - 1)) && searchComplete) {
                    return recurrenceSet.get(recurrenceSet.size() - 1);
                }
            }
            // check if the search is complete
            if (searchComplete) {
                // no other possibilities ...
                return null;
            } else {
                // new candidates may be found, search them
                boolean found = findNextCandidates();
                if (!found) {
                    // be careful: it is not because we don't find any candidate
                    // for this increment, that no candidate will be found at
                    // the next one ... Example :
                    // freq=YEARLY;BYMONTH=2;BYDAY=29;
                    // BUT to avoid searching to long, stop after a number of
                    // attempts
                    failedSearchCount++;
                    if (failedSearchCount > maxAttempts) {
                        stop = true;
                    }
                }
            }
        }
        return null;
    }

    /**
     * Returns the occurrence of the recurrence rule just after a date or null
     * if there is no occurrence date found.
     *
     * @param afterTime
     *            the reference date that is just before the occurrence to
     *            return.
     * @return the date of the occurrence just after the date in the recurrence
     *         set .
     */
    public final Date getTimeAfter(final Date afterTime) {
        if (afterTime == null) {
            throw new IllegalArgumentException("afterTime can not be null");
        }
        // check if fromDate is before until date
        if (getUntil() != null && afterTime.after(getUntil())) {
            // no next occurrence possible, return null
            return null;
        }

        int failedSearchCount = 0;
        boolean stop = false;
        while (!stop) {
            // check if the recurrence set contains the date we are interested
            // in
            if (!recurrenceSet.isEmpty()) {
                // check if afterTime is in the time period of the recurrenceSet
                if (afterTime.before(recurrenceSet.get(recurrenceSet.size() - 1))) {
                    // search the first date in recurrence set that is after
                    // afterTime
                    for (Date item : recurrenceSet) {
                        if (item.after(afterTime)) {
                            return item;
                        }
                    }
                }
            }

            // check if the search is complete
            if (searchComplete) {
                // no other possibilities ...
                return null;
            } else {
                // new candidates may be found, search them
                boolean found = findNextCandidates();
                if (!found) {
                    // be careful: it is not because we don't find any candidate
                    // for this increment, that no candidate will be found at
                    // the next one ... Example :
                    // freq=YEARLY;BYMONTH=2;BYDAY=29;
                    // BUT to avoid searching too long, stop after a number of
                    // attempts
                    failedSearchCount++;
                    if (failedSearchCount > maxAttempts) {
                        stop = true;
                    }
                }
            }
        }
        return null;
    }

    /**
     * Computes the next candidates by applying the recurrence rule one more
     * time.
     *
     * @return true if new candidates have been found.
     */
    private boolean findNextCandidates() {
        // 1. apply the frequency / interval rule parts
        // 2. apply the BYxxx recurrence rule parts
        referenceDate = applyFrequency();
        List<Date> candidates = applyByxxxRuleParts(referenceDate);

        if (!candidates.isEmpty()) {
            // first add the candidates to the recurrence set
            if (getCount() > 0) {
                int validCandidatesCount = Math.min(candidates.size(), getCount() - recurrenceSet.size());
                recurrenceSet.addAll(candidates.subList(0, validCandidatesCount));

                // check if the recurrence set is full
                searchComplete = getCount() <= recurrenceSet.size();
            } else if (getUntil() != null) {
                for (Iterator<Date> iterator = candidates.iterator(); iterator.hasNext() && !searchComplete;) {
                    Date candidate = iterator.next();
                    if (!candidate.after(getUntil())) {
                        recurrenceSet.add(candidate);
                    } else {
                        searchComplete = true;
                    }
                }
            } else {
                recurrenceSet.addAll(candidates);
            }
        }
        // optimize the next search to jump to the first candidate
        iteration = getNextIteration();
        return !candidates.isEmpty();
    }

    /**
     * Increment the iteration. An optimization is implemented by this function
     * when the frequency is less than daily. The purpose is to avoid testing a
     * lot of invalid dates and then to go faster to the next occurrence with
     * recurrence rule like this : FREQ=SECONDLY;BYYEARDAY=364;
     *
     * @return the new value of the incrementCount
     */
    private int getNextIteration() {
        // apply optimization for frequency higher than hourly
        if (Frequency.daily.compareTo(getFrequency()) < 0) {
            return iteration + 1;
        } else {
            // the goal is to find the next date that matches the recurrence
            // rule parts that limit the candidates.
            List<Date> dates = new ArrayList<Date>();
            dates.add(referenceDate);

            boolean resetSecond = false;
            boolean resetMinute = false;
            boolean resetHour = false;
            boolean resetDay = false;

            if (Frequency.secondly.compareTo(getFrequency()) >= 0) {
                resetSecond = getSecondList().isEmpty();
                dates = limitSecond(dates);
            }
            if (Frequency.minutely.compareTo(getFrequency()) >= 0) {
                resetMinute = getMinuteList().isEmpty();
                dates = limitMinute(dates, resetSecond);
            }
            if (Frequency.hourly.compareTo(getFrequency()) >= 0) {
                resetHour = getHourList().isEmpty();
                dates = limitHour(dates, resetSecond, resetMinute);
            }
            if (Frequency.daily.compareTo(getFrequency()) >= 0) {
                resetDay = getDayList().isEmpty() && getMonthDayList().isEmpty() && getYearDayList().isEmpty();
                dates = limitDay(dates, resetSecond, resetMinute, resetHour);
            }
            dates = limitMonth(dates, resetSecond, resetMinute, resetHour, resetDay);

            int newIncrement = iteration;
            if (!dates.isEmpty()) {
                // sort the dates in ascending order
                Collections.sort(dates);

                // evaluate the number of iterations of the FREQ/INTERVAL rules
                // between the reference date and the first restricted date
                long delta = dates.get(0).getTime() - referenceDate.getTime();
                if (Frequency.secondly.equals(getFrequency())) {
                    newIncrement += Math.floor(delta * 0.001 / getInterval());
                } else if (Frequency.minutely.equals(getFrequency())) {
                    newIncrement += Math.floor(delta * 0.001 / (60 * getInterval()));
                } else if (Frequency.hourly.equals(getFrequency())) {
                    newIncrement += Math.floor(delta * 0.001 / (3600 * getInterval()));
                } else if (Frequency.daily.equals(getFrequency())) {
                    newIncrement += Math.floor(delta * 0.001 / (86400 * getInterval()));
                }
            }

            // to be sure that we increment (otherwise => infinite loop)
            if (newIncrement <= iteration) {
                newIncrement = iteration + 1;
            }
            return newIncrement;
        }
    }

    /**
     * Applies a certain number of times (defined by <code>incrementCount</code>
     * ) the frequency/interval rule part to the start date.
     *
     * @return The new date.
     */
    private Date applyFrequency() {
        // create a calendar and set it to the start date
        Calendar cal = Calendar.getInstance(getTimeZone());
        cal.setLenient(false);
        cal.setTime(getStartDate());

        // apply frequency only if increment is positive, otherwise the
        // startDate is the date we search
        if (iteration > 0) {
            int increment = iteration;
            if (getInterval() > 1) {
                increment = getInterval() * iteration;
            }
            // this line is not strictly compliant with RFC 5545 :
            // for example, if we increment by 1 month 31/05/2013, the result
            // will be 30/06/2013. RFC 5545 says that the result must be
            // ignored.
            cal.add(getFrequency().getCalendarField(), increment);
        }
        return cal.getTime();
    }

    /**
     * Applies the BYxxx recurrence rule parts to the date computed by the
     * frequency/interval rule parts.
     *
     * @param date
     *            The initial date.
     * @return The list of dates matching the complete recurrence rule.
     */
    private List<Date> applyByxxxRuleParts(final Date date) {
        List<Date> dates = new ArrayList<Date>();
        dates.add(date);

        // Caution : the order to evaluate the BYxxx rule parts is
        // specified by RFC 5545 and shall not be changed :
        // if multiple BYxxx rule parts are specified, then after evaluating
        // the specified FREQ and INTERVAL rule parts, the BYxxx rule parts
        // are applied to the current set of evaluated occurrences in the
        // following order: BYMONTH, BYWEEKNO, BYYEARDAY, BYMONTHDAY, BYDAY,
        // BYHOUR, BYMINUTE, BYSECOND and BYSETPOS; then COUNT and UNTIL are
        // evaluated.

        if (!getMonthList().isEmpty()) {
            dates = applyByMonthRulePart(dates);
        }

        if (!getWeekNoList().isEmpty()) {
            dates = applyByWeekNoRulePart(dates);
        }

        if (!getYearDayList().isEmpty()) {
            dates = applyByYearDayRulePart(dates);
        }

        if (!getMonthDayList().isEmpty()) {
            dates = applyByMonthDayRulePart(dates);
        }

        if (!getDayList().isEmpty()) {
            dates = applyByDayRulePart(dates);
        }

        if (!getHourList().isEmpty()) {
            dates = applyByHourRulePart(dates);
        }

        if (!getMinuteList().isEmpty()) {
            dates = applyByMinuteRulePart(dates);
        }

        if (!getSecondList().isEmpty()) {
            dates = applyBySecondRulePart(dates);
        }

        // sort the dates before applying BYSETPOS
        Collections.sort(dates);

        if (!getSetPosList().isEmpty()) {
            dates = applyBySetPosRulePart(dates);
        }

        // filter all candidates before the start date
        if (!dates.isEmpty()) {
            if (dates.get(dates.size() - 1).before(getStartDate())) {
                // no candidate is valid against startDate
                dates.clear();
            } else if (dates.get(0).before(getStartDate())) {
                // at least one date is before startDate
                // remove all of them
                Date candidate = null;
                for (Iterator<Date> iterator = dates.iterator(); iterator.hasNext();) {
                    candidate = iterator.next();
                    if (candidate.before(getStartDate())) {
                        iterator.remove();
                    }
                }
            }
        }

        return dates;
    }

    /**
     * Applies the BYSECOND rule part to the candidate dates.
     *
     * @param candidates
     *            The list of dates to evaluate.
     * @return The list of dates matching the BYSECOND rule part.
     */
    private List<Date> applyBySecondRulePart(final List<Date> candidates) {
        /*- RFC5545:
         * +----------+--------+--------+-------+-------+------+-------+------+
         * |          |SECONDLY|MINUTELY|HOURLY |DAILY  |WEEKLY|MONTHLY|YEARLY|
         * +----------+--------+--------+-------+-------+------+-------+------+
         * |BYSECOND  |Limit   |Expand  |Expand |Expand |Expand|Expand |Expand|
         * +----------+--------+--------+-------+-------+------+-------+------+
         */

        final Calendar cal = Calendar.getInstance(getTimeZone());
        if (Frequency.minutely.compareTo(getFrequency()) <= 0) {
            // expands the behavior of the FREQ rule part
            List<Date> extraDates = new ArrayList<Date>();
            for (Date date : candidates) {
                for (Integer second : getSecondList()) {
                    // set calendar to the candidate date at each iteration
                    cal.setTime(date);
                    cal.set(Calendar.SECOND, second);
                    extraDates.add(cal.getTime());
                }
            }
            return extraDates;
        } else {
            // limits the behavior of the FREQ rule part
            List<Date> filteredDates = new ArrayList<Date>();
            for (Date date : candidates) {
                cal.setTime(date);
                // check if the candidate is compliant with the second list
                if (getSecondList().contains(cal.get(Calendar.SECOND))) {
                    filteredDates.add(cal.getTime());
                }
            }
            return filteredDates;
        }
    }

    /**
     * Applies the BYMINUTE rule part to the candidate dates.
     *
     * @param candidates
     *            The list of dates to evaluate.
     * @return The list of dates matching the BYMINUTE rule part.
     */
    private List<Date> applyByMinuteRulePart(final List<Date> candidates) {
        /*- RFC5545:
         * +----------+--------+--------+-------+-------+------+-------+------+
         * |          |SECONDLY|MINUTELY|HOURLY |DAILY  |WEEKLY|MONTHLY|YEARLY|
         * +----------+--------+--------+-------+-------+------+-------+------+
         * |BYMINUTE  |Limit   |Limit   |Expand |Expand |Expand|Expand |Expand|
         * +----------+--------+--------+-------+-------+------+-------+------+
         */

        final Calendar cal = Calendar.getInstance(getTimeZone());
        if (Frequency.hourly.compareTo(getFrequency()) <= 0) {
            // expands the behavior of the FREQ rule part
            List<Date> extraDates = new ArrayList<Date>();
            for (Date date : candidates) {
                for (Integer minute : getMinuteList()) {
                    // set calendar to the candidate date at each iteration
                    cal.setTime(date);
                    cal.set(Calendar.MINUTE, minute);
                    extraDates.add(cal.getTime());
                }
            }
            return extraDates;
        } else {
            // limits the behavior of the FREQ rule part
            List<Date> filteredDates = new ArrayList<Date>();
            for (Date date : candidates) {
                cal.setTime(date);
                // check if the candidate is compliant with the minute list
                if (getMinuteList().contains(cal.get(Calendar.MINUTE))) {
                    filteredDates.add(cal.getTime());
                }
            }
            return filteredDates;
        }
    }

    /**
     * Applies the BYHOUR rule part to the candidate dates.
     *
     * @param candidates
     *            The list of dates to evaluate.
     * @return The list of dates matching the BYHOUR rule part.
     */
    private List<Date> applyByHourRulePart(final List<Date> candidates) {
        /*- RFC5545:
         * +----------+--------+--------+-------+-------+------+-------+------+
         * |          |SECONDLY|MINUTELY|HOURLY |DAILY  |WEEKLY|MONTHLY|YEARLY|
         * +----------+--------+--------+-------+-------+------+-------+------+
         * |BYHOUR    |Limit   |Limit   |Limit  |Expand |Expand|Expand |Expand|
         * +----------+--------+--------+-------+-------+------+-------+------+
         */

        final Calendar cal = Calendar.getInstance(getTimeZone());
        if (Frequency.daily.compareTo(getFrequency()) <= 0) {
            // expands the behavior of the FREQ rule part
            List<Date> extraDates = new ArrayList<Date>();
            for (Date date : candidates) {
                for (Integer hour : getHourList()) {
                    // set calendar to the candidate date at each iteration
                    cal.setTime(date);
                    cal.set(Calendar.HOUR_OF_DAY, hour);
                    extraDates.add(cal.getTime());
                }
            }
            return extraDates;
        } else {
            // limits the behavior of the FREQ rule part
            List<Date> filteredDates = new ArrayList<Date>();
            for (Date date : candidates) {
                cal.setTime(date);
                // check if the candidate is compliant with the hour list
                if (getHourList().contains(cal.get(Calendar.HOUR_OF_DAY))) {
                    filteredDates.add(cal.getTime());
                }
            }
            return filteredDates;
        }
    }

    /**
     * Applies the BYDAY rule part to the candidate dates.
     *
     * @param candidates
     *            The list of dates to evaluate.
     * @return The list of dates matching the BYDAY rule part.
     */
    private List<Date> applyByDayRulePart(final List<Date> candidates) {
        /*- RFC5545:
         * +----------+--------+--------+-------+-------+------+-------+------+
         * |          |SECONDLY|MINUTELY|HOURLY |DAILY  |WEEKLY|MONTHLY|YEARLY|
         * +----------+--------+--------+-------+-------+------+-------+------+
         * |BYDAY     |Limit   |Limit   |Limit  |Limit  |Expand|Note 1 |Note 2|
         * +----------+--------+--------+-------+-------+------+-------+------+
         *
         * Note 1: Limit if BYMONTHDAY is present; otherwise, special expand
         *         for MONTHLY.
         * Note 2: Limit if BYYEARDAY or BYMONTHDAY is present; otherwise,
         *         special expand for WEEKLY if BYWEEKNO present; otherwise,
         *         special expand for MONTHLY if BYMONTH present; otherwise,
         *         special expand for YEARLY.
         */

        final Calendar cal = Calendar.getInstance(getTimeZone());
        if (Frequency.weekly.equals(getFrequency()) || (Frequency.yearly.equals(getFrequency())
                && getMonthDayList().isEmpty() && getYearDayList().isEmpty() && !getWeekNoList().isEmpty())) {
            // expands the behavior of the FREQ rule part
            List<Date> extraDates = new ArrayList<Date>();
            for (Date date : candidates) {
                for (ByDay byDay : getDayList()) {
                    // set calendar to the candidate date at each iteration
                    cal.setTime(date);
                    // WKST is significant when a BYDAY is specified with a
                    // weekly frequency
                    cal.setFirstDayOfWeek(getWeekStart().getCalendarDay());
                    // in this case, the byDay does not contain an occurrence
                    // ==> just apply the day of the week to the calendar
                    cal.set(Calendar.DAY_OF_WEEK, byDay.getWeekDay().getCalendarDay());
                    extraDates.add(cal.getTime());
                }
            }
            return extraDates;
        } else if ((Frequency.monthly.equals(getFrequency()) && getMonthDayList().isEmpty())
                || (Frequency.yearly.equals(getFrequency()) && getMonthDayList().isEmpty() && getYearDayList().isEmpty()
                        && !getMonthList().isEmpty())) {
            // expands the behavior of the FREQ rule part
            List<Date> extraDates = new ArrayList<Date>();
            for (Date date : candidates) {
                for (ByDay byDay : getDayList()) {
                    // set calendar to the candidate date at each iteration
                    cal.setTime(date);
                    // gets the dates in the month matching the week of the day
                    List<Date> dates = getDaysInMonth(byDay.getWeekDay().getCalendarDay(), date);
                    // applies the occurrence property of the BYDAY
                    int occurrence = byDay.getOccurrence();
                    int size = dates.size();
                    if (occurrence > 0 && occurrence <= size) {
                        extraDates.add(dates.get(occurrence - 1));
                    } else if (occurrence < 0 && occurrence >= -size) {
                        // count backward
                        extraDates.add(dates.get(size + occurrence));
                    } else if (occurrence == 0) {
                        // add all
                        extraDates.addAll(dates);
                    }
                }
            }
            return extraDates;

        } else
            if (Frequency.yearly.equals(getFrequency()) && getMonthDayList().isEmpty() && getYearDayList().isEmpty()) {
            // expands the behavior of the FREQ rule part
            List<Date> extraDates = new ArrayList<Date>();
            for (Date date : candidates) {
                for (ByDay byDay : getDayList()) {
                    // set calendar to the candidate date at each iteration
                    cal.setTime(date);
                    // gets the dates in the month matching the week of the day
                    List<Date> dates = getDaysInYear(byDay.getWeekDay().getCalendarDay(), date);
                    // applies the occurrence property of the BYDAY
                    int occurrence = byDay.getOccurrence();
                    int size = dates.size();
                    if (occurrence > 0 && occurrence <= size) {
                        extraDates.add(dates.get(occurrence - 1));
                    } else if (occurrence < 0 && occurrence >= -size) {
                        // count backward
                        extraDates.add(dates.get(size + occurrence));
                    } else if (occurrence == 0) {
                        // add all
                        extraDates.addAll(dates);
                    }
                }
            }
            return extraDates;
        } else {
            // limits the behavior of the FREQ rule part
            List<Date> filteredDates = new ArrayList<Date>();
            List<Integer> weekDayList = new ArrayList<Integer>();
            for (ByDay byDay : getDayList()) {
                weekDayList.add(byDay.getWeekDay().getCalendarDay());
            }
            for (Date date : candidates) {
                cal.setTime(date);
                // check if the candidate is compliant with the day list
                if (weekDayList.contains(cal.get(Calendar.DAY_OF_WEEK))) {
                    filteredDates.add(cal.getTime());
                }
            }
            return filteredDates;
        }
    }

    /**
     * Applies the BYMONTHDAY rule part to the candidate dates.
     *
     * @param candidates
     *            The list of dates to evaluate.
     * @return The list of dates matching the BYMONTHDAY rule part.
     */
    private List<Date> applyByMonthDayRulePart(final List<Date> candidates) {
        /*- RFC5545:
         * +----------+--------+--------+-------+-------+------+-------+------+
         * |          |SECONDLY|MINUTELY|HOURLY |DAILY  |WEEKLY|MONTHLY|YEARLY|
         * +----------+--------+--------+-------+-------+------+-------+------+
         * |BYMONTHDAY|Limit   |Limit   |Limit  |Limit  |N/A   |Expand |Expand|
         * +----------+--------+--------+-------+-------+------+-------+------+
         */

        final Calendar cal = Calendar.getInstance(getTimeZone());
        if (Frequency.monthly.compareTo(getFrequency()) <= 0) {
            // expands the behavior of the FREQ rule part
            List<Date> extraDates = new ArrayList<Date>();
            for (Date date : candidates) {
                for (Integer monthDay : getMonthDayList()) {
                    // set calendar to the candidate date at each iteration
                    cal.setTime(date);
                    // count the number of days in the year
                    int dayCount = countDaysInMonth(cal.get(Calendar.YEAR), cal.get(Calendar.MONTH));
                    // monthDay is in the range [-31 .. -1] U [1 .. 31]
                    if (monthDay > 0) {
                        // check if monthDay is less or equal the number of days
                        // in the month
                        if (monthDay <= dayCount) {
                            cal.set(Calendar.DAY_OF_MONTH, monthDay);
                            extraDates.add(cal.getTime());
                        }
                    } else {
                        // monthDay = -1 => DAY_OF_MONTH = 31 or 30 or 29 or 28
                        // (depending on the number of days in the month)
                        int dayOfMonth = dayCount + monthDay + 1;
                        // check if the resulting day number is strictly
                        // positive
                        if (dayOfMonth > 0) {
                            cal.set(Calendar.DAY_OF_MONTH, dayOfMonth);
                            extraDates.add(cal.getTime());
                        }
                    }
                }
            }
            return extraDates;
        } else {
            // limits the behavior of the FREQ rule part
            List<Date> filteredDates = new ArrayList<Date>();
            for (Date date : candidates) {
                cal.setTime(date);
                // check if the candidate is compliant with the month day list
                if (getMonthDayList().contains(cal.get(Calendar.DAY_OF_MONTH))) {
                    filteredDates.add(cal.getTime());
                }
            }
            return filteredDates;
        }
    }

    /**
     * Applies the BYYEARDAY rule part to the candidate dates.
     *
     * @param candidates
     *            The list of dates to evaluate.
     * @return The list of dates matching the BYYEARDAY rule part.
     */
    private List<Date> applyByYearDayRulePart(final List<Date> candidates) {
        /*- RFC5545:
         * +----------+--------+--------+-------+-------+------+-------+------+
         * |          |SECONDLY|MINUTELY|HOURLY |DAILY  |WEEKLY|MONTHLY|YEARLY|
         * +----------+--------+--------+-------+-------+------+-------+------+
         * |BYYEARDAY |Limit   |Limit   |Limit  |N/A    |N/A   |N/A    |Expand|
         * +----------+--------+--------+-------+-------+------+-------+------+
         */

        final Calendar cal = Calendar.getInstance(getTimeZone());
        if (Frequency.yearly.equals(getFrequency())) {
            // expands the behavior of the FREQ rule part
            List<Date> extraDates = new ArrayList<Date>();
            for (Date date : candidates) {
                for (Integer yearDay : getYearDayList()) {
                    // set calendar to the candidate date at each iteration
                    cal.setTime(date);
                    // count the number of days in the year
                    int dayCount = countDaysInYear(cal.get(Calendar.YEAR));
                    // yearDay is in the range [-366 .. -1] U [1 .. 366]
                    if (yearDay > 0) {
                        // check if yearDay is less or equal the number of days
                        // in the year
                        if (yearDay <= dayCount) {
                            cal.set(Calendar.DAY_OF_YEAR, yearDay);
                            extraDates.add(cal.getTime());
                        }
                    } else {
                        // yearDay = -1 => DAY_OF_YEAR = 365 or 366 (depending
                        // on the number of days in the year)
                        int dayOfYear = dayCount + yearDay + 1;
                        // check if the resulting day number is strictly
                        // positive
                        if (dayOfYear > 0) {
                            cal.set(Calendar.DAY_OF_YEAR, dayOfYear);
                            extraDates.add(cal.getTime());
                        }
                    }
                }
            }
            return extraDates;
        } else {
            // limits the behavior of the FREQ rule part
            List<Date> filteredDates = new ArrayList<Date>();
            for (Date date : candidates) {
                cal.setTime(date);
                // check if the candidate is compliant with the year day list
                if (getYearDayList().contains(cal.get(Calendar.DAY_OF_YEAR))) {
                    filteredDates.add(cal.getTime());
                }
            }
            return filteredDates;
        }
    }

    /**
     * Applies the BYWEEKNO rule part to the candidate dates.
     *
     * @param candidates
     *            The list of dates to evaluate.
     * @return The list of dates matching the BYWEEKNO rule part.
     */
    private List<Date> applyByWeekNoRulePart(final List<Date> candidates) {
        /*- RFC5545:
         * +----------+--------+--------+-------+-------+------+-------+------+
         * |          |SECONDLY|MINUTELY|HOURLY |DAILY  |WEEKLY|MONTHLY|YEARLY|
         * +----------+--------+--------+-------+-------+------+-------+------+
         * |BYWEEKNO  |N/A     |N/A     |N/A    |N/A    |N/A   |N/A    |Expand|
         * +----------+--------+--------+-------+-------+------+-------+------+
         */

        final Calendar cal = Calendar.getInstance(getTimeZone());
        // WKST is significant when a BYWEEKNO rule part is specified
        cal.setFirstDayOfWeek(getWeekStart().getCalendarDay());
        // BYWEEKNO only expands the behavior of the FREQ rule part
        List<Date> extraDates = new ArrayList<Date>();
        for (Date date : candidates) {
            for (Integer weekNo : getWeekNoList()) {
                cal.setTime(date);
                // count the number of weeks in the year (depends on the WKST)
                int weekCount = countWeeksInYear(cal.get(Calendar.YEAR), getWeekStart().getCalendarDay());
                // weekNo is in the range [-53 .. -1] U [1 .. 53]
                if (weekNo > 0) {
                    // check if weekNo is less or equal the number of weeks in
                    // the year
                    if (weekNo <= weekCount) {
                        cal.set(Calendar.WEEK_OF_YEAR, weekNo);
                        extraDates.add(cal.getTime());
                    }
                } else {
                    // weekNo = -1 => WEEK_OF_YEAR = 52 or 53 (depending on the
                    // number of weeks in the year)
                    int weekOfYear = weekCount + weekNo + 1;
                    // check if the resulting week number is strictly positive
                    if (weekOfYear > 0) {
                        cal.set(Calendar.WEEK_OF_YEAR, weekOfYear);
                        extraDates.add(cal.getTime());
                    }
                }
            }
        }
        return extraDates;
    }

    /**
     * Applies the BYMONTH rule part to the candidate dates.
     *
     * @param candidates
     *            The list of dates to evaluate.
     * @return The list of dates matching the BYMONTH rule part.
     */
    private List<Date> applyByMonthRulePart(final List<Date> candidates) {
        /*- RFC5545:
         * +----------+--------+--------+-------+-------+------+-------+------+
         * |          |SECONDLY|MINUTELY|HOURLY |DAILY  |WEEKLY|MONTHLY|YEARLY|
         * +----------+--------+--------+-------+-------+------+-------+------+
         * |BYMONTH   |Limit   |Limit   |Limit  |Limit  |Limit |Limit  |Expand|
         * +----------+--------+--------+-------+-------+------+-------+------+
         */
        // caution : calendar's month field is in the range [0 .. 11]

        final Calendar cal = Calendar.getInstance(getTimeZone());
        if (Frequency.yearly.equals(getFrequency())) {
            // expands the behavior of the FREQ rule part
            List<Date> extraDates = new ArrayList<Date>();
            for (Date date : candidates) {
                for (Integer month : getMonthList()) {
                    // set calendar to the candidate date at each iteration
                    cal.setTime(date);
                    // month is in the range [1 .. 12]
                    cal.roll(Calendar.MONTH, (month - 1) - cal.get(Calendar.MONTH));
                    extraDates.add(cal.getTime());
                }
            }
            return extraDates;
        } else {
            // limits the behavior of the FREQ rule part
            List<Date> filteredDates = new ArrayList<Date>();
            for (Date date : candidates) {
                cal.setTime(date);
                // check if the candidate is compliant with the month list
                if (getMonthList().contains(cal.get(Calendar.MONTH) + 1)) {
                    filteredDates.add(cal.getTime());
                }
            }
            return filteredDates;
        }
    }

    /**
     * Applies the BYSETPOS rule part to the candidate dates.
     *
     * @param candidates
     *            The list of dates to evaluate.
     * @return The list of dates matching the BYSETPOS rule part.
     */
    private List<Date> applyBySetPosRulePart(final List<Date> candidates) {
        /*- RFC5545:
         * +----------+--------+--------+-------+-------+------+-------+------+
         * |          |SECONDLY|MINUTELY|HOURLY |DAILY  |WEEKLY|MONTHLY|YEARLY|
         * +----------+--------+--------+-------+-------+------+-------+------+
         * |BYSETPOS  |Limit   |Limit   |Limit  |Limit  |Limit |Limit  |Limit |
         * +----------+--------+--------+-------+-------+------+-------+------+
         */

        int candidatesCount = candidates.size();
        List<Date> selectedDates = new ArrayList<Date>();
        for (Integer setPos : getSetPosList()) {
            if (setPos > 0 && setPos <= candidatesCount) {
                selectedDates.add(candidates.get(setPos - 1));
            } else if (setPos < 0 && setPos >= -candidatesCount) {
                selectedDates.add(candidates.get(candidatesCount + setPos));
            }
        }
        return selectedDates;
    }

    /**
     * Updates the dates to make them match the BYSECOND limiting rule.
     *
     * @param dates
     *            The dates to update
     * @return The updated dates.
     */
    private List<Date> limitSecond(final List<Date> dates) {
        if (getSecondList().isEmpty()) {
            // no limitation to apply
            return dates;
        } else {
            List<Date> results = new ArrayList<Date>();
            Calendar cal = Calendar.getInstance(getTimeZone());

            for (Integer second : getSecondList()) {
                for (Date date : dates) {
                    cal.setTime(date);
                    if (cal.get(Calendar.SECOND) > second) {
                        cal.add(Calendar.MINUTE, 1);
                    }
                    if (cal.get(Calendar.SECOND) != second) {
                        cal.set(Calendar.SECOND, second);
                    }
                    results.add(cal.getTime());
                }
            }
            return results;
        }
    }

    /**
     * Updates the dates to make them match the BYMINUTE limiting rule.
     *
     * @param dates
     *            The dates to update
     * @param resetSecond
     *            True if the method shall reset the second.
     * @return The updated dates.
     */
    private List<Date> limitMinute(final List<Date> dates, final boolean resetSecond) {
        if (getMinuteList().isEmpty()) {
            // no limitation to apply
            return dates;
        } else {
            List<Date> results = new ArrayList<Date>();
            Calendar cal = Calendar.getInstance(getTimeZone());

            for (Integer minute : getMinuteList()) {
                for (Date date : dates) {
                    cal.setTime(date);
                    if (cal.get(Calendar.MINUTE) > minute) {
                        cal.add(Calendar.HOUR_OF_DAY, 1);
                    }
                    if (cal.get(Calendar.MINUTE) != minute) {
                        cal.set(Calendar.MINUTE, minute);
                        if (resetSecond) {
                            cal.set(Calendar.SECOND, 0);
                        }
                    }
                    results.add(cal.getTime());
                }
            }
            return results;
        }
    }

    /**
     * Updates the dates to make them match the BYHOUR limiting rule.
     *
     * @param dates
     *            The dates to update
     * @param resetSecond
     *            True if the method shall reset the second.
     * @param resetMinute
     *            True if the method shall reset the minute.
     * @return The updated dates.
     */
    private List<Date> limitHour(final List<Date> dates, final boolean resetSecond, final boolean resetMinute) {
        if (getHourList().isEmpty()) {
            // no limitation to apply
            return dates;
        } else {
            List<Date> results = new ArrayList<Date>();
            Calendar cal = Calendar.getInstance(getTimeZone());

            for (Integer hour : getHourList()) {
                for (Date date : dates) {
                    cal.setTime(date);
                    if (cal.get(Calendar.HOUR_OF_DAY) > hour) {
                        cal.add(Calendar.DAY_OF_YEAR, 1);
                    }
                    if (cal.get(Calendar.HOUR_OF_DAY) != hour) {
                        cal.set(Calendar.HOUR_OF_DAY, hour);
                        if (resetSecond) {
                            cal.set(Calendar.SECOND, 0);
                        }
                        if (resetMinute) {
                            cal.set(Calendar.MINUTE, 0);
                        }
                    }
                    results.add(cal.getTime());
                }
            }
            return results;
        }
    }

    /**
     * Updates the dates to make them match the BYYEARDAY, BYMONTHDAY, BYDAY
     * limiting rule.
     *
     * @param dates
     *            The dates to update
     * @param resetSecond
     *            True if the method shall reset the second.
     * @param resetMinute
     *            True if the method shall reset the minute.
     * @param resetHour
     *            True if the method shall reset the hour.
     * @return The updated dates.
     */
    private List<Date> limitDay(final List<Date> dates, final boolean resetSecond, final boolean resetMinute,
            final boolean resetHour) {
        List<Date> results = new ArrayList<Date>();
        Calendar cal = Calendar.getInstance(getTimeZone());

        // find the days in the year
        if (!getYearDayList().isEmpty()) {
            for (Date date : dates) {
                for (Integer yearDay : getYearDayList()) {
                    cal.setTime(date);
                    boolean incYear = false;
                    do {
                        // count the number of days in the year
                        int dayCount = countDaysInYear(cal.get(Calendar.YEAR));
                        // yearDay is in the range [-366 .. -1] U [1 .. 366]
                        if (yearDay > 0) {
                            // check if yearDay is less or equal the number
                            // of days in the year
                            if (yearDay <= dayCount) {
                                incYear = cal.get(Calendar.DAY_OF_YEAR) > yearDay;
                                if (cal.get(Calendar.DAY_OF_YEAR) != yearDay) {
                                    cal.set(Calendar.DAY_OF_YEAR, yearDay);
                                    if (resetSecond) {
                                        cal.set(Calendar.SECOND, 0);
                                    }
                                    if (resetMinute) {
                                        cal.set(Calendar.MINUTE, 0);
                                    }
                                    if (resetHour) {
                                        cal.set(Calendar.HOUR_OF_DAY, 0);
                                    }
                                }
                            } else {
                                incYear = true;
                            }
                        } else {
                            // yearDay = -1 => DAY_OF_YEAR = 365 or 366
                            // (depending on the number of days in the year)
                            int dayOfYear = dayCount + yearDay + 1;
                            // check if the resulting day number is strictly
                            // positive
                            if (dayOfYear > 0) {
                                incYear = cal.get(Calendar.DAY_OF_YEAR) > yearDay;
                                if (cal.get(Calendar.DAY_OF_YEAR) != yearDay) {
                                    cal.set(Calendar.DAY_OF_YEAR, yearDay);
                                    if (resetSecond) {
                                        cal.set(Calendar.SECOND, 0);
                                    }
                                    if (resetMinute) {
                                        cal.set(Calendar.MINUTE, 0);
                                    }
                                    if (resetHour) {
                                        cal.set(Calendar.HOUR_OF_DAY, 0);
                                    }
                                }
                            } else {
                                incYear = true;
                            }
                        }
                        if (incYear) {
                            cal.add(Calendar.YEAR, 1);
                        }
                    } while (incYear);
                    results.add(cal.getTime());
                }
            }
        } else if (!getMonthDayList().isEmpty()) {
            for (Integer monthDay : getMonthDayList()) {
                for (Date date : dates) {
                    cal.setTime(date);
                    boolean incMonth = false;
                    do {
                        // count the number of days in the year
                        int dayCount = countDaysInMonth(cal.get(Calendar.YEAR), cal.get(Calendar.MONTH));
                        // monthDay is in the range [-31 .. -1] U [1 .. 31]
                        if (monthDay > 0) {
                            // check if monthDay is less or equal the number
                            // of days in the month
                            if (monthDay <= dayCount) {
                                incMonth = cal.get(Calendar.DAY_OF_MONTH) > monthDay;
                                if (cal.get(Calendar.DAY_OF_MONTH) != monthDay) {
                                    cal.set(Calendar.DAY_OF_MONTH, monthDay);
                                    if (resetSecond) {
                                        cal.set(Calendar.SECOND, 0);
                                    }
                                    if (resetMinute) {
                                        cal.set(Calendar.MINUTE, 0);
                                    }
                                    if (resetHour) {
                                        cal.set(Calendar.HOUR_OF_DAY, 0);
                                    }
                                }
                            } else {
                                incMonth = true;
                            }
                        } else {
                            // monthDay = -1 => DAY_OF_MONTH = 31 or 30 or
                            // 29 or 28 (depending on the number of days in
                            // the month)
                            int dayOfMonth = dayCount + monthDay + 1;
                            // check if the resulting day number is strictly
                            // positive
                            if (dayOfMonth > 0) {
                                incMonth = cal.get(Calendar.DAY_OF_MONTH) > monthDay;
                                if (cal.get(Calendar.DAY_OF_MONTH) != monthDay) {
                                    cal.set(Calendar.DAY_OF_MONTH, monthDay);
                                    if (resetSecond) {
                                        cal.set(Calendar.SECOND, 0);
                                    }
                                    if (resetMinute) {
                                        cal.set(Calendar.MINUTE, 0);
                                    }
                                    if (resetHour) {
                                        cal.set(Calendar.HOUR_OF_DAY, 0);
                                    }
                                }
                            } else {
                                incMonth = true;
                            }
                        }
                        if (incMonth) {
                            cal.add(Calendar.MONTH, 1);
                        }
                    } while (incMonth);
                    results.add(cal.getTime());
                }
            }
        } else if (!getDayList().isEmpty()) {
            for (ByDay byday : getDayList()) {
                for (Date date : dates) {
                    cal.setTime(date);
                    int calendarDay = byday.getWeekDay().getCalendarDay();
                    if (cal.get(Calendar.DAY_OF_WEEK) > calendarDay) {
                        cal.add(Calendar.WEEK_OF_YEAR, 1);
                    }
                    if (cal.get(Calendar.DAY_OF_WEEK) != calendarDay) {
                        cal.set(Calendar.DAY_OF_WEEK, calendarDay);
                        if (resetSecond) {
                            cal.set(Calendar.SECOND, 0);
                        }
                        if (resetMinute) {
                            cal.set(Calendar.MINUTE, 0);
                        }
                        if (resetHour) {
                            cal.set(Calendar.HOUR_OF_DAY, 0);
                        }
                    }
                    results.add(cal.getTime());
                }
            }
        } else {
            // no limitation to apply
            return dates;
        }

        return results;
    }

    /**
     * Updates the dates to make them match the BYMONTH limiting rule.
     *
     * @param dates
     *            The dates to update
     * @param resetSecond
     *            True if the method shall reset the second.
     * @param resetMinute
     *            True if the method shall reset the minute.
     * @param resetHour
     *            True if the method shall reset the hour.
     * @param resetDay
     *            True if the method shall reset the day to the first day in
     *            month.
     * @return The updated dates.
     */
    private List<Date> limitMonth(final List<Date> dates, final boolean resetSecond, final boolean resetMinute,
            final boolean resetHour, final boolean resetDay) {
        if (getMonthList().isEmpty()) {
            return dates;
        } else {
            List<Date> results = new ArrayList<Date>();
            Calendar cal = Calendar.getInstance(getTimeZone());

            for (Integer month : getMonthList()) {
                for (Date date : dates) {
                    cal.setTime(date);
                    if (cal.get(Calendar.MONTH) > month - 1) {
                        cal.add(Calendar.YEAR, 1);
                    }
                    if (cal.get(Calendar.MONTH) != month - 1) {
                        cal.set(Calendar.MONTH, month - 1);
                        if (resetSecond) {
                            cal.set(Calendar.SECOND, 0);
                        }
                        if (resetMinute) {
                            cal.set(Calendar.MINUTE, 0);
                        }
                        if (resetHour) {
                            cal.set(Calendar.HOUR_OF_DAY, 0);
                        }
                        if (resetDay) {
                            cal.set(Calendar.DAY_OF_MONTH, 1);
                        }
                    }

                    results.add(cal.getTime());
                }
            }
            return results;
        }
    }

    /**
     * Counts the number of weeks in a year.
     *
     * @param year
     *            Year to test.
     * @param firstDayOfWeek
     *            The week of the day that is considered to be the first day of
     *            the week.
     * @return THe number of weeks.
     */
    static int countWeeksInYear(final int year, final int firstDayOfWeek) {
        Calendar c = Calendar.getInstance();
        c.setFirstDayOfWeek(firstDayOfWeek);
        // set to January 1st
        c.set(year, 0, 1, 0, 0, 0);
        c.set(Calendar.MILLISECOND, 0);
        // set week of year to 53
        c.set(Calendar.WEEK_OF_YEAR, 53);
        if (c.get(Calendar.WEEK_OF_YEAR) == 53) {
            // Week of year is 53 => 53 weeks in the year
            return 53;
        } else {
            // Week of year is 1 of the next year => 52 weeks in the year
            return 52;
        }
    }

    /**
     * Counts the number of days in a year.
     *
     * @param year
     *            The year to test.
     * @return The number of days.
     */
    static int countDaysInYear(final int year) {
        Calendar c = Calendar.getInstance();
        // set to January 1st of next year
        c.set(year + 1, 0, 1, 0, 0, 0);
        c.set(Calendar.MILLISECOND, 0);
        // substract one day
        c.add(Calendar.DAY_OF_YEAR, -1);
        return c.get(Calendar.DAY_OF_YEAR);
    }

    /**
     * Counts the number of days in a month.
     *
     * @param year
     *            The year of the month to test.
     * @param month
     *            The month to test.
     * @return The number of days.
     */
    static int countDaysInMonth(final int year, final int month) {
        Calendar c = Calendar.getInstance();
        // set to 1st day of next month in the year
        c.set(year, month + 1, 1, 0, 0, 0);
        c.set(Calendar.MILLISECOND, 0);
        // substract one day
        c.add(Calendar.DAY_OF_YEAR, -1);
        return c.get(Calendar.DAY_OF_MONTH);
    }

    /**
     * Gets the dates in a month of a year that matches a day in the week (SU,
     * MO, ...).
     *
     * @param calendarDay
     *            The day of the week.
     * @param seed
     *            The reference date.
     * @return The list of dates.
     */
    static List<Date> getDaysInMonth(final int calendarDay, final Date seed) {
        Calendar cal = Calendar.getInstance();
        cal.setTime(seed);
        // store the month
        int month = cal.get(Calendar.MONTH);
        // set to 1st day of month in the year
        cal.set(Calendar.DAY_OF_MONTH, 1);

        List<Date> results = new ArrayList<Date>();
        while (cal.get(Calendar.DAY_OF_WEEK) != calendarDay) {
            cal.add(Calendar.DAY_OF_YEAR, 1);
        }

        do {
            results.add(cal.getTime());
            cal.add(Calendar.WEEK_OF_YEAR, 1);
        } while (cal.get(Calendar.MONTH) == month);

        return results;
    }

    /**
     * Gets the dates in a year that matches a day of a week (SU, MO, ...).
     *
     * @param calendarDay
     *            The day of the week.
     * @param seed
     *            The reference date.
     * @return The list of dates.
     */
    static List<Date> getDaysInYear(final int calendarDay, final Date seed) {
        Calendar cal = Calendar.getInstance();
        cal.setTime(seed);
        // store the year
        int year = cal.get(Calendar.YEAR);
        // set to January 1st of the year
        cal.set(Calendar.MONTH, 0);
        cal.set(Calendar.DAY_OF_MONTH, 1);

        List<Date> results = new ArrayList<Date>();
        while (cal.get(Calendar.DAY_OF_WEEK) != calendarDay) {
            cal.add(Calendar.DAY_OF_YEAR, 1);
        }

        do {
            results.add(cal.getTime());
            cal.add(Calendar.WEEK_OF_YEAR, 1);
        } while (cal.get(Calendar.YEAR) == year);

        return results;
    }

    /**
     * Computes a string representing the list of integers.
     *
     * @param list
     *            The list of integers.
     * @return The string representation.
     */
    static String numberListToString(final List<Integer> list) {
        final StringBuffer b = new StringBuffer();
        for (final Iterator<Integer> i = list.iterator(); i.hasNext();) {
            b.append(i.next());
            if (i.hasNext()) {
                b.append(',');
            }
        }
        return b.toString();
    }

    /**
     * Computes a string representing the list of ByDay.
     *
     * @param list
     *            The list.
     * @return The string representation.
     */
    static final String bydayListToString(final List<ByDay> list) {
        final StringBuffer b = new StringBuffer();
        for (final Iterator<ByDay> i = list.iterator(); i.hasNext();) {
            b.append(i.next().toString());
            if (i.hasNext()) {
                b.append(',');
            }
        }
        return b.toString();
    }
}
