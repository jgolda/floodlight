package net.floodlightcontroller.virtualrouter.store.gateway;

import net.floodlightcontroller.core.DefaultOFSwitchListener;
import net.floodlightcontroller.core.internal.IOFSwitchService;
import net.floodlightcontroller.core.module.FloodlightModuleContext;
import net.floodlightcontroller.core.module.FloodlightModuleException;
import net.floodlightcontroller.core.module.IFloodlightModule;
import net.floodlightcontroller.core.module.IFloodlightService;
import net.floodlightcontroller.devicemanager.IDeviceService;
import net.floodlightcontroller.storage.IStorageSourceService;
import net.floodlightcontroller.virtualrouter.Gateway;
import org.projectfloodlight.openflow.types.*;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.*;
import java.util.stream.Collectors;

public class GatewayStore implements GatewayStoreService, IFloodlightModule {

    private static final Logger logger = LoggerFactory.getLogger(GatewayStore.class);
    public static final String GATEWAY_TABLE_NAME = "virtual_gateways";

    private IStorageSourceService storage;
    private Map<DatapathId, Map<IPv4Address, Gateway>> gatewayIpMapForSwitch;
    private Map<DatapathId, Map<IPv4AddressWithMask, Gateway>> networkToGatewayMapForSwitch;
    private Map<DatapathId, Set<Gateway>> gatewaySwitchMap;
    private IDeviceService deviceService;
    private IOFSwitchService switchService;

    @Override
    public Collection<Class<? extends IFloodlightService>> getModuleServices() {
        return Collections.singleton(GatewayStoreService.class);
    }

    @Override
    public Map<Class<? extends IFloodlightService>, IFloodlightService> getServiceImpls() {
        return Collections.singletonMap(GatewayStoreService.class, this);
    }

    @Override
    public Collection<Class<? extends IFloodlightService>> getModuleDependencies() {
        return Arrays.asList(IStorageSourceService.class,
                IDeviceService.class,
                IOFSwitchService.class);
    }

    @Override
    public void init(FloodlightModuleContext context) throws FloodlightModuleException {
        storage = context.getServiceImpl(IStorageSourceService.class);
        deviceService = context.getServiceImpl(IDeviceService.class);
        switchService = context.getServiceImpl(IOFSwitchService.class);
    }

    @Override
    public void startUp(FloodlightModuleContext context) throws FloodlightModuleException {
        logger.info("Starting gateway store");

        if (!storage.getAllTableNames().contains(GATEWAY_TABLE_NAME)) {
            logger.info("Setting up storage");
            storage.createTable(GATEWAY_TABLE_NAME, Collections.emptySet());
            storage.setTablePrimaryKeyName(GATEWAY_TABLE_NAME, GatewayColumns.ID);
            logger.info("Finished setting up storage");
        }

        pushDummyGatewayIfNotExist();

        logger.info("Loading gateway cache's");
        List<Gateway> gateways = storage.executeQuery(GATEWAY_TABLE_NAME, GatewayColumns.ALL_COLUMNS, null, null, new GatewayRowMapper());

        gatewayIpMapForSwitch = gateways.stream()
                .collect(Collectors.groupingBy(Gateway::getSwitchId, Collectors.toMap(Gateway::getIpAddress, gateway -> gateway)));
        networkToGatewayMapForSwitch = gateways.stream()
                .collect(Collectors.groupingBy(Gateway::getSwitchId, Collectors.toMap(Gateway::getNetworkAddress, gateway -> gateway)));
        gatewaySwitchMap = gateways.stream()
                .collect(Collectors.groupingBy(Gateway::getSwitchId, Collectors.toSet()));
        registerGatewayDevices();
    }

    private void registerGatewayDevices() {
        switchService.addOFSwitchListener(new DefaultOFSwitchListener() {
            @Override
            public void switchAdded(DatapathId switchId) {
                gatewaySwitchMap.getOrDefault(switchId, Collections.emptySet()).stream()
                        .map(Gateway::toEntity)
                        .forEach(entity -> deviceService.registerDevice(entity));
            }
        });
    }

