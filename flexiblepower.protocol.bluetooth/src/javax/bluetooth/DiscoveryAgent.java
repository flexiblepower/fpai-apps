/**
 *  BlueCove - Java library for Bluetooth
 *
 *  Java docs licensed under the Apache License, Version 2.0
 *  http://www.apache.org/licenses/LICENSE-2.0 
 *   (c) Copyright 2001, 2002 Motorola, Inc.  ALL RIGHTS RESERVED.
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
 *  @version $Id: DiscoveryAgent.java 2530 2008-12-09 18:52:53Z skarzhevskyy $
 */
package javax.bluetooth;

import com.intel.bluetooth.BluetoothStack;
import com.intel.bluetooth.DebugLog;
import com.intel.bluetooth.RemoteDeviceHelper;
import com.intel.bluetooth.SelectServiceHandler;

/**
 * The <code>DiscoveryAgent</code> class provides methods to perform device
 * and service discovery. A local device must have only one
 * <code>DiscoveryAgent</code> object. This object must be retrieved by a call
 * to <code>getDiscoveryAgent()</code> on the <code>LocalDevice</code>
 * object.
 * 
 * <H3>Device Discovery</H3>
 * 
 * There are two ways to discover devices. First, an application may use
 * <code>startInquiry()</code> to start an inquiry to find devices in
 * proximity to the local device. Discovered devices are returned via the
 * <code>deviceDiscovered()</code> method of the interface
 * <code>DiscoveryListener</code>. The second way to discover devices is via
 * the <code>retrieveDevices()</code> method. This method will return devices
 * that have been discovered via a previous inquiry or devices that are
 * classified as pre-known. (Pre-known devices are those devices that are
 * defined in the Bluetooth Control Center as devices this device frequently
 * contacts.) The <code>retrieveDevices()</code> method does not perform an
 * inquiry, but provides a quick way to get a list of devices that may be in the
 * area.
 * 
 * <H3>Service Discovery</H3>
 * The <code>DiscoveryAgent</code> class also encapsulates the functionality
 * provided by the service discovery application profile. The class provides an
 * interface for an application to search and retrieve attributes for a
 * particular service. There are two ways to search for services. To search for
 * a service on a single device, the <code>searchServices()</code> method
 * should be used. On the other hand, if you don't care which device a service
 * is on, the <code>selectService()</code> method does a service search on a
 * set of remote devices.
 * 
 * 
 */

public class DiscoveryAgent {

	/**
	 * Takes the device out of discoverable mode.
	 * <P>
	 * The value of <code>NOT_DISCOVERABLE</code> is 0x00 (0).
	 */
	public static final int NOT_DISCOVERABLE = 0;

	/**
	 * The inquiry access code for General/Unlimited Inquiry Access Code (GIAC).
	 * This is used to specify the type of inquiry to complete or respond to.
	 * <P>
	 * The value of <code>GIAC</code> is 0x9E8B33 (10390323). This value is
	 * defined in the Bluetooth Assigned Numbers document.
	 */
	public static final int GIAC = 0x9E8B33;

	/**
	 * The inquiry access code for Limited Dedicated Inquiry Access Code (LIAC).
	 * This is used to specify the type of inquiry to complete or respond to.
	 * <P>
	 * The value of <code>LIAC</code> is 0x9E8B00 (10390272). This value is
	 * defined in the Bluetooth Assigned Numbers document.
	 */
	public static final int LIAC = 0x9E8B00;

	/**
	 * Used with the <code>retrieveDevices()</code> method to return those
	 * devices that were found via a previous inquiry. If no inquiries have been
	 * started, this will cause the method to return <code>null</code>.
	 * <P>
	 * The value of <code>CACHED</code> is 0x00 (0).
	 * 
	 * @see #retrieveDevices
	 */
	public static final int CACHED = 0x00;

	/**
	 * Used with the <code>retrieveDevices()</code> method to return those
	 * devices that are defined to be pre-known devices. Pre-known devices are
	 * specified in the BCC. These are devices that are specified by the user as
	 * devices with which the local device will frequently communicate.
	 * <P>
	 * The value of <code>PREKNOWN</code> is 0x01 (1).
	 * 
	 * @see #retrieveDevices
	 */
	public static final int PREKNOWN = 0x01;

