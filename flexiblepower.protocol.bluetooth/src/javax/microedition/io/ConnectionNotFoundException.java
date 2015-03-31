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
 *  @version $Id: ConnectionNotFoundException.java 2471 2008-12-01 03:44:20Z skarzhevskyy $
 */ 
package javax.microedition.io;

import java.io.IOException;

public class ConnectionNotFoundException extends IOException {

    private static final long serialVersionUID = 1L;
    
    /*
	 * Creates a new ConnectionNotFoundException without a detail message.
	 */
	public ConnectionNotFoundException() {
	}

	/*
	 * Creates a ConnectionNotFoundException with the specified detail message.
	 * Parameters: msg - the reason for the exception
	 */

	public ConnectionNotFoundException(String msg) {
		super(msg);
	}
}