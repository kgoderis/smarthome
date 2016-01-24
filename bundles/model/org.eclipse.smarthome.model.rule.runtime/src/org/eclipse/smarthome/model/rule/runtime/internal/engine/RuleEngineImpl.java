/**
 * Copyright (c) 2014-2015 openHAB UG (haftungsbeschraenkt) and others.
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v1.0
 * which accompanies this distribution, and is available at
 * http://www.eclipse.org/legal/epl-v10.html
 */
package org.eclipse.smarthome.model.rule.runtime.internal.engine;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.TimeUnit;

import org.eclipse.emf.ecore.EObject;
import org.eclipse.smarthome.automation.Action;
import org.eclipse.smarthome.automation.RuleRegistry;
import org.eclipse.smarthome.automation.Trigger;
import org.eclipse.smarthome.automation.Visibility;
import org.eclipse.smarthome.config.core.ConfigDescriptionParameter;
import org.eclipse.smarthome.config.core.ConfigDescriptionParameter.Type;
import org.eclipse.smarthome.core.common.ThreadPoolManager;
import org.eclipse.smarthome.core.common.ThreadPoolManager.ExpressionThreadPoolExecutor;
import org.eclipse.smarthome.core.items.events.AbstractItemEventSubscriber;
import org.eclipse.smarthome.model.core.ModelRepository;
import org.eclipse.smarthome.model.core.ModelRepositoryChangeListener;
import org.eclipse.smarthome.model.rule.rules.ChangedEventTrigger;
import org.eclipse.smarthome.model.rule.rules.CommandEventTrigger;
import org.eclipse.smarthome.model.rule.rules.EventTrigger;
import org.eclipse.smarthome.model.rule.rules.Rule;
import org.eclipse.smarthome.model.rule.rules.RuleModel;
import org.eclipse.smarthome.model.rule.rules.SystemOnShutdownTrigger;
import org.eclipse.smarthome.model.rule.rules.SystemOnStartupTrigger;
import org.eclipse.smarthome.model.rule.rules.TimerTrigger;
import org.eclipse.smarthome.model.rule.rules.UpdateEventTrigger;
import org.eclipse.smarthome.model.rule.runtime.RuleEngine;
import org.eclipse.smarthome.model.rule.runtime.RuleRuntime;
import org.eclipse.smarthome.model.rule.runtime.automation.RuleRuntimeHandlerFactory;
import org.eclipse.smarthome.model.rule.runtime.automation.SystemTriggerHandler;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.google.common.collect.Lists;
import com.google.common.collect.Sets;

/**
 * This class is the core of the openHAB rule engine.
 * It listens to changes to the rules folder, evaluates the trigger conditions of the rules and
 * schedules them for execution dependent on their triggering conditions.
 *
 * @author Kai Kreuzer - Initial contribution and API
 * @author Oliver Libutzki - Bugfixing
 * @author Karel Goderis - Migration to the Automation API
 *
 */
@SuppressWarnings("restriction")
public class RuleEngineImpl extends AbstractItemEventSubscriber implements ModelRepositoryChangeListener, RuleEngine {

    private final Logger logger = LoggerFactory.getLogger(RuleEngineImpl.class);

    public static final String CONFIG_SCRIPT_TYPE = "type";
    public static final String CONFIG_SCRIPT = "script";

    private ModelRepository modelRepository;
    private RuleRuntime ruleRuntime;
    private RuleRegistry ruleRegistry;
    private RuleRuntimeHandlerFactory factory;

    private ArrayList<String> startupTriggers = new ArrayList<String>();
    private ArrayList<String> shutdownTriggers = new ArrayList<String>();
    private HashMap<String, String> ruleMap = new HashMap<String, String>();

    private ScheduledFuture<?> startupJob;

    private Runnable startupRunnable = new Runnable() {
        @Override
        public void run() {
            runStartupRules();
        }
    };

