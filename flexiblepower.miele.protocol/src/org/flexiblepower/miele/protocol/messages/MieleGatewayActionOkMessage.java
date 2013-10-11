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
public class MieleGatewayActionOkMessage extends MieleGatewayMessage {

    protected String action;

    /**
     * @return TODO
     */
    public String getAction() {
        return action;
    }

    /**
     * @param action
     */
    public void setAction(final String action) {
        this.action = action;
    }

    @Override
    public String toString() {
        return "MieleGatewayActionOkMessage [action=" + action + "]";
    }
}
