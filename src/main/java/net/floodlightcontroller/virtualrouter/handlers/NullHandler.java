package net.floodlightcontroller.virtualrouter.handlers;

import net.floodlightcontroller.core.IListener;
import net.floodlightcontroller.core.IOFSwitch;
import net.floodlightcontroller.packet.Ethernet;
import org.projectfloodlight.openflow.protocol.OFPacketIn;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class NullHandler implements PacketHandler {

    private static final Logger logger = LoggerFactory.getLogger(NullHandler.class);

    @Override
    public IListener.Command handle(Ethernet inputFrame, IOFSwitch sw, OFPacketIn packetIn) {
        logger.debug("Packet not handled");
        return IListener.Command.CONTINUE;
    }
}
