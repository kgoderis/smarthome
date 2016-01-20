/**
 * Copyright (c) 2014-2015 openHAB UG (haftungsbeschraenkt) and others.
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v1.0
 * which accompanies this distribution, and is available at
 * http://www.eclipse.org/legal/epl-v10.html
 */
package org.eclipse.smarthome.model.script.internal;

import org.eclipse.smarthome.automation.handler.ModuleHandlerFactory;
import org.eclipse.smarthome.core.events.EventPublisher;
import org.eclipse.smarthome.core.items.ItemRegistry;
import org.eclipse.smarthome.model.core.ModelRepository;
import org.eclipse.smarthome.model.script.engine.ScriptEngine;
import org.eclipse.smarthome.model.script.engine.action.ActionService;
import org.eclipse.smarthome.model.script.engine.automation.ExecuteScriptHandlerFactory;
import org.eclipse.smarthome.model.script.engine.automation.ExecuteScriptRuleProvider;
import org.eclipse.smarthome.model.script.engine.automation.ExecuteScriptTemplateProvider;
import org.eclipse.smarthome.model.script.extension.ScriptEngineConsoleCommandExtension;
import org.osgi.framework.BundleActivator;
import org.osgi.framework.BundleContext;
import org.osgi.framework.ServiceRegistration;
import org.osgi.util.tracker.ServiceTracker;

/**
 * Extension of the default OSGi bundle activator
 *
 * @author Kai Kreuzer - Initial contribution and API
 */
public class ScriptActivator implements BundleActivator {

    public static ServiceTracker<ItemRegistry, ItemRegistry> itemRegistryTracker;
    public static ServiceTracker<EventPublisher, EventPublisher> eventPublisherTracker;
    public static ServiceTracker<ModelRepository, ModelRepository> modelRepositoryTracker;
    public static ServiceTracker<ScriptEngine, ScriptEngine> scriptEngineTracker;
    public static ServiceTracker<ActionService, ActionService> actionServiceTracker;

    @SuppressWarnings("rawtypes")
    private ServiceRegistration factoryRegistration;
    private ScriptEngineConsoleCommandExtension commands;

    private static ExecuteScriptHandlerFactory handlerFactory;
    private static ExecuteScriptRuleProvider ruleProvider;
    private static ExecuteScriptTemplateProvider templateProvider;

    /**
     * Called whenever the OSGi framework starts our bundle
     */
    @Override
    public void start(BundleContext bc) throws Exception {
        actionServiceTracker = new ServiceTracker<ActionService, ActionService>(bc, ActionService.class, null);
        actionServiceTracker.open();

        itemRegistryTracker = new ServiceTracker<ItemRegistry, ItemRegistry>(bc, ItemRegistry.class, null);
        itemRegistryTracker.open();

        eventPublisherTracker = new ServiceTracker<EventPublisher, EventPublisher>(bc, EventPublisher.class, null);
        eventPublisherTracker.open();

        modelRepositoryTracker = new ServiceTracker<ModelRepository, ModelRepository>(bc, ModelRepository.class, null);
        modelRepositoryTracker.open();

        scriptEngineTracker = new ServiceTracker<ScriptEngine, ScriptEngine>(bc, ScriptEngine.class, null);
        scriptEngineTracker.open();

        ScriptActivator.handlerFactory = new ExecuteScriptHandlerFactory(bc);
        this.factoryRegistration = bc.registerService(ModuleHandlerFactory.class.getName(),
                ScriptActivator.handlerFactory, null);

        commands = new ScriptEngineConsoleCommandExtension(bc, handlerFactory);
        commands.register(bc);

        ruleProvider = new ExecuteScriptRuleProvider();
        ruleProvider.register(bc);

        templateProvider = new ExecuteScriptTemplateProvider();
        templateProvider.register(bc);
    }

    public static ExecuteScriptHandlerFactory getExecuteScriptHandlerFactory() {
        return handlerFactory;
    }

    public static ExecuteScriptRuleProvider getExecuteScriptRuleProvider() {
        return ruleProvider;
    }

    /**
     * Called whenever the OSGi framework stops our bundle
     */
    @Override
    public void stop(BundleContext bc) throws Exception {
        itemRegistryTracker.close();
        eventPublisherTracker.close();
        modelRepositoryTracker.close();
        scriptEngineTracker.close();
        actionServiceTracker.close();

        ScriptActivator.handlerFactory.dispose();
        if (this.factoryRegistration != null) {
            this.factoryRegistration.unregister();
        }
        ScriptActivator.handlerFactory = null;

        if (commands != null) {
            commands.unregister();
        }
        commands = null;

        if (ruleProvider != null) {
            ruleProvider.unregister();
        }
        ruleProvider = null;

        if (templateProvider != null) {
            templateProvider.unregister();
        }
        templateProvider = null;
    }

}
