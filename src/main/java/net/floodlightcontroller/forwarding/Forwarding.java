/**
 *    Copyright 2011, Big Switch Networks, Inc.
 *    Originally created by David Erickson, Stanford University
 *
 *    Licensed under the Apache License, Version 2.0 (the "License"); you may
 *    not use this file except in compliance with the License. You may obtain
 *    a copy of the License at
 *
 *         http://www.apache.org/licenses/LICENSE-2.0
 *
 *    Unless required by applicable law or agreed to in writing, software
 *    distributed under the License is distributed on an "AS IS" BASIS, WITHOUT
 *    WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied. See the
 *    License for the specific language governing permissions and limitations
 *    under the License.
 **/

package net.floodlightcontroller.forwarding;

import java.util.*;
import java.util.Map.Entry;
import java.util.concurrent.ConcurrentHashMap;

import net.floodlightcontroller.core.*;
import net.floodlightcontroller.core.internal.IOFSwitchService;
import net.floodlightcontroller.core.module.FloodlightModuleContext;
import net.floodlightcontroller.core.module.FloodlightModuleException;
import net.floodlightcontroller.core.module.IFloodlightModule;
import net.floodlightcontroller.core.module.IFloodlightService;
import net.floodlightcontroller.core.types.NodePortTuple;
import net.floodlightcontroller.core.util.AppCookie;
import net.floodlightcontroller.debugcounter.IDebugCounterService;
import net.floodlightcontroller.devicemanager.IDevice;
import net.floodlightcontroller.devicemanager.IDeviceService;
import net.floodlightcontroller.devicemanager.SwitchPort;
import net.floodlightcontroller.linkdiscovery.ILinkDiscoveryListener;
import net.floodlightcontroller.linkdiscovery.ILinkDiscoveryService;
import net.floodlightcontroller.packet.*;
import net.floodlightcontroller.routing.*;
import net.floodlightcontroller.topology.ITopologyService;
import net.floodlightcontroller.util.*;

