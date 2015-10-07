/**
 * Copyright  (c) 2014 timothyb89 (https://github.com/timothyb89/lifx-java) under the MIT license
 */
package org.eclipse.smarthome.binding.lifx.fields;

import java.nio.ByteBuffer;

/**
 * Reads a wrapped field in reversed byte order.
 */
public class LittleField<T> extends Field<T> {

    private final Field<T> wrapped;

    public LittleField(Field<T> wrapped) {
        super(wrapped.length);

        this.wrapped = wrapped;
    }

    @Override
    public int defaultLength() {
        return wrapped.defaultLength();
    }

    @Override
    public T value(ByteBuffer bytes) {
        byte[] field = new byte[wrapped.length];
        bytes.get(field);

        ByteBuffer flipped = flip(ByteBuffer.wrap(field));

        T value = wrapped.value(flipped);

        return value;
    }

    @Override
    public ByteBuffer bytesInternal(T value) {
        return flip(wrapped.bytes(value));
    }

    public static ByteBuffer flip(ByteBuffer buf) {
        buf.rewind();

        ByteBuffer ret = ByteBuffer.allocate(buf.limit());

        for (int i = buf.limit() - 1; i >= 0; i--) {
            ret.put(buf.get(i));
        }

        ret.rewind();

        return ret;
    }

}
