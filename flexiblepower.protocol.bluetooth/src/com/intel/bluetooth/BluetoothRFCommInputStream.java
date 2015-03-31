/**
 *  BlueCove - Java library for Bluetooth
 *  Copyright (C) 2004 Intel Corporation
 *
 *  Licensed to the Apache Software Foundation (ASF) under one
 *  or more contributor license agreements.  See the NOTICE file
 *  distributed with this work for additional information
 *  regarding copyright ownership.  The ASF licenses this file
 *  to you under the Apache License, Version 2.0 (the
 *  "License"); you may not use this file except in compliance
 *  with the License.  You may obtain a copy of the License at
 *
 *    http://www.apache.org/licenses/LICENSE-2.0
 *
 *  Unless required by applicable law or agreed to in writing,
 *  software distributed under the License is distributed on an
 *  "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY
 *  KIND, either express or implied.  See the License for the
 *  specific language governing permissions and limitations
 *  under the License.
 *
 *  @version $Id: BluetoothRFCommInputStream.java 2484 2008-12-03 17:21:59Z skarzhevskyy $
 */
package com.intel.bluetooth;

import java.io.IOException;
import java.io.InputStream;

class BluetoothRFCommInputStream extends InputStream {

	volatile private BluetoothRFCommConnection conn;

	public BluetoothRFCommInputStream(BluetoothRFCommConnection conn) {
		this.conn = conn;
	}

	/*
	 * returns the amount of data that can be read in a single call to the read function,
	 * which may not be the same as the total amount of data queued on the socket.
	 *
	 */
	public synchronized int available() throws IOException {
		if (conn == null) {
			throw new IOException("Stream closed");
		} else {
			return conn.bluetoothStack.connectionRfReadAvailable(conn.handle);
		}
	}

	/*
	 * Reads the next byte of data from the input stream. The value byte is
	 * returned as an int in the range 0 to 255. If no byte is available because
	 * the end of the stream has been reached, the value -1 is returned. This
	 * method blocks until input data is available, the end of the stream is
	 * detected, or an exception is thrown. A subclass must provide an
	 * implementation of this method.
	 *
	 * Returns: the next byte of data, or -1 if the end of the stream is
	 * reached. Throws: IOException - if an I/O error occurs.
	 */

	public int read() throws IOException {
		if (conn == null) {
			throw new IOException("Stream closed");
		} else {
			try {
                return conn.bluetoothStack.connectionRfRead(conn.handle);
            } catch (IOException e) {
                if (isClosed()) {
                    return -1;
                } else {
                    throw e;
                }
            }
		}
	}

	/*
	 * Reads up to len bytes of data from the input stream into an array of
	 * bytes. An attempt is made to read as many as len bytes, but a smaller
	 * number may be read, possibly zero. The number of bytes actually read is
	 * returned as an integer. This method blocks until input data is available,
	 * end of file is detected, or an exception is thrown.
	 *
	 * If b is null, a NullPointerException is thrown.
	 *
	 * If off is negative, or len is negative, or off+len is greater than the
	 * length of the array b, then an IndexOutOfBoundsException is thrown.
	 *
	 * If len is zero, then no bytes are read and 0 is returned; otherwise,
	 * there is an attempt to read at least one byte. If no byte is available
	 * because the stream is at end of file, the value -1 is returned;
	 * otherwise, at least one byte is read and stored into b.
	 *
	 * The first byte read is stored into element b[off], the next one into
	 * b[off+1], and so on. The number of bytes read is, at most, equal to len.
	 * Let k be the number of bytes actually read; these bytes will be stored in
	 * elements b[off] through b[off+k-1], leaving elements b[off+k] through
	 * b[off+len-1] unaffected.
	 *
	 * In every case, elements b[0] through b[off] and elements b[off+len]
	 * through b[b.length-1] are unaffected.
	 *
	 * If the first byte cannot be read for any reason other than end of file,
	 * then an IOException is thrown. In particular, an IOException is thrown if
	 * the input stream has been closed.
	 *
	 * The read(b, off, len) method for class InputStream simply calls the
	 * method read() repeatedly. If the first such call results in an
	 * IOException, that exception is returned from the call to the read(b, off,
	 * len) method. If any subsequent call to read() results in a IOException,
	 * the exception is caught and treated as if it were end of file; the bytes
	 * read up to that point are stored into b and the number of bytes read
	 * before the exception occurred is returned. Subclasses are encouraged to
	 * provide a more efficient implementation of this method.
	 *
	 * Parameters: b - the buffer into which the data is read. off - the start
	 * offset in array b at which the data is written. len - the maximum number
	 * of bytes to read. Returns: the total number of bytes read into the
	 * buffer, or -1 if there is no more data because the end of the stream has
	 * been reached. Throws: IOException - if an I/O error occurs. See Also:
	 * read()
	 */

	public int read(byte[] b, int off, int len) throws IOException {
		if (off < 0 || len < 0 || off + len > b.length) {
			throw new IndexOutOfBoundsException();
		}

		if (conn == null) {
			throw new IOException("Stream closed");
		} else {
			if (len == 0) {
				// If the length of b is zero, then no bytes are read and 0 is returned
				return 0;
			}
			// otherwise, there is an attempt to read at least one byte.
			try {
			    return conn.bluetoothStack.connectionRfRead(conn.handle, b, off, len);
			} catch (IOException e) {
                if (isClosed()) {
                    return -1;
                } else {
                    throw e;
                }
            }
		}
	}

	/**
	 * Closes this input stream and releases any system resources associated with the stream.
	 * <p>
	 * The general contract of close is that it closes the stream. A
	 * closed stream cannot perform output operations and cannot be reopened.
	 *
	 * @throws IOException
	 *             If an I/O error occurs
	 */
	public void close() throws IOException {
		// Function is not synchronized
		BluetoothRFCommConnection c = conn;
		if (c != null) {
			conn = null;
			c.streamClosed();
		}
	}

	boolean isClosed() {
		return this.conn == null;
	}

}