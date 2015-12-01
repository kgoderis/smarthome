/**
 * Copyright (c) 2014 openHAB UG (haftungsbeschraenkt) and others.
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v1.0
 * which accompanies this distribution, and is available at
 * http://www.eclipse.org/legal/epl-v10.html
 */
package org.eclipse.smarthome.binding.lifx.handler;

import static org.eclipse.smarthome.binding.lifx.LifxBindingConstants.CHANNEL_COLOR;

import java.io.IOException;
import java.net.Inet4Address;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.net.InterfaceAddress;
import java.net.NetworkInterface;
import java.net.StandardProtocolFamily;
import java.net.StandardSocketOptions;
import java.nio.ByteBuffer;
import java.nio.channels.DatagramChannel;
import java.nio.channels.SelectableChannel;
import java.nio.channels.SelectionKey;
import java.nio.channels.Selector;
import java.nio.channels.SocketChannel;
import java.util.ArrayList;
import java.util.Enumeration;
import java.util.Iterator;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.locks.ReentrantLock;

import org.eclipse.smarthome.binding.lifx.LifxBindingConstants;
import org.eclipse.smarthome.binding.lifx.fields.MACAddress;
import org.eclipse.smarthome.binding.lifx.internal.LifxNetworkThrottler;
import org.eclipse.smarthome.binding.lifx.protocol.EchoRequestResponse;
import org.eclipse.smarthome.binding.lifx.protocol.GetEchoRequest;
import org.eclipse.smarthome.binding.lifx.protocol.GetLightPowerRequest;
import org.eclipse.smarthome.binding.lifx.protocol.GetRequest;
import org.eclipse.smarthome.binding.lifx.protocol.GetServiceRequest;
import org.eclipse.smarthome.binding.lifx.protocol.Packet;
import org.eclipse.smarthome.binding.lifx.protocol.PacketFactory;
import org.eclipse.smarthome.binding.lifx.protocol.PacketHandler;
import org.eclipse.smarthome.binding.lifx.protocol.PowerState;
import org.eclipse.smarthome.binding.lifx.protocol.SetColorRequest;
import org.eclipse.smarthome.binding.lifx.protocol.SetLightPowerRequest;
import org.eclipse.smarthome.binding.lifx.protocol.StateLabelResponse;
import org.eclipse.smarthome.binding.lifx.protocol.StatePowerResponse;
import org.eclipse.smarthome.binding.lifx.protocol.StateResponse;
import org.eclipse.smarthome.binding.lifx.protocol.StateServiceResponse;
import org.eclipse.smarthome.core.library.types.DecimalType;
import org.eclipse.smarthome.core.library.types.HSBType;
import org.eclipse.smarthome.core.library.types.IncreaseDecreaseType;
import org.eclipse.smarthome.core.library.types.OnOffType;
import org.eclipse.smarthome.core.library.types.PercentType;
import org.eclipse.smarthome.core.thing.ChannelUID;
import org.eclipse.smarthome.core.thing.Thing;
import org.eclipse.smarthome.core.thing.ThingStatus;
import org.eclipse.smarthome.core.thing.ThingStatusDetail;
import org.eclipse.smarthome.core.thing.binding.BaseThingHandler;
import org.eclipse.smarthome.core.types.Command;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * The {@link LifxLightHandler} is responsible for handling commands, which are
 * sent to one of the light channels.
 *
 * @author Dennis Nobel - Initial contribution
 * @author Stefan Bußweiler - Added new thing status handling
 * @author Karel Goderis - Rewrite for Firmware V2, and remove dependency on external libraries
 */
public class LifxLightHandler extends BaseThingHandler {

    private Logger logger = LoggerFactory.getLogger(LifxLightHandler.class);

