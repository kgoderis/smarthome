/**
 * Copyright  (c) 2014 timothyb89 (https://github.com/timothyb89/lifx-java) under the MIT license
 */
package org.eclipse.smarthome.binding.lifx.fields;

import java.nio.ByteBuffer;

public class UInt16Field extends Field<Integer> {

    @Override
    public int defaultLength() {
        return 2;
    }

    @Override
    public Integer value(ByteBuffer bytes) {
        return bytes.getShort() & 0xFFFF;
    }

    @Override
    public ByteBuffer bytesInternal(Integer value) {
        return ByteBuffer.allocate(2).putShort((short) (value & 0xFFFF));
    }

}
