package nl.tno.hexabus.protocol;

import java.util.Calendar;

import nl.tno.hexabus.protocol.TypeMap.Coded;

public abstract class Data {
    @SuppressWarnings("unchecked")
    public static final TypeMap<Data> types = TypeMap.create(Unknown.class,
                                                             Bool.class,
                                                             UInt8.class,
                                                             UInt32.class,
                                                             DateTime.class,
                                                             Float.class,
                                                             Text.class,
                                                             Timestamp.class);

    public interface Visitor<R> {
        R visit(Bool bool);

        R visit(UInt8 uInt8);

        R visit(UInt32 uInt32);

        R visit(DateTime dateTime);

        R visit(Float float1);

        R visit(Text text);

        R visit(Timestamp timestamp);
    }

    private final byte eid;

    protected Data(int eid) {
        this.eid = (byte) eid;
    }

    public byte getEid() {
        return eid;
    }

    @Override
    public boolean equals(Object obj) {
        if (obj == null || getClass() != obj.getClass()) {
            return false;
        }
        Data other = (Data) obj;
        return eid == other.eid && getValue().equals(other.getValue());
    }

    @Override
    public int hashCode() {
        return eid * 31 + getValue().hashCode();
    }

    @Override
    public String toString() {
        return getClass().getSimpleName() + " [eid=" + eid + ", value=" + getValue() + "]";
    }

    public abstract Object getValue();

    public abstract <R> R visit(Visitor<R> visitor);

    @Coded(0)
    public static final class Unknown extends Data {
        protected Unknown(int eid) {
            super(eid);
        }

        @Override
        public Object getValue() {
            return null;
        }

        @Override
        public <R> R visit(Visitor<R> visitor) {
            return null;
        }
    }

    @Coded(1)
    public static final class Bool extends Data {
        private final boolean value;

        public Bool(int eid, boolean value) {
            super(eid);
            this.value = value;
        }

        @Override
        public Boolean getValue() {
            return value;
        }

        public boolean get() {
            return value;
        }

        @Override
        public <R> R visit(Visitor<R> visitor) {
            return visitor.visit(this);
        }
    }

    @Coded(2)
    public static final class UInt8 extends Data {
        private final int value;

        public UInt8(int eid, int value) {
            super(eid);
            this.value = value & 0xff;
        }

        @Override
        public Integer getValue() {
            return value;
        }

        public int get() {
            return value;
        }

        @Override
        public <R> R visit(Visitor<R> visitor) {
            return visitor.visit(this);
        }
    }

    @Coded(3)
    public static final class UInt32 extends Data {
        private final long value;

        public UInt32(int eid, long value) {
            super(eid);
            this.value = value & 0xffffffffL;
        }

        @Override
        public Long getValue() {
            return value;
        }

        public long get() {
            return value;
        }

        @Override
        public <R> R visit(Visitor<R> visitor) {
            return visitor.visit(this);
        }
    }

    @Coded(4)
    public static final class DateTime extends Data {
        private final Calendar value;

        public DateTime(int eid, Calendar value) {
            super(eid);
            this.value = value;
        }

        @Override
        public Calendar getValue() {
            return value;
        }

        @Override
        public <R> R visit(Visitor<R> visitor) {
            return visitor.visit(this);
        }
    }

    @Coded(5)
    public static final class Float extends Data {
        private final float value;

        public Float(int eid, float value) {
            super(eid);
            this.value = value;
        }

        @Override
        public java.lang.Float getValue() {
            return value;
        }

        public float get() {
            return value;
        }

        @Override
        public <R> R visit(Visitor<R> visitor) {
            return visitor.visit(this);
        }
    }

    @Coded(6)
    public static final class Text extends Data {
        private final String value;

        public Text(int eid, java.lang.String value) {
            super(eid);
            if (!PacketCodec.ASCII.newEncoder().canEncode(value)) {
                throw new IllegalArgumentException("The given text can not be encoded as ASCII");
            }
            this.value = value;
        }

        @Override
        public String getValue() {
            return value;
        }

        @Override
        public <R> R visit(Visitor<R> visitor) {
            return visitor.visit(this);
        }
    }

    @Coded(7)
    public static final class Timestamp extends Data {
        private final long value;

        public Timestamp(int eid, long value) {
            super(eid);
            this.value = value;
        }

        @Override
        public Long getValue() {
            return value;
        }

        public long get() {
            return value;
        }

        @Override
        public <R> R visit(Visitor<R> visitor) {
            return visitor.visit(this);
        }
    }
}
