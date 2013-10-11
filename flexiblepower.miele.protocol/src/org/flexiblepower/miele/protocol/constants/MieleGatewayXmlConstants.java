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
public interface MieleGatewayXmlConstants {

    /**
     * The gateway sends the following message types
     */
    public enum MieleGatewayMessageType {
        /**
		 * 
		 */
        APPLIANCE_LIST,
        /**
		 * 
		 */
        APPLIANCE_INFO,
        /**
		 * 
		 */
        ACTION_OK,
        /**
		 * 
		 */
        ACTION_ERROR
    }

    // Elements and attributes of Miele Gateway XML response messages
    /**
	 * 
	 */
    String ELEMENT_DEVICE = "device";
    /**
	 * 
	 */
    String ELEMENT_KEY = "key";
    /**
	 * 
	 */
    String ATTR_KEY_VALUE = "value";
    /**
	 * 
	 */
    String ATTR_KEY_NAME = "name";
    /**
	 * 
	 */
    String ELEMENT_DEVICES = "devices";
    /**
	 * 
	 */
    String ELEMENT_INFORMATION = "information";
    /**
	 * 
	 */
    String ELEMENT_ACTIONS = "actions";
    /**
	 * 
	 */
    String ELEMENT_ACTION = "action";
    /**
	 * 
	 */
    String ATTR_ACTION_NAME = "name";
    /**
	 * 
	 */
    String ATTR_ACTION_URL = "URL";
    /**
	 * 
	 */
    String ELEMENT_OK = "ok";
    /**
	 * 
	 */
    String ELEMENT_ERROR = "error";
    /**
	 * 
	 */
    String ELEMENT_MESSAGE = "message";

    // Appliance information keys (German)
    // TODO: make this configurable using a property file
    /**
	 * 
	 */
    String MA_APPLIANCE_STATUS = "Geratestatus";
    /**
	 * 
	 */
    String MA_APPLIANCE_TYPE = "Gerat";
    /**
	 * 
	 */
    String MA_FRIDGE_STATUS = "Kuhlstatus";
    /**
	 * 
	 */
    String MA_FRIDGE_ACTUAL_TEMPERATURE = "Aktuelle Kuhltemperatur";
    /**
	 * 
	 */
    String MA_FRIDGE_TARGET_TEMPERATURE = "Ziel-Kuhltemperatur";
    /**
	 * 
	 */
    String MA_FREEZER_STATUS = "Gefrierstatus";
    /**
	 * 
	 */
    String MA_FREEZER_ACTUAL_TEMPERATURE = "Aktuelle Gefriertemperatur";
    /**
	 * 
	 */
    String MA_FREEZER_TARGET_TEMPERATURE = "Ziel-Gefriertemperatur";

}
