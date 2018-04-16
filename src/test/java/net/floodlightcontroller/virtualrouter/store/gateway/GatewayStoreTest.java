package net.floodlightcontroller.virtualrouter.store.gateway;

import org.junit.Test;
import org.projectfloodlight.openflow.types.MacAddress;

import static org.junit.Assert.*;

public class GatewayStoreTest {

    @Test
    public void macAddressTest() {
        MacAddress mac = MacAddress.of("08:00:27:eb:3d:ce");
        MacAddress mac2 = MacAddress.of("08:00:27:99:00:34");
    }
}