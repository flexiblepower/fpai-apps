package de.fraunhofer.itwm.hexabus;

import java.io.IOException;
import java.net.InetAddress;
import java.net.UnknownHostException;
import java.util.HashMap;

public class HexabusDevice {
    private final InetAddress address;
    private final int port;
    private final Hexabus hexabus;
    private HashMap<Integer, HexabusEndpoint> endpoints;

    /**
     * @param address
     *            The InetAddress of the device
     */
    public HexabusDevice(Hexabus hexabus, InetAddress address, int port) {
        this.hexabus = hexabus;
        this.address = address;
        this.port = port;
        endpoints = new HashMap<Integer, HexabusEndpoint>();
        addEndpoint(0, Hexabus.DataType.UINT32, "Hexabus device descriptor");
    }

    public HexabusDevice(Hexabus hexabus, String address, int port) throws UnknownHostException {
        this(hexabus, InetAddress.getByName(address), port);
    }

    /**
     * Creates an Endpoint with specified EID, data type and description and adds it to the endpoints associated with
     * the device.
     * 
     * @param eid
     *            The EID of the new endpoint
     * @param datatype
     *            The data type of the new endpoint
     * @param description
     *            A description of the new endpoint
     * @return The newly created endpoint
     */
    public HexabusEndpoint addEndpoint(int eid, Hexabus.DataType datatype, String description) {
        HexabusEndpoint endpoint = new HexabusEndpoint(this, eid, datatype, description);
        endpoints.put(eid, endpoint);
        return endpoint;
    }

    /**
     * Creates an Endpoint with specified EID, data type and empty description and adds it to the endpoints associated
     * with the device.
     * 
     * @param eid
     *            The EID of the new endpoint
     * @param datatype
     *            The data type of the new endpoint
     * @return The newly created endpoint
     */
    public HexabusEndpoint addEndpoint(int eid, Hexabus.DataType datatype) {
        HexabusEndpoint endpoint = new HexabusEndpoint(this, eid, datatype);
        endpoints.put(eid, endpoint);
        return endpoint;
    }

    /**
     * @return The InetAddress of the device
     */
    public InetAddress getInetAddress() {
        return address;
    }

    /**
     * @return The endpoints that are associated with the device
     */
    public HashMap<Integer, HexabusEndpoint> getEndpoints() {
        return endpoints;
    }

    public HexabusEndpoint getByEid(int eid) {
        if (eid > 255) {
            throw new IllegalArgumentException("EID too large");
        }

        HexabusEndpoint endpoint = endpoints.get(eid);
        if (endpoint == null) {
            throw new IllegalArgumentException("EID not found");
        } else {
            return endpoint;
        }
    }

    /**
     * Sends an endpoint query to the device and its endpoints. Replaces the currently asscociated endpoints with the
     * result.
     */
    public HashMap<Integer, HexabusEndpoint> fetchEndpoints() throws IOException {
        HashMap<Integer, HexabusEndpoint> oldEndpoints = endpoints;
        endpoints = new HashMap<Integer, HexabusEndpoint>();
        addEndpoint(0, Hexabus.DataType.UINT32, "Hexabus device descriptor");
        long reply = 0;
        try {
            reply = getByEid(0).queryUint32Endpoint(); // Device Descriptor
        } catch (IllegalArgumentException e) {
            if (e.getMessage().substring(1, 21).equals("Error packet received")) {
                endpoints = oldEndpoints;
                // TODO rethrow?
                return null;
            }
        }
        boolean moreEids = true;
        int eidOffset = 0;
        while (moreEids) {
            for (int i = 1; i < 32; i++) {
                if (((reply >>> (i - 1)) & 1) != 0) {
                    HexabusEndpointQueryPacket epquery = new HexabusEndpointQueryPacket(i + eidOffset);
                    sendPacket(epquery);
                    HexabusPacket packet = hexabus.receivePacket();

                    switch (packet.getPacketType()) {
                    case ERROR:
                        throw new IOException("Error packet received: " + ((HexabusErrorPacket) packet).getErrorCode());
                    case EPINFO:
                        HexabusEndpointInfoPacket epinfo = (HexabusEndpointInfoPacket) packet;
                        Hexabus.DataType dataType = epinfo.getDataType();
                        String description = "";
                        description = epinfo.getDescription().trim();
                        addEndpoint(i, dataType, description);
                        break;
                    default:
                        throw new IOException("Unexpected reply received");
                    }
                }
            }

            eidOffset += 32;
            HexabusPacket packet = new HexabusQueryPacket(eidOffset);
            sendPacket(packet);
            // Receive reply
            packet = hexabus.receivePacket();
            switch (packet.getPacketType()) {
            case ERROR:
                moreEids = false;
                break;
            case INFO:
                reply = ((HexabusInfoPacket) packet).getUint32();
                addEndpoint(eidOffset, Hexabus.DataType.UINT32, "Hexabus device descriptor");
                break;
            default:
                throw new IllegalArgumentException("Unexpected reply received");
            }
        }

        return endpoints;
    }

    public void sendPacket(HexabusPacket packet) throws IOException {
        hexabus.sendPacket(packet, address, port);
    }

    public HexabusPacket receivePacket() throws IOException {
        return hexabus.receivePacket(address);
    }
}
