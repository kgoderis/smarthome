/**
 * Copyright (c) 1997, 2015 by ProSyst Software GmbH and others.
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v1.0
 * which accompanies this distribution, and is available at
 * http://www.eclipse.org/legal/epl-v10.html
 */
package org.eclipse.smarthome.model.rule.runtime.automation;

import java.util.HashMap;
import java.util.Map;

import org.eclipse.smarthome.automation.Trigger;
import org.eclipse.smarthome.automation.handler.BaseModuleHandler;
import org.eclipse.smarthome.automation.handler.RuleEngineCallback;
import org.eclipse.smarthome.automation.handler.TriggerHandler;

/**
 * Trigger Handler sample implementation
 *
 * @author Karel Goderis - Initial Contribution
 */
public class SystemTriggerHandler extends BaseModuleHandler<Trigger>implements TriggerHandler {
    private RuleEngineCallback ruleCallback;
    private String ruleUID;

    public SystemTriggerHandler(Trigger module, String ruleUID) {
        super(module);
        this.ruleUID = ruleUID;
    }

    public void trigger() {
        Map<String, Object> outputs = new HashMap<String, Object>();
        ruleCallback.triggered(module, outputs);
    }

    String getTriggerID() {
        return module.getId();
    }

    @Override
    public void setRuleEngineCallback(RuleEngineCallback ruleCallback) {
        this.ruleCallback = ruleCallback;
    }

    public String getRuleUID() {
        return ruleUID;
    }

}
