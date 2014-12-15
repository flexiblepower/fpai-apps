package nl.tno.fpai.demo.scenario.data;

public class Connection {
    private final String fromRef, toRef;
    private final String fromPort, toPort;

    public Connection(String fromRef, String toRef, String fromPort, String toPort) {
        this.fromRef = fromRef;
        this.toRef = toRef;
        this.fromPort = fromPort;
        this.toPort = toPort;
    }

    public String getFromRef() {
        return fromRef;
    }

    public String getToRef() {
        return toRef;
    }

    public String getFromPort() {
        return fromPort;
    }

    public String getToPort() {
        return toPort;
    }

    @Override
    public String toString() {
        return "<connection from=\"" + fromRef + ":" + fromPort + "\" to=\"" + toRef + ":" + toPort + "\" />";
    }
}