    public void activate() {
        if (!isEnabled()) {
            logger.info("Rule engine is disabled.");
            return;
        }

        logger.debug("Started rule engine");

        // read all rule files
        Iterable<String> ruleModelNames = modelRepository.getAllModelNamesOfType("rules");
        ArrayList<String> clonedList = Lists.newArrayList(ruleModelNames);
        for (String ruleModelName : clonedList) {
            EObject model = modelRepository.getModel(ruleModelName);
            if (model instanceof RuleModel) {
                RuleModel ruleModel = (RuleModel) model;
                // triggerManager.addRuleModel(ruleModel);
                buildRuleModel(ruleModelName, ruleModel);
            }
        }

        scheduleStartupRules();
    }

    public void deactivate() {
        List<String> firedTriggers = Lists.newArrayList();

        for (String shutdownTrigger : shutdownTriggers) {
            SystemTriggerHandler handler = factory.getTriggerHandler(shutdownTrigger);
            if (handler != null) {
                logger.debug("Executing shutdown rule '{}'", ruleRegistry.get(handler.getRuleUID()).getDescription());
                firedTriggers.add(shutdownTrigger);
                handler.trigger();
            }
        }

        for (String aTrigger : firedTriggers) {
            shutdownTriggers.remove(aTrigger);
        }
    }

    public void setModelRepository(ModelRepository modelRepository) {
        this.modelRepository = modelRepository;
        modelRepository.addModelRepositoryChangeListener(this);
    }

    public void unsetModelRepository(ModelRepository modelRepository) {
        modelRepository.removeModelRepositoryChangeListener(this);
        this.modelRepository = null;
    }

    protected void setRuleRuntime(final RuleRuntime ruleRuntime) {
        this.ruleRuntime = ruleRuntime;
    }

    protected void unsetRuleRuntime(final RuleRuntime ruleRuntime) {
        this.ruleRuntime = null;
    }

    protected void setRuleRegistry(RuleRegistry registry) {
        this.ruleRegistry = registry;
    }

    protected void unsetRuleRegistry(RuleRegistry registry) {
        this.ruleRegistry = null;
    }

    public void setHandlerFactory(RuleRuntimeHandlerFactory factory) {
        this.factory = factory;
    }

    public void unsetHandlerFactory(RuleRuntimeHandlerFactory factory) {
        factory = null;
    }

    @Override
    public void modelChanged(String modelName, org.eclipse.smarthome.model.core.EventType type) {
        if (isEnabled() && modelName.endsWith("rules")) {
            RuleModel model = (RuleModel) modelRepository.getModel(modelName);

            // remove the rules from the trigger sets
            if (type == org.eclipse.smarthome.model.core.EventType.REMOVED
                    || type == org.eclipse.smarthome.model.core.EventType.MODIFIED) {
                removeRuleModel(modelName, model);
            }

            // add new and modified rules to the trigger sets
            if (model != null && (type == org.eclipse.smarthome.model.core.EventType.ADDED
                    || type == org.eclipse.smarthome.model.core.EventType.MODIFIED)) {
                buildRuleModel(modelName, model);
                // now execute all rules that are meant to trigger at startup
                scheduleStartupRules();
            }
        }
    }

    private void scheduleStartupRules() {
        if (startupJob == null || startupJob.isCancelled() || startupJob.isDone()) {
            ExpressionThreadPoolExecutor scheduler = ThreadPoolManager.getExpressionScheduledPool("automation");
            startupJob = scheduler.schedule(startupRunnable, 5, TimeUnit.SECONDS);
        }
    }

    private void runStartupRules() {
        List<String> firedTriggers = Lists.newArrayList();

        for (String startTrigger : startupTriggers) {
            SystemTriggerHandler handler = factory.getTriggerHandler(startTrigger);
            if (handler != null) {
                logger.debug("Executing startup rule '{}'", ruleRegistry.get(handler.getRuleUID()).getDescription());
                firedTriggers.add(startTrigger);
                handler.trigger();
            }
        }

        for (String aTrigger : firedTriggers) {
            startupTriggers.remove(aTrigger);
        }
    }

