/**
 * Copyright  (c) 2014 timothyb89 (https://github.com/timothyb89/lifx-java) under the MIT license
 */
package org.eclipse.smarthome.binding.lifx.fields;

import java.nio.ByteBuffer;

public class UInt8Field extends Field<Integer> {

    public UInt8Field() {
        super(1);
    }

    @Override
    public int defaultLength() {
        return 1;
    }

    @Override
    public Integer value(ByteBuffer bytes) {
        return (int) (bytes.get() & 0xFF);
    }

    @Override
    public ByteBuffer bytesInternal(Integer value) {
        return ByteBuffer.allocate(1).put((byte) (value & 0xFF));
    }

}
