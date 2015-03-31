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
 *  @version $Id: BluetoothStackBlueSoleil.java 2525 2008-12-09 03:48:51Z skarzhevskyy $
 */
package com.intel.bluetooth;

import java.io.IOException;

import javax.bluetooth.BluetoothStateException;
import javax.bluetooth.DeviceClass;
import javax.bluetooth.DiscoveryAgent;
import javax.bluetooth.DiscoveryListener;
import javax.bluetooth.RemoteDevice;
import javax.bluetooth.ServiceRecord;
import javax.bluetooth.ServiceRegistrationException;
import javax.bluetooth.UUID;

class BluetoothStackBlueSoleil implements BluetoothStack, DeviceInquiryRunnable, SearchServicesRunnable {

	private static BluetoothStackBlueSoleil singleInstance = null;

	private boolean initialized = false;

	private DiscoveryListener currentDeviceDiscoveryListener;

	BluetoothStackBlueSoleil() {
	}

	public String getStackID() {
		return BlueCoveImpl.STACK_BLUESOLEIL;
	}

	public String toString() {
		return getStackID();
	}

	// ---------------------- Library initialization

	/*
	 * (non-Javadoc)
	 * 
	 * @see com.intel.bluetooth.BluetoothStack#isNativeCodeLoaded()
	 */
	public native boolean isNativeCodeLoaded();

	/*
	 * (non-Javadoc)
	 * 
	 * @see com.intel.bluetooth.BluetoothStack#requireNativeLibraries()
	 */
	public LibraryInformation[] requireNativeLibraries() {
		return LibraryInformation.library(BlueCoveImpl.NATIVE_LIB_BLUESOLEIL);
	}

	public native int getLibraryVersion();

	public native int detectBluetoothStack();

	public native void enableNativeDebug(Class nativeDebugCallback, boolean on);

	public native boolean initializeImpl();

	public void initialize() throws BluetoothStateException {
		if (singleInstance != null) {
			throw new BluetoothStateException("Only one instance of " + getStackID() + " stack supported");
		}
		if (!initializeImpl()) {
			DebugLog.fatal("Can't initialize BlueSoleil");
			throw new BluetoothStateException("BlueSoleil BluetoothStack not found");
		}
		initialized = true;
		singleInstance = this;
	}

	private native void uninitialize();

	public void destroy() {
		if (singleInstance != this) {
			throw new RuntimeException("Destroy invalid instance");
		}
		if (initialized) {
			uninitialize();
			initialized = false;
			DebugLog.debug("BlueSoleil destroyed");
		}
		singleInstance = null;
	}

	protected void finalize() {
		destroy();
	}

	public native String getLocalDeviceBluetoothAddress();

	public native String getLocalDeviceName();

	public native int getDeviceClassImpl();

	/**
	 * There are no functions in BlueSoleil stack.
	 */
	public DeviceClass getLocalDeviceClass() {
		return new DeviceClass(getDeviceClassImpl());
	}

	/*
	 * (non-Javadoc)
	 * 
	 * @see com.intel.bluetooth.BluetoothStack#setLocalDeviceServiceClasses(int)
	 */
	public void setLocalDeviceServiceClasses(int classOfDevice) {
		throw new NotSupportedRuntimeException(getStackID());
	}

	/**
	 * There are no functions to set BlueSoleil stack discoverable status.
	 */
	public boolean setLocalDeviceDiscoverable(int mode) throws BluetoothStateException {
		return true;
	}

	native boolean isBlueSoleilStarted(int seconds);

	private native boolean isBluetoothReady(int seconds);

	/**
	 * There are no functions to find BlueSoleil discoverable status.
	 */
	public int getLocalDeviceDiscoverable() {
		if (isBluetoothReady(2)) {
			return DiscoveryAgent.GIAC;
		} else {
			return DiscoveryAgent.NOT_DISCOVERABLE;
		}
	}

	public boolean isLocalDevicePowerOn() {
		return isBluetoothReady(15);
	}