    private static final double INCREASE_DECREASE_STEP = 0.10;
    private final int BROADCAST_PORT = 56700;
    private static long NETWORK_INTERVAL = 50;
    private static int ECHO_POLLING_INTERVAL = 15;
    private static int STATE_POLLING_INTERVAL = 3;
    private static int MAXIMUM_POLLING_RETRIES = 2;
    private static int lightCounter = 1;
    private static ReentrantLock lighCounterLock = new ReentrantLock();

    private long source;
    private int service;
    private int port;
    private MACAddress macAddress = null;
    private MACAddress broadcastAddress = new MACAddress("000000000000", true);
    private int sequenceNumber = 1;
    private PowerState currentPowerState;
    private HSBType currentColorState;

    private Selector selector;
    private ScheduledFuture<?> networkJob;
    private ReentrantLock lock = new ReentrantLock();
    private long lastEchoPollingTimestamp = 0;
    private long lastStatePollingTimestamp = 0;

    private InetSocketAddress ipAddress = null;
    private DatagramChannel unicastChannel = null;
    private SelectionKey unicastKey = null;
    private SelectionKey broadcastKey = null;
    private List<InetSocketAddress> broadcastAddresses;
    private List<InetAddress> interfaceAddresses;
    private ConcurrentHashMap<Integer, Packet> sentPackets = new ConcurrentHashMap<Integer, Packet>();
    private int bufferSize = 0;

    public LifxLightHandler(Thing thing) {
        super(thing);
    }

    @Override
    public void dispose() {
        if (networkJob != null && !networkJob.isCancelled()) {
            networkJob.cancel(true);
            networkJob = null;
        }

        currentColorState = null;
        currentPowerState = null;

        try {
            if (selector != null) {
                selector.close();
            }
        } catch (IOException e) {
            logger.warn("An exception occurred while closing the selector : '{}'", e.getMessage());
        }

        if (broadcastKey != null) {
            try {
                broadcastKey.channel().close();
            } catch (IOException e) {
                // TODO Auto-generated catch block
                e.printStackTrace();
            }
        }

        if (unicastKey != null) {
            try {
                unicastKey.channel().close();
            } catch (IOException e) {
                // TODO Auto-generated catch block
                e.printStackTrace();
            }
        }

    }

    @Override
    public void initialize() {
        try {
            macAddress = new MACAddress((String) getConfig().get(LifxBindingConstants.CONFIG_PROPERTY_DEVICE_ID), true);
            logger.debug("Initializing the LIFX handler for bulb '{}'.", this.macAddress.getHex());

            if (networkJob == null || networkJob.isCancelled()) {
                networkJob = scheduler.scheduleWithFixedDelay(networkRunnable, 0, NETWORK_INTERVAL,
                        TimeUnit.MILLISECONDS);
            }

            source = UUID.randomUUID().getLeastSignificantBits() & (-1L >>> 32);
            logger.debug("The LIFX handler will use '{}' as source identifier", Long.toString(source, 16));
            broadcastAddresses = new ArrayList<InetSocketAddress>();
            interfaceAddresses = new ArrayList<InetAddress>();

            Enumeration<NetworkInterface> networkInterfaces = NetworkInterface.getNetworkInterfaces();
            while (networkInterfaces.hasMoreElements()) {
                NetworkInterface iface = networkInterfaces.nextElement();
                if (iface.isUp() && !iface.isLoopback()) {
                    for (InterfaceAddress ifaceAddr : iface.getInterfaceAddresses()) {
                        if (ifaceAddr.getAddress() instanceof Inet4Address) {
                            logger.debug("Adding '{}' as interface address with MTU {}", ifaceAddr.getAddress(),
                                    iface.getMTU());
                            if (iface.getMTU() > bufferSize) {
                                bufferSize = iface.getMTU();
                            }
                            interfaceAddresses.add(ifaceAddr.getAddress());
                            if (ifaceAddr.getBroadcast() != null) {
                                logger.debug("Adding '{}' as broadcast address", ifaceAddr.getBroadcast());
                                broadcastAddresses.add(new InetSocketAddress(ifaceAddr.getBroadcast(), BROADCAST_PORT));
                            }
                        }
                    }
                }
            }

            selector = Selector.open();

            DatagramChannel broadcastChannel = DatagramChannel.open(StandardProtocolFamily.INET)
                    .setOption(StandardSocketOptions.SO_REUSEADDR, true)
                    .setOption(StandardSocketOptions.SO_BROADCAST, true);
            broadcastChannel.configureBlocking(false);
            lighCounterLock.lock();
            logger.debug("Binding the broadcast channel on port {}", BROADCAST_PORT + lightCounter);
            broadcastChannel.bind(new InetSocketAddress(BROADCAST_PORT + lightCounter));
            lightCounter++;
            lighCounterLock.unlock();
            broadcastKey = broadcastChannel.register(selector, SelectionKey.OP_READ | SelectionKey.OP_WRITE);

            updateStatus(ThingStatus.OFFLINE, ThingStatusDetail.CONFIGURATION_PENDING);

            // look for lights on the network
            GetServiceRequest packet = new GetServiceRequest();
            broadcastPacket(packet);

        } catch (Exception ex) {
            logger.error("Error occured while initializing LIFX handler: " + ex.getMessage(), ex);
        }
    }

