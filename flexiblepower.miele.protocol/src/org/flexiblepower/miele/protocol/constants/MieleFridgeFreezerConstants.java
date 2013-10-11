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
public interface MieleFridgeFreezerConstants {

    // Actions
    // String FRIDGE_ACTION_SUPERCOOL = "supercool";
    // String FREEZER_ACTION_SUPERFROST = "superfrost";

    //
    // Freezer constants
    //
    /**
	 * 
	 */
    float FREEZER_MAX_TEMPERATURE = -16f;
    /**
	 * 
	 */
    float FREEZER_MIN_TEMPERATURE = -26f;
    /**
	 * 
	 */
    int FREEZER_POWER_CONSUMPTION = 136; // type KFN 9758 iD
                                         // 3

    // Default target temperature freezer
    /**
	 * 
	 */
    float DEFAULT_FREEZER_TARGET_TEMPERATURE = -16.0f;

    // Default freezer temperature variance for switching on/off
    /**
	 * 
	 */
    float DEFAULT_FREEZER_TEMPERATURE_VARIANCE = 2f;

    //
    // Refrigerator constants
    //
    /**
	 * 
	 */
    float FRIDGE_MAX_TEMPERATURE = 7f;
    /**
	 * 
	 */
    float FRIDGE_MIN_TEMPERATURE = 4f;
    /**
	 * 
	 */
    int FRIDGE_POWER_CONSUMPTION = 200; // Assumption

    // Default target temperature refrigerator
    /**
	 * 
	 */
    float DEFAULT_FRIDGE_TARGET_TEMPERATURE = 5.0f;

    // Default freezer temperature variance for switching on/off
    /**
	 * 
	 */
    float DEFAULT_FRIDGE_TEMPERATURE_VARIANCE = 2f;
}