	private BluetoothStack bluetoothStack;

	/**
	 * Creates a <code>DiscoveryAgent</code> object.
	 */
	private DiscoveryAgent() {
	}

	DiscoveryAgent(BluetoothStack bluetoothStack) {
		this();
		this.bluetoothStack = bluetoothStack;
	}

    /**
     * Returns an array of Bluetooth devices that have either been found by the
     * local device during previous inquiry requests or been specified as a
     * pre-known device, depending on the argument. The list of previously found
     * devices is maintained by the implementation of this API. A device can be
     * set as a pre-known device in the Bluetooth Control Center.
     * 
     * While maintenance of the list of previously found devices is an
     * implementation detail, it is essential to ensure a consistent user
     * experience in what constitutes a list of cached Bluetooth devices. Thus,
     * a Bluetooth API implementation MUST read the list of cached devices from
     * the native side every time the
     * <code>DiscoveryAgent.retrieveDevices()</code> method, if access to the
     * native list is available. If the native Bluetooth implementation does not
     * maintain the list of cached devices, or if the list is not readily
     * available, a Bluetooth API implementation MUST maintain a similar list
     * itself and return this list from the
     * <code>DiscoveryAgent.retrieveDevices()</code> method when asked for
     * CACHED devices. The returned list in either case MUST contain no more
     * than one entry for an individual remote device.
     * 
     * @param option
     *            <code>CACHED</code> if previously found devices should be
     *            returned; <code>PREKNOWN</code> if pre-known devices should be
     *            returned
     * 
     * @return an array containing the Bluetooth devices that were previously
     *         found if <code>option</code> is <code>CACHED</code>; an array of
     *         devices that are pre-known devices if <code>option</code> is
     *         <code>PREKNOWN</code>; <code>null</code> if no devices meet the
     *         criteria
     * 
     * @exception IllegalArgumentException
     *                if <code>option</code> is not <code>CACHED</code> or
     *                <code>PREKNOWN</code>
     */
	public RemoteDevice[] retrieveDevices(int option) {
		return RemoteDeviceHelper.retrieveDevices(this.bluetoothStack, option);
	}

	/**
	 * Places the device into inquiry mode. The length of the inquiry is
	 * implementation dependent. This method will search for devices with the
	 * specified inquiry access code. Devices that responded to the inquiry are
	 * returned to the application via the method
	 * <code>deviceDiscovered()</code> of the interface
	 * <code>DiscoveryListener</code>. The <code>cancelInquiry()</code>
	 * method is called to stop the inquiry.
	 * 
	 * @see #cancelInquiry
	 * @see #GIAC
	 * @see #LIAC
	 * 
	 * @param accessCode
	 *            the type of inquiry to complete
	 * 
	 * @param listener
	 *            the event listener that will receive device discovery events
	 * 
	 * @return <code>true</code> if the inquiry was started;
	 *         <code>false</code> if the inquiry was not started because the
	 *         <code>accessCode</code> is not supported
	 * 
	 * @exception IllegalArgumentException
	 *                if the access code provided is not <code>LIAC</code>,
	 *                <code>GIAC</code>, or in the range 0x9E8B00 to 0x9E8B3F
	 * 
	 * @exception NullPointerException
	 *                if <code>listener</code> is <code>null</code>
	 * 
	 * @exception BluetoothStateException
	 *                if the Bluetooth device does not allow an inquiry to be
	 *                started due to other operations that are being performed
	 *                by the device
	 */
	public boolean startInquiry(int accessCode, DiscoveryListener listener) throws BluetoothStateException {
		if (listener == null) {
			throw new NullPointerException("DiscoveryListener is null");
		}
		if ((accessCode != LIAC) && (accessCode != GIAC) && ((accessCode < 0x9E8B00) || (accessCode > 0x9E8B3F))) {
			throw new IllegalArgumentException("Invalid accessCode " + accessCode);
		}
		return this.bluetoothStack.startInquiry(accessCode, listener);
	}

