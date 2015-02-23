package nl.tno.hexabus.protocol;

import java.net.Inet6Address;

import nl.tno.hexabus.protocol.ReverseEnumMap.ReversableEnum;
import nl.tno.hexabus.protocol.TypeMap.Coded;

public abstract class Packet {
    @SuppressWarnings("unchecked")
    public static final TypeMap<Packet> types = TypeMap.create(EndpointInfo.class,
                                                               EndpointQuery.class,
                                                               Error.class,
                                                               Info.class,
                                                               Query.class,
                                                               Write.class);

    public interface Visitor {
        void visit(EndpointInfo endpointInfo);

        void visit(EndpointQuery endpointQuery);

        void visit(Error packet);

        void visit(Info packet);

        void visit(Query packet);

        void visit(Write write);
    }

    private final Inet6Address address;
    private final int port;

    public Packet(Inet6Address address, int port) {
        if (address == null) {
            throw new NullPointerException();
        }
        this.address = address;
        this.port = port;
    }

    public Inet6Address getAddress() {
        return address;
    }

    public abstract Object getContent();

    public int getPort() {
        return port;
    }

    @Override
    public boolean equals(Object obj) {
        if (obj == null || obj.getClass() != this.getClass()) {
            return false;
        }
        Packet other = (Packet) obj;
        return address.equals(other.address) && port == other.port && getContent().equals(other.getContent());
    }

    @Override
    public int hashCode() {
        return address.hashCode() * 31 + port + getContent().hashCode() * 103;
    }

    @Override
    public String toString() {
        return getClass().getSimpleName() + " [" + address + "_" + port + "] (" + getContent() + ")";
    }

    public abstract void visit(Visitor visitor);

    public abstract static class DataPacket extends Packet {
        private final Data data;

        public DataPacket(Inet6Address address, int port, Data data) {
            super(address, port);
            this.data = data;
        }

        @Override
        public Data getContent() {
            return data;
        }

        public Data getData() {
            return data;
        }
    }

    @Coded(9)
    public static class EndpointInfo extends DataPacket {
        private final Class<? extends Data> endpointType;

        public EndpointInfo(Inet6Address address, int port, Class<? extends Data> endpointType, Data.Text data) {
            super(address, port, data);
            this.endpointType = endpointType;
        }

        @Override
        public Data.Text getData() {
            return (Data.Text) super.getData();
        }

        public Class<? extends Data> getEndpointType() {
            return endpointType;
        }

        @Override
        public void visit(Visitor visitor) {
            visitor.visit(this);
        }
    }

    @Coded(10)
    public static class EndpointQuery extends Packet {
        private final byte eid;

        public EndpointQuery(Inet6Address address, int port, int eid) {
            super(address, port);
            this.eid = (byte) eid;
        }

        public byte getEid() {
            return eid;
        }

        @Override
        public Byte getContent() {
            return eid;
        }

        @Override
        public void visit(Visitor visitor) {
            visitor.visit(this);
        }
    }

    @Coded(0)
    public static class Error extends Packet {
        public static final ReverseEnumMap<Code> codes = new ReverseEnumMap<Code>(Code.class);

        public enum Code implements ReversableEnum {
            UNKNOWNEID(1), WRITEREADONLY(2), CRCFAILED(3), DATATYPE(4);

            private final byte value;

            Code(int value) {
                this.value = (byte) value;
            }

            @Override
            public byte getValue() {
                return value;
            }
        }

        private final Code code;

        public Error(Inet6Address address, int port, Code code) {
            super(address, port);
            this.code = code;
        }

        @Override
        public Code getContent() {
            return code;
        }

        public Code getErrorCode() {
            return code;
        }

        @Override
        public void visit(Visitor visitor) {
            visitor.visit(this);
        }
    }

    @Coded(1)
    public static class Info extends DataPacket {
        public Info(Inet6Address address, int port, Data data) {
            super(address, port, data);
        }

        @Override
        public void visit(Visitor visitor) {
            visitor.visit(this);
        }
    }

    @Coded(2)
    public static class Query extends Packet {
        private final byte eid;

        public Query(Inet6Address address, int port, int eid) {
            super(address, port);
            this.eid = (byte) eid;
        }

        public byte getEid() {
            return eid;
        }

        @Override
        public Byte getContent() {
            return eid;
        }

        @Override
        public void visit(Visitor visitor) {
            visitor.visit(this);
        }
    }

    @Coded(4)
    public static class Write extends DataPacket {
        public Write(Inet6Address address, int port, Data data) {
            super(address, port, data);
        }

        @Override
        public void visit(Visitor visitor) {
            visitor.visit(this);
        }
    }
}
