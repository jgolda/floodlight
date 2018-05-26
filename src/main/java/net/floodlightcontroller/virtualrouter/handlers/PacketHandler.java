package net.floodlightcontroller.virtualrouter.handlers;

import net.floodlightcontroller.core.IListener;
import net.floodlightcontroller.core.IOFSwitch;
import net.floodlightcontroller.packet.Ethernet;
import org.projectfloodlight.openflow.protocol.OFPacketIn;

public interface PacketHandler{

    IListener.Command handle(Ethernet inputFrame, IOFSwitch sw, OFPacketIn packetIn);
}
