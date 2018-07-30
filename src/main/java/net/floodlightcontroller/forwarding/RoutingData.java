package net.floodlightcontroller.forwarding;

import org.projectfloodlight.openflow.types.MacAddress;
import org.projectfloodlight.openflow.types.OFPort;

public class RoutingData {

    private MacAddress outputMac;
    private MacAddress targetMac;

    private OFPort outputPort;

    private final boolean routedRequest;

    public static RoutingDataBuilder builder() {
        return new RoutingDataBuilder();
    }

    private RoutingData(RoutingDataBuilder routingDataBuilder) {
        this.outputMac = routingDataBuilder.outputMac;
        this.targetMac = routingDataBuilder.targetMac;
        this.outputPort = routingDataBuilder.outputPort;
        this.routedRequest = routingDataBuilder.routedRequest;
    }

    public MacAddress getOutputMac() {
        return outputMac;
    }

    public MacAddress getTargetMac() {
        return targetMac;
    }

    public OFPort getOutputPort() {
        return outputPort;
    }

    public boolean isRoutedRequest() {
        return routedRequest;
    }

    public static class RoutingDataBuilder {
        private MacAddress outputMac;
        private MacAddress targetMac;
        private OFPort outputPort;
        private boolean routedRequest;

        private RoutingDataBuilder() {
        }

        public RoutingDataBuilder setOutputMac(MacAddress outputMac) {
            this.outputMac = outputMac;
            return this;
        }

        public RoutingDataBuilder setTargetMac(MacAddress targetMac) {
            this.targetMac = targetMac;
            return this;
        }

        public RoutingDataBuilder setOutputPort(OFPort outputPort) {
            this.outputPort = outputPort;
            return this;
        }

        public RoutingDataBuilder routedRequest() {
            this.routedRequest = true;
            return this;
        }

        public RoutingData build() {
            return new RoutingData(this);
        }
    }
}
