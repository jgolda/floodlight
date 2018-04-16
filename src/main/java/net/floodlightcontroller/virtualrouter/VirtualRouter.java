package net.floodlightcontroller.virtualrouter;

import net.floodlightcontroller.core.*;
import net.floodlightcontroller.core.module.FloodlightModuleContext;
import net.floodlightcontroller.core.module.FloodlightModuleException;
import net.floodlightcontroller.core.module.IFloodlightModule;
import net.floodlightcontroller.core.module.IFloodlightService;
import net.floodlightcontroller.packet.*;
import net.floodlightcontroller.virtualrouter.handlers.ARPPacketHandler;
import net.floodlightcontroller.virtualrouter.handlers.ICMPPacketHandler;
import net.floodlightcontroller.virtualrouter.handlers.NullHandler;
import net.floodlightcontroller.virtualrouter.handlers.PacketHandler;
import net.floodlightcontroller.virtualrouter.store.gateway.GatewayStoreService;
import org.projectfloodlight.openflow.protocol.*;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.*;

import static org.projectfloodlight.openflow.protocol.OFType.PACKET_IN;

public class VirtualRouter implements IFloodlightModule, IVirtualRouter, IOFMessageListener {

    private static final Logger logger = LoggerFactory.getLogger(VirtualRouter.class);

    private IFloodlightProviderService floodlightProvider;

    private GatewayStoreService gatewayStore;

    private Map<String, PacketHandler> PACKET_HANDLERS = new HashMap<>();

    @Override
    public Collection<Class<? extends IFloodlightService>> getModuleServices() {
        return Collections.singletonList(IVirtualRouter.class);
    }

    @Override
    public void init(FloodlightModuleContext context) throws FloodlightModuleException {
        logger.info("Initializing virtual router module");
        floodlightProvider = context.getServiceImpl(IFloodlightProviderService.class);
        gatewayStore = context.getServiceImpl(GatewayStoreService.class);
        buildHandlerMap();
    }

    private void buildHandlerMap() {
        PACKET_HANDLERS.put(ARPPacketHandler.KEY, new ARPPacketHandler(gatewayStore));
        PACKET_HANDLERS.put(ICMPPacketHandler.KEY, new ICMPPacketHandler(gatewayStore));
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
            PacketHandler handler = getHandlerForPacket(inputEthernetFrame);
            return handler.handle(inputEthernetFrame, sw, packetIn);
        }
        return Command.CONTINUE;
    }

    private PacketHandler getHandlerForPacket(Ethernet inputEthernetFrame) {
        String key = buildKey(inputEthernetFrame);
        return PACKET_HANDLERS.getOrDefault(key, new NullHandler());
    }

    private String buildKey(Ethernet inputEthernetFrame) {
        StringBuilder key = new StringBuilder();
        for ( IPacket packet = inputEthernetFrame; packet != null && !(packet instanceof Data); packet = packet.getPayload() ) {
            key.append(packet.getClass().getSimpleName());
        }
        return key.toString();
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
        return name.equals("forwarding");
    }
}