	native int getStackVersionInfo();

	native int getDeviceVersion();

	native int getDeviceManufacturer();

	/*
	 * (non-Javadoc)
	 * 
	 * @see com.intel.bluetooth.BluetoothStack#getFeatureSet()
	 */
	public int getFeatureSet() {
		return 0;
	}

	public String getLocalDeviceProperty(String property) {
		if (BluetoothConsts.PROPERTY_BLUETOOTH_CONNECTED_DEVICES_MAX.equals(property)) {
			return "7";
		}
		if (BluetoothConsts.PROPERTY_BLUETOOTH_SD_TRANS_MAX.equals(property)) {
			return "1";
		}
		if (BluetoothConsts.PROPERTY_BLUETOOTH_CONNECTED_INQUIRY_SCAN.equals(property)) {
			return BlueCoveImpl.TRUE;
		}
		if (BluetoothConsts.PROPERTY_BLUETOOTH_CONNECTED_PAGE_SCAN.equals(property)) {
			return BlueCoveImpl.TRUE;
		}
		if (BluetoothConsts.PROPERTY_BLUETOOTH_CONNECTED_INQUIRY.equals(property)) {
			return BlueCoveImpl.TRUE;
		}

		// service attributes are not supported.
		if (BluetoothConsts.PROPERTY_BLUETOOTH_SD_ATTR_RETRIEVABLE_MAX.equals(property)) {
			return "0";
		}

		// if ("bluecove.radio.version".equals(property)) {
		// return String.valueOf(getDeviceVersion());
		// }
		// if ("bluecove.radio.manufacturer".equals(property)) {
		// return String.valueOf(getDeviceManufacturer());
		// }
		if ("bluecove.stack.version".equals(property)) {
			return String.valueOf(getStackVersionInfo());
		}

		return null;
	}

	/*
	 * (non-Javadoc)
	 * 
	 * @see com.intel.bluetooth.BluetoothStack#isCurrentThreadInterruptedCallback()
	 */
	public boolean isCurrentThreadInterruptedCallback() {
		return UtilsJavaSE.isCurrentThreadInterrupted();
	}

	public RemoteDevice[] retrieveDevices(int option) {
		return null;
	}

	public Boolean isRemoteDeviceTrusted(long address) {
		return null;
	}

	public Boolean isRemoteDeviceAuthenticated(long address) {
		return null;
	}

	public boolean authenticateRemoteDevice(long address) throws IOException {
		return false;
	}

	public boolean authenticateRemoteDevice(long address, String passkey) throws IOException {
		return false;
	}

	/*
	 * (non-Javadoc)
	 * 
	 * @see com.intel.bluetooth.BluetoothStack#removeAuthenticationWithRemoteDevice (long)
	 */
	public void removeAuthenticationWithRemoteDevice(long address) throws IOException {
		throw new NotSupportedIOException(getStackID());
	}

	// --- Device Inquiry

	public boolean startInquiry(int accessCode, DiscoveryListener listener) throws BluetoothStateException {
		if (currentDeviceDiscoveryListener != null) {
			throw new BluetoothStateException("Another inquiry already running");
		}
		currentDeviceDiscoveryListener = listener;
		return DeviceInquiryThread.startInquiry(this, this, accessCode, listener);
	}

	public int runDeviceInquiry(DeviceInquiryThread startedNotify, int accessCode, DiscoveryListener listener)
			throws BluetoothStateException {
		try {
			startedNotify.deviceInquiryStartedCallback();
			return runDeviceInquiryImpl(startedNotify, accessCode, listener);
		} finally {
			currentDeviceDiscoveryListener = null;
		}
	}

	public native int runDeviceInquiryImpl(DeviceInquiryThread startedNotify, int accessCode, DiscoveryListener listener)
			throws BluetoothStateException;

