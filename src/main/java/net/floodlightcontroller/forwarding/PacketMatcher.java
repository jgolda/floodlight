package net.floodlightcontroller.forwarding;

import net.floodlightcontroller.core.IOFSwitch;
import net.floodlightcontroller.packet.*;
import org.projectfloodlight.openflow.protocol.OFPacketIn;
import org.projectfloodlight.openflow.protocol.OFVersion;
import org.projectfloodlight.openflow.protocol.match.Match;
import org.projectfloodlight.openflow.protocol.match.MatchField;
import org.projectfloodlight.openflow.types.*;

public class PacketMatcher {

    private MatchingConfig matchingConfig;

    public PacketMatcher(MatchingConfig matchingConfig) {
        this.matchingConfig = matchingConfig;
    }

    public Match createMatchFromPacket(IOFSwitch sw, OFPacketIn pi, Ethernet eth, OFPort inPort) {
        VlanVid vlan = null;
        if (pi.getVersion().compareTo(OFVersion.OF_11) > 0 && /* 1.0 and 1.1 do not have a match */
                pi.getMatch().get(MatchField.VLAN_VID) != null) {
            vlan = pi.getMatch().get(MatchField.VLAN_VID).getVlanVid(); /* VLAN may have been popped by switch */
        }
        if (vlan == null) {
            vlan = VlanVid.ofVlan(eth.getVlanID()); /* VLAN might still be in packet */
        }

        MacAddress srcMac = eth.getSourceMACAddress();
        MacAddress dstMac = eth.getDestinationMACAddress();

        Match.Builder mb = sw.getOFFactory().buildMatch();
        if (matchingConfig.isMatchInPort()) {
            mb.setExact(MatchField.IN_PORT, inPort);
        }

        if (matchingConfig.isMatchMac()) {
            if (matchingConfig.isMatchMacSrc()) {
                mb.setExact(MatchField.ETH_SRC, srcMac);
            }
            if (matchingConfig.isMatchMacDst()) {
                mb.setExact(MatchField.ETH_DST, dstMac);
            }
        }

        if (matchingConfig.isMatchVlan()) {
            if (!vlan.equals(VlanVid.ZERO)) {
                mb.setExact(MatchField.VLAN_VID, OFVlanVidMatch.ofVlanVid(vlan));
            }
        }

        // TODO Detect switch type and match to create hardware-implemented flow
        if (eth.getEtherType() == EthType.IPv4) { /* shallow check for equality is okay for EthType */
            IPv4 ip = (IPv4) eth.getPayload();
            IPv4Address srcIp = ip.getSourceAddress();
            IPv4Address dstIp = ip.getDestinationAddress();

            if (matchingConfig.isMatchIp()) {
                mb.setExact(MatchField.ETH_TYPE, EthType.IPv4);
                if (matchingConfig.isMatchIpSrc()) {
                    mb.setExact(MatchField.IPV4_SRC, srcIp);
                }
                if (matchingConfig.isMatchIpDst()) {
                    mb.setExact(MatchField.IPV4_DST, dstIp);
                }
            }

            if (matchingConfig.isMatchTransport()) {
                /*
                 * Take care of the ethertype if not included earlier,
                 * since it's a prerequisite for transport ports.
                 */
                if (!matchingConfig.isMatchIp()) {
                    mb.setExact(MatchField.ETH_TYPE, EthType.IPv4);
                }

                if (ip.getProtocol().equals(IpProtocol.TCP)) {
                    TCP tcp = (TCP) ip.getPayload();
                    mb.setExact(MatchField.IP_PROTO, IpProtocol.TCP);
                    if (matchingConfig.isMatchTransportSrc()) {
                        mb.setExact(MatchField.TCP_SRC, tcp.getSourcePort());
                    }
                    if (matchingConfig.isMatchTransportDst()) {
                        mb.setExact(MatchField.TCP_DST, tcp.getDestinationPort());
                    }
                    if (sw.getOFFactory().getVersion().compareTo(OFVersion.OF_15) >= 0){
                        if(matchingConfig.isMatchTcpFlag()){
                            mb.setExact(MatchField.TCP_FLAGS, U16.of(tcp.getFlags()));
                        }
                    }
                    else if(sw.getSwitchDescription().getHardwareDescription().toLowerCase().contains("open vswitch") && (
                            Integer.parseInt(sw.getSwitchDescription().getSoftwareDescription().toLowerCase().split("\\.")[0]) > 2  || (
                                    Integer.parseInt(sw.getSwitchDescription().getSoftwareDescription().toLowerCase().split("\\.")[0]) == 2 &&
                                            Integer.parseInt(sw.getSwitchDescription().getSoftwareDescription().toLowerCase().split("\\.")[1]) >= 1 ))
                    ){
                        if(matchingConfig.isMatchTcpFlag()){
                            mb.setExact(MatchField.OVS_TCP_FLAGS, U16.of(tcp.getFlags()));
                        }
                    }
                } else if (ip.getProtocol().equals(IpProtocol.UDP)) {
                    UDP udp = (UDP) ip.getPayload();
                    mb.setExact(MatchField.IP_PROTO, IpProtocol.UDP);
                    if (matchingConfig.isMatchTransportSrc()) {
                        mb.setExact(MatchField.UDP_SRC, udp.getSourcePort());
                    }
                    if (matchingConfig.isMatchTransportDst()) {
                        mb.setExact(MatchField.UDP_DST, udp.getDestinationPort());
                    }
                }
            }
        } else if (eth.getEtherType() == EthType.ARP) { /* shallow check for equality is okay for EthType */
            mb.setExact(MatchField.ETH_TYPE, EthType.ARP);
        } else if (eth.getEtherType() == EthType.IPv6) {
            IPv6 ip = (IPv6) eth.getPayload();
            IPv6Address srcIp = ip.getSourceAddress();
            IPv6Address dstIp = ip.getDestinationAddress();

            if (matchingConfig.isMatchIp()) {
                mb.setExact(MatchField.ETH_TYPE, EthType.IPv6);
                if (matchingConfig.isMatchIpSrc()) {
                    mb.setExact(MatchField.IPV6_SRC, srcIp);
                }
                if (matchingConfig.isMatchIpDst()) {
                    mb.setExact(MatchField.IPV6_DST, dstIp);
                }
            }

            if (matchingConfig.isMatchTransport()) {
                /*
                 * Take care of the ethertype if not included earlier,
                 * since it's a prerequisite for transport ports.
                 */
                if (!matchingConfig.isMatchIp()) {
                    mb.setExact(MatchField.ETH_TYPE, EthType.IPv6);
                }

                if (ip.getNextHeader().equals(IpProtocol.TCP)) {
                    TCP tcp = (TCP) ip.getPayload();
                    mb.setExact(MatchField.IP_PROTO, IpProtocol.TCP);
                    if (matchingConfig.isMatchTransportSrc()) {
                        mb.setExact(MatchField.TCP_SRC, tcp.getSourcePort());
                    }
                    if (matchingConfig.isMatchTransportDst()) {
                        mb.setExact(MatchField.TCP_DST, tcp.getDestinationPort());
                    }
                    if (sw.getOFFactory().getVersion().compareTo(OFVersion.OF_15) >= 0){
                        if(matchingConfig.isMatchTcpFlag()){
                            mb.setExact(MatchField.TCP_FLAGS, U16.of(tcp.getFlags()));
                        }
                    }
                    else if(
                            sw.getSwitchDescription().getHardwareDescription().toLowerCase().contains("open vswitch") && (
                                    Integer.parseInt(sw.getSwitchDescription().getSoftwareDescription().toLowerCase().split("\\.")[0]) > 2  || (
                                            Integer.parseInt(sw.getSwitchDescription().getSoftwareDescription().toLowerCase().split("\\.")[0]) == 2 &&
                                                    Integer.parseInt(sw.getSwitchDescription().getSoftwareDescription().toLowerCase().split("\\.")[1]) >= 1 ))
                    ){
                        if(matchingConfig.isMatchTcpFlag()){
                            mb.setExact(MatchField.OVS_TCP_FLAGS, U16.of(tcp.getFlags()));
                        }
                    }
                } else if (ip.getNextHeader().equals(IpProtocol.UDP)) {
                    UDP udp = (UDP) ip.getPayload();
                    mb.setExact(MatchField.IP_PROTO, IpProtocol.UDP);
                    if (matchingConfig.isMatchTransportSrc()) {
                        mb.setExact(MatchField.UDP_SRC, udp.getSourcePort());
                    }
                    if (matchingConfig.isMatchTransportDst()) {
                        mb.setExact(MatchField.UDP_DST, udp.getDestinationPort());
                    }
                }
            }
        }
        return mb.build();
    }
}