    @Override
    public void handleCommand(ChannelUID channelUID, Command command) {

        if (getThing().getStatusInfo().getStatus() != ThingStatus.ONLINE) {
            logger.warn("Cannot handle command: No connection to LIFX network.");
            return;
        }
        try {
            switch (channelUID.getId()) {
                case CHANNEL_COLOR:
                    if (command instanceof HSBType) {
                        handleHSBCommand((HSBType) command);
                    } else if (command instanceof PercentType) {
                        handlePercentCommand((PercentType) command);
                    } else if (command instanceof OnOffType) {
                        handleOnOffCommand((OnOffType) command);
                    } else if (command instanceof IncreaseDecreaseType) {
                        handleIncreaseDecreaseCommand((IncreaseDecreaseType) command);
                    }
                    break;
                default:
                    break;
            }
        } catch (Exception ex) {
            logger.error("Error while updating bulb.", ex);
        }
    }

    private void handleHSBCommand(HSBType hsbType) {
        SetColorRequest packet = new SetColorRequest((int) (hsbType.getHue().floatValue() / 360 * 65535.0f),
                (int) (hsbType.getSaturation().floatValue() / 100 * 65535.0f),
                (int) (hsbType.getBrightness().floatValue() / 100 * 65535.0f), 0, 0);
        packet.setResponseRequired(false);
        sendPacket(packet);

        if (currentPowerState != PowerState.ON) {
            SetLightPowerRequest secondPacket = new SetLightPowerRequest(PowerState.ON);
            secondPacket.setResponseRequired(false);
            sendPacket(secondPacket);
        }

        // the LIFX LAN protocol spec indicates that the response returned for a request would be the
        // previous value, so we explicitely demand for the latest value
        GetLightPowerRequest powerPacket = new GetLightPowerRequest();
        sendPacket(powerPacket);

        GetRequest colorPacket = new GetRequest();
        sendPacket(colorPacket);
    }

    private void handlePercentCommand(PercentType percentType) {
        if (currentColorState != null) {
            HSBType newColorState = new HSBType(currentColorState.getHue(), currentColorState.getSaturation(),
                    percentType);
            handleHSBCommand(newColorState);
        }
    }

    private void handleOnOffCommand(OnOffType onOffType) {
        PowerState lfxPowerState = onOffType == OnOffType.ON ? PowerState.ON : PowerState.OFF;
        SetLightPowerRequest packet = new SetLightPowerRequest(lfxPowerState);
        sendPacket(packet);

        // the LIFX LAN protocol spec indicates that the response returned for a request would be the
        // previous value, so we explicitely demand for the latest value
        GetLightPowerRequest powerPacket = new GetLightPowerRequest();
        sendPacket(powerPacket);
    }