	public void deviceDiscoveredCallback(DiscoveryListener listener, long deviceAddr, int deviceClass,
			String deviceName, boolean paired) {
		DebugLog.debug("deviceDiscoveredCallback", deviceName);
		RemoteDevice remoteDevice = RemoteDeviceHelper.createRemoteDevice(this, deviceAddr, deviceName, paired);
		if ((currentDeviceDiscoveryListener == null) || (currentDeviceDiscoveryListener != listener)) {
			return;
		}
		listener.deviceDiscovered(remoteDevice, new DeviceClass(deviceClass));
	}

	public native boolean cancelInquirympl();

	public boolean cancelInquiry(DiscoveryListener listener) {
		if (currentDeviceDiscoveryListener != listener) {
			return false;
		}
		// no further deviceDiscovered() events will occur for this inquiry
		currentDeviceDiscoveryListener = null;
		return cancelInquirympl();
	}

	public String getRemoteDeviceFriendlyName(long address) throws IOException {
		// TODO Properly if possible
		return null;
	}

	// --- Service search

	public int searchServices(int[] attrSet, UUID[] uuidSet, RemoteDevice device, DiscoveryListener listener)
			throws BluetoothStateException {
		return SearchServicesThread.startSearchServices(this, this, attrSet, uuidSet, device, listener);
	}

	public boolean cancelServiceSearch(int transID) {
		return false;
	}

	private native int runSearchServicesImpl(SearchServicesThread startedNotify, DiscoveryListener listener,
			byte[] uuidValue, long address, RemoteDevice device) throws BluetoothStateException;

	public int runSearchServices(SearchServicesThread startedNotify, int[] attrSet, UUID[] uuidSet,
			RemoteDevice device, DiscoveryListener listener) throws BluetoothStateException {
		startedNotify.searchServicesStartedCallback();
		UUID uuid = null;
		if ((uuidSet != null) && (uuidSet.length > 0)) {
			uuid = uuidSet[uuidSet.length - 1];
		}
		return runSearchServicesImpl(startedNotify, listener, Utils.UUIDToByteArray(uuid), RemoteDeviceHelper
				.getAddress(device), device);
	}

	/*
	 * This is all we have under the BlueSoleil. struct SPPEX_SERVICE_INFO { DWORD dwSize; DWORD dwSDAPRecordHanlde;
	 * UUID serviceClassUuid128; CHAR szServiceName[MAX_SERVICE_NAME_LENGTH]; UCHAR ucServiceChannel; }
	 */

	public void servicesFoundCallback(SearchServicesThread startedNotify, DiscoveryListener listener,
			RemoteDevice device, String serviceName, byte[] uuidValue, int channel, long recordHanlde) {

		ServiceRecordImpl record = new ServiceRecordImpl(this, device, 0);

		UUID uuid = new UUID(Utils.UUIDByteArrayToString(uuidValue), false);

		record.populateRFCOMMAttributes(recordHanlde, channel, uuid, serviceName, BluetoothConsts.obexUUIDs
				.contains(uuid));
		DebugLog.debug("servicesFoundCallback", record);

		RemoteDevice listedDevice = RemoteDeviceHelper.createRemoteDevice(this, device);
		RemoteDeviceHelper.setStackAttributes(this, listedDevice, "RFCOMM_channel" + channel, uuid);

		ServiceRecord[] records = new ServiceRecordImpl[1];
		records[0] = record;
		listener.servicesDiscovered(startedNotify.getTransID(), records);
	}

	public boolean populateServicesRecordAttributeValues(ServiceRecordImpl serviceRecord, int[] attrIDs)
			throws IOException {
		return false;
	}

	// --- Client RFCOMM connections

	private native long connectionRfOpenImpl(long address, byte[] uuidValue) throws IOException;

	public long connectionRfOpenClientConnection(BluetoothConnectionParams params) throws IOException {
		if (params.authenticate || params.encrypt) {
			throw new IOException("authenticate not supported on BlueSoleil");
		}
		RemoteDevice listedDevice = RemoteDeviceHelper.getCashedDevice(this, params.address);
		if (listedDevice == null) {
			throw new IOException("Device not discovered");
		}
		UUID uuid = (UUID) RemoteDeviceHelper.getStackAttributes(this, listedDevice, "RFCOMM_channel" + params.channel);
		if (uuid == null) {
			throw new IOException("Device service not discovered");
		}
		DebugLog.debug("Connect to service UUID", uuid);
		return connectionRfOpenImpl(params.address, Utils.UUIDToByteArray(uuid));
	}

