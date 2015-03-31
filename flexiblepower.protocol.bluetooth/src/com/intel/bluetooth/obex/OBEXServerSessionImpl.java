/**
 *  BlueCove - Java library for Bluetooth
 *  Copyright (C) 2007-2008 Vlad Skarzhevskyy
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
 *  @version $Id: OBEXServerSessionImpl.java 2643 2008-12-23 00:24:46Z skarzhevskyy $
 */
package com.intel.bluetooth.obex;

import java.io.EOFException;
import java.io.IOException;

import javax.microedition.io.StreamConnection;
import javax.obex.Authenticator;
import javax.obex.ResponseCodes;
import javax.obex.ServerRequestHandler;

import com.intel.bluetooth.BlueCoveImpl;
import com.intel.bluetooth.BluetoothServerConnection;
import com.intel.bluetooth.DebugLog;
import com.intel.bluetooth.UtilsJavaSE;

class OBEXServerSessionImpl extends OBEXSessionBase implements Runnable, BluetoothServerConnection {

	private ServerRequestHandler handler;

	private OBEXServerOperation operation;

	private boolean closeRequested = false;

	private volatile boolean delayClose = false;

	private Object canCloseEvent = new Object();

	private Object stackID;

	private Thread handlerThread;

	private static int threadNumber;

	private static synchronized int nextThreadNum() {
		return threadNumber++;
	}

	static int errorCount = 0;

	OBEXServerSessionImpl(StreamConnection connection, ServerRequestHandler handler, Authenticator authenticator,
			OBEXConnectionParams obexConnectionParams) throws IOException {
		super(connection, obexConnectionParams);
		this.requestSent = true;
		this.handler = handler;
		this.authenticator = authenticator;
		stackID = BlueCoveImpl.getCurrentThreadBluetoothStackID();
		handlerThread = new Thread(this, "OBEXServerSessionThread-" + nextThreadNum());
		UtilsJavaSE.threadSetDaemon(handlerThread);
	}

	void startSessionHandlerThread() {
		handlerThread.start();
	}

	public void run() {
		// Let the acceptAndOpen return to the caller.
		Thread.yield();
		try {
			if (stackID != null) {
				BlueCoveImpl.setThreadBluetoothStackID(stackID);
			}
			while (!isClosed() && !closeRequested) {
				if (!handleRequest()) {
					return;
				}
			}
		} catch (Throwable e) {
			synchronized (OBEXServerSessionImpl.class) {
				errorCount++;
			}
			if (this.isConnected) {
				DebugLog.error("OBEXServerSession error", e);
			} else {
				DebugLog.debug("OBEXServerSession error", e);
			}
		} finally {
			DebugLog.debug("OBEXServerSession ends");
			try {
				super.close();
			} catch (IOException e) {
				DebugLog.debug("OBEXServerSession close error", e);
			}
		}
	}

	public void close() throws IOException {
		closeRequested = true;
		while (delayClose) {
			synchronized (canCloseEvent) {
				try {
					if (delayClose) {
						canCloseEvent.wait(700);
					}
				} catch (InterruptedException e) {
				}
				delayClose = false;
			}
		}
		if (!isClosed()) {
			DebugLog.debug("OBEXServerSession close");
			// (new Throwable()).printStackTrace();
			if (operation != null) {
				operation.close();
				operation = null;
			}
		}
		super.close();
	}

	private boolean handleRequest() throws IOException {
		DebugLog.debug("OBEXServerSession handleRequest");
		delayClose = false;
		byte[] b;
		try {
			b = readPacket();
		} catch (EOFException e) {
			if (isConnected) {
				throw e;
			}
			DebugLog.debug("OBEXServerSession got EOF");
			close();
			return false;
		}
		delayClose = true;
		try {
			int opcode = b[0] & 0xFF;
			boolean finalPacket = ((opcode & OBEXOperationCodes.FINAL_BIT) != 0);
			if (finalPacket) {
				DebugLog.debug("OBEXServerSession got operation finalPacket");
			}
			switch (opcode) {
			case OBEXOperationCodes.CONNECT:
				processConnect(b);
				break;
			case OBEXOperationCodes.DISCONNECT:
				processDisconnect(b);
				break;
			case OBEXOperationCodes.PUT_FINAL:
			case OBEXOperationCodes.PUT:
				processPut(b, finalPacket);
				break;
			case OBEXOperationCodes.SETPATH | OBEXOperationCodes.FINAL_BIT:
			case OBEXOperationCodes.SETPATH:
				processSetPath(b, finalPacket);
				break;
			case OBEXOperationCodes.ABORT:
				processAbort();
				break;
			case OBEXOperationCodes.GET_FINAL:
			case OBEXOperationCodes.GET:
				processGet(b, finalPacket);
				break;
			default:
				writePacket(ResponseCodes.OBEX_HTTP_NOT_IMPLEMENTED, null);
			}
		} finally {
			delayClose = false;
		}
		synchronized (canCloseEvent) {
			canCloseEvent.notifyAll();
		}
		return true;
	}

