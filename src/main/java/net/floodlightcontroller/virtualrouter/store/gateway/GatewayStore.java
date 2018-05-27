package net.floodlightcontroller.virtualrouter.store.gateway;

import net.floodlightcontroller.core.module.FloodlightModuleContext;
import net.floodlightcontroller.core.module.FloodlightModuleException;
import net.floodlightcontroller.core.module.IFloodlightModule;
import net.floodlightcontroller.core.module.IFloodlightService;
import net.floodlightcontroller.devicemanager.IDeviceService;
import net.floodlightcontroller.devicemanager.internal.Entity;
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
    private Map<IPv4Address, Gateway> gatewayIpMap;
    private Map<MacAddress, Gateway> gatewayMacMap;
    private Map<DatapathId, Set<Gateway>> gatewaySwitchMap;
    private IDeviceService deviceService;

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
                IDeviceService.class);
    }

    @Override
    public void init(FloodlightModuleContext context) throws FloodlightModuleException {
        storage = context.getServiceImpl(IStorageSourceService.class);
        deviceService = context.getServiceImpl(IDeviceService.class);
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

        gatewayIpMap = gateways.stream()
                .collect(Collectors.toMap(Gateway::getIpAddress, gateway -> gateway));
        gatewayMacMap = gateways.stream()
                .collect(Collectors.toMap(Gateway::getMacAddress, gateway -> gateway));

        gatewaySwitchMap = gateways.stream()
                .collect(Collectors.groupingBy(Gateway::getSwitchId, Collectors.toSet()));
    }

    private void pushDummyGatewayIfNotExist() {
        final IPv4Address dummyIp124 = IPv4Address.of("192.168.124.1");
        final MacAddress dummyMac124 = MacAddress.of("08:00:27:eb:3d:ce");
        Entity dummyEntity124 = new Entity(dummyMac124,
                VlanVid.ZERO,
                dummyIp124,
                IPv6Address.NONE,
                DatapathId.of("00:00:08:00:27:99:00:34"),
                OFPort.of(3),
                new Date(),
                true);

        deviceService.registerDevice(dummyEntity124);

        Map<String, Object> record124 = Gateway.builder()
                .setSwitchId("00:00:08:00:27:99:00:34")
                .setPortId("eth3")
                .setIpAddress(dummyIp124.toString())
                .setNetMask("255.255.255.0")
                .setMacAddress(dummyMac124.toString())
                .build()
                .toRecord();

        record124.put(GatewayColumns.ID, 124L);

        storage.insertRow(GATEWAY_TABLE_NAME, record124);

        final IPv4Address dummyIp122 = IPv4Address.of("192.168.122.1");
        final MacAddress dummyMac122 = MacAddress.of("08:00:27:99:00:34");

        Map<String, Object> record122 = Gateway.builder()
                .setSwitchId("00:00:08:00:27:99:00:34")
                .setPortId("eth2")
                .setIpAddress(dummyIp122.toString())
                .setNetMask("255.255.255.0")
                .setMacAddress(dummyMac122.toString())
                .build()
                .toRecord();

        Entity dummyEntity122 = new Entity(dummyMac122,
                VlanVid.ZERO,
                dummyIp122,
                IPv6Address.NONE,
                DatapathId.of("00:00:08:00:27:99:00:34"),
                OFPort.of(3),
                new Date(),
                true);

        deviceService.registerDevice(dummyEntity122);

        record122.put(GatewayColumns.ID, 122L);

        storage.insertRow(GATEWAY_TABLE_NAME, record122);
    }

    public boolean isGatewayIp(IPv4Address address) {
        return gatewayIpMap.containsKey(address);
    }

    @Override
    public Optional<Gateway> getGateway(IPv4Address address, DatapathId switchId) {
        return Optional.ofNullable(gatewayIpMap.get(address));
    }

    @Override
    public boolean existGateway(DatapathId switchId) {
        return gatewaySwitchMap.containsKey(switchId) && ! gatewaySwitchMap.get(switchId).isEmpty();
    }
}
