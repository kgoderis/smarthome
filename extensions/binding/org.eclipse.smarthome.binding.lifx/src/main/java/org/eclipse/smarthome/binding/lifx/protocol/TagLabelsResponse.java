/**
 * Copyright  (c) 2014 timothyb89 (https://github.com/timothyb89/lifx-java) under the MIT license
 */
package org.eclipse.smarthome.binding.lifx.protocol;

import java.nio.ByteBuffer;

import org.eclipse.smarthome.binding.lifx.fields.Field;
import org.eclipse.smarthome.binding.lifx.fields.StringField;
import org.eclipse.smarthome.binding.lifx.fields.UInt64Field;

public class TagLabelsResponse extends Packet {

    public static final int TYPE = 0x1F;

    public static final Field<Long> FIELD_TAGS = new UInt64Field();
    public static final Field<String> FIELD_LABEL = new StringField(32).utf8();

    private long tags;
    private String label;

    public long getTags() {
        return tags;
    }

    public String getLabel() {
        return label;
    }

    @Override
    public int packetType() {
        return TYPE;
    }

    @Override
    protected int packetLength() {
        return 40;
    }

    @Override
    protected void parsePacket(ByteBuffer bytes) {
        tags = FIELD_TAGS.value(bytes);
        label = FIELD_LABEL.value(bytes);
    }

    @Override
    protected ByteBuffer packetBytes() {
        return ByteBuffer.allocate(packetLength()).put(FIELD_TAGS.bytes(tags)).put(FIELD_LABEL.bytes(label));
    }

    @Override
    public int[] expectedResponses() {
        return new int[] {};
    }

}