	private void processConnect(byte[] b) throws IOException {
		DebugLog.debug("Connect operation");
		if (b[3] != OBEXOperationCodes.OBEX_VERSION) {
			throw new IOException("Unsupported client OBEX version " + b[3]);
		}
		if (b.length < 7) {
			throw new IOException("Corrupted OBEX data");
		}
		int requestedMTU = OBEXUtils.bytesToShort(b[5], b[6]);
		if (requestedMTU < OBEXOperationCodes.OBEX_MINIMUM_MTU) {
			throw new IOException("Invalid MTU " + requestedMTU);
		}
		this.mtu = requestedMTU;
		DebugLog.debug("mtu selected", this.mtu);

		int rc;
		OBEXHeaderSetImpl replyHeaders = createOBEXHeaderSetImpl();
		OBEXHeaderSetImpl requestHeaders = OBEXHeaderSetImpl.readHeaders(b, 7);
		if (!handleAuthenticationResponse(requestHeaders)) {
			rc = ResponseCodes.OBEX_HTTP_UNAUTHORIZED;
		} else {
			handleAuthenticationChallenge(requestHeaders, (OBEXHeaderSetImpl) replyHeaders);
			rc = ResponseCodes.OBEX_HTTP_INTERNAL_ERROR;
			try {
				rc = handler.onConnect(requestHeaders, replyHeaders);
			} catch (Throwable e) {
				DebugLog.error("onConnect", e);
			}
		}
		byte[] connectResponse = new byte[4];
		connectResponse[0] = OBEXOperationCodes.OBEX_VERSION;
		connectResponse[1] = 0; /* Flags */
		connectResponse[2] = OBEXUtils.hiByte(obexConnectionParams.mtu);
		connectResponse[3] = OBEXUtils.loByte(obexConnectionParams.mtu);
		writePacketWithFlags(rc, connectResponse, replyHeaders);
		if (rc == ResponseCodes.OBEX_HTTP_OK) {
			this.isConnected = true;
		}
	}

	boolean handleAuthenticationResponse(OBEXHeaderSetImpl incomingHeaders) throws IOException {
		return handleAuthenticationResponse(incomingHeaders, handler);
	}

	private boolean validateConnection() throws IOException {
		if (this.isConnected) {
			return true;
		}
		writePacket(ResponseCodes.OBEX_HTTP_BAD_REQUEST, null);
		return false;
	}

	private void processDisconnect(byte[] b) throws IOException {
		DebugLog.debug("Disconnect operation");
		if (!validateConnection()) {
			return;
		}
		OBEXHeaderSetImpl requestHeaders = OBEXHeaderSetImpl.readHeaders(b, 3);
		OBEXHeaderSetImpl replyHeaders = createOBEXHeaderSetImpl();
		int rc = ResponseCodes.OBEX_HTTP_OK;
		try {
			handler.onDisconnect(requestHeaders, replyHeaders);
		} catch (Throwable e) {
			rc = ResponseCodes.OBEX_HTTP_UNAVAILABLE;
			DebugLog.error("onDisconnect", e);
		}
		this.isConnected = false;
		writePacket(rc, replyHeaders);
	}

	private void processDelete(OBEXHeaderSetImpl requestHeaders) throws IOException {
		DebugLog.debug("Delete operation");
		OBEXHeaderSetImpl replyHeaders = createOBEXHeaderSetImpl();
		handleAuthenticationChallenge(requestHeaders, replyHeaders);
		int rc = ResponseCodes.OBEX_HTTP_OK;
		try {
			rc = handler.onDelete(requestHeaders, replyHeaders);
		} catch (Throwable e) {
			rc = ResponseCodes.OBEX_HTTP_UNAVAILABLE;
			DebugLog.error("onDelete", e);
		}
		writePacket(rc, replyHeaders);
	}

