package nl.tno.hexabus.protocol;

import java.net.Inet6Address;
import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.Map;
import java.util.Queue;
import java.util.concurrent.ConcurrentLinkedQueue;

import nl.tno.hexabus.protocol.Channel.Communicator;
import nl.tno.hexabus.protocol.Packet.EndpointInfo;
import nl.tno.hexabus.protocol.Packet.EndpointQuery;
import nl.tno.hexabus.protocol.Packet.Error;
import nl.tno.hexabus.protocol.Packet.Info;
import nl.tno.hexabus.protocol.Packet.Query;
import nl.tno.hexabus.protocol.Packet.Write;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class Device implements Communicator {
    static final Logger log = LoggerFactory.getLogger(Device.class);

    public interface Listener {
        void updated(Endpoint<?> endpoint, Data data);
    }

    <D extends Data> Endpoint<D> createEndpoint(byte eid, Class<D> endpointType, String description) {
        return new Endpoint<D>(eid, endpointType, description);
    }

    public class Endpoint<D extends Data> {
        private final byte eid;
        private final Class<D> type;
        private final String description;
        private D data;

        protected Endpoint(int eid, Class<D> type, String description) {
            this.eid = (byte) eid;
            this.type = type;
            this.description = description;
            this.data = null;

            log.debug("Created {}_{} {}", address, port, this);
            query();
        }

        public byte getEid() {
            return eid;
        }

        public Class<D> getType() {
            return type;
        }

        public String getDescription() {
            return description;
        }

        public D getData() {
            return data;
        }

        @SuppressWarnings("unchecked")
        void update(Data data) {
            if (data.getClass() != type) {
                throw new IllegalArgumentException("Expected data of type [" + type.getSimpleName()
                                                   + "], received type ["
                                                   + data.getClass().getSimpleName()
                                                   + "]");
            }

            synchronized (this) {
                this.data = (D) data;
                notifyAll();
                log.debug("Updated {}_{} {}", address, port, this);

                if (listener != null) {
                    listener.updated(this, data);
                }
            }
        }

        public void query() {
            addRequest(new Packet.Query(address, port, eid));
        }

        public synchronized D queryAndWait() {
            query();
            try {
                wait(10000);
            } catch (InterruptedException e) {
            }
            return data;
        }

        @Override
        public String toString() {
            return "Endpoint <" + eid + ": " + description + "> (" + data + ")";
        }
    }

    class DescriptorEndpoint extends Endpoint<Data.UInt32> {
        protected DescriptorEndpoint(int eid) {
            super(eid, Data.UInt32.class, "Description of the endpoints [" + (eid + 1) + "-" + (eid + 31) + "]");
        }

        @Override
        public void update(Data data) {
            super.update(data);
            long endpoints = (Long) data.getValue();

            for (int i = 1; i < 32; i++) {
                if (((endpoints >>> (i - 1)) & 1) != 0) {
                    if (!Device.this.endpoints.containsKey(i + getEid())) {
                        addRequest(new Packet.EndpointQuery(address, port, i + getEid()));
                    }
                }
            }

            addRequest(new Packet.EndpointQuery(address, port, getEid() + 32));
        }
    }

    class PushButtonEndpoint extends Endpoint<Data.Bool> {
        protected PushButtonEndpoint(int eid, String description) {
            super(eid, Data.Bool.class, description);
        }

        @Override
        void update(Data data) {
            super.update(data);

            if ((Boolean) data.getValue()) {
                for (Endpoint<?> ep : getEndpoints()) {
                    ep.query();
                }
            }
        }
    }

    private final Inet6Address address;
    private final int port;
    private final Map<Byte, Endpoint<?>> endpoints;

    private volatile Packet pendingRequest;
    private final Queue<Packet> sendingQueue;

    public Device(Inet6Address address, int port) {
        this.address = address;
        this.port = port;
        endpoints = new HashMap<Byte, Device.Endpoint<?>>();

        pendingRequest = null;
        sendingQueue = new ConcurrentLinkedQueue<Packet>();

        // Start to query for the endpoints on the device
        addRequest(new Packet.EndpointQuery(address, port, 0));
    }

    private volatile Listener listener;

    public void setListener(Listener listener) {
        this.listener = listener;
    }

    @Override
    public boolean handlePacket(Packet packet, Channel channel) {
        packet.visit(packetHandler);
        return pendingRequest != null || !sendingQueue.isEmpty();
    }

    @Override
    public Packet getNextRequest() {
        // Resend if we didn't get an answer, otherwise try to determine the next one
        if (pendingRequest == null) {
            pendingRequest = sendingQueue.poll();
            if (pendingRequest != null) {
                log.trace("Sending next request {}, in queue {}", pendingRequest, sendingQueue);
            }
        } else {
            log.trace("Resending request {}, in queue {}", pendingRequest, sendingQueue);
        }
        return pendingRequest;
    }

    void addRequest(Packet packet) {
        synchronized (sendingQueue) {
            if (!packet.equals(pendingRequest) && !sendingQueue.contains(packet)) {
                sendingQueue.add(packet);
            }
        }
    }

    @Override
    public String toString() {
        return "Device on (" + address + " - " + port + ") -> " + endpoints.values();
    }

    public Collection<Endpoint<?>> getEndpoints() {
        return Collections.unmodifiableCollection(endpoints.values());
    }

    private final Packet.Visitor packetHandler = new Packet.Visitor() {
        @Override
        public void visit(EndpointInfo endpointInfo) {
            byte eid = endpointInfo.getData().getEid();
            Data.Text data = endpointInfo.getData();

            if (pendingRequest instanceof EndpointQuery && ((EndpointQuery) pendingRequest).getEid() == data.getEid()) {
                log.trace("Handling expected {}", endpointInfo);
                pendingRequest = null;
            } else {
                log.trace("Handling unexpected {}", endpointInfo);
            }

            if (endpointInfo.getEndpointType() != Data.Unknown.class) {
                if (!endpoints.containsKey(eid)) {
                    if ((eid & 31) == 0) {
                        endpoints.put(eid, new DescriptorEndpoint(eid));
                    } else if (data.getValue().contains("Pushbutton")) {
                        endpoints.put(eid, new PushButtonEndpoint(eid, data.getValue()));
                    } else {
                        endpoints.put(eid, createEndpoint(eid, endpointInfo.getEndpointType(), data.getValue()));
                    }
                }
            }
        }

        @Override
        public void visit(EndpointQuery endpointQuery) {
            // We should never receive this, so we'll ignore it
        }

        @Override
        public void visit(Error packet) {
            log.trace("The packet {} got an error response {}", pendingRequest, packet.getErrorCode());
            if (packet.getErrorCode() != Error.Code.CRCFAILED) {
                pendingRequest = null; // Drop the request that causes the error
            }
        }

        @Override
        public void visit(Info packet) {
            Data data = packet.getData();
            Endpoint<?> endpoint = endpoints.get(data.getEid());

            if (pendingRequest instanceof Query && ((Query) pendingRequest).getEid() == data.getEid()) {
                log.trace("Handling expected query {}", packet);
                pendingRequest = null;
            } else if (pendingRequest instanceof Write && ((Write) pendingRequest).getData().equals(packet.getData())) {
                log.trace("Handling expected write {}", packet);
                pendingRequest = null;
            } else {
                log.trace("Handling unexpected {}", packet);
            }

            if (endpoint != null) {
                endpoint.update(data);
            }
        }

        @Override
        public void visit(Query packet) {
            // We should never receive this, so we'll ignore it
        }

        @Override
        public void visit(Write write) {
            // We should never receive this, so we'll ignore it
        }
    };

    public <D extends Data> void write(D data) {
        Endpoint<?> endpoint = endpoints.get(data.getEid());
        if (endpoint == null) {
            log.warn("Trying to write data to a non-existing endpoint {}", data.getEid());
        } else if (endpoint.getType() != data.getClass()) {
            log.warn("Trying to write data of an wrong type to the endpoint {}, data = {}", endpoint, data);
        } else {
            addRequest(new Packet.Write(address, port, data));
        }
    }

    public Inet6Address getAddress() {
        return address;
    }
}
