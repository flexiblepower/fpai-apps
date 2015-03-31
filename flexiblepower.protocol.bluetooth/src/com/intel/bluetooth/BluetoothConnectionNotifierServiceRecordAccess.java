/**
 *  BlueCove - Java library for Bluetooth
 *  Copyright (C) 2004 Intel Corporation
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
 *  @version $Id: BluetoothConnectionNotifierServiceRecordAccess.java 2476 2008-12-01 17:41:59Z skarzhevskyy $
 */
package com.intel.bluetooth;

import javax.bluetooth.ServiceRecord;
import javax.bluetooth.ServiceRegistrationException;

/**
 * Used when client application has only access to Proxy of the connection. e.g.
 * WebStart in MicroEmulator
 *
 * <p>
 * <b><u>Your application should not use this class directly.</u></b>
 *
 */
public interface BluetoothConnectionNotifierServiceRecordAccess {

	public ServiceRecord getServiceRecord();

	/**
	 * @param acceptAndOpen
	 *            wrap validation in ServiceRegistrationException
	 * @throws ServiceRegistrationException
	 */
	public void updateServiceRecord(boolean acceptAndOpen) throws ServiceRegistrationException;
}
