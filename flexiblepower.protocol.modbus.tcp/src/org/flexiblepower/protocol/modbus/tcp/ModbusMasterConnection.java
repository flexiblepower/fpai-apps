package org.flexiblepower.protocol.modbus.tcp;

import java.io.Closeable;
import java.io.IOException;
import java.net.InetSocketAddress;
import java.nio.ByteBuffer;
import java.nio.channels.ClosedChannelException;
import java.nio.channels.SocketChannel;
import java.util.Map;
import java.util.TreeMap;
import java.util.concurrent.atomic.AtomicInteger;

import javax.measure.Measurable;
import javax.measure.quantity.Quantity;

import org.flexiblepower.protocol.modbus.tcp.DeviceParameter.Coil;
import org.flexiblepower.protocol.modbus.tcp.DeviceParameter.Register;
import org.flexiblepower.protocol.modbus.tcp.ModbusMessage.Function;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class ModbusMasterConnection implements Closeable {
    private static final Logger logger = LoggerFactory.getLogger(ModbusMasterConnection.class);

    private final Device device;
    private final AtomicInteger transactionId;

    private SocketChannel channel;

    private volatile boolean closed;

    /**
     * Creates a new {@link ModbusMasterConnection} to the given device and opens the channel for communication.
     *
     * @param device
     *            The device to which we should open the channel.
     * @throws IOException
     *             When the connection could not be established (e.g. address could not be reached).
     */
    public ModbusMasterConnection(Device device) throws IOException {
        this.device = device;
        transactionId = new AtomicInteger(1);
        closed = false;
        openChannel();
    }

    private synchronized SocketChannel openChannel() throws IOException {
        if (channel != null && !channel.isConnected()) {
            close();
            channel = null;
        }

        if (channel == null) {
            if (closed) {
                throw new ClosedChannelException();
            }

            channel = SocketChannel.open();
            channel.connect(new InetSocketAddress(device.getAddress(), device.getPort()));
        }

        return channel;
    }

    @Override
    public synchronized void close() {
        try {
            channel.close();
        } catch (IOException ex) {
        } finally {
            channel = null;
        }
    }

    public synchronized void read(DeviceParameter parameter) throws IOException {
        try {
            final SocketChannel channel = openChannel();
            parameter.visit(new DeviceParameterVisitor<IOException>() {
                @Override
                public <Q extends Quantity> int visit(Register<Q> register) throws IOException {
                    synchronized (ModbusMasterConnection.this) {
                        Function function = register.isWritable() ? Function.READ_HOLDING_REGISTER
                                                                 : Function.READ_INPUT_REGISTER;
                        ModbusMessage coder = new ModbusMessage(device, transactionId.getAndIncrement());
                        ByteBuffer buffer = coder.startData(function)
                                                 .putWord(register.getAddress())
                                                 .putWord(1)
                                                 .finish();
                        channel.write(buffer);

                        ModbusMessage decoder = ModbusMessage.decode(channel);
                        ByteBuffer data = decoder.getData();
                        if (decoder.getFunction().isError()) {
                            throw new ModbusException(decoder.getFunction() + " code " + data.get());
                        } else {
                            int bytecount = data.get();
                            if (bytecount != 2) {
                                throw new ModbusException("Expected a single value as return");
                            }
                            return data.getShort();
                        }
                    }
                }

                @Override
                public boolean visit(Coil coil) throws IOException {
                    Function function = Function.READ_COIL;
                    ModbusMessage coder = new ModbusMessage(device, transactionId.getAndIncrement());
                    ByteBuffer buffer = coder.startData(function).putWord(coil.getAddress()).putWord(1).finish();
                    channel.write(buffer);

                    ModbusMessage decoder = ModbusMessage.decode(channel);
                    if (decoder.getFunction().isError()) {
                        throw new ModbusException("Error code " + decoder.getData().get());
                    } else {
                        ByteBuffer data = decoder.getData();
                        int bytecount = data.get();
                        if (bytecount != 1) {
                            throw new ModbusException("Expected a single value as return");
                        }
                        return data.get() != 0;
                    }
                }
            });
        } catch (ClosedChannelException ex) {
            logger.warn("Reading device parameters while already closed");
        } catch (IOException ex) {
            // I/O error while reading, close the socket and try again
            rethrow(ex);
        }
    }

    public synchronized Map<Integer, Short> readRegisters(int from, int count) throws IOException {
        SocketChannel channel = openChannel();

        try {
            ModbusMessage coder = new ModbusMessage(device, transactionId.getAndIncrement());
            ByteBuffer buffer = coder.startData(Function.READ_HOLDING_REGISTER)
                                     .putWord(from)
                                     .putWord(Math.min(125, count))
                                     .finish();
            channel.write(buffer);

            ModbusMessage decoder = ModbusMessage.decode(channel);
            ByteBuffer data = decoder.getData();
            if (decoder.getFunction().isError()) {
                throw new ModbusException(decoder.getFunction() + " code " + data.get());
            } else {
                int bytecount = data.get() & 0xff;
                Map<Integer, Short> result = new TreeMap<Integer, Short>();
                for (int registerId = from, bytes = 0; bytes < bytecount; bytes += 2, registerId++) {
                    short newValue = data.getShort();
                    Short oldValue = result.put(registerId, newValue);
                    if (oldValue != null && oldValue != newValue) {
                        logger.debug("registerId " + registerId + " oldValue=" + oldValue + " newValue=" + newValue);
                    }
                }
                return result;
            }
        } catch (IOException ex) {
            // I/O error while reading, close the socket and try again
            return rethrow(ex);
        }
    }

    public synchronized void write(Coil coil, boolean value) throws IOException {
        SocketChannel channel = openChannel();

        try {
            Function function = Function.WRITE_SINGLE_COIL;
            ModbusMessage coder = new ModbusMessage(device, transactionId.getAndIncrement());
            ByteBuffer buffer = coder.startData(function)
                                     .putWord(coil.getAddress())
                                     .putWord(value ? 0xff00 : 0)
                                     .finish();
            channel.write(buffer);

            ModbusMessage decoder = ModbusMessage.decode(channel);
            if (decoder.getFunction().isError()) {
                throw new ModbusException("Error code " + decoder.getData().get());
            } else {
                coil.setValue(decoder.getData().getShort() != 0);
            }
        } catch (IOException ex) {
            // I/O error while reading, close the socket and try again
            rethrow(ex);
        }
    }

    public synchronized <Q extends Quantity> void write(Register<Q> register, Measurable<Q> value) throws IOException {
        write(register, (int) value.longValue(register.getUnit()));
    }

    public synchronized <Q extends Quantity> void write(Register<Q> register, int value) throws IOException {
        SocketChannel channel = openChannel();

        try {
            Function function = Function.WRITE_SINGLE_REGISTER;
            ModbusMessage coder = new ModbusMessage(device, transactionId.getAndIncrement());
            ByteBuffer buffer = coder.startData(function).putWord(register.getAddress()).putWord(value).finish();
            channel.write(buffer);

            ModbusMessage decoder = ModbusMessage.decode(channel);
            if (decoder.getFunction().isError()) {
                throw new ModbusException("Error code " + decoder.getData().get());
            } else {
                ByteBuffer data = decoder.getData();
                short address = data.getShort();
                assert address == register.getAddress();
                register.setValue(data.getShort());
            }
        } catch (IOException ex) {
            // I/O error while reading, close the socket and try again
            rethrow(ex);
        }
    }

    private <T> T rethrow(IOException ex) throws IOException {
        try {
            channel.close();
        } catch (IOException ex1) {
        }
        channel = null;
        throw ex;
    }
}
