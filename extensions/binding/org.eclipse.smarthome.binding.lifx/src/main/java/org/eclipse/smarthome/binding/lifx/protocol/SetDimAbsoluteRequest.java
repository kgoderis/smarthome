/**
 * Copyright  (c) 2014 timothyb89 (https://github.com/timothyb89/lifx-java) under the MIT license
 */
package org.eclipse.smarthome.binding.lifx.protocol;

import java.nio.ByteBuffer;

import org.eclipse.smarthome.binding.lifx.fields.Field;
import org.eclipse.smarthome.binding.lifx.fields.UInt16Field;
import org.eclipse.smarthome.binding.lifx.fields.UInt32Field;

public class SetDimAbsoluteRequest extends Packet {

    public static final int TYPE = 0x68;

    public static final Field<Integer> FIELD_DIM = new UInt16Field().little();
    public static final Field<Long> FIELD_DURATION = new UInt32Field().little();

    private int dim;
    private long duration;

    public int getDim() {
        return dim;
    }

    public long getDuration() {
        return duration;
    }

    public SetDimAbsoluteRequest() {

    }

    public SetDimAbsoluteRequest(int dim, long duration) {
        this.dim = dim;
        this.duration = duration;
    }

    @Override
    public int packetType() {
        return TYPE;
    }

    @Override
    protected int packetLength() {
        return 6;
    }

    @Override
    protected void parsePacket(ByteBuffer bytes) {
        dim = FIELD_DIM.value(bytes);
        duration = FIELD_DURATION.value(bytes);
    }

    @Override
    protected ByteBuffer packetBytes() {
        return ByteBuffer.allocate(packetLength()).put(FIELD_DIM.bytes(dim)).put(FIELD_DURATION.bytes(duration));
    }

    @Override
    public int[] expectedResponses() {
        return new int[] {};
    }

}
