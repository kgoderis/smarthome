/**
 * Copyright  (c) 2014 timothyb89 (https://github.com/timothyb89/lifx-java) under the MIT license
 */
package org.eclipse.smarthome.binding.lifx.protocol;

import java.nio.ByteBuffer;

public class GetMeshFirmwareRequest extends Packet {

    public static final int TYPE = 0x0E;

    @Override
    public int packetType() {
        return TYPE;
    }

    @Override
    protected int packetLength() {
        return 0;
    }

    @Override
    protected void parsePacket(ByteBuffer bytes) {
        // do nothing
    }

    @Override
    protected ByteBuffer packetBytes() {
        return ByteBuffer.allocate(0);
    }

    @Override
    public int[] expectedResponses() {
        return new int[] { StateMeshFirmwareResponse.TYPE };
    }

}
