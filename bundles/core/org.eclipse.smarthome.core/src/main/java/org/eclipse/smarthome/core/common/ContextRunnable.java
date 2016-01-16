/**
 * Copyright (c) 2014-2015 openHAB UG (haftungsbeschraenkt) and others.
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v1.0
 * which accompanies this distribution, and is available at
 * http://www.eclipse.org/legal/epl-v10.html
 */
package org.eclipse.smarthome.core.common;

import java.util.HashMap;
import java.util.Map;

/**
 * <code>ContextRunnable</code> is a Runnable with a context to store {key,value} pairs for later retrieval in the
 * execution process
 *
 *
 * @author Karel Goderis - Initial contribution
 *
 */
public abstract class ContextRunnable implements Runnable {

    private Map<String, Object> map;

    public ContextRunnable() {
        map = new HashMap<String, Object>();
    }

    public void put(String key, Object value) {
        if (!(key instanceof String)) {
            throw new IllegalArgumentException("Keys in map must be Strings.");
        }
        map.put(key, value);
    }

    public Object get(String key) {
        return map.get(key);
    }

}
