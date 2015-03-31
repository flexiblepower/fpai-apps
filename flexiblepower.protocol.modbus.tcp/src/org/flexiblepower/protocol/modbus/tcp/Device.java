package org.flexiblepower.protocol.modbus.tcp;

import java.io.IOException;
import java.net.InetAddress;
import java.net.UnknownHostException;
import java.util.Collection;
import java.util.Collections;
import java.util.SortedMap;
import java.util.SortedSet;
import java.util.TreeMap;
import java.util.TreeSet;

import javax.measure.quantity.Quantity;
import javax.measure.unit.Unit;

import org.flexiblepower.protocol.modbus.tcp.DeviceParameter.Coil;
import org.flexiblepower.protocol.modbus.tcp.DeviceParameter.Register;

/**
 * A {@link Device} is a description of a device that is reachable through modbus. It defines how it can be reached and
 * which DeviceParameters it has. To create a Device, use the {@link Builder} through the {@link #host(String)} or
 * {@link #address(InetAddress)} methods.
 */
public final class Device {
    /**
     * The default TCP port on which Modbus over TCP will run: 502.
     */
    public static final int DEFAULT_PORT = 502;
    /**
     * The default deviceId that will be used when not configured: 0.
     */
    public static final int DEFAULT_DEVICE_ID = 0;

    /**
     * Starts creating a new {@link Device} using the hostname as a base.
     *
     * @param hostname
     *            The internet hostname that will be used to determine where the device can be reached.
     * @return A {@link Builder} object that can be used to further configure the device.
     * @throws UnknownHostException
     *             When the hostname can not be translated into an IP address.
     */
    public static Device.Builder host(String hostname) throws UnknownHostException {
        return new Device.Builder(InetAddress.getByName(hostname));
    }

    /**
     * Starts creating a new {@link Device} using the {@link InetAddress} as a base.
     *
     * @param address
     *            The {@link InetAddress} that will be used to determine where the device can be reached.
     * @return A {@link Builder} object that can be used to further configure the device.
     */
    public static Device.Builder address(InetAddress address) {
        return new Device.Builder(address);
    }

    /**
     * This object can be used to create a {@link Device}. After configuring the device, you should call the
     * {@link #create()} method to build it finally. Get a {@link Builder} through the {@link Device#host(String)} or
     * {@link Device#address(InetAddress)} methods.
     */
    public static final class Builder {
        private final InetAddress address;
        private int port;
        private int deviceId;
        private final SortedSet<DeviceParameter> parameters;

        Builder(InetAddress address) {
            if (address == null) {
                throw new NullPointerException("address");
            }
            this.address = address;
            port = DEFAULT_PORT;
            deviceId = DEFAULT_DEVICE_ID;
            parameters = new TreeSet<DeviceParameter>();
        }

        /**
         * Sets the portnumber.
         *
         * @param port
         *            The TCP portnumber on which the device can be reached.
         * @return This builder.
         */
        public Builder setPort(int port) {
            if (port <= 0 || port > 65535) {
                throw new IllegalArgumentException("Illegal port number [1-65535], but was " + port);
            }
            this.port = port;
            return this;
        }

        /**
         * Sets the device identifier.
         *
         * @param deviceId
         *            The device identifier in the modbus protocol.
         * @return This builder.
         */
        public Builder setDeviceId(int deviceId) {
            if (deviceId < 0 || deviceId > 255) {
                throw new IllegalArgumentException("Illegal device id [0-255], but was " + deviceId);
            }
            this.deviceId = deviceId;
            return this;
        }

        /**
         * Adds a {@link DeviceParameter} to the {@link Device} that is being constructed. This device parameter can
         * later be used to read or write specific values.
         *
         * @param deviceParameter
         *            The {@link DeviceParameter} that
         * @return This builder.
         */
        public Builder add(DeviceParameter deviceParameter) {
            if (deviceParameter != null) {
                parameters.add(deviceParameter);
            }
            return this;
        }

        /**
         * Creates a new {@link Coil} and adds it to the {@link Device} that is being constructed.
         *
         * @param description
         *            The human readable description what the coil represents (e.g. a on/off switch)
         * @param isWritable
         *            Tells if the coil can be changed from an external source
         * @param address
         *            The modbus address
         * @return This builder
         */
        public Builder coil(String description, boolean isWritable, int address) {
            return add(DeviceParameter.coil(description, isWritable, address));
        }

