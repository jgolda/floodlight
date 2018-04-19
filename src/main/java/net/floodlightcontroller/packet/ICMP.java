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

package net.floodlightcontroller.packet;

import java.nio.ByteBuffer;
import java.util.Collections;
import java.util.HashMap;
import java.util.Map;

import org.projectfloodlight.openflow.types.IpProtocol;

/**
 * Implements ICMP packet format
 * @author shudong.zhou@bigswitch.com
 */
public class ICMP extends BasePacket {

    public enum Type {
        ECHO_REPLY(0),
        DESTINATION_UNREACHABLE(3, 4),
        ECHO_REQUEST(8),
        TIME_EXCEEDED(11, 4),

        UNHANDLED(-127);

        private final byte value;

        private final short numberOfPaddingBytes;

        private static final Map<Byte, Type> types = Collections.unmodifiableMap(setupEnumMap());

        Type(int value) {
            this(value, 0);
        }

        Type(int value, int numberOfPaddingBytes) {
            this.value = (byte) value;
            this.numberOfPaddingBytes = (short) numberOfPaddingBytes;
        }

        static Type from(byte value) {
            return types.getOrDefault(value, UNHANDLED);
        }

        public byte value() {
            return value;
        }

        public short numberOfPaddingBytes() {
            return numberOfPaddingBytes;
        }

        private static Map<Byte, Type> setupEnumMap() {
            HashMap<Byte, Type> result = new HashMap<>();
            for ( Type type : Type.values() ) {
                result.put(type.value(), type);
            }
            return result;
        }
    }

    public enum Code {
        ECHO_REPLY(Type.ECHO_REPLY, 0),
        ECHO_REQUEST(Type.ECHO_REQUEST, 0),
        TTL_EXPIRED(Type.TIME_EXCEEDED, 0),
        FRAGMENT_REASSEMBLY_TIME_EXCEEDED(Type.TIME_EXCEEDED, 1),

        UNHANDLED(Type.UNHANDLED, -127);

        private final Type type;

        private final byte value;

        private static final Map<Short, Code> codes = Collections.unmodifiableMap(setupEnumMap());

        Code(Type type, int value) {
            this.type = type;
            this.value = (byte) value;
        }

        public static Code from(Type type, byte value) {
            return codes.getOrDefault(buildMapKey(type, value), UNHANDLED);
        }

        public Type getType() {
            return type;
        }

        public byte value() {
            return value;
        }

        private static Map<Short, Code> setupEnumMap() {
            HashMap<Short, Code> result = new HashMap<>();
            for ( Code code : Code.values() ) {
                result.put(buildMapKey(code.getType(), code.value()), code);
            }
            return result;
        }

        private static Short buildMapKey(Type type, byte code) {
            return (short) (((short) (type.value() << 8)) + code);
        }
    }

    private Code code;

    protected short checksum;

    public Type getType() {
        return code.getType();
    }

    public Code getCode() {
        return code;
    }

    public ICMP setCode(Code code) {
        this.code = code;
        return this;
    }

    /**
     * @return the checksum
     */
    public short getChecksum() {
        return checksum;
    }

    /**
     * @param checksum the checksum to set
     */
    public ICMP setChecksum(short checksum) {
        this.checksum = checksum;
        return this;
    }

    /**
     * Serializes the packet. Will compute and set the following fields if they
     * are set to specific values at the time serialize is called:
     *      -checksum : 0
     *      -length : 0
     */
    @Override
    public byte[] serialize() {
        short padding = code.getType().numberOfPaddingBytes();

        int length = 4 + padding;
        byte[] payloadData = null;
        if (payload != null) {
            payload.setParent(this);
            payloadData = payload.serialize();
            length += payloadData.length;
        }

        byte[] data = new byte[length];
        ByteBuffer bb = ByteBuffer.wrap(data);

        bb.put(code.getType().value());
        bb.put(code.value());
        bb.putShort(checksum);
        for (int i = 0; i < padding; i++)
            bb.put((byte) 0);

        if (payloadData != null)
            bb.put(payloadData);

        if (this.parent != null && this.parent instanceof IPv4)
            ((IPv4)this.parent).setProtocol(IpProtocol.ICMP);

        // compute checksum if needed
        if (this.checksum == 0) {
            bb.rewind();
            int accumulation = 0;

            for (int i = 0; i < length / 2; ++i) {
                accumulation += 0xffff & bb.getShort();
            }
            // pad to an even number of shorts
            if (length % 2 > 0) {
                accumulation += (bb.get() & 0xff) << 8;
            }

            accumulation = ((accumulation >> 16) & 0xffff)
                    + (accumulation & 0xffff);
            this.checksum = (short) (~accumulation & 0xffff);
            bb.putShort(2, this.checksum);
        }
        return data;
    }

    /* (non-Javadoc)
     * @see java.lang.Object#hashCode()
     */
    @Override
    public int hashCode() {
        final int prime = 5807;
        int result = super.hashCode();
        result = prime * result + code.getType().value();
        result = prime * result + code.value();
        result = prime * result + checksum;
        return result;
    }

    /* (non-Javadoc)
     * @see java.lang.Object#equals(java.lang.Object)
     */
    @Override
    public boolean equals(Object obj) {
        if (this == obj)
            return true;
        if (!super.equals(obj))
            return false;
        if (!(obj instanceof ICMP))
            return false;
        ICMP other = (ICMP) obj;
        if (code.getType() != other.getType())
            return false;
        if (code != other.code)
            return false;
        if (checksum != other.checksum)
            return false;
        return true;
    }

    @Override
    public IPacket deserialize(byte[] data, int offset, int length)
            throws PacketParsingException {
        ByteBuffer bb = ByteBuffer.wrap(data, offset, length);
        Type type = Type.from(bb.get());
        code = Code.from(type, bb.get());
        checksum = bb.getShort();

        short padding = type.numberOfPaddingBytes();
        bb.position(bb.position() + padding);

        this.payload = new Data();
        this.payload = payload.deserialize(data, bb.position(), bb.limit()-bb.position());
        this.payload.setParent(this);
        return this;
    }
}
