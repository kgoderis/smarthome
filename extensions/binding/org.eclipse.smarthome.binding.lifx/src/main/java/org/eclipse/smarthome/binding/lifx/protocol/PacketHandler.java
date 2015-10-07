/**
 * Copyright  (c) 2014 timothyb89 (https://github.com/timothyb89/lifx-java) under the MIT license
 */
package org.eclipse.smarthome.binding.lifx.protocol;

import java.nio.ByteBuffer;

/**
 * A packet handler responsible for converting a ByteBuffer into a Packet
 * instance.
 * 
 * @param <T> the generic packet type
 */
public interface PacketHandler<T extends Packet> {

    public abstract T handle(ByteBuffer buf);

}
