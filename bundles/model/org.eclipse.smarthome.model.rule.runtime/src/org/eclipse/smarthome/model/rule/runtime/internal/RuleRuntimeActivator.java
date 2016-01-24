/**
 * Copyright (c) 2014-2015 openHAB UG (haftungsbeschraenkt) and others.
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v1.0
 * which accompanies this distribution, and is available at
 * http://www.eclipse.org/legal/epl-v10.html
 */
package org.eclipse.smarthome.model.rule.runtime.internal;

import org.eclipse.smarthome.model.rule.RulesStandaloneSetup;
import org.eclipse.smarthome.model.rule.runtime.RuleRuntime;
import org.eclipse.smarthome.model.rule.runtime.automation.RuleRuntimeHandlerFactory;
import org.osgi.framework.BundleContext;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Extension of the default OSGi bundle activator
 *
 * @author Kai Kreuzer - Initial contribution and API
 * @author Karel Goderis - addition of RuleRuntimeHandlerFactory
 */
public class RuleRuntimeActivator implements RuleRuntime {

    private final Logger logger = LoggerFactory.getLogger(RuleRuntimeActivator.class);

    private static RuleRuntimeHandlerFactory handlerFactory;

    public void activate(BundleContext bc) throws Exception {

        RulesStandaloneSetup.doSetup();
        logger.debug("Registered 'rule' configuration parser");

        handlerFactory = new RuleRuntimeHandlerFactory(bc);
        handlerFactory.register();
    }

    public void deactivate() throws Exception {

        if (handlerFactory != null) {
            handlerFactory.unregister();
            handlerFactory.dispose();
        }
    }
}
