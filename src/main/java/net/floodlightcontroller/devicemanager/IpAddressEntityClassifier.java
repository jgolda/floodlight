package net.floodlightcontroller.devicemanager;

import net.floodlightcontroller.devicemanager.IDeviceService.DeviceField;
import net.floodlightcontroller.devicemanager.internal.DefaultEntityClassifier;

import java.util.EnumSet;

public class IpAddressEntityClassifier extends DefaultEntityClassifier {

    @Override
    public EnumSet<DeviceField> getKeyFields() {
        return EnumSet.of(DeviceField.IPv4);
    }
}
