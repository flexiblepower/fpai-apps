package org.flexiblepower.miele.protocol.constants;

/********************************************
 * Copyright (c) 2011 Alliander.            *
 * All rights reserved.                     *
 *                                          *
 * Contributors:                            *
 *     IBM - initial API and implementation *
 *     TNO - Modifications for FPS			*
 *******************************************/

/**
 * @author IBM, TNO
 * @version 0.7.0
 */
public interface MieleGatewayConstants {

    // Miele Gateway property name definitions
    /**
	 * 
	 */
    String MG_PROPERTY_PORT_NUMBER = "miele.gateway.portnumber";
    /**
	 * 
	 */
    String MG_PROPERTY_ID = "miele.gateway.id";

    // Miele Gateway defaults
    /**
	 * 
	 */
    String MG_DEFAULT_PORT_NUMBER = "8080";
    /**
	 * 
	 */
    String MG_DEFAULT_ID = "MIELE_GW_2000";
    /**
	 * 
	 */
    String MG_DEFAULT_ENCODING = "UTF-8";

    // Miele Gateway message constants
    /**
	 * 
	 */
    String ERROR_APPLIANCE_MISSING = "appliance_missing";
    /**
	 * 
	 */
    String ERROR_ACTION_MISSING = "action_missing";
    /**
	 * 
	 */
    String ERROR_ACTION_INCORRECT_PARAMS = "action_incorrect_params";
    /**
	 * 
	 */
    String ERROR_ACTION_EXECUTE = "action_execute";

    /**
	 * 
	 */
    String ERROR_MSG_APPLIANCE_MISSING = "Fehler: Apparat unbekannt.";
    /**
	 * 
	 */
    String ERROR_MSG_ACTION_MISSING = "Fehler: Aktion unbekannt.";
    /**
	 * 
	 */
    String ERROR_MSG_ACTION_INCORRECT_PARAMS = "Fehler: Parameters nicht korrekt.";
    /**
	 * 
	 */
    String ERROR_MSG_ACTION_EXECUTE = "Fehler: start Aktion kann derzeit nicht ausgeführt werden";
    /**
	 * 
	 */
    String ERROR_MSG_NOT_SUPPORTED_BY_STUB = "Currently not supported by stub.";

    // Miele constants for the URL definitions
    /**
	 * 
	 */
    String MG_URL_HOMEBUS_TARGET = "/homebus";
    /**
	 * 
	 */
    String MG_URL_DEVICE_TARGET = MG_URL_HOMEBUS_TARGET + "/device";
    /**
	 * 
	 */
    String MG_URL_PARAM_LANGUAGE = "language";
    /**
	 * 
	 */
    String MG_URL_PARAM_APPLIANCE_ID = "id";
    /**
	 * 
	 */
    String MG_URL_PARAM_APPLIANCE_ACTION = "action";
    /**
	 * 
	 */
    String MG_URL_PARAM_APPLIANCE_TYPE = "type";
    /**
	 * 
	 */
    String MG_URL_PARAM_APPLIANCE_P1 = "p1";
    /**
	 * 
	 */
    String MG_APPLIANCE_ACTION_ON = "on";
    /**
	 * 
	 */
    String MG_APPLIANCE_ACTION_OFF = "off";

    /**
	 * 
	 */
    float MA_INITIAL_TEMP_FRIDGE = 6;
    /**
	 * 
	 */
    float MA_INITIAL_TEMP_FREEZER = -16f;

    /**
	 * 
	 */
    String LANGUAGE_GERMAN = "de_DE";
    /**
	 * 
	 */
    String LANGUAGE_ENGLISH = "en_EN";
    /**
	 * 
	 */
    String LANGUAGE_DUTCH = "nl_NL";

}
