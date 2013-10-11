package org.flexiblepower.miele.protocol.http;

/********************************************
 * Copyright (c) 2011 Alliander.            *
 * All rights reserved.                     *
 *                                          *
 * Contributors:                            *
 *     IBM - initial API and implementation *
 *     TNO - modifications for FPS          *
 *******************************************/

/**
 * @author IBM, TNO
 * @version 0.7.0
 */
public class HttpUtilException extends Exception {

    /**
	 * 
	 */
    private static final long serialVersionUID = 4737650105850804444L;

    /**
     * @param msg
     */
    public HttpUtilException(final String msg) {
        super(msg);
    }

    /**
     * @param message
     * @param cause
     */
    public HttpUtilException(final String message, final Throwable cause) {
        super(message, cause);
    }
}
