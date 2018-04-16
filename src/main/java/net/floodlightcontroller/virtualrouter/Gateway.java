package net.floodlightcontroller.virtualrouter;

import net.floodlightcontroller.devicemanager.internal.Entity;
import org.apache.commons.lang.StringUtils;
import org.projectfloodlight.openflow.types.*;

import java.util.Date;
import java.util.HashMap;
import java.util.Map;

import static net.floodlightcontroller.virtualrouter.store.gateway.GatewayColumns.*;

public class Gateway {

    private Long id;

    private DatapathId switchId;

    private final OFPort forwardingPort;

    private OFPort devicePort;

    private IPv4Address ipAddress;

    private IPv4AddressWithMask networkAddress;

    private MacAddress macAddress;

    private Gateway(GatewayBuilder builder) {
        this.id = builder.id;
        this.switchId = DatapathId.of(builder.switchId);
        this.devicePort = OFPort.of(builder.devicePort);
        this.forwardingPort = OFPort.of(builder.forwardingPort);
        this.ipAddress = IPv4Address.of(builder.ipAddress);
        this.networkAddress = buildNetworkAddress(builder);
        this.macAddress = MacAddress.of(builder.macAddress);
    }

    private IPv4AddressWithMask buildNetworkAddress(GatewayBuilder builder) {
        if (StringUtils.isNotEmpty(builder.networkAddress)) {
            return IPv4AddressWithMask.of(builder.networkAddress);
        } else {
            return IPv4AddressWithMask.of(ipAddress, IPv4Address.of(builder.mask));
        }
    }

    public static GatewayBuilder builder() {
        return new GatewayBuilder();
    }

    public Entity toEntity() {
        return new Entity(macAddress,
                VlanVid.ZERO,
                ipAddress,
                IPv6Address.NONE,
                switchId,
                devicePort,
                new Date(),
                true);
    }

    public Long getId() {
        return id;
    }

    public DatapathId getSwitchId() {
        return switchId;
    }

    public OFPort getDevicePort() {
        return devicePort;
    }

    public OFPort getForwardingPort() {
        return forwardingPort;
    }

    public IPv4Address getIpAddress() {
        return ipAddress;
    }

    public IPv4AddressWithMask getNetworkAddress() {
        return networkAddress;
    }

    public MacAddress getMacAddress() {
        return macAddress;
    }

    public Map<String, Object> toRecord() {
        HashMap<String, Object> result = new HashMap<>();
        result.put(SWITCH_ID, getSwitchId());
        result.put(DEVICE_PORT_ID, getDevicePort());
        result.put(FORWARDING_PORT_ID, getForwardingPort());
        result.put(GATEWAY_IP_ADDRESS, getIpAddress().toString());
        result.put(GATEWAY_NETWORK_ADDRESS, getNetworkAddress().toString());
        result.put(GATEWAY_MAC_ADDRESS, getMacAddress().toString());
        return result;
    }

    public static class GatewayBuilder {
        private Long id;
        private String switchId;
        private Integer devicePort;
        private String ipAddress;
        private String networkAddress;
        private String mask;
        private String macAddress;
        private Integer forwardingPort;

        private GatewayBuilder() {
        }

        public GatewayBuilder setId(Long id) {
            this.id = id;
            return this;
        }

        public GatewayBuilder setSwitchId(String switchId) {
            this.switchId = switchId;
            return this;
        }

        public GatewayBuilder setDevicePort(OFPort devicePort) {
            this.devicePort = devicePort.getPortNumber();
            return this;
        }

        public GatewayBuilder setDevicePortId(Integer portId) {
            this.devicePort = portId;
            return this;
        }

        public GatewayBuilder setForwardingPort(OFPort forwardingPort) {
            this.forwardingPort = forwardingPort.getPortNumber();
            return this;
        }

        public GatewayBuilder setForwardingPort(Integer portId) {
            this.forwardingPort = portId;
            return this;
        }

        public GatewayBuilder setIpAddress(String ipAddress) {
            this.ipAddress = ipAddress;
            return this;
        }

        public GatewayBuilder setNetMask(String netMask) {
            this.mask = netMask;
            return this;
        }

        public GatewayBuilder setNetworkAddress(String networkAddress) {
            this.networkAddress = networkAddress;
            return this;
        }

        public GatewayBuilder setMacAddress(String macAddress) {
            this.macAddress = macAddress;
            return this;
        }

        public Gateway build() {
            return new Gateway(this);
        }
    }
}
