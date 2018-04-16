package net.floodlightcontroller.util;

import org.junit.Before;
import org.junit.Test;
import org.projectfloodlight.openflow.protocol.OFPacketIn;
import org.projectfloodlight.openflow.protocol.OFVersion;
import org.projectfloodlight.openflow.protocol.match.Match;
import org.projectfloodlight.openflow.protocol.match.MatchField;
import org.projectfloodlight.openflow.types.OFPort;

import static org.junit.Assert.assertEquals;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

public class OFMessageUtilDeterminePortTest {

    private OFPacketIn openflow11PacketIn;
    private OFPacketIn openflow12PacketIn;

    private OFPort openFlow11Port = mock(OFPort.class);
    private OFPort openFlow12Port = mock(OFPort.class);

    @Before
    public void initialize() {

        openflow11PacketIn = mock(OFPacketIn.class);
        when(openflow11PacketIn.getVersion()).thenReturn(OFVersion.OF_11);
        when(openflow11PacketIn.getInPort()).thenReturn(openFlow11Port);

        Match match = mock(Match.class);
        when(match.get(eq(MatchField.IN_PORT))).thenReturn(openFlow12Port);

        openflow12PacketIn = mock(OFPacketIn.class);
        when(openflow12PacketIn.getVersion()).thenReturn(OFVersion.OF_12);
        when(openflow12PacketIn.getMatch()).thenReturn(match);
    }

    @Test
    public void shouldReturnInPortForOpenFlow11() {
        OFPort result = OFMessageUtils.determinePort(openflow11PacketIn);
        assertEquals(openFlow11Port, result);
    }

    @Test
    public void shouldReturnInPortFromMatchForOpenFlow12() {
        OFPort result = OFMessageUtils.determinePort(openflow12PacketIn);
        assertEquals(openFlow12Port, result);
    }
}