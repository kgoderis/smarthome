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
public class GetTagLabelsRequest extends Packet {

    public static final int TYPE = 0x1D;

    public static final Field<Long> FIELD_TAGS = new UInt64Field();

    private long tags;

    public long getTags() {
        return tags;
    }

    public GetTagLabelsRequest() {
    }

    public GetTagLabelsRequest(long tags) {
        this.tags = tags;
    }

    @Override
    public int packetType() {
        return TYPE;
    }

    @Override
    protected int packetLength() {
        return 8;
    }

    @Override
    protected void parsePacket(ByteBuffer bytes) {
        tags = FIELD_TAGS.value(bytes);
    }

    @Override
    protected ByteBuffer packetBytes() {
        return ByteBuffer.allocate(packetLength()).put(FIELD_TAGS.bytes(tags));
    }

    @Override
    public int[] expectedResponses() {
        return new int[] {};
    }

}