    private void pushDummyGatewayIfNotExist() {
        // switch 1
        final IPv4Address dummyIp126 = IPv4Address.of("192.168.126.1");
        final MacAddress dummyMac126 = MacAddress.of("08:00:27:1b:a2:7c");

        Map<String, Object> record126 = Gateway.builder()
                .setSwitchId("00:00:08:00:27:1b:a2:7c")
                .setDevicePort(OFPort.of(5))
                .setForwardingPort(OFPort.of(5))
                .setIpAddress(dummyIp126.toString())
                .setNetMask("255.255.255.0")
                .setMacAddress(dummyMac126.toString())
                .build()
                .toRecord();

        record126.put(GatewayColumns.ID, 126L);

        storage.insertRow(GATEWAY_TABLE_NAME, record126);

        final IPv4Address dummyIp1501 = IPv4Address.of("192.168.150.1");
        final MacAddress dummyMac1501 = MacAddress.of("08:00:27:45:9a:46");

        Map<String, Object> record1501 = Gateway.builder()
                .setSwitchId("00:00:08:00:27:1b:a2:7c")
                .setDevicePort(OFPort.of(5))
                .setForwardingPort(OFPort.of(6))
                .setIpAddress(dummyIp1501.toString())
                .setNetMask("255.255.255.0")
                .setMacAddress(dummyMac1501.toString())
                .build()
                .toRecord();

        record1501.put(GatewayColumns.ID, 1501L);

        storage.insertRow(GATEWAY_TABLE_NAME, record1501);

        final IPv4Address dummyIp1511 = IPv4Address.of("192.168.151.1");
        final MacAddress dummyMac1511 = MacAddress.of("08:00:27:60:f9:01");

        Map<String, Object> record1511 = Gateway.builder()
                .setSwitchId("00:00:08:00:27:1b:a2:7c")
                .setDevicePort(OFPort.of(5))
                .setForwardingPort(OFPort.of(4))
                .setIpAddress(dummyIp1511.toString())
                .setNetMask("255.255.255.0")
                .setMacAddress(dummyMac1511.toString())
                .build()
                .toRecord();

        record1511.put(GatewayColumns.ID, 1511L);

        storage.insertRow(GATEWAY_TABLE_NAME, record1511);


        // switch 2

        final IPv4Address dummyIp124 = IPv4Address.of("192.168.124.1");
        final MacAddress dummyMac124 = MacAddress.of("08:00:27:eb:3d:ce");

        Map<String, Object> record124 = Gateway.builder()
                .setSwitchId("00:00:08:00:27:99:00:34")
                .setDevicePort(OFPort.of(3))
                .setForwardingPort(OFPort.of(3))
                .setIpAddress(dummyIp124.toString())
                .setNetMask("255.255.255.0")
                .setMacAddress(dummyMac124.toString())
                .build()
                .toRecord();

        record124.put(GatewayColumns.ID, 124L);

        storage.insertRow(GATEWAY_TABLE_NAME, record124);

        final IPv4Address dummyIp1502 = IPv4Address.of("192.168.150.2");
        final MacAddress dummyMac1502 = MacAddress.of("08:00:27:99:00:34");

        Map<String, Object> record1502 = Gateway.builder()
                .setSwitchId("00:00:08:00:27:99:00:34")
                .setDevicePort(OFPort.of(3))
                .setForwardingPort(OFPort.of(2))
                .setIpAddress(dummyIp1502.toString())
                .setNetMask("255.255.255.0")
                .setMacAddress(dummyMac1502.toString())
                .build()
                .toRecord();

        record1502.put(GatewayColumns.ID, 1502L);

        storage.insertRow(GATEWAY_TABLE_NAME, record1502);

        final IPv4Address dummyIp1521 = IPv4Address.of("192.168.152.1");
        final MacAddress dummyMac1521 = MacAddress.of("08:00:27:ae:c7:8a");

        Map<String, Object> record1521 = Gateway.builder()
                .setSwitchId("00:00:08:00:27:99:00:34")
                .setDevicePort(OFPort.of(3))
                .setForwardingPort(OFPort.of(1))
                .setIpAddress(dummyIp1521.toString())
                .setNetMask("255.255.255.0")
                .setMacAddress(dummyMac1521.toString())
                .build()
                .toRecord();

        record1521.put(GatewayColumns.ID, 1521L);

        storage.insertRow(GATEWAY_TABLE_NAME, record1521);


        // switch 3

        final IPv4Address dummyIp1231 = IPv4Address.of("192.168.123.1");
        final MacAddress dummyMac1231 = MacAddress.of("08:00:27:68:05:8f");

        Map<String, Object> record1231 = Gateway.builder()
                .setSwitchId("00:00:08:00:27:42:41:8d")
                .setDevicePort(OFPort.of(3))
                .setForwardingPort(OFPort.of(3))
                .setIpAddress(dummyIp1231.toString())
                .setNetMask("255.255.255.0")
                .setMacAddress(dummyMac1231.toString())
                .build()
                .toRecord();

        record1231.put(GatewayColumns.ID, 1231L);

        storage.insertRow(GATEWAY_TABLE_NAME, record1231);

        final IPv4Address dummyIp1512 = IPv4Address.of("192.168.151.2");
        final MacAddress dummyMac1512 = MacAddress.of("08:00:27:42:41:8d");

        Map<String, Object> record1512 = Gateway.builder()
                .setSwitchId("00:00:08:00:27:42:41:8d")
                .setDevicePort(OFPort.of(3))
                .setForwardingPort(OFPort.of(2))
                .setIpAddress(dummyIp1512.toString())
                .setNetMask("255.255.255.0")
                .setMacAddress(dummyMac1512.toString())
                .build()
                .toRecord();

        record1512.put(GatewayColumns.ID, 1512L);

        storage.insertRow(GATEWAY_TABLE_NAME, record1512);

        final IPv4Address dummyIp1522 = IPv4Address.of("192.168.152.2");
        final MacAddress dummyMac1522 = MacAddress.of("08:00:27:49:ac:50");

        Map<String, Object> record1522 = Gateway.builder()
                .setSwitchId("00:00:08:00:27:42:41:8d")
                .setDevicePort(OFPort.of(3))
                .setForwardingPort(OFPort.of(1))
                .setIpAddress(dummyIp1522.toString())
                .setNetMask("255.255.255.0")
                .setMacAddress(dummyMac1522.toString())
                .build()
                .toRecord();

        record1522.put(GatewayColumns.ID, 1522L);

        storage.insertRow(GATEWAY_TABLE_NAME, record1522);
    }

    @Override
    public Optional<Gateway> getGateway(IPv4Address address, DatapathId switchId) {
        return Optional.ofNullable(gatewayIpMapForSwitch.getOrDefault(switchId, Collections.emptyMap()).get(address));
    }

    @Override
    public Optional<Gateway> getGateway(IPv4AddressWithMask networkAddress, DatapathId switchId) {
        return Optional.ofNullable(networkToGatewayMapForSwitch.getOrDefault(switchId, Collections.emptyMap()).get(networkAddress));
    }
}