    private void handleIncreaseDecreaseCommand(IncreaseDecreaseType increaseDecreaseType) {
        if (currentColorState != null) {
            float brightness = currentColorState.getBrightness().floatValue() / 100;

            if (increaseDecreaseType == IncreaseDecreaseType.INCREASE) {
                brightness = (float) (brightness + INCREASE_DECREASE_STEP);
                if (brightness > 1) {
                    brightness = 1;
                }

            }
            if (increaseDecreaseType == IncreaseDecreaseType.DECREASE) {
                brightness = (float) (brightness - INCREASE_DECREASE_STEP);
                if (brightness < 0) {
                    brightness = 0;
                }
            }

            PercentType newBrightness = new PercentType(Math.round(brightness * 100));
            handlePercentCommand(newBrightness);
        }
    }

    private Runnable networkRunnable = new Runnable() {

        @Override
        public void run() {
            try {
                lock.lock();

                if (selector != null) {
                    try {
                        selector.selectNow();
                    } catch (IOException e) {
                        logger.error("An exception occurred while selecting: {}", e.getMessage());
                    }

                    Set<SelectionKey> selectedKeys = selector.selectedKeys();
                    Iterator<SelectionKey> keyIterator = selectedKeys.iterator();

                    while (keyIterator.hasNext()) {
                        SelectionKey key = keyIterator.next();

                        if (key.isValid() && key.isAcceptable()) {
                            // a connection was accepted by a ServerSocketChannel.
                            // block of code only for completeness purposes

                        } else if (key.isValid() && key.isConnectable()) {
                            // a connection was established with a remote server.
                            // block of code only for completeness purposes

                        } else if (key.isValid() && key.isReadable()) {
                            // a channel is ready for reading
                            SelectableChannel channel = key.channel();
                            InetSocketAddress address = null;
                            int messageLength = 0;

                            ByteBuffer readBuffer = ByteBuffer.allocate(bufferSize);
                            try {
                                if (channel instanceof DatagramChannel) {
                                    address = (InetSocketAddress) ((DatagramChannel) channel).receive(readBuffer);
                                } else if (channel instanceof SocketChannel) {
                                    address = (InetSocketAddress) ((SocketChannel) channel).getRemoteAddress();
                                    ((SocketChannel) channel).read(readBuffer);
                                }
                                messageLength = readBuffer.position();
                            } catch (Exception e) {
                                logger.warn("An exception occurred while reading data : '{}'", e.getMessage());
                            }

                            if (!interfaceAddresses.contains(address.getAddress())) {

                                readBuffer.rewind();

                                ByteBuffer packetSize = readBuffer.slice();
                                packetSize.position(0);
                                packetSize.limit(2);
                                int size = Packet.FIELD_SIZE.value(packetSize);

                                if (messageLength == size) {

                                    ByteBuffer packetType = readBuffer.slice();
                                    packetType.position(32);
                                    packetType.limit(34);
                                    int type = Packet.FIELD_PACKET_TYPE.value(packetType);

                                    PacketHandler handler = PacketFactory.createHandler(type);

                                    if (handler == null) {
                                        logger.trace("Unknown packet type: {} (source: {})",
                                                String.format("0x%02X", type), address.toString());
                                        continue;
                                    }

                                    Packet packet = handler.handle(readBuffer);
                                    if (packet == null) {
                                        logger.warn("Handler {} was unable to handle packet",
                                                handler.getClass().getName());
                                    } else {
                                        handlePacket(packet, address);
                                    }
                                }
                            }
                        } else if (key.isValid() && key.isWritable()) {
                            // a channel is ready for writing
                            // block of code only for completeness purposes
                        }

                    }
                }
            } catch (Exception e) {
                logger.error("An exception orccurred while communicating with the bulb : '{}'", e.getMessage());
                e.printStackTrace();
            } finally {
                lock.unlock();
            }

            // poll the device
            if ((System.currentTimeMillis() - lastEchoPollingTimestamp) > ECHO_POLLING_INTERVAL * 1000) {
                if (getThing().getStatus() != ThingStatus.OFFLINE) {
                    logger.trace("{} : Polling", macAddress.getHex());
                    int counter = 0;
                    for (Packet aPacket : sentPackets.values()) {
                        if (aPacket instanceof GetEchoRequest) {
                            counter++;
                        }
                    }

                    lastEchoPollingTimestamp = System.currentTimeMillis();

                    if (counter < MAXIMUM_POLLING_RETRIES) {
                        ByteBuffer payload = ByteBuffer.allocate(Long.SIZE / 8);
                        payload.putLong(lastEchoPollingTimestamp);

                        GetEchoRequest request = new GetEchoRequest();
                        request.setResponseRequired(true);
                        request.setPayload(payload);

                        sendPacket(request);
                    } else {
                        updateStatus(ThingStatus.OFFLINE, ThingStatusDetail.COMMUNICATION_ERROR);
                        sentPackets.clear();
                    }
                } else {
                    // are we not configured? let's broadcast instead
                    logger.trace("{} : The bulb is not online, let's broadcast instead", macAddress.getHex());
                    lastEchoPollingTimestamp = System.currentTimeMillis();
                    GetServiceRequest packet = new GetServiceRequest();
                    broadcastPacket(packet);
                }
            }

            if ((System.currentTimeMillis() - lastStatePollingTimestamp) > STATE_POLLING_INTERVAL * 1000) {
                if (getThing().getStatus() != ThingStatus.OFFLINE) {
                    logger.trace("{} : Polling the state of the bulb", macAddress.getHex());
                    lastStatePollingTimestamp = System.currentTimeMillis();

                    GetLightPowerRequest powerPacket = new GetLightPowerRequest();
                    sendPacket(powerPacket);

                    GetRequest colorPacket = new GetRequest();
                    sendPacket(colorPacket);
                } else {
                    logger.trace("{} : The bulb is not online, there is not point polling it", macAddress.getHex());
                    lastStatePollingTimestamp = System.currentTimeMillis();
                }
            }

        }
    };

