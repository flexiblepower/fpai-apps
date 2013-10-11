package org.flexiblepower.miele.protocol.xml;

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
public class MieleGatewayMessageParserException extends Exception {

    /**
	 * 
	 */
    private static final long serialVersionUID = -6272001335553874524L;

    /**
     * @param msg
     */
    public MieleGatewayMessageParserException(final String msg) {
        super(msg);
    }

    /**
     * @param message
     * @param cause
     */
    public MieleGatewayMessageParserException(final String message, final Throwable cause) {
        super(message, cause);
    }
}
