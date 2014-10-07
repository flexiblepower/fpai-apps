package org.flexiblepower.protocol.rxtx.test;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;

import org.flexiblepower.protocol.rxtx.Connection;
import org.flexiblepower.protocol.rxtx.ConnectionFactory;
import org.flexiblepower.protocol.rxtx.SerialConnectionOptions;
import org.flexiblepower.protocol.rxtx.SerialConnectionOptions.Baudrate;
import org.flexiblepower.protocol.rxtx.SerialConnectionOptions.Databits;
import org.flexiblepower.protocol.rxtx.SerialConnectionOptions.Parity;
import org.flexiblepower.protocol.rxtx.SerialConnectionOptions.Stopbits;
import org.flexiblepower.protocol.rxtx.impl.ConnectionFactoryImpl;

public class ConsoleTest {
    public static void main(String[] args) throws IOException {
        ConnectionFactory factory = new ConnectionFactoryImpl();
        Connection connection = factory.openSerialConnection("COM10", new SerialConnectionOptions(Baudrate.B9600,
                                                                                                  Databits.D7,
                                                                                                  Stopbits.S1,
                                                                                                  Parity.Mark));

        BufferedReader reader = new BufferedReader(new InputStreamReader(connection.getInputStream()));
        String line = null;
        while ((line = reader.readLine()) != null) {
            System.out.println(line);
        }
    }
}
