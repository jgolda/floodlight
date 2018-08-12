package net.floodlightcontroller.virtualrouter.store;

import net.floodlightcontroller.core.module.IFloodlightService;
import net.floodlightcontroller.virtualrouter.store.route.RoutingRule;
import org.projectfloodlight.openflow.types.DatapathId;
import org.projectfloodlight.openflow.types.IPv4AddressWithMask;

import java.util.Optional;

public interface RouteStoreService extends IFloodlightService {
    Optional<RoutingRule> findRule(DatapathId switchId, IPv4AddressWithMask targetNetworkAddress);
}
