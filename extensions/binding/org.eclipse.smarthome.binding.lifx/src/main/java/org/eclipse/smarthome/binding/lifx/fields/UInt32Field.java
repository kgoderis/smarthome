/**
 * Copyright  (c) 2014 timothyb89 (https://github.com/timothyb89/lifx-java) under the MIT license
 */
package org.eclipse.smarthome.binding.lifx.fields;

import java.nio.ByteBuffer;

public class UInt32Field extends Field<Long> {

    @Override
    public int defaultLength() {
        return 4;
    }

    @Override
    public Long value(ByteBuffer bytes) {
        return bytes.getInt() & 0xFFFFFFFFL;
    }

    @Override
    public ByteBuffer bytesInternal(Long value) {
        return ByteBuffer.allocate(4).putInt((int) (value & 0xFFFFFFFFL));
    }

}
