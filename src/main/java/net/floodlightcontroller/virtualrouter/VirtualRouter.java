package net.floodlightcontroller.virtualrouter;

import net.floodlightcontroller.core.FloodlightContext;
import net.floodlightcontroller.core.IFloodlightProviderService;
import net.floodlightcontroller.core.IOFMessageListener;
import net.floodlightcontroller.core.IOFSwitch;
import net.floodlightcontroller.core.module.FloodlightModuleContext;
import net.floodlightcontroller.core.module.FloodlightModuleException;
import net.floodlightcontroller.core.module.IFloodlightModule;
import net.floodlightcontroller.core.module.IFloodlightService;
import net.floodlightcontroller.packet.*;
import net.floodlightcontroller.util.OFMessageUtils;
import net.floodlightcontroller.virtualrouter.store.GatewayStoreService;
import org.projectfloodlight.openflow.protocol.*;
import org.projectfloodlight.openflow.types.*;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Collection;
import java.util.Collections;
import java.util.Map;
import java.util.Optional;

import static org.projectfloodlight.openflow.protocol.OFType.PACKET_IN;

public class VirtualRouter implements IFloodlightModule, IVirtualRouter, IOFMessageListener {

    private static final Logger logger = LoggerFactory.getLogger(VirtualRouter.class);

    private IFloodlightProviderService floodlightProvider;

    private GatewayStoreService gatewayStore;

    @Override
    public Collection<Class<? extends IFloodlightService>> getModuleServices() {
        return Collections.singletonList(IVirtualRouter.class);
    }

    @Override
    public void init(FloodlightModuleContext context) throws FloodlightModuleException {
        logger.info("Initializing virtual router module");
        floodlightProvider = context.getServiceImpl(IFloodlightProviderService.class);
        gatewayStore = context.getServiceImpl(GatewayStoreService.class);
    }

    @Override
    public Map<Class<? extends IFloodlightService>, IFloodlightService> getServiceImpls() {
        return Collections.singletonMap(IVirtualRouter.class, this);
    }

    @Override
    public Collection<Class<? extends IFloodlightService>> getModuleDependencies() {
        return Collections.singleton(GatewayStoreService.class);
    }

    @Override
    public void startUp(FloodlightModuleContext context) throws FloodlightModuleException {
        logger.info("Starting virtual router module");
        floodlightProvider.addOFMessageListener(PACKET_IN, this);
    }

    @Override
    public Command receive(IOFSwitch sw, OFMessage msg, FloodlightContext cntx) {
        if ( PACKET_IN.equals(msg.getType()) ) {
            OFPacketIn packetIn = (OFPacketIn) msg;
            Ethernet inputEthernetFrame = IFloodlightProviderService.bcStore.get(cntx, IFloodlightProviderService.CONTEXT_PI_PAYLOAD);
            if ( inputEthernetFrame.getPayload() instanceof ARP ) {
                logger.debug("handling arp message");
                ARP inputArpRequest = (ARP) inputEthernetFrame.getPayload();
                logger.debug(String.format("ARP Operation: %s", inputArpRequest.getOpCode().toString()));
                String message = String.format("%s is asking for %s mac address. Source mac address: %s, arrived target mac address: %s", inputArpRequest.getSenderProtocolAddress(), inputArpRequest.getTargetProtocolAddress(), inputArpRequest.getSenderHardwareAddress(), inputArpRequest.getTargetHardwareAddress());
                logger.debug(message);
                Optional<Gateway> optionalGateway = gatewayStore.getGateway(inputArpRequest.getTargetProtocolAddress());
                if ( optionalGateway.isPresent()) {
                    Gateway queriedGateway = optionalGateway.get();
                    logger.debug(String.format("Request for virtual gateway: %s", queriedGateway.getIpAddress()));
                    OFPacketOut packetOut = createResponse(sw, packetIn, inputEthernetFrame, inputArpRequest, queriedGateway);
                    sw.write(packetOut);
                    logger.debug("Successfully sent arp response");
                    return Command.STOP;
                }
            } else if (inputEthernetFrame.getPayload() instanceof IPv4 ) {
                IPv4 inputIpPacket = (IPv4) inputEthernetFrame.getPayload();
                if ( inputIpPacket.getPayload() instanceof ICMP ) {
                    logger.debug("handling icmp packet");
                    ICMP inputIcmpPacket = (ICMP) inputIpPacket.getPayload();
                    logger.debug(inputIpPacket.getSourceAddress() + " is icmping " + inputIpPacket.getDestinationAddress() + ". ICMP type: " + inputIcmpPacket.getType().name() + ", in byte: " + inputIcmpPacket.getType().value() + ". ICMP code: " + inputIcmpPacket.getCode().name() + ", in byte: " + inputIcmpPacket.getCode().value());
                    Optional<Gateway> optionalGateway = gatewayStore.getGateway(inputIpPacket.getDestinationAddress());
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
                        return Command.STOP;
                    }
                }
            }
        }
        return Command.CONTINUE;
    }

    private OFPacketOut createResponse(IOFSwitch sw, OFPacketIn msg, Ethernet inputEthernetFrame, ARP inputArpRequest, Gateway queriedGateway) {
        ARP arpResponse = createArpResponse(inputArpRequest, queriedGateway);
        IPacket response = createResponsePacket(inputEthernetFrame, inputArpRequest, arpResponse, queriedGateway);
        return createPacketOut(sw, msg, response);
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

    @Override
    public String getName() {
        return "virtualrouter";
    }

    @Override
    public boolean isCallbackOrderingPrereq(OFType type, String name) {
        return false;
    }

    @Override
    public boolean isCallbackOrderingPostreq(OFType type, String name) {
        return name.equals("devicemanager");
    }
}
