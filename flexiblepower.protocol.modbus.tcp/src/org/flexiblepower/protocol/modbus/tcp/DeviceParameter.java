package org.flexiblepower.protocol.modbus.tcp;

import javax.measure.Measurable;
import javax.measure.Measure;
import javax.measure.quantity.Quantity;
import javax.measure.unit.Unit;

/**
 * A {@link DeviceParameter} is a description of a register or coil in a device. You can give it a description, address
 * and tell it if it is writable.
 */
public abstract class DeviceParameter implements Comparable<DeviceParameter> {
    /**
     * Creates a new {@link Coil}.
     *
     * @param description
     *            The human readable description what the coil represents (e.g. a on/off switch)
     * @param isWritable
     *            Tells if the coil can be changed from an external source
     * @param address
     *            The modbus address
     * @return The new {@link Coil} object
     */
    public static Coil coil(String description, boolean isWritable, int address) {
        return new Coil(description, isWritable, address);
    }

    /**
     * Creates a new {@link Register}.
     *
     * @param description
     *            The human readable description what the register represents (e.g. a temperatue measurement)
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
    public static <Q extends Quantity> Register<Q> register(String description,
                                                            boolean isWritable,
                                                            int address,
                                                            Unit<Q> unit,
                                                            boolean isSigned) {
        return new Register<Q>(description, isWritable, address, unit, isSigned);
    }

    private final String description;
    private final boolean isWritable;
    private final int address;

    DeviceParameter(String description, boolean isWritable, int address) {
        this.description = description;
        this.isWritable = isWritable;
        this.address = address;
    }

    /**
     * @return The human readable description what the parameter represents.
     */
    public String getDescription() {
        return description;
    }

    /**
     * @return if the parameter can be changed.
     */
    public boolean isWritable() {
        return isWritable;
    }

    /**
     * @return The modbus address.
     */
    public int getAddress() {
        return address;
    }

    @Override
    public int compareTo(DeviceParameter other) {
        if (getClass() != other.getClass()) {
            if (other instanceof Coil) {
                return -1;
            } else {
                return 1;
            }
        } else {
            return address - other.address;
        }
    }

    /**
     * This is the visitor pattern for the device parameters. This makes it easier to implement writers or reader for a
     * generic device parameter.
     *
     * @param visitor
     *            The {@link DeviceParameterVisitor} that will be called back on the correct type function.
     * @param <EX>
     *            The type of exception that will be thrown. This may be a RuntimeException when you don't want to catch
     *            anything.
     * @throws EX
     *             the exception that will be thrown through
     */
    abstract <EX extends Exception> void visit(DeviceParameterVisitor<EX> visitor) throws EX;

    /**
     * A switch in the device.
     */
    public static final class Coil extends DeviceParameter {
        private boolean value;

        Coil(String description, boolean isWritable, int address) {
            super(description, isWritable, address);
            value = false;
        }

        /**
         * @return the value of the coil.
         */
        public boolean getValue() {
            return value;
        }

        @Override
        <EX extends Exception> void visit(DeviceParameterVisitor<EX> visitor) throws EX {
            value = visitor.visit(this);
        }

        /**
         * Sets the value of this coil. This won't be written directly, only after a write action with the parameter has
         * been called.
         *
         * @param value
         *            The new value.
         * @return This {@link Coil}
         */
        public Coil setValue(boolean value) {
            this.value = value;
            return this;
        }
    }

    /**
     * A register in the device.
     *
     * @param <Q>
     *            The quantity that the value in the register represents.
     */
    public static final class Register<Q extends Quantity> extends DeviceParameter {
        private final Unit<Q> unit;
        private final boolean isSigned;
        private int value;

        Register(String description, boolean isWritable, int address, Unit<Q> unit, boolean isSigned) {
            super(description, isWritable, address);
            this.unit = unit;
            this.isSigned = isSigned;
        }

        /**
         * @return The value as a new {@link Measure} value with the correct unit.
         */
        public Measurable<Q> getMeasurable() {
            return Measure.valueOf(value, unit);
        }

        /**
         * @return The unit in which this register has been defined.
         */
        public Unit<Q> getUnit() {
            return unit;
        }

        /**
         * @return The raw value of this register.
         */
        public int getValue() {
            if (isSigned) {
                return (short) value;
            } else {
                return value;
            }
        }

        @Override
        <EX extends Exception> void visit(DeviceParameterVisitor<EX> visitor) throws EX {
            int value = visitor.visit(this);
            if (!isSigned) {
                this.value = (value & 0xffff);
            } else {
                this.value = value;
            }
        }

        /**
         * Sets the value of this register. This won't be written directly, only after a write action with the parameter
         * has been called.
         *
         * @param value
         *            The new value.
         * @return This {@link Register}
         */
        public Register<Q> setValue(int value) {
            this.value = value & 0xffff;
            return this;
        }

        /**
         * Sets the value of this register. This won't be written directly, only after a write action with the parameter
         * has been called.
         *
         * @param value
         *            The new value.
         * @return This {@link Register}
         */
        public Register<Q> setValue(Measurable<Q> value) {
            return setValue((int) value.longValue(getUnit()));
        }
    }
}