    private void sendPacket(Packet packet) {
        if (ipAddress != null) {
            packet.setSource(source);
            packet.setTarget(macAddress);

            if (sentPackets.containsKey(sequenceNumber)) {
                logger.warn(
                        "A messages with sequence number '{}' has already been sent to the bulb. Is it missing in action? ",
                        sequenceNumber);
            }
            packet.setSequence(sequenceNumber);

            LifxNetworkThrottler.lock(macAddress.getAsLabel());
            sendPacket(packet, ipAddress, unicastKey);
            LifxNetworkThrottler.unlock(macAddress.getAsLabel());

            sequenceNumber++;
            if (sequenceNumber > 255) {
                sequenceNumber = 1;
            }
        }
    }

    private void broadcastPacket(Packet packet) {

        if (sentPackets.containsKey(sequenceNumber)) {
            logger.warn(
                    "A messages with sequence number '{}' has already been sent to the bulb. Is it missing in action? ",
                    sequenceNumber);
        }

        packet.setSequence(sequenceNumber);
        packet.setSource(source);

        for (InetSocketAddress address : broadcastAddresses) {
            boolean result = false;
            while (!result) {
                LifxNetworkThrottler.lock();
                result = sendPacket(packet, address, broadcastKey);
                LifxNetworkThrottler.unlock();
            }
        }

        sequenceNumber++;
        if (sequenceNumber > 255) {
            sequenceNumber = 1;
        }
    }