        /**
         * Creates a new {@link Register} and adds it to the {@link Device} that is being constructed.
         *
         * @param description
         *            The human readable description what the register represents (e.g. a temperature measurement)
         * @param isWritable
         *            Tells if the register can be changed from an external source
         * @param address
         *            The modbus address
         * @param unit
         *            The unit what the address represents
         * @param isSigned
         *            Whether the value is signed or not in the protocol
         * @param <Q>
         *            The type of quantity, as determined by the unit
         * @return The new {@link Register} object
         */
        public <Q extends Quantity> Builder register(String description,
                                                     boolean isWritable,
                                                     int address,
                                                     Unit<Q> unit,
                                                     boolean isSigned) {
            return add(DeviceParameter.<Q> register(description, isWritable, address, unit, isSigned));
        }

        /**
         * @return The new {@link Device} that has been created. After this call, the builder should be discarded on not
         *         be used anymore.
         */
        public Device create() {
            return new Device(address, port, deviceId, parameters);
        }
    }

    private final InetAddress address;
    private final int port;
    private final int deviceId;

    private final SortedMap<Integer, Coil> coils;
    private final SortedMap<Integer, Register<?>> registers;

    private Device(InetAddress address, int port, int deviceId, Collection<DeviceParameter> parameters) {
        this.address = address;
        this.port = port;
        this.deviceId = deviceId & 0xff;

        SortedMap<Integer, Coil> coils = new TreeMap<Integer, Coil>();
        SortedMap<Integer, Register<?>> registers = new TreeMap<Integer, Register<?>>();
        for (DeviceParameter dp : parameters) {
            if (dp instanceof Coil) {
                coils.put(dp.getAddress(), (Coil) dp);
            } else if (dp instanceof Register) {
                registers.put(dp.getAddress(), (Register<?>) dp);
            }
        }

        this.coils = Collections.unmodifiableSortedMap(coils);
        this.registers = Collections.unmodifiableSortedMap(registers);
    }

    /**
     * @return An {@link Iterable} object that returns all the {@link Coil}s that have been defined.
     */
    public Iterable<Coil> coils() {
        return coils.values();
    }

    /**
     * @return An {@link Iterable} object that returns all the {@link Register}s that have been defined.
     */
    public Iterable<Register<?>> registers() {
        return registers.values();
    }

    /**
     * @param address
     *            The modbus address of the coil.
     * @return The {@link Coil} that has been defined on that address.
     * @throws IllegalArgumentException
     *             When no {@link Coil} has bee defined on that address.
     */
    public Coil getCoil(int address) {
        Coil coil = coils.get(address);
        if (coil == null) {
            throw new IllegalArgumentException("No coil has been defined at this address");
        } else {
            return coil;
        }
    }

    /**
     * @param address
     *            The modbus address of the register.
     * @return The {@link Register} that has been defined on that address.
     * @throws IllegalArgumentException
     *             When no {@link Register} has bee defined on that address.
     */
    public Register<?> getRegister(int address) {
        Register<?> register = registers.get(address);
        if (register == null) {
            throw new IllegalArgumentException("No register has been defined at this address");
        } else {
            return register;
        }
    }

    /**
     * @return The {@link InetAddress} on which this device can be reached.
     */
    public InetAddress getAddress() {
        return address;
    }

    /**
     * @return The TCP port number on which this device can be reached.
     */
    public int getPort() {
        return port;
    }

    /**
     * @return The modbus device identifier on which this device can be reached.
     */
    public int getDeviceId() {
        return deviceId;
    }

    /**
     * @return A new {@link ModbusMasterConnection} to this device.
     * @throws IOException
     *             When the connection could not be established (e.g. address could not be reached).
     */
    public ModbusMasterConnection openConnection() throws IOException {
        return new ModbusMasterConnection(this);
    }

    @Override
    public int hashCode() {
        return 17 * deviceId + 31 * address.hashCode() + 63 * coils.hashCode() + 67 * registers.hashCode() + 97 * port;
    }

    @Override
    public boolean equals(Object obj) {
        if (this == obj) {
            return true;
        } else if (obj == null || getClass() != obj.getClass()) {
            return false;
        } else {
            Device other = (Device) obj;
            return deviceId == other.deviceId
                   && address.equals(other.address)
                   && coils.equals(other.coils)
                   && registers.equals(other.registers)
                   && port == other.port;
        }
    }
}
