package net.floodlightcontroller.virtualrouter.handlers;

import net.floodlightcontroller.core.IListener;
import net.floodlightcontroller.core.IOFSwitch;
import net.floodlightcontroller.packet.Ethernet;
import net.floodlightcontroller.packet.ICMP;
import net.floodlightcontroller.packet.IPacket;
import net.floodlightcontroller.packet.IPv4;
import net.floodlightcontroller.util.OFMessageUtils;
import net.floodlightcontroller.virtualrouter.Gateway;
import net.floodlightcontroller.virtualrouter.store.gateway.GatewayStoreService;
import org.projectfloodlight.openflow.protocol.OFFactory;
import org.projectfloodlight.openflow.protocol.OFPacketIn;
import org.projectfloodlight.openflow.protocol.OFPacketOut;
import org.projectfloodlight.openflow.types.EthType;
import org.projectfloodlight.openflow.types.IpProtocol;
import org.projectfloodlight.openflow.types.OFBufferId;
import org.projectfloodlight.openflow.types.OFPort;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Collections;
import java.util.Optional;

public class ICMPPacketHandler implements PacketHandler {

    private static final Logger logger = LoggerFactory.getLogger(ICMPPacketHandler.class);

    private GatewayStoreService gatewayStore;

    public static final String KEY = Ethernet.class.getSimpleName() + IPv4.class.getSimpleName() + ICMP.class.getSimpleName();

    public ICMPPacketHandler(GatewayStoreService gatewayStore) {
        this.gatewayStore = gatewayStore;
    }

    @Override
    public IListener.Command handle(Ethernet inputEthernetFrame, IOFSwitch sw, OFPacketIn packetIn) {
        IPv4 inputIpPacket = (IPv4) inputEthernetFrame.getPayload();
        if ( inputIpPacket.getPayload() instanceof ICMP ) {
            logger.debug("handling icmp packet");
            ICMP inputIcmpPacket = (ICMP) inputIpPacket.getPayload();
            logger.debug(inputIpPacket.getSourceAddress() + " is icmping " + inputIpPacket.getDestinationAddress() + ". ICMP type: " + inputIcmpPacket.getType().name() + ". ICMP code: " + inputIcmpPacket.getCode().name() + ". Received from switch: " + sw.getId());
            Optional<Gateway> optionalGateway = gatewayStore.getGateway(inputIpPacket.getDestinationAddress(), sw.getId());
            if (ICMP.Code.ECHO_REQUEST.equals(inputIcmpPacket.getCode()) && optionalGateway.isPresent()) {
                Gateway queriedGateway = optionalGateway.get();
                logger.debug("responding to icmp echo request packet");
                IPacket icmpResponse = new ICMP()
                        .setCode(ICMP.Code.ECHO_REPLY)
                        .setPayload(inputIcmpPacket.getPayload());

                IPacket ipResponse = new IPv4()
                        .setDestinationAddress(inputIpPacket.getSourceAddress())
                        .setSourceAddress(inputIpPacket.getDestinationAddress())
                        .setTtl((byte) 10)
                        .setProtocol(IpProtocol.ICMP)
                        .setPayload(icmpResponse);

                IPacket ethernetResponse = new Ethernet()
                        .setDestinationMACAddress(inputEthernetFrame.getSourceMACAddress())
                        .setSourceMACAddress(queriedGateway.getMacAddress())
                        .setEtherType(EthType.IPv4)
                        .setVlanID(inputEthernetFrame.getVlanID())
                        .setPriorityCode(inputEthernetFrame.getPriorityCode())
                        .setPayload(ipResponse);

                OFPacketOut packetOut = createPacketOut(sw, packetIn, ethernetResponse);
                sw.write(packetOut);
                logger.debug("Successfully sent ICMP response");
                return IListener.Command.STOP;
            }
        }
        return IListener.Command.CONTINUE;
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
}
