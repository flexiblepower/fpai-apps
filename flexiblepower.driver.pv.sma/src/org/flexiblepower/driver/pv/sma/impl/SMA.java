package org.flexiblepower.driver.pv.sma.impl;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.URL;
import java.net.URLConnection;
import java.net.URLStreamHandler;
import java.net.URLStreamHandlerFactory;
import java.text.ParseException;
import java.util.HashMap;
import java.util.Map;

import javax.bluetooth.LocalDevice;

import org.flexiblepower.driver.pv.sma.impl.data.OperationInfo;
import org.flexiblepower.driver.pv.sma.impl.data.ProductionInfo;
import org.flexiblepower.driver.pv.sma.impl.data.SpotAcInfo;
import org.flexiblepower.driver.pv.sma.impl.request.DataRequestPacket;
import org.flexiblepower.driver.pv.sma.impl.request.LogOffPacket;
import org.flexiblepower.driver.pv.sma.impl.request.LogOnPacket;
import org.flexiblepower.driver.pv.sma.impl.response.DataResponsePacket;
import org.flexiblepower.driver.pv.sma.impl.response.DataResponsePacket.Code;
import org.flexiblepower.driver.pv.sma.impl.response.DataResponsePacket.Element;
import org.flexiblepower.driver.pv.sma.impl.utils.ByteUtils;
import org.flexiblepower.protocol.bluetooth.BluetoothUrlStreamHandler;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class SMA {

    private static final String INVERTER_ADDRESS = "00802529EC47";
    private static final String INVERTER_PASSWORD = "0000";

    private static final Logger logger = LoggerFactory.getLogger(SMA.class);

    private String sourceAddress = null;
    private String destinationAddress = null;
    private URLConnection connection = null;
    private InputStream is = null;
    private OutputStream os = null;

    public static void main(String[] args) {
        try {
            // Work-around to get the URL connector working without OSGi
            URL.setURLStreamHandlerFactory(new URLStreamHandlerFactory() {
                @Override
                public URLStreamHandler createURLStreamHandler(String protocol) {
                    if ("btspp".equals(protocol)) {
                        return new BluetoothUrlStreamHandler();
                    } else {
                        return null;
                    }
                }
            });

            SMA sma = new SMA();
            sma.openConnection(INVERTER_ADDRESS, INVERTER_PASSWORD);
            logger.info("Operation  : {}", sma.requestOperationInfo());
            logger.info("Production : {}", sma.requestProductionInfo());
            logger.info("Spot AC    : {}", sma.requestSpotAcInfo());
            sma.closeConnection();
        } catch (Exception e) {
            logger.error("Error communicating with SMA inverter", e);
        }
    }

    public void closeConnection() {
        if (is != null) {
            try {
                is.close();
            } catch (IOException e) {
            }
        }
        if (os != null) {
            try {
                os.close();
            } catch (IOException e) {
            }
        }
    }

    public void openConnection(String inverterAddress, String inverterPassword) throws IOException {
        logger.info("Opening connection with SMA inverter {}", inverterAddress);
        sourceAddress = LocalDevice.getLocalDevice().getBluetoothAddress();
        logger.debug("Found local bluetooth device with address {}", sourceAddress);

        destinationAddress = inverterAddress;
        connection = new URL("btspp://" + destinationAddress + ":1;authenticate=false;encrypt=false;master=false").openConnection();
        is = connection.getInputStream();
        os = connection.getOutputStream();

        // Handshake
        logger.debug("Reading handshake1");
        L1Packet handshake1 = L1Codec.readPacket(is);
        logger.debug("Handshake1: {}", handshake1);

        logger.debug("Writing handshake2");
        L1Codec.writePacket(new L1Packet(ByteUtils.BROADCAST_ADDRESS,
                                         destinationAddress,
                                         L1Command.HANDSHAKE_2,
                                         handshake1.getData()), os);

        logger.debug("Reading handshake3");
        L1Packet handshake3 = L1Codec.readPacket(is);
        logger.debug("Handshake3: {}", handshake3);

        logger.debug("Reading handshake4");
        L1Packet handshake4 = L1Codec.readPacket(is);
        logger.debug("Handshake4: {}", handshake4);

        logger.debug("Reading handshake5");
        L1Packet handshake5 = L1Codec.readPacket(is);
        logger.debug("Handshake5: {}", handshake5);

        // Log off
        logger.debug("Writing logoff");
        L2Codec.writePacket(LogOffPacket.create(sourceAddress), os);

        // Log on
        logger.debug("Writing logon");
        L2Codec.writePacket(LogOnPacket.create(sourceAddress, inverterPassword), os);
        L2Codec.readPacket(is);
    }

    private void checkConnected() throws IOException {
        if (is == null || os == null) {
            throw new IOException("SMA Connection was not opened");
        }
    }

    public OperationInfo requestOperationInfo() throws IOException, ParseException {
        checkConnected();

        logger.debug("Writing operation request");
        L2Codec.writePacket(DataRequestPacket.create(sourceAddress,
                                                     destinationAddress,
                                                     DataRequestPacket.Type.OPERATION_TIME), os);
        L2Packet resultPacket = L2Codec.readPacket(is);
        return new OperationInfo(DataResponsePacket.parse(resultPacket).getElements());
    }

    public ProductionInfo requestProductionInfo() throws IOException, ParseException {
        checkConnected();

        logger.debug("Writing production request");
        L2Codec.writePacket(DataRequestPacket.create(sourceAddress,
                                                     destinationAddress,
                                                     DataRequestPacket.Type.PRODUCTION), os);
        L2Packet resultPacket = L2Codec.readPacket(is);
        return new ProductionInfo(DataResponsePacket.parse(resultPacket).getElements());
    }

    public SpotAcInfo requestSpotAcInfo() throws IOException, ParseException {
        checkConnected();

        logger.debug("Writing spot AC request");

        L2Codec.writePacket(DataRequestPacket.create(sourceAddress,
                                                     destinationAddress,
                                                     DataRequestPacket.Type.SPOT_AC_POWER), os);
        L2Packet resultPacket = L2Codec.readPacket(is);
        Map<Code, Element> powerElements = DataResponsePacket.parse(resultPacket).getElements();

        L2Codec.writePacket(DataRequestPacket.create(sourceAddress,
                                                     destinationAddress,
                                                     DataRequestPacket.Type.SPOT_AC_FREQUENCY), os);
        resultPacket = L2Codec.readPacket(is);
        Map<Code, Element> frequencyElements = DataResponsePacket.parse(resultPacket).getElements();

        Map<Code, Element> elements = new HashMap<Code, Element>();
        elements.putAll(powerElements);
        elements.putAll(frequencyElements);

        return new SpotAcInfo(elements);
    }
}
