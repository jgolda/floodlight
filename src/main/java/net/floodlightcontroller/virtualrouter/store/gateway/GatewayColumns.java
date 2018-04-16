package net.floodlightcontroller.virtualrouter.store.gateway;

import java.util.Arrays;
import java.util.Collections;
import java.util.HashSet;
import java.util.Set;

public class GatewayColumns {

    public static final String ID = "id";
    public static final String SWITCH_ID = "switch_id";
    public static final String DEVICE_PORT_ID = "device_port_id";
    public static final String FORWARDING_PORT_ID = "forwarding_port_id";
    public static final String GATEWAY_IP_ADDRESS = "gateway_ip_address";
    public static final String GATEWAY_NETWORK_ADDRESS = "gateway_network_address";
    public static final String GATEWAY_MAC_ADDRESS = "mac_address";

    public static final String[] ALL_COLUMNS = { ID, SWITCH_ID, DEVICE_PORT_ID, GATEWAY_IP_ADDRESS, GATEWAY_MAC_ADDRESS };
    public static final Set<String> ALL_COLUMNS_SET = Collections.unmodifiableSet(new HashSet<>(Arrays.asList(ALL_COLUMNS)));
}
