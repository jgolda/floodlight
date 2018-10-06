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

        pushDummyGatewayIfNotExistTwoNetworks();

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

    private void pushDummyGatewayIfNotExistTwoNetworks() {
        final IPv4Address dummyIp124 = IPv4Address.of("192.168.127.1");
        final MacAddress dummyMac124 = MacAddress.of("08:00:27:60:f9:01");

        Map<String, Object> record124 = Gateway.builder()
                .setSwitchId("00:00:08:00:27:1b:a2:7c")
                .setDevicePort(OFPort.of(5))
                .setForwardingPort(OFPort.of(4))
                .setIpAddress(dummyIp124.toString())
                .setNetMask("255.255.255.0")
                .setMacAddress(dummyMac124.toString())
                .build()
                .toRecord();

        record124.put(GatewayColumns.ID, 124L);

        storage.insertRow(GATEWAY_TABLE_NAME, record124);

        final IPv4Address dummyIp122 = IPv4Address.of("192.168.128.1");
        final MacAddress dummyMac122 = MacAddress.of("08:00:27:1b:a2:7c");

        Map<String, Object> record122 = Gateway.builder()
                .setSwitchId("00:00:08:00:27:1b:a2:7c")
                .setDevicePort(OFPort.of(5))
                .setForwardingPort(OFPort.of(5))
                .setIpAddress(dummyIp122.toString())
                .setNetMask("255.255.255.0")
                .setMacAddress(dummyMac122.toString())
                .build()
                .toRecord();

        record122.put(GatewayColumns.ID, 122L);

        storage.insertRow(GATEWAY_TABLE_NAME, record122);
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
