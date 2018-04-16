package net.floodlightcontroller.virtualrouter.store;

import net.floodlightcontroller.core.module.FloodlightModuleContext;
import net.floodlightcontroller.core.module.FloodlightModuleException;
import net.floodlightcontroller.core.module.IFloodlightModule;
import net.floodlightcontroller.core.module.IFloodlightService;
import net.floodlightcontroller.storage.IStorageSourceService;
import net.floodlightcontroller.virtualrouter.store.route.RoutingRule;
import org.projectfloodlight.openflow.types.DatapathId;
import org.projectfloodlight.openflow.types.IPv4Address;
import org.projectfloodlight.openflow.types.IPv4AddressWithMask;

import java.util.*;

public class RouteStore implements RouteStoreService, IFloodlightModule {

    private IStorageSourceService storage;

    private Map<DatapathId, Set<RoutingRule>> dummyRules = new HashMap<>();

    @Override
    public Collection<Class<? extends IFloodlightService>> getModuleServices() {
        return Collections.singleton(RouteStoreService.class);
    }

    @Override
    public Map<Class<? extends IFloodlightService>, IFloodlightService> getServiceImpls() {
        return Collections.singletonMap(RouteStoreService.class, this);
    }

    @Override
    public Collection<Class<? extends IFloodlightService>> getModuleDependencies() {
        return Arrays.asList(
                IStorageSourceService.class
        );
    }

    @Override
    public void init(FloodlightModuleContext context) throws FloodlightModuleException {
        storage = context.getServiceImpl(IStorageSourceService.class);
    }

    @Override
    public void startUp(FloodlightModuleContext context) throws FloodlightModuleException {
        RoutingRule to124 = new RoutingRule(IPv4AddressWithMask.of(IPv4Address.of("192.168.126.1"), IPv4Address.of("255.255.255.0")), IPv4Address.of("192.168.125.2"));
        RoutingRule to126 = new RoutingRule(IPv4AddressWithMask.of(IPv4Address.of("192.168.124.1"), IPv4Address.of("255.255.255.0")), IPv4Address.of("192.168.125.1"));

        dummyRules.put(DatapathId.of("00:00:08:00:27:99:00:34"), Collections.singleton(to124));
        dummyRules.put(DatapathId.of("00:00:08:00:27:1b:a2:7c"), Collections.singleton(to126));
    }

    @Override
    public Optional<RoutingRule> findRule(DatapathId switchId, IPv4AddressWithMask targetNetworkAddress) {
        return dummyRules.getOrDefault(switchId, Collections.emptySet()).stream()
                .filter(rule -> rule.getNetworkAddress().equals(targetNetworkAddress))
                .findFirst();
    }
}
