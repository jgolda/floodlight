package net.floodlightcontroller.virtualrouter.store.route;

import org.projectfloodlight.openflow.types.IPv4Address;
import org.projectfloodlight.openflow.types.IPv4AddressWithMask;

public class RoutingRule {

    private IPv4AddressWithMask networkAddress;
    private IPv4Address targetDeviceAddress;

    public RoutingRule(IPv4AddressWithMask networkAddress, IPv4Address targetDeviceAddress) {
        this.networkAddress = networkAddress;
        this.targetDeviceAddress = targetDeviceAddress;
    }

    public IPv4AddressWithMask getNetworkAddress() {
        return networkAddress;
    }

    public IPv4Address getTargetDeviceAddress() {
        return targetDeviceAddress;
    }
}
