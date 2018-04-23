package net.floodlightcontroller.virtualrouter.store;

import net.floodlightcontroller.core.module.FloodlightModuleContext;
import net.floodlightcontroller.core.module.FloodlightModuleException;
import net.floodlightcontroller.core.module.IFloodlightModule;
import net.floodlightcontroller.core.module.IFloodlightService;
import net.floodlightcontroller.storage.IStorageSourceService;
import net.floodlightcontroller.virtualrouter.Gateway;
import org.projectfloodlight.openflow.types.IPv4Address;
import org.projectfloodlight.openflow.types.MacAddress;
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
        return Collections.singleton(IStorageSourceService.class);
    }

    @Override
    public void init(FloodlightModuleContext context) throws FloodlightModuleException {
        storage = context.getServiceImpl(IStorageSourceService.class);
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
    }

    private void pushDummyGatewayIfNotExist() {
        final IPv4Address dummyIp = IPv4Address.of("192.168.124.1");
        final MacAddress dummyMac = MacAddress.of(98765412738123413L);

        Map<String, Object> record = Gateway.builder()
                .setSwitchId("85397242-c3f5-48ea-92e0-f6a8979458ae")
                .setPortId("eth3")
                .setIpAddress(dummyIp.toString())
                .setNetMask("255.255.255.0")
                .setMacAddress(dummyMac.toString())
                .build()
                .toRecord();

        record.put(GatewayColumns.ID, 1234L);

        storage.insertRow(GATEWAY_TABLE_NAME, record);
    }

    public boolean isGatewayIp(IPv4Address address) {
        return gatewayIpMap.containsKey(address);
    }

    @Override
    public Optional<Gateway> getGateway(IPv4Address address) {
        return Optional.ofNullable(gatewayIpMap.get(address));
    }
}
