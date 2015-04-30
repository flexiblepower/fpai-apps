package org.flexiblepower.protocol.rxtx.impl;

import gnu.io.CommPortIdentifier;
import gnu.io.PortInUseException;
import gnu.io.SerialPort;
import gnu.io.UnsupportedCommOperationException;

import java.io.IOException;
import java.util.Enumeration;

import org.flexiblepower.protocol.rxtx.Connection;
import org.flexiblepower.protocol.rxtx.ConnectionFactory;
import org.flexiblepower.protocol.rxtx.SerialConnectionOptions;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class ConnectionFactoryImpl implements ConnectionFactory {
    private static final Logger log = LoggerFactory.getLogger(ConnectionFactoryImpl.class);

    @Override
    public Connection openSerialConnection(String portName, SerialConnectionOptions options) throws IOException {
        @SuppressWarnings("unchecked")
        Enumeration<CommPortIdentifier> portList = CommPortIdentifier.getPortIdentifiers();

        SerialPort serialPort = null;

        while (portList.hasMoreElements()) {
            CommPortIdentifier commPortIdentifier = portList.nextElement();
            log.debug("Found port {}", commPortIdentifier.getName());

            if (commPortIdentifier.getName().equals(portName)) {
                try {
                    serialPort = (SerialPort) commPortIdentifier.open("p1meter", 2000);
                } catch (PortInUseException e) {
                    String msg = "The port " + portName + " is already in use";
                    log.warn(msg, e);
                    throw new IOException(msg, e);
                }

                try {
                    serialPort.setSerialPortParams(options.getBaudrate().getBaudrate(),
                                                   options.getDatabits().getDatabits(),
                                                   options.getStopbits().getStopbits(),
                                                   options.getParity().getParity());
                } catch (UnsupportedCommOperationException e) {
                    String msg = "Unsupported options " + options + " for port " + portName;
                    log.error(msg, e);
                    throw new IOException(msg, e);
                }

                return new SerialConnection(serialPort);
            }
        }

        throw new IOException("Could not find port " + portName);
    }
}