	/**
	 * Removes the device from inquiry mode.
	 * <P>
	 * An <code>inquiryCompleted()</code> event will occur with a type of
	 * <code>INQUIRY_TERMINATED</code> as a result of calling this method.
	 * After receiving this event, no further <code>deviceDiscovered()</code>
	 * events will occur as a result of this inquiry.
	 * 
	 * <P>
	 * 
	 * This method will only cancel the inquiry if the <code>listener</code>
	 * provided is the listener that started the inquiry.
	 * 
	 * @param listener
	 *            the listener that is receiving inquiry events
	 * 
	 * @return <code>true</code> if the inquiry was canceled; otherwise
	 *         <code>false</code> if the inquiry was not canceled or if the
	 *         inquiry was not started using <code>listener</code>
	 * 
	 * @exception NullPointerException
	 *                if <code>listener</code> is <code>null</code>
	 */
	public boolean cancelInquiry(DiscoveryListener listener) {
		if (listener == null) {
			throw new NullPointerException("DiscoveryListener is null");
		}
		DebugLog.debug("cancelInquiry");
		return this.bluetoothStack.cancelInquiry(listener);
	}

	/**
	 * Searches for services on a remote Bluetooth device that have all the
	 * UUIDs specified in <code>uuidSet</code>. Once the service is found,
	 * the attributes specified in <code>attrSet</code> and the default
	 * attributes are retrieved. The default attributes are ServiceRecordHandle
	 * (0x0000), ServiceClassIDList (0x0001), ServiceRecordState (0x0002),
	 * ServiceID (0x0003), and ProtocolDescriptorList (0x0004).If
	 * <code>attrSet</code> is <code>null</code> then only the default
	 * attributes will be retrieved. <code>attrSet</code> does not have to be
	 * sorted in increasing order, but must only contain values in the range [0 -
	 * (2<sup>16</sup>-1)].
	 * 
	 * @see DiscoveryListener
	 * 
	 * @param attrSet
	 *            indicates the attributes whose values will be retrieved on
	 *            services which have the UUIDs specified in
	 *            <code>uuidSet</code>
	 * 
	 * @param uuidSet
	 *            the set of UUIDs that are being searched for; all services
	 *            returned will contain all the UUIDs specified here
	 * 
	 * @param btDev
	 *            the remote Bluetooth device to search for services on
	 * 
	 * @param discListener
	 *            the object that will receive events when services are
	 *            discovered
	 * 
	 * @return the transaction ID of the service search; this number must be
	 *         positive
	 * 
	 * @exception BluetoothStateException
	 *                if the number of concurrent service search transactions
	 *                exceeds the limit specified by the
	 *                <code>bluetooth.sd.trans.max</code> property obtained
	 *                from the class <code>LocalDevice</code> or the system is
	 *                unable to start one due to current conditions
	 * 
	 * @exception IllegalArgumentException
	 *                if <code>attrSet</code> has an illegal service attribute
	 *                ID or exceeds the property
	 *                <code>bluetooth.sd.attr.retrievable.max</code> defined
	 *                in the class <code>LocalDevice</code>; if
	 *                <code>attrSet</code> or <code>uuidSet</code> is of
	 *                length 0; if <code>attrSet</code> or
	 *                <code>uuidSet</code> contains duplicates
	 * 
	 * @exception NullPointerException
	 *                if <code>uuidSet</code>, <code>btDev</code>, or
	 *                <code>discListener</code> is <code>null</code>; if an
	 *                element in <code>uuidSet</code> array is
	 *                <code>null</code>
	 * 
	 */
	public int searchServices(int[] attrSet, UUID[] uuidSet, RemoteDevice btDev, DiscoveryListener discListener)
			throws BluetoothStateException {
		if (uuidSet == null) {
			throw new NullPointerException("uuidSet is null");
		}
		if (uuidSet.length == 0) {
			// The same as on Motorola, Nokia and SE Phones
			throw new IllegalArgumentException("uuidSet is empty");
		}
		for (int u1 = 0; u1 < uuidSet.length; u1++) {
			if (uuidSet[u1] == null) {
				throw new NullPointerException("uuidSet[" + u1 + "] is null");
			}
			for (int u2 = u1 + 1; u2 < uuidSet.length; u2++) {
				if (uuidSet[u1].equals(uuidSet[u2])) {
					throw new IllegalArgumentException("uuidSet has duplicate values " + uuidSet[u1].toString());
				}
			}
		}
		if (btDev == null) {
			throw new NullPointerException("RemoteDevice is null");
		}
		if (discListener == null) {
			throw new NullPointerException("DiscoveryListener is null");
		}
		for (int i = 0; attrSet != null && i < attrSet.length; i++) {
			if (attrSet[i] < 0x0000 || attrSet[i] > 0xffff) {
				throw new IllegalArgumentException("attrSet[" + i + "] not in range");
			}
		}
		return this.bluetoothStack.searchServices(attrSet, uuidSet, btDev, discListener);
	}

