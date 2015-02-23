package org.flexiblepower.protocol.rxtx.impl;

import gnu.io.SerialPort;
import gnu.io.SerialPortEvent;
import gnu.io.SerialPortEventListener;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.io.PipedInputStream;
import java.io.PipedOutputStream;
import java.util.TooManyListenersException;

import org.flexiblepower.protocol.rxtx.Connection;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class SerialConnection implements Connection, SerialPortEventListener {
    private static final Logger log = LoggerFactory.getLogger(SerialConnection.class);

    private final SerialPort serialPort;
    private final InputStream inputStream;
    private final OutputStream outputStream;
    private final PipedOutputStream src;

    public SerialConnection(SerialPort serialPort) throws IOException {
        this.serialPort = serialPort;
        try {
            serialPort.addEventListener(this);
            serialPort.notifyOnDataAvailable(true);
        } catch (TooManyListenersException e) {
            // We are always the first to connect, so this should never happen
            throw new AssertionError(e);
        }

        src = new PipedOutputStream();
        inputStream = new PipedInputStream(src, 4096);
        outputStream = serialPort.getOutputStream();
    }

    @Override
    public InputStream getInputStream() {
        return inputStream;
    }

    @Override
    public OutputStream getOutputStream() {
        return outputStream;
    }

    @Override
    public void close() {
        serialPort.close();
        try {
            inputStream.close();
        } catch (IOException e) {
        }
    }

    @Override
    public void serialEvent(SerialPortEvent ev) {
        if (ev.getEventType() == SerialPortEvent.DATA_AVAILABLE) {
            int read = 0;
            try {
                while ((read = serialPort.getInputStream().read()) >= 0) {
                    src.write(read);
                }
            } catch (IOException ex) {
                log.error("I/O error while reading serial connection", ex);
            }
        }
    }
}
