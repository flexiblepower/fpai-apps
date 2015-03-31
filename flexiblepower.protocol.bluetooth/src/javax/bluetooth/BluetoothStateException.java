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
 *  @version $Id: BluetoothStateException.java 2530 2008-12-09 18:52:53Z skarzhevskyy $
 */ 
package javax.bluetooth;

import java.io.IOException;

/**
 * The <code>BluetoothStateException</code> is thrown when
 * a request is made to the Bluetooth system that
 * the system cannot support in its present state.  If, however, the
 * Bluetooth system was not in this state, it could support this operation.
 * For example, some Bluetooth systems do not allow the device to go into
 * inquiry mode if a connection is established.  This exception would be
 * thrown if <code>startInquiry()</code> were called.
 *
 */
public class BluetoothStateException extends IOException {

	private static final long serialVersionUID = 1L;

	/**
     * Creates a new <code>BluetoothStateException</code> without a detail
     * message.
     */
	public BluetoothStateException() {
	}

    /**
     * Creates a <code>BluetoothStateException</code> with the specified
     * detail message.
     *
     * @param msg the reason for the exception
	 */

	public BluetoothStateException(String msg) {
		super(msg);
	}
}