	private void processPut(byte[] b, boolean finalPacket) throws IOException {
		DebugLog.debug("Put/Delete operation");
		if (!validateConnection()) {
			return;
		}
		OBEXHeaderSetImpl requestHeaders = OBEXHeaderSetImpl.readHeaders(b, 3);
		// OFF; Not tested in TCK.
		// while ((!finalPacket) && (!operation.isIncommingDataReceived())) {
		// finalPacket = operation.exchangeRequestPhasePackets();
		// }
		// if (operation.isErrorReceived()) {
		// return;
		// }

		// If Client re-send the command packet with an Authenticate Response
		if (!handleAuthenticationResponse(requestHeaders, handler)) {
			writePacket(ResponseCodes.OBEX_HTTP_UNAUTHORIZED, null);
			return;
		}
		// A PUT operation with NO Body or End-of-Body headers whatsoever should
		// be treated as a delete request.
		if (finalPacket && (!requestHeaders.hasIncommingData())) {
			processDelete(requestHeaders);
			return;
		}
		DebugLog.debug("Put operation");
		operation = new OBEXServerOperationPut(this, requestHeaders, finalPacket);
		try {
			int rc = ResponseCodes.OBEX_HTTP_OK;
			try {
				rc = handler.onPut(operation);
			} catch (Throwable e) {
				rc = ResponseCodes.OBEX_HTTP_UNAVAILABLE;
				DebugLog.error("onPut", e);
			}
			if (!operation.isAborted) {
				operation.writeResponse(rc);
			}
		} finally {
			operation.close();
			operation = null;
		}
	}

	private void processGet(byte[] b, boolean finalPacket) throws IOException {
		DebugLog.debug("Get operation");
		if (!validateConnection()) {
			return;
		}
		OBEXHeaderSetImpl requestHeaders = OBEXHeaderSetImpl.readHeaders(b, 3);
		// If Client re-send the command packet with an Authenticate Response
		if (!handleAuthenticationResponse(requestHeaders, handler)) {
			writePacket(ResponseCodes.OBEX_HTTP_UNAUTHORIZED, null);
			return;
		}

		operation = new OBEXServerOperationGet(this, requestHeaders, finalPacket);

		// OFF; Not tested in TCK
		// while ((!finalPacket) && (!operation.isIncommingDataReceived())) {
		// finalPacket = operation.exchangeRequestPhasePackets();
		// }
		// if (operation.isErrorReceived()) {
		// return;
		// }
		try {
			int rc = ResponseCodes.OBEX_HTTP_OK;
			try {
				rc = handler.onGet(operation);
			} catch (Throwable e) {
				rc = ResponseCodes.OBEX_HTTP_UNAVAILABLE;
				DebugLog.error("onGet", e);
			}
			if (!operation.isAborted) {
				operation.writeResponse(rc);
			}
		} finally {
			operation.close();
			operation = null;
		}
	}

	private void processAbort() throws IOException {
		DebugLog.debug("Abort operation");
		if (!validateConnection()) {
			return;
		}
		if (operation != null) {
			operation.isAborted = true;
			operation.close();
			operation = null;
			writePacket(OBEXOperationCodes.OBEX_RESPONSE_SUCCESS, null);
		} else {
			writePacket(ResponseCodes.OBEX_HTTP_BAD_REQUEST, null);
		}
	}

	private void processSetPath(byte[] b, boolean finalPacket) throws IOException {
		DebugLog.debug("SetPath operation");
		if (!validateConnection()) {
			return;
		}
		if (b.length < 5) {
			throw new IOException("Corrupted OBEX data");
		}
		OBEXHeaderSetImpl requestHeaders = OBEXHeaderSetImpl.readHeaders(b, 5);
		// DebugLog.debug("setPath b[3]", b[3]);
		// b[4] = (byte) ((backup?1:0) | (create?0:2));
		boolean backup = ((b[3] & 1) != 0);
		boolean create = ((b[3] & 2) == 0);
		DebugLog.debug("setPath backup", backup);
		DebugLog.debug("setPath create", create);

		if (!handleAuthenticationResponse(requestHeaders, handler)) {
			writePacket(ResponseCodes.OBEX_HTTP_UNAUTHORIZED, null);
			return;
		}

		OBEXHeaderSetImpl replyHeaders = createOBEXHeaderSetImpl();
		handleAuthenticationChallenge(requestHeaders, replyHeaders);
		int rc = ResponseCodes.OBEX_HTTP_OK;
		try {
			rc = handler.onSetPath(requestHeaders, replyHeaders, backup, create);
		} catch (Throwable e) {
			rc = ResponseCodes.OBEX_HTTP_UNAVAILABLE;
			DebugLog.error("onSetPath", e);
		}
		writePacket(rc, replyHeaders);
	}

}