	public native void connectionRfCloseClientConnection(long handle) throws IOException;

	private native long rfServerOpenImpl(byte[] uuidValue, String name, boolean authenticate, boolean encrypt)
			throws IOException;

	private native int rfServerSCN(long handle) throws IOException;

	public long rfServerOpen(BluetoothConnectionNotifierParams params, ServiceRecordImpl serviceRecord)
			throws IOException {
		if (params.authenticate || params.encrypt) {
			throw new IOException("authenticate not supported on BlueSoleil");
		}
		byte[] uuidValue = Utils.UUIDToByteArray(params.uuid);
		long handle = rfServerOpenImpl(uuidValue, params.name, params.authenticate, params.encrypt);
		int channel = rfServerSCN(handle);
		DebugLog.debug("serverSCN", channel);
		int serviceRecordHandle = (int) handle;

		serviceRecord.populateRFCOMMAttributes(serviceRecordHandle, channel, params.uuid, params.name, false);

		return handle;
	}

	public void rfServerUpdateServiceRecord(long handle, ServiceRecordImpl serviceRecord, boolean acceptAndOpen)
			throws ServiceRegistrationException {
		if (!acceptAndOpen) {
			throw new ServiceRegistrationException("Not Supported on " + getStackID());
		}
	}

	public native long rfServerAcceptAndOpenRfServerConnection(long handle) throws IOException;

	public void connectionRfCloseServerConnection(long handle) throws IOException {
		connectionRfCloseClientConnection(handle);
	}

	public native void rfServerClose(long handle, ServiceRecordImpl serviceRecord) throws IOException;

	public native long getConnectionRfRemoteAddress(long handle) throws IOException;

	public native int connectionRfRead(long handle) throws IOException;

	public native int connectionRfRead(long handle, byte[] b, int off, int len) throws IOException;

	public native int connectionRfReadAvailable(long handle) throws IOException;

	public native void connectionRfWrite(long handle, int b) throws IOException;

	public native void connectionRfWrite(long handle, byte[] b, int off, int len) throws IOException;

	public native void connectionRfFlush(long handle) throws IOException;

	public int rfGetSecurityOpt(long handle, int expected) throws IOException {
		return ServiceRecord.NOAUTHENTICATE_NOENCRYPT;
	}

	/*
	 * (non-Javadoc)
	 * 
	 * @see com.intel.bluetooth.BluetoothStack#l2Encrypt(long,long,boolean)
	 */
	public boolean rfEncrypt(long address, long handle, boolean on) throws IOException {
		return false;
	}

	// ---------------------- Client and Server L2CAP connections

	/*
	 * (non-Javadoc)
	 * 
	 * @see com.intel.bluetooth.BluetoothStack#l2OpenClientConnection(com.intel.bluetooth .BluetoothConnectionParams,
	 * int, int)
	 */
	public long l2OpenClientConnection(BluetoothConnectionParams params, int receiveMTU, int transmitMTU)
			throws IOException {
		throw new NotSupportedIOException(getStackID());
	}

	/*
	 * (non-Javadoc)
	 * 
	 * @see com.intel.bluetooth.BluetoothStack#l2CloseClientConnection(long)
	 */
	public void l2CloseClientConnection(long handle) throws IOException {
		throw new NotSupportedIOException(getStackID());
	}

	/*
	 * (non-Javadoc)
	 * 
	 * @seecom.intel.bluetooth.BluetoothStack#l2ServerOpen(com.intel.bluetooth. BluetoothConnectionNotifierParams, int,
	 * int, com.intel.bluetooth.ServiceRecordImpl)
	 */
	public long l2ServerOpen(BluetoothConnectionNotifierParams params, int receiveMTU, int transmitMTU,
			ServiceRecordImpl serviceRecord) throws IOException {
		throw new NotSupportedIOException(getStackID());
	}

