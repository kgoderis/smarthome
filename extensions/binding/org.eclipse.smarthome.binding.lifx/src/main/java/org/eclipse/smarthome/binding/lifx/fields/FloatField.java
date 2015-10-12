/**
 * Copyright  (c) 2014 timothyb89 (https://github.com/timothyb89/lifx-java) under the MIT license
 */
package org.eclipse.smarthome.binding.lifx.fields;

import java.nio.ByteBuffer;

/**
 * @author Tim Buckley
 */
public class FloatField extends Field<Float> {

    @Override
    public int defaultLength() {
        return 4;
    }

    @Override
    public Float value(ByteBuffer bytes) {
        return bytes.getFloat();
    }

    @Override
    protected ByteBuffer bytesInternal(Float value) {
        return ByteBuffer.allocate(4).putFloat(value);
    }

}
