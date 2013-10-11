package org.flexiblepower.miele.protocol;

import java.io.IOException;

import org.flexiblepower.miele.protocol.messages.MieleGatewayMessage;

public interface MieleProtocol {

    /**
     * Perform action on appliance by sending a request to the Miele Gateway.
     * 
     * @param action
     *            The action to be performed.
     * @param paramName
     *            The parameter name (e.g. 'p1').
     * @param paramValue
     *            The value of the parameter.
     * @return The action result (ActionResult).
     */
            MieleGatewayMessage
            performApplianceAction(MieleAppliance appliance, MieleApplianceAction action) throws IOException;

}
