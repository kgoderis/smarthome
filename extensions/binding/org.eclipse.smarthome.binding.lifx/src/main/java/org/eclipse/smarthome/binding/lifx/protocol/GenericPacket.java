/**
 * Copyright  (c) 2014 timothyb89 (https://github.com/timothyb89/lifx-java) under the MIT license
 */
package org.eclipse.smarthome.binding.lifx.protocol;

import java.nio.ByteBuffer;

public class GenericPacket extends Packet {

    @Override
    public int packetType() {
        return 0;
    }

    @Override
    protected int packetLength() {
        return 0;
    }

    @Override
    protected void parsePacket(ByteBuffer bytes) {

    }

    @Override
    protected ByteBuffer packetBytes() {
        return ByteBuffer.allocate(0);
    }

    @Override
    public int[] expectedResponses() {
        return new int[] {};
    }

}
