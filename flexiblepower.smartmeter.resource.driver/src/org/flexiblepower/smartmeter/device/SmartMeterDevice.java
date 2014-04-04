package org.flexiblepower.smartmeter.device;

import gnu.io.CommPortIdentifier;
import gnu.io.PortInUseException;
import gnu.io.SerialPort;
import gnu.io.SerialPortEvent;
import gnu.io.SerialPortEventListener;
import gnu.io.UnsupportedCommOperationException;

import java.io.IOException;
import java.nio.ByteBuffer;
import java.util.Enumeration;
import java.util.TooManyListenersException;

import org.flexiblepower.smartmeter.resource.driver.SmartMeterDriverImpl;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class SmartMeterDevice implements SerialPortEventListener, Runnable {

    private final Logger logger = LoggerFactory.getLogger(getClass());

    private final SmartMeterDriverImpl resourceDriver;
    private SerialPort serialPort;
    private String portName = "COM23"; // "/dev/ttyUSB0";
    private ByteBuffer readByteBuffer;
    private final int START_CHARACTER = 47; // "/"
    private final int FINISH_CHARACTER = 33; // "!";

    private SmartMeterListener smartMeterListener;

    protected int maxBufferSize = 1024;

    public SmartMeterDevice(String portName, SmartMeterDriverImpl resourceDriver) {
        this.portName = portName;
        this.resourceDriver = resourceDriver;
    }

    public void init() {

        logger.info("Initializing SmartMeterDevice");

        smartMeterListener = new SmartMeterListenerImpl();

        Enumeration<CommPortIdentifier> portList = CommPortIdentifier.getPortIdentifiers();

        int count = 0;

        while (portList.hasMoreElements()) {
            count++;
            CommPortIdentifier commPortIdentifier = portList.nextElement();
            logger.info("Port found: " + commPortIdentifier.getName());

            if (commPortIdentifier.getName().equals(portName)) {
                try {
                    logger.info("Serial Port Found1: " + portName);
                    serialPort = (SerialPort) commPortIdentifier.open("p1meter", 2000);
                    logger.info("Serial Port Found2: " + portName);
                } catch (PortInUseException e) {
                    logger.error(e.toString(), e);
                }

                try {
                    serialPort.addEventListener(this);
                } catch (TooManyListenersException e) {
                    logger.error(e.toString(), e);
                }

                serialPort.notifyOnDataAvailable(true);

                try {
                    serialPort.setSerialPortParams(9600,
                                                   SerialPort.DATABITS_7,
                                                   SerialPort.STOPBITS_1,
                                                   SerialPort.PARITY_EVEN);
                } catch (UnsupportedCommOperationException e) {
                    logger.error(e.toString(), e);
                }
            }
        }

        readByteBuffer = ByteBuffer.allocate(maxBufferSize);

        logger.info("Finished initializing SmartMeterDevice. Found " + count + " devices");
    }

    @Override
    public void serialEvent(SerialPortEvent serialPortEvent) {
        if (serialPortEvent.getEventType() == SerialPortEvent.DATA_AVAILABLE) {

            try {
                int nextByte;

                while ((nextByte = serialPort.getInputStream().read()) != -1) {
                    readByteBuffer.put((byte) nextByte);

                    if (nextByte == FINISH_CHARACTER) {
                        smartMeterListener.put(new String(readByteBuffer.array()), resourceDriver);
                        readByteBuffer.clear();
                    }
                }
            } catch (IOException e) {
                logger.error(e.toString(), e);
            }
        }
    }

    public void setSmartMeterListener(SmartMeterListener smartMeterListener) {
        this.smartMeterListener = smartMeterListener;
    }

    public void disconnect() {
        if (serialPort != null) {
            // close the i/o streams.
            serialPort.close();

        }
    }

    @Override
    public void run() {
        init();
    }
}
