package net.floodlightcontroller.virtualrouter.handlers;

import net.floodlightcontroller.core.IListener;
import net.floodlightcontroller.core.IOFSwitch;
import net.floodlightcontroller.packet.ARP;
import net.floodlightcontroller.packet.Ethernet;
import net.floodlightcontroller.packet.IPacket;
import net.floodlightcontroller.util.OFMessageUtils;
import net.floodlightcontroller.virtualrouter.Gateway;
import net.floodlightcontroller.virtualrouter.store.gateway.GatewayStoreService;
import org.projectfloodlight.openflow.protocol.OFFactory;
import org.projectfloodlight.openflow.protocol.OFPacketIn;
import org.projectfloodlight.openflow.protocol.OFPacketOut;
import org.projectfloodlight.openflow.types.ArpOpcode;
import org.projectfloodlight.openflow.types.EthType;
import org.projectfloodlight.openflow.types.OFBufferId;
import org.projectfloodlight.openflow.types.OFPort;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Collections;
import java.util.Optional;

public class ARPPacketHandler implements PacketHandler {

    private Logger logger = LoggerFactory.getLogger(ARPPacketHandler.class);

    private GatewayStoreService gatewayStore;

    public static final String KEY = Ethernet.class.getSimpleName() + ARP.class.getSimpleName();

    public ARPPacketHandler(GatewayStoreService gatewayStore) {
        this.gatewayStore = gatewayStore;
    }

    @Override
    public IListener.Command handle(Ethernet inputEthernetFrame, IOFSwitch sw, OFPacketIn packetIn) {
        logger.debug("handling arp message");
        ARP inputArpRequest = (ARP) inputEthernetFrame.getPayload();
        logger.debug(String.format("ARP Operation: %s", inputArpRequest.getOpCode().toString()));
        String message = String.format("%s is asking for %s mac address. Source mac address: %s, arrived target mac address: %s", inputArpRequest.getSenderProtocolAddress(), inputArpRequest.getTargetProtocolAddress(), inputArpRequest.getSenderHardwareAddress(), inputArpRequest.getTargetHardwareAddress());
        logger.debug(message);
        Optional<Gateway> optionalGateway = gatewayStore.getGateway(inputArpRequest.getTargetProtocolAddress(), sw.getId());
        if ( optionalGateway.isPresent()) {
            Gateway queriedGateway = optionalGateway.get();
            logger.debug(String.format("Request for virtual gateway: %s", queriedGateway.getIpAddress()));
            OFPacketOut packetOut = createResponse(sw, packetIn, inputEthernetFrame, inputArpRequest, queriedGateway);
            sw.write(packetOut);
            logger.debug("Successfully sent arp response");
            return IListener.Command.STOP;
        }
        return IListener.Command.CONTINUE;
    }

    private OFPacketOut createResponse(IOFSwitch sw, OFPacketIn msg, Ethernet inputEthernetFrame, ARP inputArpRequest, Gateway queriedGateway) {
        ARP arpResponse = createArpResponse(inputArpRequest, queriedGateway);
        IPacket response = createResponsePacket(inputEthernetFrame, inputArpRequest, arpResponse, queriedGateway);
        return createPacketOut(sw, msg, response);
    }

    private ARP createArpResponse(ARP inputArpRequest, Gateway queriedGateway) {
        return new ARP()
                .setOpCode(ArpOpcode.REPLY)
                .setTargetHardwareAddress(inputArpRequest.getSenderHardwareAddress())
                .setTargetProtocolAddress(inputArpRequest.getSenderProtocolAddress())
                .setSenderHardwareAddress(queriedGateway.getMacAddress())
                .setSenderProtocolAddress(queriedGateway.getIpAddress())
                .setHardwareType(ARP.HW_TYPE_ETHERNET)
                .setProtocolType(ARP.PROTO_TYPE_IP)
                .setHardwareAddressLength((byte) 6)
                .setProtocolAddressLength((byte) 4);
    }

    private OFPacketOut createPacketOut(IOFSwitch sw, OFPacketIn msg, IPacket response) {
        OFFactory factory = sw.getOFFactory();
        OFPacketOut.Builder arpResponseBuilder = factory.buildPacketOut();
        arpResponseBuilder.setBufferId(OFBufferId.NO_BUFFER)
                .setActions(Collections.singletonList(factory.actions().buildOutput().setPort(OFMessageUtils.determinePort(msg)).setMaxLen(Integer.MAX_VALUE).build()))
                .setData(response.serialize());

        OFMessageUtils.setInPort(arpResponseBuilder, OFPort.ANY);

        return arpResponseBuilder.build();
    }

    private IPacket createResponsePacket(Ethernet ethernet, ARP arp, ARP arpResponse, Gateway queriedGateway) {
        return new Ethernet()
                .setSourceMACAddress(queriedGateway.getMacAddress())
                .setDestinationMACAddress(arp.getSenderHardwareAddress())
                .setEtherType(EthType.ARP)
                .setVlanID(ethernet.getVlanID())
                .setPriorityCode(ethernet.getPriorityCode())
                .setPayload(arpResponse);
    }
}