	/**
	 * Cancels the service search transaction that has the specified transaction
	 * ID. The ID was assigned to the transaction by the method
	 * <code>searchServices()</code>. A <code>serviceSearchCompleted()</code>
	 * event with a discovery type of <code>SERVICE_SEARCH_TERMINATED</code>
	 * will occur when this method is called. After receiving this event, no
	 * further <code>servicesDiscovered()</code> events will occur as a result
	 * of this search.
	 * 
	 * @param transID
	 *            the ID of the service search transaction to cancel; returned
	 *            by <code>searchServices()</code>
	 * 
	 * @return <code>true</code> if the service search transaction is
	 *         terminated, else <code>false</code> if the <code>transID</code>
	 *         does not represent an active service search transaction
	 */
	public boolean cancelServiceSearch(int transID) {
		DebugLog.debug("cancelServiceSearch", transID);
		return this.bluetoothStack.cancelServiceSearch(transID);
	}

    /**
     * Attempts to locate a service that contains <code>uuid</code> in the
     * ServiceClassIDList of its service record. This method will return a
     * string that may be used in <code>Connector.open()</code> to establish a
     * connection to the service.
     * 
     * This method MUST return immediately after a suitable service (i.e. a
     * service that matches the specified UUID) is found. The Bluetooth inquiry
     * or the Bluetooth service discovery MUST NOT continue after a suitable
     * service is found. Note that if there are several suitable services in the
     * vicinity, different invocations of this method MAY produce different
     * results (i.e. return connection strings pointing at different services).
     * This is because Bluetooth inquiry and service discovery processes are
     * non-deterministic by their nature.
     * 
     * @see ServiceRecord#NOAUTHENTICATE_NOENCRYPT
     * @see ServiceRecord#AUTHENTICATE_NOENCRYPT
     * @see ServiceRecord#AUTHENTICATE_ENCRYPT
     * 
     * @param uuid
     *            the UUID to search for in the ServiceClassIDList
     * 
     * @param security
     *            specifies the security requirements for a connection to this
     *            service; must be one of
     *            <code>ServiceRecord.NOAUTHENTICATE_NOENCRYPT</code>,
     *            <code>ServiceRecord.AUTHENTICATE_NOENCRYPT</code>, or
     *            <code>ServiceRecord.AUTHENTICATE_ENCRYPT</code>
     * 
     * @param master
     *            determines if this client must be the master of the
     *            connection; <code>true</code> if the client must be the
     *            master; <code>false</code> if the client can be the master or
     *            the slave
     * 
     * @return the connection string used to connect to the service with a UUID
     *         of <code>uuid</code>; or <code>null</code> if no service could be
     *         found with a UUID of <code>uuid</code> in the ServiceClassIDList
     * 
     * @exception BluetoothStateException
     *                if the Bluetooth system cannot start the request due to
     *                the current state of the Bluetooth system
     * 
     * @exception NullPointerException
     *                if <code>uuid</code> is <code>null</code>
     * 
     * @exception IllegalArgumentException
     *                if <code>security</code> is not
     *                <code>ServiceRecord.NOAUTHENTICATE_NOENCRYPT</code>,
     *                <code>ServiceRecord.AUTHENTICATE_NOENCRYPT</code>, or
     *                <code>ServiceRecord.AUTHENTICATE_ENCRYPT</code>
     */
	public String selectService(UUID uuid, int security, boolean master) throws BluetoothStateException {
		return (new SelectServiceHandler(this)).selectService(uuid, security, master);
	}

}