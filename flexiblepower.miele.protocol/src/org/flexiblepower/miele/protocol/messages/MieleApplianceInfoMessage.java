package org.flexiblepower.miele.protocol.messages;

/********************************************
 * Copyright (c) 2011 Alliander.            *
 * All rights reserved.                     *
 *                                          *
 * Contributors:                            *
 *     IBM - initial API and implementation *
 *     TNO - modifications for FPS          *
 *******************************************/

import java.util.Collection;
import java.util.Iterator;
import java.util.Map;

/**
 * @author IBM, TNO
 * @version 0.7.0
 */
public class MieleApplianceInfoMessage extends MieleGatewayMessage {

    protected String applianceId;
    protected int applianceType;
    protected int applianceState;
    protected int applianceClass;

    protected Map<String, String> actions;

    /**
     * @return TODO
     */
    public Map<String, String> getActions() {
        return actions;
    }

    /**
     * @return TODO
     */
    public int getApplianceClass() {
        return applianceClass;
    }

    /**
     * @return TODO
     */
    public String getApplianceId() {
        return applianceId;
    }

    /**
     * @return TODO
     */
    public int getApplianceState() {
        return applianceState;
    }

    /**
     * @return TODO
     */
    public int getApplianceType() {
        return applianceType;
    }

    /**
     * @param actions
     */
    public void setActions(final Map<String, String> actions) {
        this.actions = actions;
    }

    /**
     * @param applianceClass
     */
    public void setApplianceClass(final int applianceClass) {
        this.applianceClass = applianceClass;
    }

    /**
     * @param applianceId
     */
    public void setApplianceId(final String applianceId) {
        this.applianceId = applianceId;
    }

    /**
     * @param applianceState
     */
    public void setApplianceState(final int applianceState) {
        this.applianceState = applianceState;
    }

    /**
     * @param applianceType
     */
    public void setApplianceType(final int applianceType) {
        this.applianceType = applianceType;
    }

    @Override
    public String toString() {
        final int maxLen = 10;
        return "MieleApplianceInfoMessage [applianceId=" + applianceId
               + ", applianceType="
               + applianceType
               + ", applianceState="
               + applianceState
               + ", applianceClass="
               + applianceClass
               + ", actions="
               + (actions != null ? toString(actions.entrySet(), maxLen) : null)
               + "]";
    }

    private String toString(Collection<?> collection, int maxLen) {
        StringBuilder builder = new StringBuilder();
        builder.append("[");
        int i = 0;
        for (Iterator<?> iterator = collection.iterator(); iterator.hasNext() && i < maxLen; i++) {
            if (i > 0) {
                builder.append(", ");
            }
            builder.append(iterator.next());
        }
        builder.append("]");
        return builder.toString();
    }

}
