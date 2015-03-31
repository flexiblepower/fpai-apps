/**
 *  BlueCove - Java library for Bluetooth
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
 *  @version $Id: Protocol.java 2476 2008-12-01 17:41:59Z skarzhevskyy $
 */
package com.sun.cdc.io.j2me.tcpobex;

import java.io.IOException;

import javax.microedition.io.Connection;

import com.intel.bluetooth.BluetoothConsts;
import com.intel.bluetooth.MicroeditionConnector;
import com.sun.cdc.io.ConnectionBaseInterface;

/**
 * This class is Proxy for tcpobex (OBEX over TCP) Connection implementations used in WTK and MicroEmulator
 *
 * <p>
 * <b><u>Your application should not use this class directly.</u></b>
 */
public class Protocol implements ConnectionBaseInterface {

	public Connection openPrim(String name, int mode, boolean timeouts) throws IOException {
		return MicroeditionConnector.open(BluetoothConsts.PROTOCOL_SCHEME_TCP_OBEX + ":" + name, mode, timeouts);
	}

}