/**
 *  BlueCove - Java library for Bluetooth
 *  Copyright (C) 2006-2008 Vlad Skarzhevskyy
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
 *  @author vlads
 *  @version $Id: BluetoothL2CAPConnection.java 2476 2008-12-01 17:41:59Z skarzhevskyy $
 */
package com.intel.bluetooth;

import java.io.IOException;

import javax.bluetooth.L2CAPConnection;
import javax.bluetooth.RemoteDevice;
import javax.bluetooth.ServiceRecord;

/**
 *
 *
 */
abstract class BluetoothL2CAPConnection implements L2CAPConnection, BluetoothConnectionAccess {

	protected BluetoothStack bluetoothStack;

	protected volatile long handle;

	protected int securityOpt;

	private RemoteDevice remoteDevice;

	private boolean isClosed;

	protected BluetoothL2CAPConnection(BluetoothStack bluetoothStack, long handle) {
		this.bluetoothStack = bluetoothStack;
		this.handle = handle;
		this.isClosed = false;
	}

	/*
	 * (non-Javadoc)
	 *
	 * @see com.intel.bluetooth.BluetoothConnectionAccess#getRemoteAddress()
	 */
	public long getRemoteAddress() throws IOException {
		if (isClosed) {
			throw new IOException("Connection closed");
		}
		return bluetoothStack.l2RemoteAddress(handle);
	}

	/*
	 * (non-Javadoc)
	 *
	 * @see javax.bluetooth.L2CAPConnection#getReceiveMTU()
	 */
	public int getReceiveMTU() throws IOException {
		if (isClosed) {
			throw new IOException("Connection closed");
		}
		return bluetoothStack.l2GetReceiveMTU(handle);
	}

	/*
	 * (non-Javadoc)
	 *
	 * @see javax.bluetooth.L2CAPConnection#getTransmitMTU()
	 */
	public int getTransmitMTU() throws IOException {
		if (isClosed) {
			throw new IOException("Connection closed");
		}
		return bluetoothStack.l2GetTransmitMTU(handle);
	}

	/*
	 * (non-Javadoc)
	 *
	 * @see javax.bluetooth.L2CAPConnection#ready()
	 */
	public boolean ready() throws IOException {
		if (isClosed) {
			throw new IOException("Connection closed");
		}
		return bluetoothStack.l2Ready(handle);
	}

	/*
	 * (non-Javadoc)
	 *
	 * @see javax.bluetooth.L2CAPConnection#receive(byte[])
	 */
	public int receive(byte[] inBuf) throws IOException {
		if (isClosed) {
			throw new IOException("Connection closed");
		}
		if (inBuf == null) {
			throw new NullPointerException("inBuf is null");
		}
		return bluetoothStack.l2Receive(handle, inBuf);
	}

	/*
	 * (non-Javadoc)
	 *
	 * @see javax.bluetooth.L2CAPConnection#send(byte[])
	 */
	public void send(byte[] data) throws IOException {
		if (isClosed) {
			throw new IOException("Connection closed");
		}
		if (data == null) {
			throw new NullPointerException("data is null");
		}
		bluetoothStack.l2Send(handle, data);
	}

	abstract void closeConnectionHandle(long handle) throws IOException;

	/*
	 * (non-Javadoc)
	 *
	 * @see javax.microedition.io.Connection#close()
	 */
	public void close() throws IOException {
		if (isClosed) {
			return;
		}

		isClosed = true;
		shutdown();
	}

	/*
	 * (non-Javadoc)
	 *
	 * @see com.intel.bluetooth.BluetoothConnectionAccess#shutdown()
	 */
	public void shutdown() throws IOException {
		if (handle != 0) {
			DebugLog.debug("closing L2CAP Connection", handle);
			// close() can be called safely in another thread
			long synchronizedHandle;
			synchronized (this) {
				synchronizedHandle = handle;
				handle = 0;
			}
			if (synchronizedHandle != 0) {
				closeConnectionHandle(synchronizedHandle);
			}
		}
	}

	protected void finalize() {
		try {
			close();
		} catch (IOException e) {
		}
	}

	/*
	 * (non-Javadoc)
	 *
	 * @see com.intel.bluetooth.BluetoothConnectionAccess#isClosed()
	 */
	public boolean isClosed() {
		return isClosed;
	}

	/*
	 * (non-Javadoc)
	 *
	 * @see com.intel.bluetooth.BluetoothConnectionAccess#markAuthenticated()
	 */
	public void markAuthenticated() {
		if (this.securityOpt == ServiceRecord.NOAUTHENTICATE_NOENCRYPT) {
			this.securityOpt = ServiceRecord.AUTHENTICATE_NOENCRYPT;
		}
	}

	/*
	 * (non-Javadoc)
	 *
	 * @see com.intel.bluetooth.BluetoothConnectionAccess#getSecurityOpt()
	 */
	public int getSecurityOpt() {
		try {
			this.securityOpt = bluetoothStack.l2GetSecurityOpt(this.handle, this.securityOpt);
		} catch (IOException notChanged) {
		}
		return this.securityOpt;
	}

	/*
	 * (non-Javadoc)
	 *
	 * @see com.intel.bluetooth.BluetoothConnectionAccess#encrypt(boolean)
	 * @see javax.bluetooth.RemoteDevice#encrypt(Connection , boolean)
	 */
	public boolean encrypt(long address, boolean on) throws IOException {
		if (isClosed) {
			throw new IOException("L2CAP Connection is already closed");
		}
		boolean changed = bluetoothStack.l2Encrypt(address, this.handle, on);
		if (changed) {
			if (on) {
				this.securityOpt = ServiceRecord.AUTHENTICATE_ENCRYPT;
			} else {
				this.securityOpt = ServiceRecord.AUTHENTICATE_NOENCRYPT;
			}
		}
		return changed;
	}

	/*
	 * (non-Javadoc)
	 *
	 * @see com.intel.bluetooth.BluetoothConnectionAccess#getRemoteDevice()
	 */
	public RemoteDevice getRemoteDevice() {
		return this.remoteDevice;
	}

	/*
	 * (non-Javadoc)
	 *
	 * @see com.intel.bluetooth.BluetoothConnectionAccess#setRemoteDevice(javax.bluetooth.RemoteDevice)
	 */
	public void setRemoteDevice(RemoteDevice remoteDevice) {
		this.remoteDevice = remoteDevice;
	}

	/*
	 * (non-Javadoc)
	 *
	 * @see com.intel.bluetooth.BluetoothConnectionAccess#getBluetoothStack()
	 */
	public BluetoothStack getBluetoothStack() {
		return bluetoothStack;
	}

}
