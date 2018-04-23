package net.floodlightcontroller.virtualrouter.store;

import net.floodlightcontroller.core.module.IFloodlightService;
import net.floodlightcontroller.virtualrouter.Gateway;
import org.projectfloodlight.openflow.types.IPv4Address;

import java.util.Optional;

public interface GatewayStoreService extends IFloodlightService {
    Optional<Gateway> getGateway(IPv4Address address);
}
