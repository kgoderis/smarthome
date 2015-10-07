/**
 * Copyright  (c) 2014 timothyb89 (https://github.com/timothyb89/lifx-java) under the MIT license
 */
package org.eclipse.smarthome.binding.lifx.fields;

import java.nio.ByteBuffer;

public class ByteField extends Field<ByteBuffer> {

    public ByteField() {
    }

    public ByteField(int length) {
        super(length);
    }

    @Override
    public int defaultLength() {
        return 2;
    }

    @Override
    public ByteBuffer value(ByteBuffer bytes) {
        byte[] data = new byte[length];
        bytes.get(data);

        return ByteBuffer.wrap(data);
    }

    @Override
    public ByteBuffer bytesInternal(ByteBuffer value) {
        return value;
    }

}