import net.floodlightcontroller.virtualrouter.Gateway;
import net.floodlightcontroller.virtualrouter.store.gateway.GatewayStoreService;
import org.projectfloodlight.openflow.protocol.*;
import org.projectfloodlight.openflow.protocol.action.OFAction;
import org.projectfloodlight.openflow.protocol.action.OFActionOutput;
import org.projectfloodlight.openflow.protocol.match.Match;
import org.projectfloodlight.openflow.protocol.match.MatchField;
import org.projectfloodlight.openflow.types.*;
import org.python.google.common.collect.ImmutableList;
import org.python.google.common.collect.Maps;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class Forwarding extends DefaultOFSwitchListener implements IFloodlightModule, ILinkDiscoveryListener, IRoutingDecisionChangedListener, IOFMessageListener {
    protected static final Logger log = LoggerFactory.getLogger(Forwarding.class);

    /*
     * Cookies are 64 bits:
     * Example: 0x0123456789ABCDEF
     * App ID:  0xFFF0000000000000
     * User:    0x000FFFFFFFFFFFFF
     * 
     * Of the user portion, we further subdivide into routing decision 
     * bits and flowset bits. The former relates the flow to routing
     * decisions, such as firewall allow or deny/drop. It allows for 
     * modification of the flows upon a future change in the routing 
     * decision. The latter indicates a "family" of flows or "flowset" 
     * used to complete an end-to-end connection between two devices
     * or hosts in the network. It is used to assist in the entire
     * flowset removal upon a link or port down event anywhere along
     * the path. This is required in order to allow a new path to be
     * used and a new flowset installed.
     * 
     * TODO: shrink these masks if you need to add more subfields
     * or need to allow for a larger number of routing decisions
     * or flowsets
     */

    private static final short DECISION_BITS = 24;
    private static final short DECISION_SHIFT = 0;
    private static final long DECISION_MASK = ((1L << DECISION_BITS) - 1) << DECISION_SHIFT;

    private static final short FLOWSET_BITS = 28;
    protected static final short FLOWSET_SHIFT = DECISION_BITS;
    private static final long FLOWSET_MASK = ((1L << FLOWSET_BITS) - 1) << FLOWSET_SHIFT;
    private static final long FLOWSET_MAX = (long) (Math.pow(2, FLOWSET_BITS) - 1);
    protected static FlowSetIdRegistry flowSetIdRegistry;

    public static int FLOWMOD_DEFAULT_IDLE_TIMEOUT = 5; // in seconds
    public static int FLOWMOD_DEFAULT_HARD_TIMEOUT = 0; // infinite
    public static int FLOWMOD_DEFAULT_PRIORITY = 1; // 0 is the default table-miss flow in OF1.3+, so we need to use 1

    protected static TableId FLOWMOD_DEFAULT_TABLE_ID = TableId.ZERO;

    protected static boolean FLOWMOD_DEFAULT_SET_SEND_FLOW_REM_FLAG = false;

    protected static boolean FLOOD_ALL_ARP_PACKETS = false;

    protected static boolean REMOVE_FLOWS_ON_LINK_OR_PORT_DOWN = true;

    protected IFloodlightProviderService floodlightProviderService;
    protected IOFSwitchService switchService;
    protected IDeviceService deviceManagerService;
    protected IRoutingService routingEngineService;
    protected ITopologyService topologyService;
    protected IDebugCounterService debugCounterService;
    protected ILinkDiscoveryService linkService;
    private GatewayStoreService gatewayStore;

    // flow-mod - for use in the cookie
    public static final int FORWARDING_APP_ID = 2;
    static {
        AppCookie.registerApp(FORWARDING_APP_ID, "forwarding");
    }
    protected static final U64 DEFAULT_FORWARDING_COOKIE = AppCookie.makeCookie(FORWARDING_APP_ID, 0);

    protected OFMessageDamper messageDamper;
    private static int OFMESSAGE_DAMPER_CAPACITY = 10000;
    private static int OFMESSAGE_DAMPER_TIMEOUT = 250; // ms
    private PacketMatcher packetMatcher;
    private MatchingConfig matchingConfig;

    @Override
    public String getName() {
        return "forwarding";
    }

    @Override
    public Command receive(IOFSwitch sw, OFMessage msg, FloodlightContext cntx) {
        switch (msg.getType()) {
            case PACKET_IN:
                IRoutingDecision decision = null;
                if (cntx != null) {
                    decision = RoutingDecision.rtStore.get(cntx, IRoutingDecision.CONTEXT_DECISION);
                }

                return this.processPacketInMessage(sw, (OFPacketIn) msg, decision, cntx);
            default:
                break;
        }
        return Command.CONTINUE;
    }

    /**
     * Push routes from back to front
     * @param route Route to push
     * @param match OpenFlow fields to match on
     * @param cookie The cookie to set in each flow_mod
     * @param cntx The floodlight context
     * @param requestFlowRemovedNotification if set to true then the switch would
     *        send a flow mod removal notification when the flow mod expires
     * @param flowModCommand flow mod. command to use, e.g. OFFlowMod.OFPFC_ADD,
     *        OFFlowMod.OFPFC_MODIFY etc.
     * @param build
     * @return true if a packet out was sent on the first-hop switch of this route
     */
    public boolean pushRoute(Path route, Match match, OFPacketIn pi,
                             DatapathId pinSwitch, U64 cookie, FloodlightContext cntx,
                             boolean requestFlowRemovedNotification, OFFlowModCommand flowModCommand, RoutingData routingData) {

        boolean packetOutSent = false;

        List<NodePortTuple> switchPortList = route.getPath();

        for (int indx = switchPortList.size() - 1; indx > 0; indx -= 2) {
            // indx and indx-1 will always have the same switch DPID.
            DatapathId switchDPID = switchPortList.get(indx).getNodeId();
            IOFSwitch sw = switchService.getSwitch(switchDPID);

            if (sw == null) {
                if (log.isWarnEnabled()) {
                    log.warn("Unable to push route, switch at DPID {} " + "not available", switchDPID);
                }
                return packetOutSent;
            }

            // need to build flow mod based on what type it is. Cannot set command later
            OFFlowMod.Builder fmb;
            switch (flowModCommand) {
                case ADD:
                    fmb = sw.getOFFactory().buildFlowAdd();
                    break;
                case DELETE:
                    fmb = sw.getOFFactory().buildFlowDelete();
                    break;
                case DELETE_STRICT:
                    fmb = sw.getOFFactory().buildFlowDeleteStrict();
                    break;
                case MODIFY:
                    fmb = sw.getOFFactory().buildFlowModify();
                    break;
                default:
                    log.error("Could not decode OFFlowModCommand. Using MODIFY_STRICT. (Should another be used as the default?)");
                case MODIFY_STRICT:
                    fmb = sw.getOFFactory().buildFlowModifyStrict();
                    break;
            }

            OFActionOutput.Builder aob = sw.getOFFactory().actions().buildOutput();
            List<OFAction> actions = new ArrayList<OFAction>();
            Match.Builder mb = MatchUtils.convertToVersion(match, sw.getOFFactory().getVersion());

            // set input and output ports on the switch
            OFPort outPort = switchPortList.get(indx).getPortId();
            OFPort inPort = switchPortList.get(indx - 1).getPortId();
            if (matchingConfig.isMatchInPort()) {
                mb.setExact(MatchField.IN_PORT, inPort);
            }
            if (!routingData.isRoutedRequest()) {
                aob.setPort(outPort);
            } else {
                OFFactory factory = sw.getOFFactory();
                actions.add(factory.actions().setField(factory.oxms().ethSrc(routingData.getOutputMac())));
                actions.add(factory.actions().setField(factory.oxms().ethDst(routingData.getTargetMac())));
                aob.setPort(routingData.getOutputPort());
            }
            aob.setMaxLen(Integer.MAX_VALUE);
            actions.add(aob.build());

            if (FLOWMOD_DEFAULT_SET_SEND_FLOW_REM_FLAG || requestFlowRemovedNotification) {
                Set<OFFlowModFlags> flags = new HashSet<>();
                flags.add(OFFlowModFlags.SEND_FLOW_REM);
                fmb.setFlags(flags);
            }

            fmb.setMatch(mb.build())
                    .setIdleTimeout(FLOWMOD_DEFAULT_IDLE_TIMEOUT)
                    .setHardTimeout(FLOWMOD_DEFAULT_HARD_TIMEOUT)
                    .setBufferId(OFBufferId.NO_BUFFER)
                    .setCookie(cookie)
                    .setOutPort(outPort)
                    .setPriority(FLOWMOD_DEFAULT_PRIORITY);

            FlowModUtils.setActions(fmb, actions, sw);

            /* Configure for particular switch pipeline */
            if (sw.getOFFactory().getVersion().compareTo(OFVersion.OF_10) != 0) {
                fmb.setTableId(FLOWMOD_DEFAULT_TABLE_ID);
            }

            if (log.isTraceEnabled()) {
                log.info("Pushing Route flowmod routeIndx={} " +
                                "sw={} inPort={} outPort={}",
                        new Object[] {indx,
                                sw,
                                fmb.getMatch().get(MatchField.IN_PORT),
                                outPort });
            }

            if (OFDPAUtils.isOFDPASwitch(sw)) {
                OFDPAUtils.addLearningSwitchFlow(sw, cookie,
                        FLOWMOD_DEFAULT_PRIORITY,
                        FLOWMOD_DEFAULT_HARD_TIMEOUT,
                        FLOWMOD_DEFAULT_IDLE_TIMEOUT,
                        fmb.getMatch(),
                        null, // TODO how to determine output VLAN for lookup of L2 interface group
                        outPort);
            } else {
                messageDamper.write(sw, fmb.build());
            }

            /* Push the packet out the first hop switch */
            if (sw.getId().equals(pinSwitch) &&
                    !fmb.getCommand().equals(OFFlowModCommand.DELETE) &&
                    !fmb.getCommand().equals(OFFlowModCommand.DELETE_STRICT)) {
                /* Use the buffered packet at the switch, if there's one stored */
                pushPacket(sw, pi, outPort, true, cntx);
                packetOutSent = true;
            }
        }

        return packetOutSent;
    }

    /**
     * Pushes a packet-out to a switch. The assumption here is that
     * the packet-in was also generated from the same switch. Thus, if the input
     * port of the packet-in and the outport are the same, the function will not
     * push the packet-out.
     * @param sw switch that generated the packet-in, and from which packet-out is sent
     * @param pi packet-in
     * @param outport output port
     * @param useBufferedPacket use the packet buffered at the switch, if possible
     * @param cntx context of the packet
     */
    protected void pushPacket(IOFSwitch sw, OFPacketIn pi, OFPort outport, boolean useBufferedPacket, FloodlightContext cntx) {
        if (pi == null) {
            return;
        }

        // The assumption here is (sw) is the switch that generated the
        // packet-in. If the input port is the same as output port, then
        // the packet-out should be ignored.
        if ((pi.getVersion().compareTo(OFVersion.OF_12) < 0 ? pi.getInPort() : pi.getMatch().get(MatchField.IN_PORT)).equals(outport)) {
            if (log.isDebugEnabled()) {
                log.debug("Attempting to do packet-out to the same " +
                                "interface as packet-in. Dropping packet. " +
                                " SrcSwitch={}, pi={}",
                        new Object[]{sw, pi});
                return;
            }
        }

        if (log.isTraceEnabled()) {
            log.trace("PacketOut srcSwitch={} pi={}",
                    new Object[] {sw, pi});
        }

        OFPacketOut.Builder pob = sw.getOFFactory().buildPacketOut();
        List<OFAction> actions = new ArrayList<OFAction>();
        actions.add(sw.getOFFactory().actions().output(outport, Integer.MAX_VALUE));
        pob.setActions(actions);

        /* Use packet in buffer if there is a buffer ID set */
        if (useBufferedPacket) {
            pob.setBufferId(pi.getBufferId()); /* will be NO_BUFFER if there isn't one */
        } else {
            pob.setBufferId(OFBufferId.NO_BUFFER);
        }

        if (pob.getBufferId().equals(OFBufferId.NO_BUFFER)) {
            byte[] packetData = pi.getData();
            pob.setData(packetData);
        }

        OFMessageUtils.setInPort(pob, OFMessageUtils.getInPort(pi));
        messageDamper.write(sw, pob.build());
    }

    @Override
    public boolean isCallbackOrderingPrereq(OFType type, String name) {
        return (type.equals(OFType.PACKET_IN) && (name.equals("topology") || name.equals("devicemanager")));
    }

    @Override
    public boolean isCallbackOrderingPostreq(OFType type, String name) {
        return false;
    }



    protected static class FlowSetIdRegistry {
        private volatile Map<NodePortTuple, Set<U64>> nptToFlowSetIds;
        private volatile Map<U64, Set<NodePortTuple>> flowSetIdToNpts;
        
        private volatile long flowSetGenerator = -1;

        private static volatile FlowSetIdRegistry instance;

        private FlowSetIdRegistry() {
            nptToFlowSetIds = new ConcurrentHashMap<NodePortTuple, Set<U64>>();
            flowSetIdToNpts = new ConcurrentHashMap<U64, Set<NodePortTuple>>();
        }

        protected static FlowSetIdRegistry getInstance() {
            if (instance == null) {
                instance = new FlowSetIdRegistry();
            }
            return instance;
        }
        
        /**
         * Only for use by unit test to help w/ordering
         * @param seed
         */
        protected void seedFlowSetIdForUnitTest(int seed) {
            flowSetGenerator = seed;
        }
        
        protected synchronized U64 generateFlowSetId() {
            flowSetGenerator += 1;
            if (flowSetGenerator == FLOWSET_MAX) {
                flowSetGenerator = 0;
                log.warn("Flowset IDs have exceeded capacity of {}. Flowset ID generator resetting back to 0", FLOWSET_MAX);
            }
            U64 id = U64.of(flowSetGenerator << FLOWSET_SHIFT);
            log.debug("Generating flowset ID {}, shifted {}", flowSetGenerator, id);
            return id;
        }

        private void registerFlowSetId(NodePortTuple npt, U64 flowSetId) {
            if (nptToFlowSetIds.containsKey(npt)) {
                Set<U64> ids = nptToFlowSetIds.get(npt);
                ids.add(flowSetId);
            } else {
                Set<U64> ids = new HashSet<U64>();
                ids.add(flowSetId);
                nptToFlowSetIds.put(npt, ids);
            }  

            if (flowSetIdToNpts.containsKey(flowSetId)) {
                Set<NodePortTuple> npts = flowSetIdToNpts.get(flowSetId);
                npts.add(npt);
            } else {
                Set<NodePortTuple> npts = new HashSet<NodePortTuple>();
                npts.add(npt);
                flowSetIdToNpts.put(flowSetId, npts);
            }
        }

        private Set<U64> getFlowSetIds(NodePortTuple npt) {
            return nptToFlowSetIds.get(npt);
        }

        private Set<NodePortTuple> getNodePortTuples(U64 flowSetId) {
            return flowSetIdToNpts.get(flowSetId);
        }

        private void removeNodePortTuple(NodePortTuple npt) {
            nptToFlowSetIds.remove(npt);

            Iterator<Set<NodePortTuple>> itr = flowSetIdToNpts.values().iterator();
            while (itr.hasNext()) {
                Set<NodePortTuple> npts = itr.next();
                npts.remove(npt);
            }
        }

        private void removeExpiredFlowSetId(U64 flowSetId, NodePortTuple avoid, Iterator<U64> avoidItr) {
            flowSetIdToNpts.remove(flowSetId);

            Iterator<Entry<NodePortTuple, Set<U64>>> itr = nptToFlowSetIds.entrySet().iterator();
            boolean removed = false;
            while (itr.hasNext()) {
                Entry<NodePortTuple, Set<U64>> e = itr.next();
                if (e.getKey().equals(avoid) && ! removed) {
                    avoidItr.remove();
                    removed = true;
                } else {
                    Set<U64> ids = e.getValue();
                    ids.remove(flowSetId);
                }
            }
        }
    }

    public Command processPacketInMessage(IOFSwitch sw, OFPacketIn pi, IRoutingDecision decision, FloodlightContext cntx) {
        Ethernet eth = IFloodlightProviderService.bcStore.get(cntx, IFloodlightProviderService.CONTEXT_PI_PAYLOAD);
        // We found a routing decision (i.e. Firewall is enabled... it's the only thing that makes RoutingDecisions)
        if (decision != null) {
            if (log.isTraceEnabled()) {
                log.trace("Forwarding decision={} was made for PacketIn={}", decision.getRoutingAction().toString(), pi);
            }

            switch(decision.getRoutingAction()) {
            case NONE:
                // don't do anything
                return Command.CONTINUE;
            case FORWARD_OR_FLOOD:
            case FORWARD:
                doForwardFlow(sw, pi, decision, cntx, false);
                return Command.CONTINUE;
            case MULTICAST:
                // treat as broadcast
                doFlood(sw, pi);
                return Command.CONTINUE;
            case DROP:
                doDropFlow(sw, pi, decision, cntx);
                return Command.CONTINUE;
            default:
                log.error("Unexpected decision made for this packet-in={}", pi, decision.getRoutingAction());
                return Command.CONTINUE;
            }
        } else { // No routing decision was found. Forward to destination or flood if bcast or mcast.
            if (log.isTraceEnabled()) {
                log.trace("No decision was made for PacketIn={}, forwarding", pi);
            }

            if (eth.isBroadcast() || eth.isMulticast()) {
                log.info("flooding flow");
                doFlood(sw, pi);
            } else {
                log.info("forwarding flow");
                doForwardFlow(sw, pi, decision, cntx, false);
            }
        }

        return Command.CONTINUE;
    }

    /**
     * Builds a cookie that includes routing decision information.
     *
     * @param decision The routing decision providing a descriptor, or null
     * @return A cookie with our app id and the required fields masked-in
     */
    protected U64 makeForwardingCookie(IRoutingDecision decision, U64 flowSetId) {
        long user_fields = 0;

        U64 decision_cookie = (decision == null) ? null : decision.getDescriptor();
        if (decision_cookie != null) {
            user_fields |= AppCookie.extractUser(decision_cookie) & DECISION_MASK;
        }

        if (flowSetId != null) {
            user_fields |= AppCookie.extractUser(flowSetId) & FLOWSET_MASK;
        }

        // TODO: Mask in any other required fields here

        if (user_fields == 0) {
            return DEFAULT_FORWARDING_COOKIE;
        }
        return AppCookie.makeCookie(FORWARDING_APP_ID, user_fields);
    }

    /** Called when the handleDecisionChange is triggered by an event (routing decision was changed in firewall).
     *  
     *  @param changedDecisions Masked routing descriptors for flows that should be deleted from the switch.
     */
    @Override
    public void routingDecisionChanged(Iterable<Masked<U64>> changedDecisions) {
        deleteFlowsByDescriptor(changedDecisions);
    }

    /**
     * Converts a sequence of masked IRoutingDecision descriptors into masked Forwarding cookies.
     *
     * This generates a list of masked cookies that can then be matched in flow-mod messages.
     *
     * @param maskedDescriptors A sequence of masked cookies describing IRoutingDecision descriptors
     * @return A collection of masked cookies suitable for flow-mod operations
     */
    protected Collection<Masked<U64>> convertRoutingDecisionDescriptors(Iterable<Masked<U64>> maskedDescriptors) {
        if (maskedDescriptors == null) {
            return null;
        }

        ImmutableList.Builder<Masked<U64>> resultBuilder = ImmutableList.builder();
        for (Masked<U64> maskedDescriptor : maskedDescriptors) {
            long user_mask = AppCookie.extractUser(maskedDescriptor.getMask()) & DECISION_MASK;
            long user_value = AppCookie.extractUser(maskedDescriptor.getValue()) & user_mask;

            // TODO combine in any other cookie fields you need here.

            resultBuilder.add(
                    Masked.of(
                            AppCookie.makeCookie(FORWARDING_APP_ID, user_value),
                            AppCookie.getAppFieldMask().or(U64.of(user_mask))
                            )
                    );
        }

        return resultBuilder.build();
    }

    /**
     * On all active switches, deletes all flows matching the IRoutingDecision descriptors provided
     * as arguments.
     *
     * @param descriptors The descriptors and masks describing which flows to delete.
     */
    protected void deleteFlowsByDescriptor(Iterable<Masked<U64>> descriptors) {
        Collection<Masked<U64>> masked_cookies = convertRoutingDecisionDescriptors(descriptors);

        if (masked_cookies != null && !masked_cookies.isEmpty()) {
            Map<OFVersion, List<OFMessage>> cache = Maps.newHashMap();

            for (DatapathId dpid : switchService.getAllSwitchDpids()) {
                IOFSwitch sw = switchService.getActiveSwitch(dpid);
                if (sw == null) {
                    continue;
                }

                OFVersion ver = sw.getOFFactory().getVersion();
                if (cache.containsKey(ver)) {
                    sw.write(cache.get(ver));
                } else {
                    ImmutableList.Builder<OFMessage> msgsBuilder = ImmutableList.builder();
                    for (Masked<U64> masked_cookie : masked_cookies) {
                        // Consider OpenFlow version when using cookieMask property
                        if (ver.compareTo(OFVersion.OF_10) == 0) {
                            msgsBuilder.add(
                                    sw.getOFFactory().buildFlowDelete()
                                            .setCookie(masked_cookie.getValue())
                                            // maskCookie not support in OpenFlow 1.0
                                            .build()
                            );
                        }
                        else {
                            msgsBuilder.add(
                                    sw.getOFFactory().buildFlowDelete()
                                            .setCookie(masked_cookie.getValue())
                                            .setCookieMask(masked_cookie.getMask())
                                            .build()
                            );
                        }

                    }

                    List<OFMessage> msgs = msgsBuilder.build();
                    sw.write(msgs);
                    cache.put(ver, msgs);
                }
            }
        }
    }


    protected void doDropFlow(IOFSwitch sw, OFPacketIn pi, IRoutingDecision decision, FloodlightContext cntx) {
        OFPort inPort = OFMessageUtils.getInPort(pi);
        Match m = createMatchFromPacket(sw, inPort, pi, cntx);
        OFFlowMod.Builder fmb = sw.getOFFactory().buildFlowAdd();
        List<OFAction> actions = new ArrayList<OFAction>(); // set no action to drop
        U64 flowSetId = flowSetIdRegistry.generateFlowSetId();
        U64 cookie = makeForwardingCookie(decision, flowSetId); 

        /* If link goes down, we'll remember to remove this flow */
        if (! m.isFullyWildcarded(MatchField.IN_PORT)) {
            flowSetIdRegistry.registerFlowSetId(new NodePortTuple(sw.getId(), m.get(MatchField.IN_PORT)), flowSetId);
        }

        log.info("Dropping");
        fmb.setCookie(cookie)
        .setHardTimeout(FLOWMOD_DEFAULT_HARD_TIMEOUT)
        .setIdleTimeout(FLOWMOD_DEFAULT_IDLE_TIMEOUT)
        .setBufferId(OFBufferId.NO_BUFFER) 
        .setMatch(m)
        .setPriority(FLOWMOD_DEFAULT_PRIORITY);

        FlowModUtils.setActions(fmb, actions, sw);

        /* Configure for particular switch pipeline */
        if (sw.getOFFactory().getVersion().compareTo(OFVersion.OF_10) != 0) {
            fmb.setTableId(FLOWMOD_DEFAULT_TABLE_ID);
        }

        if (log.isDebugEnabled()) {
            log.debug("write drop flow-mod sw={} match={} flow-mod={}",
                    new Object[] { sw, m, fmb.build() });
        }
        boolean dampened = messageDamper.write(sw, fmb.build());
        log.debug("OFMessage dampened: {}", dampened);
    }

    protected void doForwardFlow(IOFSwitch sw, OFPacketIn pi, IRoutingDecision decision, FloodlightContext cntx, boolean requestFlowRemovedNotifn) {
        OFPort srcPort = OFMessageUtils.getInPort(pi);
        DatapathId srcSw = sw.getId();
        IDevice dstDevice = IDeviceService.fcStore.get(cntx, IDeviceService.CONTEXT_DST_DEVICE);
        IDevice srcDevice = IDeviceService.fcStore.get(cntx, IDeviceService.CONTEXT_SRC_DEVICE);

        if (dstDevice == null) {
            log.debug("Destination device unknown. Flooding packet");
            doFlood(sw, pi);
            return;
        }

        RoutingData.RoutingDataBuilder builder = RoutingData.builder();
        if (dstDevice.isVirtualInterface()) {
            log.info("target device is a virtual gateway");
            Ethernet ethernet = IFloodlightProviderService.bcStore.get(cntx, IFloodlightProviderService.CONTEXT_PI_PAYLOAD);
            if (EthType.IPv4.equals(ethernet.getEtherType())) {
                IPv4 ipPacket = (IPv4) ethernet.getPayload();
                IPv4Address destinationAddress = ipPacket.getDestinationAddress();
                IPv4AddressWithMask masked = IPv4AddressWithMask.of(destinationAddress, IPv4Address.of("255.255.255.0"));
                log.info("searching for gateway for network: " + masked + ", for switch: " + sw.getId());
                log.info("seraching for target device for ip : " + ipPacket.getDestinationAddress());
                Optional<Gateway> optionalOutputGateway = gatewayStore.getGateway(masked, sw.getId());
                Optional<IDevice> optionalTargetDevice = deviceManagerService.findByIpAddress(ipPacket.getDestinationAddress());
                if (optionalOutputGateway.isPresent() && optionalTargetDevice.isPresent()) {
                    builder.routedRequest();
                    Gateway gateway = optionalOutputGateway.get();
                    log.info("found output gateway for packet: " + gateway.getIpAddress());
                    OFPort outputPort = gateway.getForwardingPort();
                    builder.setOutputPort(outputPort);
                    MacAddress outputMacAddress = gateway.getMacAddress();
                    builder.setOutputMac(outputMacAddress);

                    log.info("found target device in index for ip: " + ipPacket.getDestinationAddress());
                    builder.setTargetMac(optionalTargetDevice.get().getMACAddress());
                }
            }
        }

        if (srcDevice == null) {
            log.info("No device entry found for source device. Is the device manager running? If so, report bug.");
            return;
        }

        /* Some physical switches partially support or do not support ARP flows */
        if (FLOOD_ALL_ARP_PACKETS && 
                IFloodlightProviderService.bcStore.get(cntx, IFloodlightProviderService.CONTEXT_PI_PAYLOAD).getEtherType() 
                == EthType.ARP) {
            log.info("ARP flows disabled in Forwarding. Flooding ARP packet");
            doFlood(sw, pi);
            return;
        }

        /* This packet-in is from a switch in the path before its flow was installed along the path */
        if (!topologyService.isEdge(srcSw, srcPort) && !dstDevice.isVirtualInterface() && !srcDevice.isVirtualInterface()) {
            log.info("Packet destination is known, but packet was not received on an edge port (rx on {}/{}). Flooding packet", srcSw, srcPort);
            doFlood(sw, pi);
            return; 
        }   

        /* 
         * Search for the true attachment point. The true AP is
         * not an endpoint of a link. It is a switch port w/o an
         * associated link. Note this does not necessarily hold
         * true for devices that 'live' between OpenFlow islands.
         * 
         * TODO Account for the case where a device is actually
         * attached between islands (possibly on a non-OF switch
         * in between two OpenFlow switches).
         */
        SwitchPort dstAp = null;
        for (SwitchPort ap : dstDevice.getAttachmentPoints()) {
            if (topologyService.isEdge(ap.getNodeId(), ap.getPortId())) {
                dstAp = ap;
                break;
            }
        }	

        /* 
         * This should only happen (perhaps) when the controller is
         * actively learning a new topology and hasn't discovered
         * all links yet, or a switch was in standalone mode and the
         * packet in question was captured in flight on the dst point
         * of a link.
         */
        if (dstAp == null) {
            log.info("Could not locate edge attachment point for destination device {}. Flooding packet", dstDevice.getMACAddress());
            doFlood(sw, pi);
            return; 
        }

        /* Validate that the source and destination are not on the same switch port */
        if (!dstDevice.isVirtualInterface() && sw.getId().equals(dstAp.getNodeId()) && srcPort.equals(dstAp.getPortId())) {
            log.info("Both source and destination are on the same switch/port {}/{}. Dropping packet", sw.toString(), srcPort);
            return;
        }			

        U64 flowSetId = flowSetIdRegistry.generateFlowSetId();
        U64 cookie = makeForwardingCookie(decision, flowSetId);
        Path path = routingEngineService.getPath(srcSw, 
                srcPort,
                dstAp.getNodeId(),
                dstAp.getPortId());

        Match m = createMatchFromPacket(sw, srcPort, pi, cntx);

        if (! path.getPath().isEmpty()) {
            if (log.isDebugEnabled()) {
                log.debug("pushRoute inPort={} route={} " +
                        "destination={}:{}",
                        new Object[] { srcPort, path,
                                dstAp.getNodeId(),
                                dstAp.getPortId()});
                log.info("Creating flow rules on the route, match rule: {}", m);
            }

            pushRoute(path, m, pi, sw.getId(), cookie, 
                    cntx, requestFlowRemovedNotifn,
                    OFFlowModCommand.ADD, builder.build());

            /* 
             * Register this flowset with ingress and egress ports for link down
             * flow removal. This is done after we push the path as it is blocking.
             */
            for (NodePortTuple npt : path.getPath()) {
                flowSetIdRegistry.registerFlowSetId(npt, flowSetId);
            }
        } /* else no path was found */
    }

    /**
     * Instead of using the Firewall's routing decision Match, which might be as general
     * as "in_port" and inadvertently Match packets erroneously, construct a more
     * specific Match based on the deserialized OFPacketIn's payload, which has been 
     * placed in the FloodlightContext already by the Controller.
     * 
     * @param sw, the switch on which the packet was received
     * @param inPort, the ingress switch port on which the packet was received
     * @param cntx, the current context which contains the deserialized packet
     * @return a composed Match object based on the provided information
     */
    protected Match createMatchFromPacket(IOFSwitch sw, OFPort inPort, OFPacketIn pi, FloodlightContext cntx) {
        // The packet in match will only contain the port number.
        // We need to add in specifics for the hosts we're routing between.
        Ethernet eth = IFloodlightProviderService.bcStore.get(cntx, IFloodlightProviderService.CONTEXT_PI_PAYLOAD);

        log.info("Creating match from packet");
        return packetMatcher.createMatchFromPacket(sw, pi, eth, inPort);
    }

    /**
     * Creates a OFPacketOut with the OFPacketIn data that is flooded on all ports unless
     * the port is blocked, in which case the packet will be dropped.
     * @param sw The switch that receives the OFPacketIn
     * @param pi The OFPacketIn that came to the switch
     */
    protected void doFlood(IOFSwitch sw, OFPacketIn pi) {
        OFPort inPort = OFMessageUtils.getInPort(pi);
        OFPacketOut.Builder pob = sw.getOFFactory().buildPacketOut();
        List<OFAction> actions = new ArrayList<OFAction>();
        Set<OFPort> broadcastPorts = this.topologyService.getSwitchBroadcastPorts(sw.getId());

        if (broadcastPorts.isEmpty()) {
            log.debug("No broadcast ports found. Using FLOOD output action");
            broadcastPorts = Collections.singleton(OFPort.FLOOD);
        }

        for (OFPort p : broadcastPorts) {
            if (p.equals(inPort)) continue;
            actions.add(sw.getOFFactory().actions().output(p, Integer.MAX_VALUE));
        }
        pob.setActions(actions);
        // log.info("actions {}",actions);
        // set buffer-id, in-port and packet-data based on packet-in
        pob.setBufferId(OFBufferId.NO_BUFFER);
        OFMessageUtils.setInPort(pob, inPort);
        pob.setData(pi.getData());

        if (log.isTraceEnabled()) {
            log.trace("Writing flood PacketOut switch={} packet-in={} packet-out={}",
                    new Object[] {sw, pi, pob.build()});
        }
        messageDamper.write(sw, pob.build());

        return;
    }

    // IFloodlightModule methods

    @Override
    public Collection<Class<? extends IFloodlightService>> getModuleServices() {
        // We don't export any services
        return null;
    }

    @Override
    public Map<Class<? extends IFloodlightService>, IFloodlightService>
    getServiceImpls() {
        // We don't have any services
        return null;
    }

    @Override
    public Collection<Class<? extends IFloodlightService>> getModuleDependencies() {
        return Arrays.asList(
                IFloodlightProviderService.class,
                IDeviceService.class,
                IRoutingService.class,
                ITopologyService.class,
                IDebugCounterService.class,
                ILinkDiscoveryService.class,
                GatewayStoreService.class
        );
    }

    @Override
    public void init(FloodlightModuleContext context) throws FloodlightModuleException {
        messageDamper = new OFMessageDamper(OFMESSAGE_DAMPER_CAPACITY,
                EnumSet.of(OFType.FLOW_MOD),
                OFMESSAGE_DAMPER_TIMEOUT);
        this.floodlightProviderService = context.getServiceImpl(IFloodlightProviderService.class);
        this.deviceManagerService = context.getServiceImpl(IDeviceService.class);
        this.routingEngineService = context.getServiceImpl(IRoutingService.class);
        this.topologyService = context.getServiceImpl(ITopologyService.class);
        this.debugCounterService = context.getServiceImpl(IDebugCounterService.class);
        this.switchService = context.getServiceImpl(IOFSwitchService.class);
        this.linkService = context.getServiceImpl(ILinkDiscoveryService.class);
        this.gatewayStore = context.getServiceImpl(GatewayStoreService.class);

        MatchingConfig.ConfigBuilder configBuilder = MatchingConfig.builder();
        flowSetIdRegistry = FlowSetIdRegistry.getInstance();

        Map<String, String> configParameters = context.getConfigParams(this);
        String tmp = configParameters.get("hard-timeout");
        if (tmp != null) {
            FLOWMOD_DEFAULT_HARD_TIMEOUT = ParseUtils.parseHexOrDecInt(tmp);
            log.info("Default hard timeout set to {}.", FLOWMOD_DEFAULT_HARD_TIMEOUT);
        } else {
            log.info("Default hard timeout not configured. Using {}.", FLOWMOD_DEFAULT_HARD_TIMEOUT);
        }
        tmp = configParameters.get("idle-timeout");
        if (tmp != null) {
            FLOWMOD_DEFAULT_IDLE_TIMEOUT = ParseUtils.parseHexOrDecInt(tmp);
            log.info("Default idle timeout set to {}.", FLOWMOD_DEFAULT_IDLE_TIMEOUT);
        } else {
            log.info("Default idle timeout not configured. Using {}.", FLOWMOD_DEFAULT_IDLE_TIMEOUT);
        }
        tmp = configParameters.get("table-id");
        if (tmp != null) {
            FLOWMOD_DEFAULT_TABLE_ID = TableId.of(ParseUtils.parseHexOrDecInt(tmp));
            log.info("Default table ID set to {}.", FLOWMOD_DEFAULT_TABLE_ID);
        } else {
            log.info("Default table ID not configured. Using {}.", FLOWMOD_DEFAULT_TABLE_ID);
        }
        tmp = configParameters.get("priority");
        if (tmp != null) {
            FLOWMOD_DEFAULT_PRIORITY = ParseUtils.parseHexOrDecInt(tmp);
            log.info("Default priority set to {}.", FLOWMOD_DEFAULT_PRIORITY);
        } else {
            log.info("Default priority not configured. Using {}.", FLOWMOD_DEFAULT_PRIORITY);
        }
        tmp = configParameters.get("set-send-flow-rem-flag");
        if (tmp != null) {
            FLOWMOD_DEFAULT_SET_SEND_FLOW_REM_FLAG = Boolean.parseBoolean(tmp);
            log.info("Default flags will be set to SEND_FLOW_REM {}.", FLOWMOD_DEFAULT_SET_SEND_FLOW_REM_FLAG);
        } else {
            log.info("Default flags will be empty.");
        }
        tmp = configParameters.get("match");
        if (tmp != null) {
            tmp = tmp.toLowerCase();
            if (!tmp.contains("in-port") && !tmp.contains("vlan") 
                    && !tmp.contains("mac") && !tmp.contains("ip") 
                    && !tmp.contains("transport") && !tmp.contains("flag")) {
                /* leave the default configuration -- blank or invalid 'match' value */
            } else {
                configBuilder.setMatchInPort(tmp.contains("in-port"))
                        .setMatchVlan(tmp.contains("vlan"))
                        .setMatchMac(tmp.contains("mac"))
                        .setMatchIp(tmp.contains("ip"))
                        .setMatchTransport(tmp.contains("transport"))
                        .setMatchTcpFlag(tmp.contains("flag"));
            }
        }

        tmp = configParameters.get("detailed-match");
        if (tmp != null) {
            tmp = tmp.toLowerCase();
            if (!tmp.contains("src-mac") && !tmp.contains("dst-mac") 
                    && !tmp.contains("src-ip") && !tmp.contains("dst-ip")
                    && !tmp.contains("src-transport") && !tmp.contains("dst-transport")) {
                /* leave the default configuration -- both src and dst for layers defined above */
            } else {
                configBuilder.setMatchMacSrc(tmp.contains("src-mac"))
                        .setMatchMacDst(tmp.contains("dst-mac"))
                        .setMatchIpSrc(tmp.contains("src-ip"))
                        .setMatchIpDst(tmp.contains("dst-ip"))
                        .setMatchTransportSrc(tmp.contains("src-transport"))
                        .setMatchTransportDst(tmp.contains("dst-transport"));
            }
        }
        matchingConfig = configBuilder.build();
        packetMatcher = new PacketMatcher(matchingConfig);

        tmp = configParameters.get("flood-arp");
        if (tmp != null) {
            tmp = tmp.toLowerCase();
            if (!tmp.contains("yes") && !tmp.contains("yep") && !tmp.contains("true") && !tmp.contains("ja") && !tmp.contains("stimmt")) {
                FLOOD_ALL_ARP_PACKETS = false;
                log.info("Not flooding ARP packets. ARP flows will be inserted for known destinations");
            } else {
                FLOOD_ALL_ARP_PACKETS = true;
                log.info("Flooding all ARP packets. No ARP flows will be inserted");
            }
        }

        tmp = configParameters.get("remove-flows-on-link-or-port-down");
        if (tmp != null) {
            REMOVE_FLOWS_ON_LINK_OR_PORT_DOWN = Boolean.parseBoolean(tmp);
        }
        if (REMOVE_FLOWS_ON_LINK_OR_PORT_DOWN) {
            log.info("Flows will be removed on link/port down events");
        } else {
            log.info("Flows will not be removed on link/port down events");
        }
    }

    @Override
    public void startUp(FloodlightModuleContext context) {
        floodlightProviderService.addOFMessageListener(OFType.PACKET_IN, this);
        switchService.addOFSwitchListener(this);
        routingEngineService.addRoutingDecisionChangedListener(this);

        /* Register only if we want to remove stale flows */
        if (REMOVE_FLOWS_ON_LINK_OR_PORT_DOWN) {
            linkService.addListener(this);
        }
    }

    @Override
    public void switchActivated(DatapathId switchId) {
        IOFSwitch sw = switchService.getSwitch(switchId);
        if (sw == null) {
            log.warn("Switch {} was activated but had no switch object in the switch service. Perhaps it quickly disconnected", switchId);
            return;
        }
        if (OFDPAUtils.isOFDPASwitch(sw)) {
            messageDamper.write(sw, sw.getOFFactory().buildFlowDelete()
                    .setTableId(TableId.ALL)
                    .build()
                    );
            messageDamper.write(sw, sw.getOFFactory().buildGroupDelete()
                    .setGroup(OFGroup.ANY)
                    .setGroupType(OFGroupType.ALL)
                    .build()
                    );
            messageDamper.write(sw, sw.getOFFactory().buildGroupDelete()
                    .setGroup(OFGroup.ANY)
                    .setGroupType(OFGroupType.INDIRECT)
                    .build()
                    );
            messageDamper.write(sw, sw.getOFFactory().buildBarrierRequest().build());

            List<OFPortModeTuple> portModes = new ArrayList<OFPortModeTuple>();
            for (OFPortDesc p : sw.getPorts()) {
                portModes.add(OFPortModeTuple.of(p.getPortNo(), OFPortMode.ACCESS));
            }
            if (log.isWarnEnabled()) {
                log.warn("For OF-DPA switch {}, initializing VLAN {} on ports {}", new Object[] { switchId, VlanVid.ZERO, portModes});
            }
            OFDPAUtils.addLearningSwitchPrereqs(sw, VlanVid.ZERO, portModes);
        }
    }

    @Override
    public void linkDiscoveryUpdate(List<LDUpdate> updateList) {
        for (LDUpdate u : updateList) {
            /* Remove flows on either side if link/port went down */
            if (u.getOperation() == UpdateOperation.LINK_REMOVED ||
                    u.getOperation() == UpdateOperation.PORT_DOWN ||
                    u.getOperation() == UpdateOperation.TUNNEL_PORT_REMOVED) {
                Set<OFMessage> msgs = new HashSet<OFMessage>();

                if (u.getSrc() != null && !u.getSrc().equals(DatapathId.NONE)) {
                    IOFSwitch srcSw = switchService.getSwitch(u.getSrc());
                    /* src side of link */
                    if (srcSw != null) {
                        Set<U64> ids = flowSetIdRegistry.getFlowSetIds(
                                new NodePortTuple(u.getSrc(), u.getSrcPort()));
                        if (ids != null) {
                            Iterator<U64> i = ids.iterator();
                            while (i.hasNext()) {
                                U64 id = i.next();
                                U64 cookie = id.or(DEFAULT_FORWARDING_COOKIE);
                                U64 cookieMask = U64.of(FLOWSET_MASK).or(AppCookie.getAppFieldMask());

                                /* Delete flows matching on src port and outputting to src port */
                                msgs = buildDeleteFlows(u.getSrcPort(), msgs, srcSw, cookie, cookieMask);
                                messageDamper.write(srcSw, msgs);
                                log.debug("src: Removing flows to/from DPID={}, port={}", u.getSrc(), u.getSrcPort());
                                log.debug("src: Cookie/mask {}/{}", cookie, cookieMask);

                                /* 
                                 * Now, for each ID on this particular failed link, remove
                                 * all other flows in the network using this ID.
                                 */
                                Set<NodePortTuple> npts = flowSetIdRegistry.getNodePortTuples(id);
                                if (npts != null) {
                                    for (NodePortTuple npt : npts) {
                                        msgs.clear();
                                        IOFSwitch sw = switchService.getSwitch(npt.getNodeId());
                                        if (sw != null) {

                                            /* Delete flows matching on npt port and outputting to npt port*/
                                            msgs = buildDeleteFlows(npt.getPortId(), msgs, sw, cookie, cookieMask);
                                            messageDamper.write(sw, msgs);
                                            log.debug("src: Removing same-cookie flows to/from DPID={}, port={}", npt.getNodeId(), npt.getPortId());
                                            log.debug("src: Cookie/mask {}/{}", cookie, cookieMask);
                                        }
                                    }
                                }
                                flowSetIdRegistry.removeExpiredFlowSetId(id, new NodePortTuple(u.getSrc(), u.getSrcPort()), i);
                            }
                        }
                    }
                    flowSetIdRegistry.removeNodePortTuple(new NodePortTuple(u.getSrc(), u.getSrcPort()));
                }

                /* must be a link, not just a port down, if we have a dst switch */
                if (u.getDst() != null && !u.getDst().equals(DatapathId.NONE)) {
                    /* dst side of link */
                    IOFSwitch dstSw = switchService.getSwitch(u.getDst());
                    if (dstSw != null) {
                        Set<U64> ids = flowSetIdRegistry.getFlowSetIds(
                                new NodePortTuple(u.getDst(), u.getDstPort()));
                        if (ids != null) {
                            Iterator<U64> i = ids.iterator();
                            while (i.hasNext()) {
                                U64 id = i.next();
                                U64 cookie = id.or(DEFAULT_FORWARDING_COOKIE);
                                U64 cookieMask = U64.of(FLOWSET_MASK).or(AppCookie.getAppFieldMask());
                                /* Delete flows matching on dst port and outputting to dst port */
                                msgs = buildDeleteFlows(u.getDstPort(), msgs, dstSw, cookie, cookieMask);
                                messageDamper.write(dstSw, msgs);
                                log.debug("dst: Removing flows to/from DPID={}, port={}", u.getDst(), u.getDstPort());
                                log.debug("dst: Cookie/mask {}/{}", cookie, cookieMask);

                                /* 
                                 * Now, for each ID on this particular failed link, remove
                                 * all other flows in the network using this ID.
                                 */
                                Set<NodePortTuple> npts = flowSetIdRegistry.getNodePortTuples(id);
                                if (npts != null) {
                                    for (NodePortTuple npt : npts) {
                                        msgs.clear();
                                        IOFSwitch sw = switchService.getSwitch(npt.getNodeId());
                                        if (sw != null) {
                                            /* Delete flows matching on npt port and outputting on npt port */
                                            msgs = buildDeleteFlows(npt.getPortId(), msgs, sw, cookie, cookieMask);
                                            messageDamper.write(sw, msgs);
                                            log.debug("dst: Removing same-cookie flows to/from DPID={}, port={}", npt.getNodeId(), npt.getPortId());
                                            log.debug("dst: Cookie/mask {}/{}", cookie, cookieMask);
                                        }
                                    }
                                }
                                flowSetIdRegistry.removeExpiredFlowSetId(id, new NodePortTuple(u.getDst(), u.getDstPort()), i);
                            }
                        }
                    }
                    flowSetIdRegistry.removeNodePortTuple(new NodePortTuple(u.getDst(), u.getDstPort()));
                }
            }
        }
    }

    private Set<OFMessage> buildDeleteFlows(OFPort port, Set<OFMessage> msgs, IOFSwitch sw, U64 cookie, U64 cookieMask) {
        if(sw.getOFFactory().getVersion().compareTo(OFVersion.OF_10) == 0) {
            msgs.add(sw.getOFFactory().buildFlowDelete()
                    .setCookie(cookie)
                    // cookie mask not supported in OpenFlow 1.0
                    .setMatch(sw.getOFFactory().buildMatch()
                            .setExact(MatchField.IN_PORT, port)
                            .build())
                    .build());

            msgs.add(sw.getOFFactory().buildFlowDelete()
                    .setCookie(cookie)
                    // cookie mask not supported in OpenFlow 1.0
                    .setOutPort(port)
                    .build());
        }
        else {
            msgs.add(sw.getOFFactory().buildFlowDelete()
                    .setCookie(cookie)
                    .setCookieMask(cookieMask)
                    .setMatch(sw.getOFFactory().buildMatch()
                            .setExact(MatchField.IN_PORT, port)
                            .build())
                    .build());

            msgs.add(sw.getOFFactory().buildFlowDelete()
                    .setCookie(cookie)
                    .setCookieMask(cookieMask)
                    .setOutPort(port)
                    .build());
        }

        return msgs;

    }

}