    /**
     * we need to be able to deactivate the rule execution, otherwise the openHAB designer
     * would also execute the rules.
     *
     * @return true, if rules should be executed, false otherwise
     */
    private boolean isEnabled() {
        return !"true".equalsIgnoreCase(System.getProperty("noRules"));
    }

    private void buildRuleModel(String modelName, RuleModel ruleModel) {
        for (Rule rule : ruleModel.getRules()) {
            buildRule(modelName, rule);
        }
    }

    private void buildRule(String modelName, Rule rule) {

        ArrayList<Trigger> triggers = new ArrayList<Trigger>();

        String ruleName = rule.getName();
        String ruleUID = UUID.randomUUID().toString();

        logger.debug("Adding the rule '{}' from '{}' to the RuleEngine", rule.getName(), modelName);

        int counter = 1;
        for (EventTrigger t : rule.getEventtrigger()) {
            // add the rule to the lookup map for the trigger kind
            if (t instanceof SystemOnStartupTrigger) {
                Trigger aTrigger = new Trigger(UUID.randomUUID().toString(), "SystemTrigger", null);
                aTrigger.setDescription(rule.getName() + " : " + "Startup Trigger " + "#" + counter);
                startupTriggers.add(ruleUID + aTrigger.getId());
                triggers.add(aTrigger);
            } else if (t instanceof SystemOnShutdownTrigger) {
                Trigger aTrigger = new Trigger(UUID.randomUUID().toString(), "SystemTrigger", null);
                aTrigger.setDescription(rule.getName() + " : " + "Shutdown Trigger " + "#" + counter);
                shutdownTriggers.add(ruleUID + aTrigger.getId());
                triggers.add(aTrigger);
            } else if (t instanceof CommandEventTrigger) {
                CommandEventTrigger ceTrigger = (CommandEventTrigger) t;
                Map<String, String> triggerConfig = new HashMap<String, String>();
                triggerConfig.put("itemName", ceTrigger.getItem());
                Trigger aTrigger = new Trigger(UUID.randomUUID().toString(), "ItemCommandTrigger", triggerConfig);
                aTrigger.setDescription(rule.getName() + " : " + "Command Trigger " + "#" + ceTrigger.getItem());
                triggers.add(aTrigger);
            } else if (t instanceof UpdateEventTrigger) {
                UpdateEventTrigger ueTrigger = (UpdateEventTrigger) t;
                Map<String, String> triggerConfig = new HashMap<String, String>();
                triggerConfig.put("itemName", ueTrigger.getItem());
                Trigger aTrigger = new Trigger(UUID.randomUUID().toString(), "ItemStateChangeTrigger", triggerConfig);
                aTrigger.setDescription(rule.getName() + " : " + "Update  Trigger " + "#" + ueTrigger.getItem());
                triggers.add(aTrigger);
            } else if (t instanceof ChangedEventTrigger) {
                ChangedEventTrigger ceTrigger = (ChangedEventTrigger) t;
                Map<String, String> triggerConfig = new HashMap<String, String>();
                triggerConfig.put("itemName", ceTrigger.getItem());
                Trigger aTrigger = new Trigger(UUID.randomUUID().toString(), "ItemStateChangedTrigger", triggerConfig);
                aTrigger.setDescription(rule.getName() + " : " + "Changed Trigger " + "#" + ceTrigger.getItem());
                triggers.add(aTrigger);
            } else if (t instanceof TimerTrigger) {
                TimerTrigger tTrigger = (TimerTrigger) t;
                String cronExpression = getCronExpression(tTrigger, rule);
                Map<String, String> triggerConfig = new HashMap<String, String>();
                triggerConfig.put("cronExpression", cronExpression);
                Trigger aTrigger = new Trigger(UUID.randomUUID().toString(), "CronTrigger", triggerConfig);
                aTrigger.setDescription(rule.getName() + " : " + "Timer Trigger " + "#" + cronExpression);
                triggers.add(aTrigger);
                counter++;
            }
        }

        Map<String, String> actionConfig = new HashMap<String, String>();
        actionConfig.put(CONFIG_SCRIPT_TYPE, "eclipse/rule");
        actionConfig.put(CONFIG_SCRIPT, modelName + "." + rule.getName());

        ArrayList<Action> actions = new ArrayList<Action>();
        Action action = new Action(UUID.randomUUID().toString(), "ScriptAction", actionConfig, null);
        action.setDescription(rule.getName() + " : " + "Script Action");
        actions.add(action);

        List<ConfigDescriptionParameter> configDescriptions = new ArrayList<ConfigDescriptionParameter>();
        ConfigDescriptionParameter type = new ConfigDescriptionParameter(CONFIG_SCRIPT_TYPE, Type.TEXT, null, null,
                null, null, true, true, false, null, null, "Type", "Script type", null, null, null, null, null, null);
        ConfigDescriptionParameter script = new ConfigDescriptionParameter(CONFIG_SCRIPT, Type.TEXT, null, null, null,
                null, true, true, false, null, null, "Script", "Script expression", null, null, null, null, null, null);
        configDescriptions.add(type);
        configDescriptions.add(script);

        org.eclipse.smarthome.automation.Rule theRule = new org.eclipse.smarthome.automation.Rule(ruleUID, triggers,
                null, actions, configDescriptions, null, Visibility.VISIBLE);
        theRule.setName(modelName + " : " + rule.getName());

        ruleMap.put(ruleName, ruleUID);

        ruleRegistry.add(theRule);
    }

