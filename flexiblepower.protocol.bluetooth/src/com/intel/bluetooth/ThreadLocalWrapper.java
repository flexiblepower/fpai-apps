/**
 *  BlueCove - Java library for Bluetooth
 *  Copyright (C) 2008 Vlad Skarzhevskyy
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
 *  @version $Id: ThreadLocalWrapper.java 2476 2008-12-01 17:41:59Z skarzhevskyy $
 */
package com.intel.bluetooth;

/**
 *
 * When ThreadLocal not available on Java 1.1 or MIDP will do nothing.
 *
 */
class ThreadLocalWrapper {

	static boolean java11 = false;

	private Object threadLocal;

	private Object java11Object;

	ThreadLocalWrapper() {
		if (java11) {
			return;
		}
		try {
			threadLocal = new ThreadLocal();
		} catch (Throwable ejava11) {
			java11 = true;
		}
	}

	public Object get() {
		if (java11) {
			return java11Object;
		} else {
			return ((ThreadLocal) threadLocal).get();
		}
	}

	public void set(Object value) {
		if (java11) {
			java11Object = value;
		} else {
			((ThreadLocal) threadLocal).set(value);
		}
	}
}
