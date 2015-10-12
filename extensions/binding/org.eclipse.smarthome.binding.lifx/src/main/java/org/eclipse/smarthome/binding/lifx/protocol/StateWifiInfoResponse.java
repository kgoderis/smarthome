/**
 * Copyright (c) 2014 openHAB UG (haftungsbeschraenkt) and others.
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v1.0
 * which accompanies this distribution, and is available at
 * http://www.eclipse.org/legal/epl-v10.html
 */
package org.eclipse.smarthome.binding.lifx.protocol;

import java.nio.ByteBuffer;

import org.eclipse.smarthome.binding.lifx.fields.Field;
import org.eclipse.smarthome.binding.lifx.fields.FloatField;
import org.eclipse.smarthome.binding.lifx.fields.UInt16Field;
import org.eclipse.smarthome.binding.lifx.fields.UInt32Field;

/**
 * @author Tim Buckley - Initial Contribution
 * @author Karel Goderis - Enhancement for the V2 LIFX Firmware and LAN Protocol Specification
 */
public class StateWifiInfoResponse extends Packet {

    public static final int TYPE = 0x11;

    public static final Field<Float> FIELD_SIGNAL = new FloatField().little();
    public static final Field<Long> FIELD_RX = new UInt32Field().little();
    public static final Field<Long> FIELD_TX = new UInt32Field().little();
    public static final Field<Integer> FIELD_TEMP = new UInt16Field();

    private float signal;
    private long rx;
    private long tx;
    private int mcuTemperature;

    public float getSignal() {
        return signal;
    }

    public long getRx() {
        return rx;
    }

    public long getTx() {
        return tx;
    }

    public int getMcuTemperature() {
        return mcuTemperature;
    }

    @Override
    public int packetType() {
        return TYPE;
    }

    @Override
    protected int packetLength() {
        return 14;
    }

    @Override
    protected void parsePacket(ByteBuffer bytes) {
        signal = FIELD_SIGNAL.value(bytes);
        rx = FIELD_RX.value(bytes);
        tx = FIELD_TX.value(bytes);
        mcuTemperature = FIELD_TEMP.value(bytes);
    }

    @Override
    protected ByteBuffer packetBytes() {
        return ByteBuffer.allocate(packetLength()).put(FIELD_SIGNAL.bytes(signal)).put(FIELD_RX.bytes(rx))
                .put(FIELD_TX.bytes(tx)).put(FIELD_TEMP.bytes(mcuTemperature));
    }

    @Override
    public int[] expectedResponses() {
        return new int[] {};
    }

}
