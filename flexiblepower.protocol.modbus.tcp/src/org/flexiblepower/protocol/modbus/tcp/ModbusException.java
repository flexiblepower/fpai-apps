package org.flexiblepower.protocol.modbus.tcp;

import java.io.IOException;

public class ModbusException extends IOException {
    private static final long serialVersionUID = -6247248212816813920L;

    public ModbusException() {
    }
    
    public ModbusException(String message) {
        super(message);
    }
    
    public ModbusException(String message, Throwable throwable) {
        super(message, throwable);
    }
}
