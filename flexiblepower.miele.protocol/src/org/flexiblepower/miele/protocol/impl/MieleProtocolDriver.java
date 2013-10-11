package org.flexiblepower.miele.protocol.impl;

import java.io.IOException;
import java.net.URL;
import java.security.AccessController;
import java.security.PrivilegedActionException;
import java.security.PrivilegedExceptionAction;
import java.util.Date;
import java.util.Map;

import org.flexiblepower.miele.protocol.MieleAppliance;
import org.flexiblepower.miele.protocol.MieleApplianceAction;
import org.flexiblepower.miele.protocol.MieleProtocol;
import org.flexiblepower.miele.protocol.configuration.MieleApplianceConfiguration;
import org.flexiblepower.miele.protocol.constants.MieleGatewayConstants;
import org.flexiblepower.miele.protocol.http.HttpUtilException;
import org.flexiblepower.miele.protocol.http.HttpUtils;
import org.flexiblepower.miele.protocol.impl.MieleProtocolDriver.Config;
import org.flexiblepower.miele.protocol.messages.MieleApplianceInfoMessage;
import org.flexiblepower.miele.protocol.messages.MieleGatewayErrorMessage;
import org.flexiblepower.miele.protocol.messages.MieleGatewayMessage;
import org.flexiblepower.miele.protocol.xml.MieleGatewayMessageParser;
import org.flexiblepower.miele.protocol.xml.MieleGatewayMessageParserException;
import org.flexiblepower.time.TimeService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import aQute.bnd.annotation.component.Activate;
import aQute.bnd.annotation.component.Component;
import aQute.bnd.annotation.component.Reference;
import aQute.bnd.annotation.metatype.Configurable;
import aQute.bnd.annotation.metatype.Meta;

@Component(designate = Config.class, provide = MieleProtocol.class)
public class MieleProtocolDriver implements MieleProtocol {
    @Meta.OCD
    interface Config {
        @Meta.AD(deflt = "http://miele-gateway.labsgn.tno.nl")
        URL gatewayUrl();

        @Meta.AD(deflt = MieleApplianceConfiguration.MIELE_AGENT_LANGUAGE_CODE_DEFAULT)
        String languageCode();
    }

    private static final Logger logger = LoggerFactory.getLogger(MieleProtocolDriver.class);

    // Number of seconds between actions
    private static final int ACTION_TIME_INTERVAL = 3;

    // Parser for Miele Gateway responses
    private final MieleGatewayMessageParser parser;

    // URL base for appliance operations
    private String applianceUrlBase;

    private Config config;

    public MieleProtocolDriver() {
        parser = new MieleGatewayMessageParser();
    }

    private TimeService timeService;

    @Reference
    public void setTimeService(TimeService timeService) {
        this.timeService = timeService;
    }

    @Activate
    public void init(Map<String, ?> properties) {
        config = Configurable.createConfigurable(Config.class, properties);
        applianceUrlBase = config.gatewayUrl().toString() + MieleGatewayConstants.MG_URL_DEVICE_TARGET;

        if (logger.isDebugEnabled()) {
            logger.debug("Updating protocol driver with properties: applianceUrlBase=" + applianceUrlBase);
        }
    }

    /*
     * (non-Javadoc)
     * 
     * @see
     * org.flexiblepower.miele.protocol.MieleProtocol#performApplianceAction(org.flexiblepower.miele.protocol.MieleAppliance
     * , org.flexiblepower.miele.protocol.MieleApplianceAction)
     */
    @Override
    public MieleGatewayMessage
            performApplianceAction(MieleAppliance appliance, MieleApplianceAction action) throws IOException {
        synchronized (appliance) {
            String actionURL = null;
            String parameters = action.parametersToString();
            if (action.isInfoAction()) {
                actionURL = applianceUrlBase;
            } else {
                actionURL = getActionUrl(appliance, action.getAction());
            }

            if (actionURL == null) {
                throw new IOException("Invalid action: " + action);
            }

            // Check time interval between actions, wait if needed
            checkActionWaitTime(appliance);

            // Perform request and parse the response
            MieleGatewayMessage response = sendGatewayRequest(actionURL, parameters);

            return response;
        }
    }

    /**
     * Retrieve the action from the appliance info object and return the action. If the action is not available log an
     * error and return null
     * 
     * @param action
     *            The action name.
     * @return The action url if currently available or otherwise null.
     */
    private String getActionUrl(MieleAppliance appliance, final String action) throws IOException {
        String actionURL = null;

        MieleApplianceInfoMessage info = getApplianceInfo(appliance);

        if (info.getActions() != null) {
            // Get the action from the available action list of the appliance
            actionURL = info.getActions().get(action);
        }

        return actionURL;
    }

    /**
     * Checks the time stamp of the last action to prevent that the gateway will be overloaded by requests and to give
     * it time to process the action.
     */
    private void checkActionWaitTime(MieleAppliance targetAppliance) {
        Date lastActionTime = targetAppliance.getLastActionTime();
        Date current = timeService.getTime();

        int timeSinceLastAction = Math.round((current.getTime() - lastActionTime.getTime()) / 1000);

        // Wait before completing action
        if (timeSinceLastAction < ACTION_TIME_INTERVAL) {
            try {
                Thread.sleep((ACTION_TIME_INTERVAL - timeSinceLastAction) * 1000);
            } catch (InterruptedException e) {
                // Do nothing
            }
        }
    }

    /**
     * Get information about the appliance from the gateway. The return value is a MieleGatewayMessage message instance
     * 
     * @param applianceID
     *            identifier of the appliance
     * @param applianceType
     *            type of the appliance
     * @return A MieleGatewayMessage from the Miele Gateway
     */
    private MieleApplianceInfoMessage getApplianceInfo(MieleAppliance targetAppliance) throws IOException {

        MieleApplianceAction action = MieleApplianceAction.createInfoAction(targetAppliance);
        String parameters = action.parametersToString();

        MieleGatewayMessage response = sendGatewayRequest(applianceUrlBase, parameters);

        if (response instanceof MieleApplianceInfoMessage) {
            return (MieleApplianceInfoMessage) response;
        } else {
            String msg = "No appliance info received. ";
            if (response instanceof MieleGatewayErrorMessage) {
                msg += "Error: " + ((MieleGatewayErrorMessage) response).getMessage();
            }
            logger.error(msg);
            throw new IOException("Could not get appliance info. " + msg);
        }
    }

    private MieleGatewayMessage sendGatewayRequest(final String url, final String parameters) throws IOException {
        try {
            return AccessController.doPrivileged(new PrivilegedExceptionAction<MieleGatewayMessage>() {
                @Override
                public MieleGatewayMessage run() throws IOException {
                    try {
                        return parser.parse(HttpUtils.httpGet(url,
                                                              parameters,
                                                              MieleGatewayConstants.MG_DEFAULT_ENCODING),
                                            config.languageCode());
                    } catch (MieleGatewayMessageParserException e) {
                        throw new IOException("Error parsing the Miele gateway response", e);
                    } catch (HttpUtilException e) {
                        throw new IOException("HTTP error when requesting Miele Gateway information: " + e.getMessage());
                    }
                }
            });
        } catch (PrivilegedActionException e) {
            if (e.getException() instanceof IOException) {
                throw (IOException) e.getException();
            } else if (e.getException() instanceof RuntimeException) {
                throw (RuntimeException) e.getException();
            } else {
                throw new RuntimeException(e.getMessage(), e.getException());
            }
        }
    }

    @Override
    public String toString() {
        return "MieleProtocolDriver [" + applianceUrlBase + "]";
    }
}
