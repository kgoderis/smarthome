/**
 * Copyright (c) 2014-2015 openHAB UG (haftungsbeschraenkt) and others.
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v1.0
 * which accompanies this distribution, and is available at
 * http://www.eclipse.org/legal/epl-v10.html
 */
package org.eclipse.smarthome.model.script.runtime.internal;

import org.eclipse.smarthome.model.core.ModelRepository;
import org.eclipse.smarthome.model.script.runtime.ScriptRuntime;
import org.osgi.framework.BundleActivator;
import org.osgi.framework.BundleContext;
import org.osgi.util.tracker.ServiceTracker;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class ScriptRuntimeActivator implements ScriptRuntime, BundleActivator {

    private final Logger logger = LoggerFactory.getLogger(ScriptRuntimeActivator.class);

    public static ServiceTracker<ModelRepository, ModelRepository> modelRepositoryTracker;

    @Override
    public void start(BundleContext bc) throws Exception {
        modelRepositoryTracker = new ServiceTracker<ModelRepository, ModelRepository>(bc, ModelRepository.class, null);
        modelRepositoryTracker.open();
    }

    @Override
    public void stop(BundleContext bc) throws Exception {
        modelRepositoryTracker.close();

    }

    public void activate() throws Exception {
        ScriptRuntimeStandaloneSetup.doSetup();
        logger.debug("Registered 'script' configuration parser");
    }

    public void deactivate() throws Exception {
    }

}
