/**
 * Copyright (c) 2014-2015 openHAB UG (haftungsbeschraenkt) and others.
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v1.0
 * which accompanies this distribution, and is available at
 * http://www.eclipse.org/legal/epl-v10.html
 */
package org.eclipse.smarthome.core.common;

import java.text.ParseException;
import java.util.ArrayList;
import java.util.Calendar;
import java.util.Collections;
import java.util.Date;
import java.util.StringTokenizer;
import java.util.TimeZone;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * <code>AbstractExpression</code> is an abstract implementation of {@link Expression} that provides common
 * functionality to other concrete implementations of <code>Expression</code>
 *
 * @author Karel Goderis - Initial Contribution
 *
 */
public abstract class AbstractExpression<E extends AbstractExpressionPart> implements Expression {

    final Logger logger = LoggerFactory.getLogger(this.getClass().getName());

    protected int minimumCandidates = 10;
    protected int maximumCandidates = 100;

    protected String expression;
    protected String delimiters;
    protected ArrayList<E> expressionParts = new ArrayList<E>();

    protected boolean continueSearch;
    protected ArrayList<Date> candidates = new ArrayList<Date>();
    protected Date startDate = null;
    protected TimeZone timeZone = null;

    /**
     * Build an {@link Expression}
     *
     * @param expression - the expression
     * @param delimiters - delimiters to consider when splitting the expression into expression parts
     * @param startDate - the start date of the expression
     * @param timeZone - the time zone of the expression
     * @param minimumCandidates - the minimum number of candidate dates to calculate
     * @throws ParseException - when the expression can not be parsed correctly
     */
    public AbstractExpression(String expression, String delimiters, Date startDate, TimeZone timeZone,
            int minimumCandidates) throws ParseException {
        super();

        if (expression == null) {
            throw new IllegalArgumentException("The expression cannot be null");
        }

        this.expression = expression;
        this.delimiters = delimiters;
        this.startDate = startDate;
        this.timeZone = timeZone;
        this.minimumCandidates = minimumCandidates;

        setStartDate(startDate);
        setTimeZone(timeZone);
        parseExpression(expression);
    }

    @Override
    public final Date getStartDate() {
        if (startDate == null) {
            startDate = Calendar.getInstance(getTimeZone()).getTime();
        }
        return startDate;
    }

    @Override
    public void setStartDate(Date startDate) {
        if (startDate == null) {
            throw new IllegalArgumentException("The start date of the rule can not be null");
        }
        this.startDate = startDate;
    }

    @Override
    public TimeZone getTimeZone() {
        if (timeZone == null) {
            timeZone = TimeZone.getDefault();
        }

        return timeZone;
    }

    @Override
    public void setTimeZone(TimeZone timeZone) {
        this.timeZone = timeZone;
    }

    @Override
    public String getExpression() {
        return expression;
    }

    @Override
    public void setExpression(String expression) throws ParseException {
        this.expression = expression;
        parseExpression(expression);
    }

    @Override
    public String toString() {
        return expression;
    }

    /**
     * Parse the given expression
     * 
     * @param expression - the expression to parse
     * @throws ParseException - when the expression can not be successfully be parsed
     * @throws IllegalArgumentException - when expression parts conflict with each other
     */
    @SuppressWarnings("unchecked")
    public void parseExpression(String expression) throws ParseException, IllegalArgumentException {

        StringTokenizer expressionTokenizer = new StringTokenizer(expression, delimiters, false);
        int position = 0;

        while (expressionTokenizer.hasMoreTokens()) {
            String token = expressionTokenizer.nextToken().trim();
            position++;
            expressionParts.add(parseToken(token, position));
        }

        Collections.sort(expressionParts);

        validateExpression();

        if (startDate == null) {
            setStartDate(Calendar.getInstance().getTime());
        }

        applyExpressionParts();

        continueSearch = true;
        while (candidates.size() < minimumCandidates && continueSearch) {
            populateWithSeeds();
            candidates.clear();
            applyExpressionParts();
        }
        continueSearch = false;

        for (Date aDate : candidates) {
            logger.trace("Final candidate {} is {}", candidates.indexOf(aDate), aDate);
        }
    }

    abstract protected void validateExpression() throws IllegalArgumentException;

    @SuppressWarnings("unchecked")
    protected void applyExpressionParts() {
        Collections.sort(expressionParts);
        for (ExpressionPart part : expressionParts) {
            logger.trace("Expanding {} from {} candidates", part.getClass().getSimpleName(), candidates.size());
            candidates = part.apply(startDate, candidates);
            logger.trace("Expanded to {} candidates", candidates.size());
            for (Date aDate : candidates) {
                logger.trace("Candidate {} is {}", candidates.indexOf(aDate), aDate);
            }
            prune();
        }
    }

    protected void prune() {
        Collections.sort(candidates);

        ArrayList<Date> beforeDates = new ArrayList<Date>();

        for (Date candidate : candidates) {
            if (candidate.before(startDate)) {
                beforeDates.add(candidate);
            }
        }

        candidates.removeAll(beforeDates);

        if (candidates.size() > maximumCandidates) {
            logger.debug("Pruning {} candidates to {}", candidates.size(), maximumCandidates);
            int size = candidates.size();
            for (int i = maximumCandidates; i < size; i++) {
                candidates.remove(candidates.size() - 1);
            }
        }
    }

    @Override
    public Date getTimeAfter(Date afterTime) {
        if (candidates.isEmpty()) {
            try {
                setStartDate(afterTime);
                parseExpression(expression);
            } catch (ParseException e) {
                logger.error("An exception occurred while parsing the expression : '{}'", e.getMessage());
            }
        }

        if (!candidates.isEmpty()) {

            Collections.sort(candidates);

            for (Date candidate : candidates) {
                if (candidate.after(afterTime)) {
                    return candidate;
                }
            }
        }

        return null;
    }

    @Override
    public abstract Date getFinalFireTime();

    abstract protected E parseToken(String token, int position) throws ParseException;

    abstract protected void populateWithSeeds();

    public ExpressionPart getExpressionPart(Class<?> part) {
        for (ExpressionPart aPart : expressionParts) {
            if (aPart.getClass().equals(part)) {
                return aPart;
            }
        }
        return null;
    }

}
