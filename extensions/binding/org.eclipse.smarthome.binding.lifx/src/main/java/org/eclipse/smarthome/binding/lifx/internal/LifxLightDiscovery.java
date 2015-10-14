/**
 * Copyright (c) 2014 openHAB UG (haftungsbeschraenkt) and others.
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v1.0
 * which accompanies this distribution, and is available at
 * http://www.eclipse.org/legal/epl-v10.html
 */
package org.eclipse.smarthome.binding.lifx.internal;

import java.net.DatagramPacket;
import java.net.Inet4Address;
import java.net.InetSocketAddress;
import java.net.InterfaceAddress;
import java.net.NetworkInterface;
import java.net.SocketAddress;
import java.net.SocketTimeoutException;
import java.net.StandardProtocolFamily;
import java.net.StandardSocketOptions;
import java.nio.ByteBuffer;
import java.nio.channels.DatagramChannel;
import java.util.ArrayList;
import java.util.Enumeration;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.TimeUnit;

import org.apache.commons.lang.StringUtils;
import org.eclipse.smarthome.binding.lifx.LifxBindingConstants;
import org.eclipse.smarthome.binding.lifx.fields.MACAddress;
import org.eclipse.smarthome.binding.lifx.protocol.GetServiceRequest;
import org.eclipse.smarthome.binding.lifx.protocol.Packet;
import org.eclipse.smarthome.binding.lifx.protocol.PacketFactory;
import org.eclipse.smarthome.binding.lifx.protocol.PacketHandler;
import org.eclipse.smarthome.binding.lifx.protocol.StateServiceResponse;
import org.eclipse.smarthome.config.discovery.AbstractDiscoveryService;
import org.eclipse.smarthome.config.discovery.DiscoveryResult;
import org.eclipse.smarthome.config.discovery.DiscoveryResultBuilder;
import org.eclipse.smarthome.core.thing.ThingUID;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.google.common.collect.Sets;

/**
 * The {@link LifxLightDiscovery} provides support for auto-discovery of LIFX
 * lights.
 *
 * @author Dennis Nobel - Initial contribution
 * @author Karel Goderis - Rewrite for Firmware V2, and remove dependency on external libraries
 */
public class LifxLightDiscovery extends AbstractDiscoveryService {

    private Logger logger = LoggerFactory.getLogger(LifxLightDiscovery.class);

    private List<InetSocketAddress> broadcastAddresses;
    private final int BROADCAST_PORT = 56700;
    private static int BUFFER_SIZE = 255;
    private static int REFRESH_INTERVAL = 60;
    private static int BROADCAST_TIMEOUT = 5000;

    private ScheduledFuture<?> discoveryJob;

    public LifxLightDiscovery() throws IllegalArgumentException {
        super(Sets.newHashSet(LifxBindingConstants.THING_TYPE_LIGHT), 1, true);
    }

    @Override
    protected void activate(Map<String, Object> configProperties) {
        super.activate(configProperties);
    }

    @Override
    protected void deactivate() {
        super.deactivate();
    }

    @Override
    protected void startBackgroundDiscovery() {
        logger.debug("Starting LIFX device background discovery");

        Runnable discoveryRunnable = new Runnable() {
            @Override
            public void run() {
                doScan();
            }
        };

        if (discoveryJob == null || discoveryJob.isCancelled()) {
            discoveryJob = scheduler.scheduleAtFixedRate(discoveryRunnable, 0, REFRESH_INTERVAL, TimeUnit.SECONDS);
        }
    }

    @Override
    protected void stopBackgroundDiscovery() {
        logger.debug("Stopping LIFX device background discovery");
        if (discoveryJob != null && !discoveryJob.isCancelled()) {
            discoveryJob.cancel(true);
            discoveryJob = null;
        }
    }

    protected void doScan() {

        try {
            broadcastAddresses = new ArrayList<InetSocketAddress>();

            Enumeration<NetworkInterface> networkInterfaces = NetworkInterface.getNetworkInterfaces();
            while (networkInterfaces.hasMoreElements()) {
                NetworkInterface iface = networkInterfaces.nextElement();
                if (iface.isUp() && !iface.isLoopback()) {
                    for (InterfaceAddress ifaceAddr : iface.getInterfaceAddresses()) {
                        if (ifaceAddr.getAddress() instanceof Inet4Address) {
                            broadcastAddresses.add(new InetSocketAddress(ifaceAddr.getBroadcast(), BROADCAST_PORT));
                        }
                    }
                }
            }

            DatagramChannel broadcastChannel = DatagramChannel.open(StandardProtocolFamily.INET)
                    .setOption(StandardSocketOptions.SO_REUSEADDR, true)
                    .setOption(StandardSocketOptions.SO_BROADCAST, true);
            broadcastChannel.configureBlocking(true);
            broadcastChannel.socket().setSoTimeout(BROADCAST_TIMEOUT);
            broadcastChannel.bind(new InetSocketAddress(BROADCAST_PORT));

            // look for lights on the network
            GetServiceRequest packet = new GetServiceRequest();
            packet.setSequence(0);
            packet.setSource(0);

            for (InetSocketAddress address : broadcastAddresses) {
                broadcastChannel.send(packet.bytes(), address);
            }

            ByteBuffer readBuffer = ByteBuffer.allocate(BUFFER_SIZE);
            SocketAddress address = null;
            try {
                DatagramPacket p = new DatagramPacket(readBuffer.array(), readBuffer.array().length);
                broadcastChannel.socket().receive(p);
                address = p.getSocketAddress();
            } catch (SocketTimeoutException e) {
                address = null;
            }

            while (address != null) {

                readBuffer.rewind();

                ByteBuffer packetType = readBuffer.slice();
                packetType.position(32);
                packetType.limit(34);

                int type = Packet.FIELD_PACKET_TYPE.value(packetType);

                PacketHandler handler = PacketFactory.createHandler(type);

                if (handler == null) {
                    continue;
                }

                Packet returnedPacket = handler.handle(readBuffer);

                if (returnedPacket instanceof StateServiceResponse) {
                    DiscoveryResult discoveryResult = createDiscoveryResult((StateServiceResponse) returnedPacket);
                    thingDiscovered(discoveryResult);
                }

                readBuffer.clear();
                try {
                    DatagramPacket p = new DatagramPacket(readBuffer.array(), readBuffer.array().length);
                    broadcastChannel.socket().receive(p);
                    address = p.getSocketAddress();
                } catch (SocketTimeoutException e) {
                    address = null;
                }
            }
        } catch (Exception e) {
            logger.debug("An exception occurred while discovering LIFX lights : '{}", e.getMessage());
        }

    }

    @Override
    protected void startScan() {
        doScan();
    }

    @Override
    protected synchronized void stopScan() {
        super.stopScan();
        removeOlderResults(getTimestampOfLastScan());
    }

    private DiscoveryResult createDiscoveryResult(StateServiceResponse packet) {

        MACAddress discoveredAddress = packet.getTarget();
        int port = (int) packet.getPort();
        int service = packet.getService();

        ThingUID thingUID = getUID(discoveredAddress.getAsLabel());

        String label = "";

        if (StringUtils.isBlank(label))
            label = "LIFX";

        return DiscoveryResultBuilder.create(thingUID).withLabel(label)
                .withProperty(LifxBindingConstants.CONFIG_PROPERTY_DEVICE_ID, discoveredAddress.getHex()).build();
    }

    private ThingUID getUID(String hex) {
        ThingUID thingUID = new ThingUID(LifxBindingConstants.THING_TYPE_LIGHT, hex);
        return thingUID;
    }

}