    private void removeRuleModel(String modelName, RuleModel ruleModel) {
        for (Rule rule : ruleModel.getRules()) {
            removeRule(modelName, rule);
        }
    }

    private void removeRule(String modelName, Rule rule) {
        logger.debug("Removing the rule '{}' from the RuleEngine", rule.getName());

        List<String> removeTriggers = Lists.newArrayList();
        for (String aTrigger : startupTriggers) {
            if (aTrigger.contains(ruleMap.get(rule.getName()))) {
                removeTriggers.add(aTrigger);
            }
        }
        for (String aTrigger : removeTriggers) {
            startupTriggers.remove(aTrigger);
        }

        removeTriggers = Lists.newArrayList();
        for (String aTrigger : shutdownTriggers) {
            if (aTrigger.contains(ruleMap.get(rule.getName()))) {
                removeTriggers.add(aTrigger);
            }
        }
        for (String aTrigger : removeTriggers) {
            shutdownTriggers.remove(aTrigger);
        }

        Set<org.eclipse.smarthome.automation.Rule> removeRules = Sets.newHashSet();
        for (org.eclipse.smarthome.automation.Rule aRule : ruleRegistry.getAll()) {
            if (aRule.getUID().equals(ruleMap.get(rule.getName()))) {
                removeRules.add(aRule);
            }
        }
        for (org.eclipse.smarthome.automation.Rule aRule : removeRules) {
            ruleRegistry.remove(aRule.getUID());
        }
    }

    private String getCronExpression(TimerTrigger t, Rule rule) {
        String cronExpression = t.getCron();
        if (t.getTime() != null) {
            if (t.getTime().equals("noon")) {
                cronExpression = "0 0 12 * * ?";
            } else if (t.getTime().equals("midnight")) {
                cronExpression = "0 0 0 * * ?";
            } else {
                logger.warn("Unrecognized time expression '{}' in rule '{}'",
                        new Object[] { t.getTime(), rule.getName() });
                cronExpression = null;
            }
        }

        return cronExpression;
    }
}
