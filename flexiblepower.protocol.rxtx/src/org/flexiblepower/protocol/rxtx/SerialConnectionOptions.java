package org.flexiblepower.protocol.rxtx;

public class SerialConnectionOptions {
    public static enum Baudrate {
        B110(110),
        B300(300),
        B600(600),
        B1200(1200),
        B2400(2400),
        B4800(4800),
        B9600(9600),
        B14400(14400),
        B19200(19200),
        B28800(28800),
        B38400(38400),
        B56000(56000),
        B57600(57600),
        B115200(115200);

        private final int baudrate;

        private Baudrate(int baudrate) {
            this.baudrate = baudrate;
        }

        public int getBaudrate() {
            return baudrate;
        }
    }

    public static enum Databits {
        D5(5), D6(6), D7(7), D8(8);

        private final int databits;

        private Databits(int databits) {
            this.databits = databits;
        }

        public int getDatabits() {
            return databits;
        }
    }

    public static enum Stopbits {
        S1(1), S1_5(3), S2(2);

        private final int stopbits;

        private Stopbits(int stopbits) {
            this.stopbits = stopbits;
        }

        public int getStopbits() {
            return stopbits;
        }
    }

    public static enum Parity {
        None(0), Odd(1), Even(2), Mark(3), Space(4);

        private final int parity;

        private Parity(int parity) {
            this.parity = parity;
        }

        public int getParity() {
            return parity;
        }
    }

    private final Baudrate baudrate;
    private final Databits databits;
    private final Stopbits stopbits;
    private final Parity parity;

    public SerialConnectionOptions(Baudrate baudrate, Databits databits, Stopbits stopbits, Parity parity) {
        if (baudrate == null || databits == null || stopbits == null || parity == null) {
            throw new NullPointerException();
        }

        this.baudrate = baudrate;
        this.databits = databits;
        this.stopbits = stopbits;
        this.parity = parity;
    }

    public Baudrate getBaudrate() {
        return baudrate;
    }

    public Databits getDatabits() {
        return databits;
    }

    public Stopbits getStopbits() {
        return stopbits;
    }

    public Parity getParity() {
        return parity;
    }

    @Override
    public int hashCode() {
        final int prime = 31;
        int result = 1;
        result = prime * result + baudrate.hashCode();
        result = prime * result + databits.hashCode();
        result = prime * result + parity.hashCode();
        result = prime * result + stopbits.hashCode();
        return result;
    }

    @Override
    public boolean equals(Object obj) {
        if (this == obj) {
            return true;
        } else if (obj == null || getClass() != obj.getClass()) {
            return false;
        }
        SerialConnectionOptions other = (SerialConnectionOptions) obj;
        if (baudrate != other.baudrate) {
            return false;
        } else if (databits != other.databits) {
            return false;
        } else if (parity != other.parity) {
            return false;
        } else if (stopbits != other.stopbits) {
            return false;
        } else {
            return true;
        }
    }

    @Override
    public String toString() {
        return "SerialConnectionOptions [baudrate=" + baudrate
               + ", databits="
               + databits
               + ", stopbits="
               + stopbits
               + ", parity="
               + parity
               + "]";
    }
}
