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

import javax.management.*;
import java.lang.management.ManagementFactory;
import java.util.*;

public class RouteStore implements RouteStoreService, IFloodlightModule, RouteStoreMBean {

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
        registerMBeans();
    }

    private void registerMBeans() {
        try {
            MBeanServer server = ManagementFactory.getPlatformMBeanServer();
            ObjectName name = new ObjectName("floodlight.route.value:type=RouteStore");
            StandardMBean mbean = new StandardMBean(this, RouteStoreMBean.class);
            server.registerMBean(mbean, name);
        } catch ( MalformedObjectNameException | NotCompliantMBeanException | MBeanRegistrationException | InstanceAlreadyExistsException e ) {
            e.printStackTrace();
        }
    }

    @Override
    public void startUp(FloodlightModuleContext context) throws FloodlightModuleException {
        pushDefaultRoutingRules();
    }

    @Override
    public Optional<RoutingRule> findRule(DatapathId switchId, IPv4AddressWithMask targetNetworkAddress) {
        return dummyRules.getOrDefault(switchId, Collections.emptySet()).stream()
                .filter(rule -> rule.getNetworkAddress().equals(targetNetworkAddress))
                .findFirst();
    }

    @Override
    public String changeRouting(String routingOption) {
        if ("default".equals(routingOption)) {
            dummyRules.clear();
            pushDefaultRoutingRules();
        } else if ("no150".equals(routingOption)) {
            dummyRules.clear();
            pushNo150RoutingRules();
        }
        return routingOption;
    }

    private void pushNo150RoutingRules() {
        RoutingRule from126to123 = new RoutingRule(IPv4AddressWithMask.of(IPv4Address.of("192.168.123.1"), IPv4Address.of("255.255.255.0")), IPv4Address.of("192.168.151.2"));
        RoutingRule from126to124 = new RoutingRule(IPv4AddressWithMask.of(IPv4Address.of("192.168.124.1"), IPv4Address.of("255.255.255.0")), IPv4Address.of("192.168.151.2"));
        RoutingRule from124to123 = new RoutingRule(IPv4AddressWithMask.of(IPv4Address.of("192.168.123.1"), IPv4Address.of("255.255.255.0")), IPv4Address.of("192.168.152.2"));
        RoutingRule from124to126 = new RoutingRule(IPv4AddressWithMask.of(IPv4Address.of("192.168.126.1"), IPv4Address.of("255.255.255.0")), IPv4Address.of("192.168.152.2"));
        RoutingRule from123to124 = new RoutingRule(IPv4AddressWithMask.of(IPv4Address.of("192.168.124.1"), IPv4Address.of("255.255.255.0")), IPv4Address.of("192.168.152.1"));
        RoutingRule from123to126 = new RoutingRule(IPv4AddressWithMask.of(IPv4Address.of("192.168.126.1"), IPv4Address.of("255.255.255.0")), IPv4Address.of("192.168.151.1"));

        dummyRules.put(DatapathId.of("00:00:08:00:27:1b:a2:7c"), new HashSet<>(Arrays.asList(
                from126to123,
                from126to124
        )));
        dummyRules.put(DatapathId.of("00:00:08:00:27:99:00:34"), new HashSet<>(Arrays.asList(
                from124to123,
                from124to126
        )));
        dummyRules.put(DatapathId.of("00:00:08:00:27:42:41:8d"), new HashSet<>(Arrays.asList(
                from123to124,
                from123to126
        )));
    }

    private void pushDefaultRoutingRules() {
        RoutingRule from126to123 = new RoutingRule(IPv4AddressWithMask.of(IPv4Address.of("192.168.123.1"), IPv4Address.of("255.255.255.0")), IPv4Address.of("192.168.151.2"));
        RoutingRule from126to124 = new RoutingRule(IPv4AddressWithMask.of(IPv4Address.of("192.168.124.1"), IPv4Address.of("255.255.255.0")), IPv4Address.of("192.168.150.2"));
        RoutingRule from124to123 = new RoutingRule(IPv4AddressWithMask.of(IPv4Address.of("192.168.123.1"), IPv4Address.of("255.255.255.0")), IPv4Address.of("192.168.152.2"));
        RoutingRule from124to126 = new RoutingRule(IPv4AddressWithMask.of(IPv4Address.of("192.168.126.1"), IPv4Address.of("255.255.255.0")), IPv4Address.of("192.168.150.1"));
        RoutingRule from123to124 = new RoutingRule(IPv4AddressWithMask.of(IPv4Address.of("192.168.124.1"), IPv4Address.of("255.255.255.0")), IPv4Address.of("192.168.152.1"));
        RoutingRule from123to126 = new RoutingRule(IPv4AddressWithMask.of(IPv4Address.of("192.168.126.1"), IPv4Address.of("255.255.255.0")), IPv4Address.of("192.168.151.1"));

        dummyRules.put(DatapathId.of("00:00:08:00:27:1b:a2:7c"), new HashSet<>(Arrays.asList(
                from126to123,
                from126to124
        )));
        dummyRules.put(DatapathId.of("00:00:08:00:27:99:00:34"), new HashSet<>(Arrays.asList(
                from124to123,
                from124to126
        )));
        dummyRules.put(DatapathId.of("00:00:08:00:27:42:41:8d"), new HashSet<>(Arrays.asList(
                from123to124,
                from123to126
        )));
    }
}