    private boolean sendPacket(Packet packet, InetSocketAddress address, SelectionKey selectedKey) {

        boolean result = false;

        try {
            lock.lock();

            boolean sent = false;

            while (!sent) {
                try {
                    selector.selectNow();
                } catch (IOException e) {
                    logger.error("An exception occurred while selecting: {}", e.getMessage());
                }

                Set<SelectionKey> selectedKeys = selector.selectedKeys();
                Iterator<SelectionKey> keyIterator = selectedKeys.iterator();

                while (keyIterator.hasNext()) {
                    SelectionKey key = keyIterator.next();

                    if (key.isValid() && key.isWritable() && key.equals(selectedKey)) {
                        SelectableChannel channel = key.channel();
                        try {
                            if (channel instanceof DatagramChannel) {
                                logger.debug(
                                        "{} : Sending packet type '{}' from '{}' to '{}' for '{}' with sequence '{}' and source '{}'",
                                        new Object[] { macAddress.getHex(), packet.getClass().getSimpleName(),
                                                ((InetSocketAddress) ((DatagramChannel) channel).getLocalAddress())
                                                        .toString(),
                                                address.toString(), packet.getTarget().getHex(), packet.getSequence(),
                                                Long.toString(packet.getSource(), 16) });
                                int number = ((DatagramChannel) channel).send(packet.bytes(), address);
                                if (packet.getResponseRequired()) {
                                    sentPackets.put(packet.getSequence(), packet);
                                }
                                sent = true;
                                result = true;
                            } else if (channel instanceof SocketChannel) {
                                ((SocketChannel) channel).write(packet.bytes());
                            }
                        } catch (Exception e) {
                            logger.error("An exception occurred while writing data : '{}'", e.getMessage());
                        }
                    }
                }
            }
        } catch (Exception e) {
            logger.error("An exception occurred while communicating with the bulb : '{}'", e.getMessage());
        } finally {
            lock.unlock();
        }

        return result;
    }

    private void handlePacket(Packet packet, InetSocketAddress address) {

        if ((packet.getTarget().equals(macAddress) || packet.getTarget().equals(broadcastAddress))
                && (packet.getSource() == source || packet.getSource() == 0)) {

            logger.debug("{} : Packet type '{}' received from '{}' for '{}' with sequence '{}' and source '{}'",
                    new Object[] { macAddress.getHex(), packet.getClass().getSimpleName(), address.toString(),
                            packet.getTarget().getHex(), packet.getSequence(), Long.toString(packet.getSource(), 16) });

            Packet originalPacket = sentPackets.get(packet.getSequence());
            if (originalPacket != null) {
                logger.trace("{} : Packet is a response to a packet of type '{}' with sequence number '{}'",
                        new Object[] { macAddress.getHex(), originalPacket.getClass().getSimpleName(),
                                packet.getSequence() });
                logger.trace("{} : This response was {}expected", macAddress.getHex(),
                        originalPacket.isExpectedResponse(packet.getPacketType()) ? "" : "not ");
                if (originalPacket.isFulfilled(packet)) {
                    sentPackets.remove(packet.getSequence());
                    logger.trace("{} : There are now {} unanswered packets remaining", macAddress.getHex(),
                            sentPackets.size());
                    for (Packet aPacket : sentPackets.values()) {
                        logger.trace("{} : sentPackets contains {} with sequence number {}", new Object[] {
                                macAddress.getHex(), aPacket.getClass().getSimpleName(), aPacket.getSequence() });
                    }
                }
            }

            if (packet instanceof StateServiceResponse) {
                MACAddress discoveredAddress = ((StateServiceResponse) packet).getTarget();
                if (macAddress.equals(discoveredAddress)) {
                    if (!address.equals(ipAddress) && port != (int) ((StateServiceResponse) packet).getPort()
                            && service != ((StateServiceResponse) packet).getService()
                            || getThing().getStatus() == ThingStatus.OFFLINE) {
                        this.port = (int) ((StateServiceResponse) packet).getPort();
                        this.service = ((StateServiceResponse) packet).getService();

                        if (port == 0) {
                            logger.warn("The service with ID '{}' is currently not available", service);
                            updateStatus(ThingStatus.OFFLINE, ThingStatusDetail.COMMUNICATION_ERROR);
                        } else {

                            if (unicastChannel != null && unicastKey != null) {
                                try {
                                    unicastChannel.close();
                                } catch (IOException e) {
                                    logger.error("An exception occurred while closing the channel : '{}'",
                                            e.getMessage());
                                }
                                unicastKey.cancel();
                            }

                            try {
                                ipAddress = new InetSocketAddress(address.getAddress(), port);
                                unicastChannel = DatagramChannel.open(StandardProtocolFamily.INET)
                                        .setOption(StandardSocketOptions.SO_REUSEADDR, true);
                                unicastChannel.configureBlocking(false);
                                unicastKey = unicastChannel.register(selector,
                                        SelectionKey.OP_READ | SelectionKey.OP_WRITE);
                                unicastChannel.connect(ipAddress);
                                logger.trace("Connected to a bulb via {}", unicastChannel.getLocalAddress().toString());
                            } catch (Exception e) {
                                logger.warn("An exception occurred while connectin to the bulb's IP address : '{}'",
                                        e.getMessage());
                                updateStatus(ThingStatus.OFFLINE, ThingStatusDetail.COMMUNICATION_ERROR);
                                return;
                            }

                            updateStatus(ThingStatus.ONLINE);

                            // populate the current state variables
                            GetLightPowerRequest powerPacket = new GetLightPowerRequest();
                            sendPacket(powerPacket);

                            GetRequest colorPacket = new GetRequest();
                            sendPacket(colorPacket);

                        }
                    }
                }

                return;
            }

            if (packet instanceof StateResponse) {
                handleLightStatus((StateResponse) packet);
                return;
            }

            if (packet instanceof StatePowerResponse) {
                handlePowerStatus((StatePowerResponse) packet);
                return;
            }

            if (packet instanceof StateLabelResponse) {
                // Do we care about labels?
                return;
            }

            if (packet instanceof EchoRequestResponse) {
                handleEchoRequestReponse((EchoRequestResponse) packet);
                return;
            }
        }
    }

