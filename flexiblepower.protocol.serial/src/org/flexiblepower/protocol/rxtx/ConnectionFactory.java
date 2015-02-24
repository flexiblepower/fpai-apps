package org.flexiblepower.protocol.rxtx;

import java.io.IOException;

public interface ConnectionFactory {
    /**
     * @param portName
     *            The name of the port to be opened. On Windows this will be something like COM1, on Unix this could be
     *            something like /dev/tty1.
     * @param options
     *            The options of the serial port.
     * @return A {@link Connection} object that represents the input- and outputstream. It will never return
     *         <code>null</code>.
     * @throws IOException
     *             When the connection could not be opened. This can be either due to the fact that the port is not
     *             defined, already in use, doesn't support the options or any other lower level I/O errors.
     * @throws NullPointerException
     *             when the portName or the options are <code>null</code>.
     */
    Connection openSerialConnection(String portName, SerialConnectionOptions options) throws IOException;
}
