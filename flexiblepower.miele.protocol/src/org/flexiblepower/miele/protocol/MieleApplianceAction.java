package org.flexiblepower.miele.protocol;

import java.util.Collection;
import java.util.HashMap;
import java.util.Iterator;
import java.util.Map;

import org.flexiblepower.miele.protocol.configuration.MieleApplianceConfiguration;
import org.flexiblepower.miele.protocol.constants.MieleGatewayConstants;

public final class MieleApplianceAction {

    private final MieleAppliance targetAppliance;
    private final String action;
    private final Map<String, String> parameters;

    private final boolean isInfoAction;

    private MieleApplianceAction(MieleAppliance targetAppliance,
                                 String action,
                                 boolean isInfoAction,
                                 Map<String, String> parameters) {
        super();
        this.targetAppliance = targetAppliance;
        this.action = action;
        this.isInfoAction = isInfoAction;
        this.parameters = parameters;
    }

    public static MieleApplianceAction createInfoAction(MieleAppliance targetAppliance) {
        Map<String, String> params = new HashMap<String, String>();
        addBaseParameters(params);
        params.put(MieleGatewayConstants.MG_URL_PARAM_APPLIANCE_ID, targetAppliance.getApplianceId());
        params.put(MieleGatewayConstants.MG_URL_PARAM_APPLIANCE_TYPE, targetAppliance.getApplianceType());
        return new MieleApplianceAction(targetAppliance, null, true, params);
    }

    public static MieleApplianceAction createAction(MieleAppliance targetAppliance, String action) {
        Map<String, String> params = new HashMap<String, String>();
        return new MieleApplianceAction(targetAppliance, action, false, params);
    }

    public boolean isInfoAction() {
        return isInfoAction;
    }

    public String getAction() {
        return action;
    }

    public String parametersToString() {
        String result = "";

        if (parameters.isEmpty()) {
            return result;
        } else {
            for (String key : parameters.keySet()) {
                result += "&" + key + "=" + parameters.get(key);
            }
            return result.substring(1);
        }
    }

    private static void addBaseParameters(Map<String, String> params) {
        params.put(MieleGatewayConstants.MG_URL_PARAM_LANGUAGE,
                   MieleApplianceConfiguration.MIELE_AGENT_LANGUAGE_CODE_DEFAULT);
    }

    @Override
    public String toString() {
        final int maxLen = 10;
        return "MieleApplianceAction [targetAppliance=" + targetAppliance
               + ", action="
               + action
               + ", parameters="
               + (parameters != null ? toString(parameters.entrySet(), maxLen) : null)
               + ", isInfoAction="
               + isInfoAction
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
