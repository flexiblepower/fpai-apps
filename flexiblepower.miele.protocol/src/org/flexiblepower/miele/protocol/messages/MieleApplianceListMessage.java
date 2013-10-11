package org.flexiblepower.miele.protocol.messages;

/********************************************
 * Copyright (c) 2011 Alliander.            *
 * All rights reserved.                     *
 *                                          *
 * Contributors:                            *
 *     IBM - initial API and implementation *
 *     TNO - modifications for FPS          *
 *******************************************/

import java.util.ArrayList;
import java.util.List;

/**
 * @author IBM, TNO
 * @version 0.7.0
 */
public class MieleApplianceListMessage extends MieleGatewayMessage {

    List<MieleApplianceInfoMessage> appliances;

    /**
     * @param appliance
     */
    public void addAppliance(final MieleApplianceInfoMessage appliance) {
        if (appliances == null) {
            appliances = new ArrayList<MieleApplianceInfoMessage>();
        }
        appliances.add(appliance);
    }

    /**
     * @return TODO
     */
    public List<MieleApplianceInfoMessage> getAppliances() {
        return appliances;
    }

    /**
     * @param appliances
     */
    public void setAppliances(final List<MieleApplianceInfoMessage> appliances) {
        this.appliances = appliances;
    }

    @Override
    public String toString() {
        final int maxLen = 10;
        return "MieleApplianceListMessage [appliances=" + (appliances != null ? appliances.subList(0,
                                                                                                   Math.min(appliances.size(),
                                                                                                            maxLen))
                                                                             : null)
               + "]";
    }
}
