/**
 * Copyright  (c) 2014 timothyb89 (https://github.com/timothyb89/lifx-java) under the MIT license
 */
package org.eclipse.smarthome.binding.lifx.fields;

import java.nio.ByteBuffer;
import java.nio.charset.Charset;
import java.nio.charset.StandardCharsets;

public class StringField extends Field<String> {

    public static final Charset CHARSET = StandardCharsets.US_ASCII;

    private Charset charset;

    public StringField() {
        charset = StandardCharsets.US_ASCII;
    }

    public StringField(int length) {
        super(length);

        charset = StandardCharsets.US_ASCII;
    }

    public StringField(int length, Charset charset) {
        super(length);

        this.charset = charset;
    }

    @Override
    public int defaultLength() {
        return 3;
    }

    @Override
    public String value(ByteBuffer bytes) {
        byte[] buf = new byte[length];
        bytes.get(buf);

        ByteBuffer field = ByteBuffer.wrap(buf);

        String ret = CHARSET.decode(field).toString();
        ret = ret.replace("\0", "");

        return ret;
    }

    @Override
    public ByteBuffer bytesInternal(String value) {
        return CHARSET.encode(value);
    }

    public StringField ascii() {
        charset = StandardCharsets.US_ASCII;
        return this;
    }

    public StringField utf8() {
        charset = StandardCharsets.UTF_8;
        return this;
    }

}
