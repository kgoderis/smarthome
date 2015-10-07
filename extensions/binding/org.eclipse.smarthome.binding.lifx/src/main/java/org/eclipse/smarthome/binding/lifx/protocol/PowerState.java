/**
 * Copyright  (c) 2014 timothyb89 (https://github.com/timothyb89/lifx-java) under the MIT license
 */
package org.eclipse.smarthome.binding.lifx.protocol;

/**
 * Represents bulb power states (on or off).
 *
 * @author tim
 */
public enum PowerState {

    ON(0xFFFF),
    OFF(0x0000);

    private final int value;

    private PowerState(int value) {
        this.value = value;
    }

    /**
     * Gets the integer value of this power state.
     *
     * @return the integer value
     */
    public int getValue() {
        return value;
    }

    public static PowerState fromValue(int value) {
        for (PowerState p : values()) {
            if (p.getValue() == value) {
                return p;
            }
        }

        return null;
    }

}
