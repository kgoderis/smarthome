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
import org.eclipse.smarthome.binding.lifx.fields.UInt64Field;

/**
 * @author Tim Buckley - Initial Contribution
 * @author Karel Goderis - Enhancement for the V2 LIFX Firmware and LAN Protocol Specification
 */
public class StateWifiFirmwareResponse extends Packet {

    public static final int TYPE = 0x13;

    public static final Field<Long> FIELD_BUILD = new UInt64Field().little();
    public static final Field<Long> FIELD_RESERVED = new UInt64Field().little();
    public static final Field<Long> FIELD_VERSION = new UInt64Field().little();

    private long build;
    private long reserved;
    private long version;

    public long getBuild() {
        return build;
    }

    public void setBuild(long build) {
        this.build = build;
    }

    public long getVersion() {
        return version;
    }

    public void setVersion(long version) {
        this.version = version;
    }

    public StateWifiFirmwareResponse() {
        setTagged(false);
        setAddressable(true);
        setResponseRequired(true);
    }

    @Override
    public int packetType() {
        return TYPE;
    }

    @Override
    protected int packetLength() {
        return 20;
    }

    @Override
    protected void parsePacket(ByteBuffer bytes) {
        build = FIELD_BUILD.value(bytes);
        reserved = FIELD_RESERVED.value(bytes);
        version = FIELD_VERSION.value(bytes);
    }

    @Override
    protected ByteBuffer packetBytes() {
        return ByteBuffer.allocate(packetLength()).put(FIELD_BUILD.bytes(build)).put(FIELD_RESERVED.bytes(reserved))
                .put(FIELD_VERSION.bytes(version));
    }

    @Override
    public int[] expectedResponses() {
        return new int[] {};
    }

}
