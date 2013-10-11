package org.flexiblepower.miele.protocol.configuration;

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
public interface MieleApplianceConfiguration {

    /**
	 * 
	 */
    String MIELE_APPLIANCE_ID = "appliance.id";
    /**
	 * 
	 */
    String MIELE_APPLIANCE_TYPE = "appliance.type";
    /**
	 * 
	 */
    String MIELE_AGENT_LANGUAGE_CODE = "language.code";
    /**
	 * 
	 */
    String MIELE_APPLIANCE_POWER_CONSUMPTION = "appliance.power";

    /** Configuration property: Miele gateway protocol property */
    String MIELE_GATEWAY_PROTOCOL_PROPERTY = "gateway.protocol";

    /** Configuration property: Miele gateway hostname property */
    String MIELE_GATEWAY_HOSTNAME_PROPERTY = "gateway.hostname";

    /** Configuration property: Miele gateway port property */
    String MIELE_GATEWAY_PORT_PROPERTY = "gateway.port";

    // Default values
    /**
	 * 
	 */
    String MIELE_AGENT_LANGUAGE_CODE_DEFAULT = "en_EN"; // or de_DE

    /** Default Miele appliance power consumption when not configured */
    int MIELE_APPLIANCE_POWER_CONSUMPTION_DEFAULT = 200;
    /**
	 * 
	 */
    String MIELE_APPLIANCE_POWER_CONSUMPTION_DEFAULT_STR = "200";

    /** Miele gateway protocol default value */
    String MIELE_GATEWAY_PROTOCOL_DEFAULT = "http";

    /** Miele gateway hostname default value */
    String MIELE_GATEWAY_HOSTNAME_DEFAULT = "localhost";

    /** Miele gateway port default value */
    String MIELE_GATEWAY_PORT_DEFAULT = "80";

    /**
     * @return TODO
     */
    String appliance_id();

    /**
     * @return TODO
     */
    double appliance_power();

    /**
     * @return TODO
     */
    String appliance_type();

    /**
     * @return TODO
     */
    String hostname();

    /**
     * @return TODO
     */
    String language_code();

    /**
     * @return TODO
     */
    String port();

    /**
     * @return TODO
     */
    String protocol();

}
