package org.flexiblepower.protocol.rxtx;

import java.io.Closeable;
import java.io.InputStream;
import java.io.OutputStream;

public interface Connection extends Closeable {
    InputStream getInputStream();

    OutputStream getOutputStream();

    @Override
    void close();
}