    public void handleLightStatus(StateResponse packet) {
        DecimalType hue = new DecimalType(packet.getHue() * 360 / 65535.0f);
        PercentType saturation = new PercentType(Math.round((packet.getSaturation() / 65535.0f) * 100));
        PercentType brightness = new PercentType(Math.round((packet.getBrightness() / 65535.0f) * 100));

        currentColorState = new HSBType(hue, saturation, brightness);
        currentPowerState = packet.getPower();

        if (currentPowerState == PowerState.OFF) {
            updateState(CHANNEL_COLOR, OnOffType.OFF);
        } else if (currentColorState != null) {
            updateState(CHANNEL_COLOR, currentColorState);
        } else {
            updateState(CHANNEL_COLOR, OnOffType.ON);
        }

        updateStatus(ThingStatus.ONLINE);
    }

    public void handlePowerStatus(StatePowerResponse packet) {
        currentPowerState = packet.getState();

        if (packet.getState() == PowerState.OFF) {
            updateState(CHANNEL_COLOR, OnOffType.OFF);
        } else if (currentColorState != null) {
            updateState(CHANNEL_COLOR, currentColorState);
        } else {
            updateState(CHANNEL_COLOR, OnOffType.ON);
        }

        updateStatus(ThingStatus.ONLINE);
    }

    public void handleEchoRequestReponse(EchoRequestResponse packet) {
        // remove any unanswered echo requests
        for (Packet aPacket : sentPackets.values()) {
            if (aPacket.getPacketType() == GetEchoRequest.TYPE) {
                sentPackets.remove(aPacket.getSequence());
            }
        }
        if (getThing().getStatus() == ThingStatus.OFFLINE) {
            // remove any unanswered packet that were sent before
            sentPackets.clear();
            updateStatus(ThingStatus.ONLINE);
        }
    }
}
