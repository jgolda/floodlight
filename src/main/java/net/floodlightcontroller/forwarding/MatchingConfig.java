package net.floodlightcontroller.forwarding;

public class MatchingConfig {
    private boolean matchInPort;
    private boolean matchVlan;
    private boolean matchMac;
    private boolean matchIp;
    private boolean matchTransport;
    private boolean matchMacSrc;
    private boolean matchMacDst;
    private boolean matchIpSrc;
    private boolean matchIpDst;
    private boolean matchTransportSrc;
    private boolean matchTransportDst;
    private boolean matchTcpFlag;

    public static ConfigBuilder builder() {
        return new ConfigBuilder();
    }

    private MatchingConfig(ConfigBuilder builder) {
        this.matchInPort = builder.matchInPort;
        this.matchVlan = builder.matchVlan;
        this.matchMac = builder.matchMac;
        this.matchIp = builder.matchIp;
        this.matchTransport = builder.matchTransport;
        this.matchMacSrc = builder.matchMacSrc;
        this.matchMacDst = builder.matchMacDst;
        this.matchIpSrc = builder.matchIpSrc;
        this.matchIpDst = builder.matchIpDst;
        this.matchTransportSrc = builder.matchTransportSrc;
        this.matchTransportDst = builder.matchTransportDst;
        this.matchTcpFlag = builder.matchTcpFlag;
    }

    public boolean isMatchInPort() {
        return matchInPort;
    }

    public boolean isMatchVlan() {
        return matchVlan;
    }

    public boolean isMatchMac() {
        return matchMac;
    }

    public boolean isMatchIp() {
        return matchIp;
    }

    public boolean isMatchTransport() {
        return matchTransport;
    }

    public boolean isMatchMacSrc() {
        return matchMacSrc;
    }

    public boolean isMatchMacDst() {
        return matchMacDst;
    }

    public boolean isMatchIpSrc() {
        return matchIpSrc;
    }

    public boolean isMatchIpDst() {
        return matchIpDst;
    }

    public boolean isMatchTransportSrc() {
        return matchTransportSrc;
    }

    public boolean isMatchTransportDst() {
        return matchTransportDst;
    }

    public boolean isMatchTcpFlag() {
        return matchTcpFlag;
    }

    public static class ConfigBuilder {
        private boolean matchInPort = true;
        private boolean matchVlan = true;
        private boolean matchMac = true;
        private boolean matchIp = true;
        private boolean matchTransport = true;
        private boolean matchMacSrc = true;
        private boolean matchMacDst = true;
        private boolean matchIpSrc = true;
        private boolean matchIpDst = true;
        private boolean matchTransportSrc = true;
        private boolean matchTransportDst = true;
        private boolean matchTcpFlag = true;

        private ConfigBuilder() {
        }

        public ConfigBuilder setMatchInPort(boolean matchInPort) {
            this.matchInPort = matchInPort;
            return this;
        }

        public ConfigBuilder setMatchVlan(boolean matchVlan) {
            this.matchVlan = matchVlan;
            return this;
        }

        public ConfigBuilder setMatchMac(boolean matchMac) {
            this.matchMac = matchMac;
            return this;
        }

        public ConfigBuilder setMatchIp(boolean matchIp) {
            this.matchIp = matchIp;
            return this;
        }

        public ConfigBuilder setMatchTransport(boolean matchTransport) {
            this.matchTransport = matchTransport;
            return this;
        }

        public ConfigBuilder setMatchMacSrc(boolean matchMacSrc) {
            this.matchMacSrc = matchMacSrc;
            return this;
        }

        public ConfigBuilder setMatchMacDst(boolean matchMacDst) {
            this.matchMacDst = matchMacDst;
            return this;
        }

        public ConfigBuilder setMatchIpSrc(boolean matchIpSrc) {
            this.matchIpSrc = matchIpSrc;
            return this;
        }

        public ConfigBuilder setMatchIpDst(boolean matchIpDst) {
            this.matchIpDst = matchIpDst;
            return this;
        }

        public ConfigBuilder setMatchTransportSrc(boolean matchTransportSrc) {
            this.matchTransportSrc = matchTransportSrc;
            return this;
        }

        public ConfigBuilder setMatchTransportDst(boolean matchTransportDst) {
            this.matchTransportDst = matchTransportDst;
            return this;
        }

        public ConfigBuilder setMatchTcpFlag(boolean matchTcpFlag) {
            this.matchTcpFlag = matchTcpFlag;
            return this;
        }

        public MatchingConfig build() {
            return new MatchingConfig(this);
        }
    }
}