	/*
	 * (non-Javadoc)
	 * 
	 * @see com.intel.bluetooth.BluetoothStack#l2ServerUpdateServiceRecord(long, com.intel.bluetooth.ServiceRecordImpl,
	 * boolean)
	 */
	public void l2ServerUpdateServiceRecord(long handle, ServiceRecordImpl serviceRecord, boolean acceptAndOpen)
			throws ServiceRegistrationException {
		throw new ServiceRegistrationException("Not Supported on" + getStackID());
	}

	/*
	 * (non-Javadoc)
	 * 
	 * @see com.intel.bluetooth.BluetoothStack#l2ServerAcceptAndOpenServerConnection (long)
	 */
	public long l2ServerAcceptAndOpenServerConnection(long handle) throws IOException {
		throw new NotSupportedIOException(getStackID());
	}

	/*
	 * (non-Javadoc)
	 * 
	 * @see com.intel.bluetooth.BluetoothStack#l2CloseServerConnection(long)
	 */
	public void l2CloseServerConnection(long handle) throws IOException {
		throw new NotSupportedIOException(getStackID());
	}

	/*
	 * (non-Javadoc)
	 * 
	 * @see com.intel.bluetooth.BluetoothStack#l2ServerClose(long, com.intel.bluetooth.ServiceRecordImpl)
	 */
	public void l2ServerClose(long handle, ServiceRecordImpl serviceRecord) throws IOException {
		throw new NotSupportedIOException(getStackID());
	}

	/*
	 * (non-Javadoc)
	 * 
	 * @see com.intel.bluetooth.BluetoothStack#l2GetSecurityOpt(long, int)
	 */
	public int l2GetSecurityOpt(long handle, int expected) throws IOException {
		throw new NotSupportedIOException(getStackID());
	}

	/*
	 * (non-Javadoc)
	 * 
	 * @see com.intel.bluetooth.BluetoothStack#l2Ready(long)
	 */
	public boolean l2Ready(long handle) throws IOException {
		throw new NotSupportedIOException(getStackID());
	}

	/*
	 * (non-Javadoc)
	 * 
	 * @see com.intel.bluetooth.BluetoothStack#l2receive(long, byte[])
	 */
	public int l2Receive(long handle, byte[] inBuf) throws IOException {
		throw new NotSupportedIOException(getStackID());
	}

	/*
	 * (non-Javadoc)
	 * 
	 * @see com.intel.bluetooth.BluetoothStack#l2send(long, byte[])
	 */
	public void l2Send(long handle, byte[] data) throws IOException {
		throw new NotSupportedIOException(getStackID());
	}

	/*
	 * (non-Javadoc)
	 * 
	 * @see com.intel.bluetooth.BluetoothStack#l2GetReceiveMTU(long)
	 */
	public int l2GetReceiveMTU(long handle) throws IOException {
		throw new NotSupportedIOException(getStackID());
	}

	/*
	 * (non-Javadoc)
	 * 
	 * @see com.intel.bluetooth.BluetoothStack#l2GetTransmitMTU(long)
	 */
	public int l2GetTransmitMTU(long handle) throws IOException {
		throw new NotSupportedIOException(getStackID());
	}

	/*
	 * (non-Javadoc)
	 * 
	 * @see com.intel.bluetooth.BluetoothStack#l2RemoteAddress(long)
	 */
	public long l2RemoteAddress(long handle) throws IOException {
		throw new NotSupportedIOException(getStackID());
	}

	/*
	 * (non-Javadoc)
	 * 
	 * @see com.intel.bluetooth.BluetoothStack#l2Encrypt(long,long,boolean)
	 */
	public boolean l2Encrypt(long address, long handle, boolean on) throws IOException {
		throw new NotSupportedIOException(getStackID());
	}

}
