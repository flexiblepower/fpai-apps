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
public interface MieleDishWasherConstants extends MieleApplianceConstants {

    /**
     * Default duration in minutes of a program in the DW stub
     */
    int DEFAULT_PROGRAM_DURATION = 45;

    /**
     * Default delay before in minutes before program start in the DW start
     */
    int DEFAULT_PROGRAM_DELAY = 15;

    /**
	 * 
	 */
    String PROGRAM_STRONG_65C = "Stark 65°C";

}
