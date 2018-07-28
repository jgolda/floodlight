package net.floodlightcontroller.core;

import org.projectfloodlight.openflow.protocol.OFPortDesc;
import org.projectfloodlight.openflow.types.DatapathId;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class DefaultOFSwitchListener implements IOFSwitchListener {

    private static final Logger logger = LoggerFactory.getLogger(DefaultOFSwitchListener.class);

    private static final String DEFAULT_IMPLEMENTATION_WARNING = "Default implementation used";

    @Override
    public void switchAdded(DatapathId switchId) {
        logger.trace(DEFAULT_IMPLEMENTATION_WARNING);
    }

    @Override
    public void switchRemoved(DatapathId switchId) {
        logger.trace(DEFAULT_IMPLEMENTATION_WARNING);
    }

    @Override
    public void switchActivated(DatapathId switchId) {
        logger.trace(DEFAULT_IMPLEMENTATION_WARNING);
    }

    @Override
    public void switchPortChanged(DatapathId switchId, OFPortDesc port, PortChangeType type) {
        logger.trace(DEFAULT_IMPLEMENTATION_WARNING);
    }

    @Override
    public void switchChanged(DatapathId switchId) {
        logger.trace(DEFAULT_IMPLEMENTATION_WARNING);
    }

    @Override
    public void switchDeactivated(DatapathId switchId) {
        logger.trace(DEFAULT_IMPLEMENTATION_WARNING);
    }
}
