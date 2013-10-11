package org.flexiblepower.miele.protocol.messages;

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
public class MieleGatewayErrorMessage extends MieleGatewayMessage {

    protected String message;
    protected String errorType;

    /**
     * @return TODO
     */
    public String getErrorType() {
        return errorType;
    }

    /**
     * @return TODO
     */
    public String getMessage() {
        return message;
    }

    /**
     * @param errorType
     */
    public void setErrorType(final String errorType) {
        this.errorType = errorType;
    }

    /**
     * @param message
     */
    public void setMessage(final String message) {
        this.message = message;
    }

    @Override
    public String toString() {
        return "MieleGatewayErrorMessage [message=" + message + ", errorType=" + errorType + "]";
    }
}
