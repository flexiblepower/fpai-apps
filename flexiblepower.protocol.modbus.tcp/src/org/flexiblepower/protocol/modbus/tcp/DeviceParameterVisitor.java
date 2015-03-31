package org.flexiblepower.protocol.modbus.tcp;

import javax.measure.quantity.Quantity;

import org.flexiblepower.protocol.modbus.tcp.DeviceParameter.Coil;
import org.flexiblepower.protocol.modbus.tcp.DeviceParameter.Register;

/**
 * The visitor part of the visitor pattern that can be applied to {@link DeviceParameter}s.
 *
 * @param <EX>
 *            The type of exception that could be thrown by the visitor
 */
interface DeviceParameterVisitor<EX extends Exception> {
    /**
     * @param coil
     *            The coil that has been visited
     * @return The value of that coil
     * @throws EX
     *             Anything
     */
    boolean visit(Coil coil) throws EX;

    /**
     * @param register
     *            The {@link Register} that has been visited
     * @return The value of that register
     * @throws EX
     *             Anything
     * @param <Q>
     *            The quantity type of the register
     */
    <Q extends Quantity> int visit(Register<Q> register) throws EX;
}
