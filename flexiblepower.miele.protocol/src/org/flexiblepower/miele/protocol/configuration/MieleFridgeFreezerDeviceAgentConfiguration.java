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
public interface MieleFridgeFreezerDeviceAgentConfiguration extends MieleApplianceConfiguration {

    /** Configuration property: minimal duration SuperCool (in seconds) */
    String MIELE_MIN_DURATION_SUPERCOOL = "min.duration.supercool";

    /** Configuration property: minimal duration SuperFrost (in seconds) */
    String MIELE_MIN_DURATION_SUPERFROST = "min.duration.superfrost";

    /** Configuration property: Refrigerator target temperature property */
    String MIELE_FRIDGE_TARGET_TEMPERATURE = "fridge.temperature";

    /** Configuration property: Refrigerator target temperature property */
    String MIELE_FREEZER_TARGET_TEMPERATURE = "freezer.temperature";

    // Default values
    /**
	 * 
	 */
    float MIELE_FREEZER_TARGET_TEMPERATURE_DEFAULT = -20f;
    /**
	 * 
	 */
    String MIELE_FREEZER_TARGET_TEMPERATURE_DEFAULT_STR = "-20";
    /**
	 * 
	 */
    float MIELE_FREEZER_TARGET_TEMPERATURE_MARGIN = -0.5f;

    /**
	 * 
	 */
    float MIELE_FRIDGE_TARGET_TEMPERATURE_DEFAULT = 5f;
    /**
	 * 
	 */
    String MIELE_FRIDGE_TARGET_TEMPERATURE_DEFAULT_STR = "5";
    /**
	 * 
	 */
    float MIELE_FRIDGE_TARGET_TEMPERATURE_MARGIN = 0.5f;

    /**
     * Margin for switching off SuperFrost when current temperature is lower then target temperature.
     */
    float SUPERFROST_OFF_TARGET_MARGIN = -0.5f;

    /**
     * Margin for switching off SuperCool when current temperature is lower then target temperature.
     */
    float SUPERCOOL_OFF_TARGET_MARGIN = -0.5f;

    /**
     * Threshold for switching off SuperFrost when current price is higher than bid price
     */
    float SUPERFROST_OFF_MINIMUM_CHANGE = -2.0f;

    /**
     * Threshold for switching off SuperFrost when current price is higher than bid price
     */
    float SUPERCOOL_OFF_MINIMUM_CHANGE = -0.5f;

    /** Default value for minimal duration SuperFrost (in seconds) */
    int MIELE_MIN_DURATION_SUPERFROST_DEFAULT = 300;
    /**
	 * 
	 */
    String MIELE_MIN_DURATION_SUPERFROST_DEFAULT_STR = "300";

    /** Default value for minimal duration SuperCool (in seconds) */
    int MIELE_MIN_DURATION_SUPERCOOL_DEFAULT = 300;
    /**
	 * 
	 */
    String MIELE_MIN_DURATION_SUPERCOOL_DEFAULT_STR = "300";

    /**
     * @return TODO
     */
    float freezer_temperature();

    /**
     * @return TODO
     */
    float fridge_temperature();

    /**
     * @return TODO
     */
    int min_duration_supercool();

    /**
     * @return TODO
     */
    int min_duration_superfrost();
}
