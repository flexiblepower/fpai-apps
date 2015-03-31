package org.flexiblepower.protocol.modbus.tcp;

import java.nio.ByteBuffer;

public final class BitVector {
    private final int size;
    private final byte[] data;

    public BitVector(int size) {
        this.size = size;
        data = new byte[(size + 7) / 8];
    }

    public BitVector(int size, ByteBuffer buffer) {
        this(size);
        buffer.get(data);
    }

    public void writeTo(ByteBuffer buffer) {
        buffer.put(data);
    }

    public boolean getBit(int index) {
        return (data[byteIndex(index)] & (1 << bitIndex(index))) != 0;
    }

    public void setBit(int index, boolean value) {
        int byteNum = byteIndex(index);
        int bitNum = bitIndex(index);
        if (value) {
            data[byteNum] |= 1 << bitNum;
        } else {
            data[byteNum] &= ~(1 << bitNum);
        }
    }

    public int size() {
        return size;
    }

    public int byteSize() {
        return data.length;
    }

    @Override
    public String toString() {
        StringBuilder sb = new StringBuilder();
        for (int ix = 0; ix < size(); ix++) {
            sb.append(getBit(ix) ? '1' : '0');
            if (ix % 8 == 7) {
                sb.append(' ');
            }
        }
        return sb.toString();
    }

    private int bitIndex(int index) {
        if (index < 0 || index >= size) {
            throw new IndexOutOfBoundsException();
        }
        return index % 8;
    }

    private int byteIndex(int index) {
        if (index < 0 || index >= size) {
            throw new IndexOutOfBoundsException();
        }
        return index / 8;
    }
}
