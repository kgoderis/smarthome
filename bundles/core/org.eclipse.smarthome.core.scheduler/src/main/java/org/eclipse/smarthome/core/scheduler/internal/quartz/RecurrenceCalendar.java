package org.eclipse.smarthome.core.scheduler.internal.quartz;

import org.quartz.Calendar;

import net.fortuna.ical4j.filter.PeriodRule;
import net.fortuna.ical4j.model.DateTime;
import net.fortuna.ical4j.model.Period;
import net.fortuna.ical4j.model.component.VEvent;

public class RecurrenceCalendar implements Calendar {

    private static final long serialVersionUID = 4125254628802633435L;

    private Calendar baseCalendar;

    private String description;

    private final VEvent event;

    public RecurrenceCalendar(VEvent event) {
        this.event = event;
    }

    @Override
    public void setBaseCalendar(Calendar baseCalendar) {
        this.baseCalendar = baseCalendar;
    }

    @Override
    public Calendar getBaseCalendar() {
        return baseCalendar;
    }

    public VEvent getEvent() {
        return this.event;
    }

    @Override
    public boolean isTimeIncluded(long timeStamp) {
        final DateTime dateTime = new DateTime(timeStamp);
        final PeriodRule<?> rule = new PeriodRule(new Period(dateTime, dateTime));

        boolean timeIncluded = !rule.match(event);
        if (!timeIncluded && baseCalendar != null) {
            timeIncluded = baseCalendar.isTimeIncluded(timeStamp);
        }
        return timeIncluded;
    }

    @Override
    public long getNextIncludedTime(long timeStamp) {
        return baseCalendar.getNextIncludedTime(timeStamp);
    }

    @Override
    public String getDescription() {
        return description;
    }

    @Override
    public void setDescription(String description) {
        this.description = description;
    }

    @Override
    public Object clone() {
        return null;
    }

}
