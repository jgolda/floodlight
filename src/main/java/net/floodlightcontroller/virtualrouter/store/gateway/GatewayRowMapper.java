package net.floodlightcontroller.virtualrouter.store.gateway;

import net.floodlightcontroller.storage.IResultSet;
import net.floodlightcontroller.storage.RowMapper;
import net.floodlightcontroller.virtualrouter.Gateway;

public class GatewayRowMapper implements RowMapper<Gateway> {

    @Override
    public Gateway mapRow(IResultSet resultSet) {
        return Gateway.builder()
                .setId(resultSet.getLong(GatewayColumns.ID))
                .setSwitchId(resultSet.getString(GatewayColumns.SWITCH_ID))
                .setDevicePortId(resultSet.getInt(GatewayColumns.DEVICE_PORT_ID))
                .setForwardingPort(resultSet.getInt(GatewayColumns.FORWARDING_PORT_ID))
                .setIpAddress(resultSet.getString(GatewayColumns.GATEWAY_IP_ADDRESS))
                .setNetworkAddress(resultSet.getString(GatewayColumns.GATEWAY_NETWORK_ADDRESS))
                .setMacAddress(resultSet.getString(GatewayColumns.GATEWAY_MAC_ADDRESS))
                .build();
    }
}
