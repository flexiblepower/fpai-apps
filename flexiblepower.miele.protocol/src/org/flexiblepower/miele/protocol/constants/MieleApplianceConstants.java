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
public interface MieleApplianceConstants {

    // Action names (keys) German
    /**
	 * 
	 */
    String APPLIANCE_ACTION_START = "Start";
    /**
	 * 
	 */
    String APPLIANCE_ACTION_STOP = "Stop";
    /**
	 * 
	 */
    String APPLIANCE_ACTION_SUPERFROST_ON = "SuperFrost Ein";
    /**
	 * 
	 */
    String APPLIANCE_ACTION_SUPERFROST_OFF = "SuperFrost Aus";
    /**
	 * 
	 */
    String APPLIANCE_ACTION_SUPERCOOLING_ON = "SuperKühlen Ein";
    /**
	 * 
	 */
    String APPLIANCE_ACTION_SUPERCOOLING_OFF = "SuperKühlen Aus";

    // Action names (key) English
    /**
	 * 
	 */
    String APPLIANCE_ACTION_START_EN = "Start";
    /**
	 * 
	 */
    String APPLIANCE_ACTION_STOP_EN = "Stop";
    /**
	 * 
	 */
    String APPLIANCE_ACTION_SUPERFROST_ON_EN = "SuperFrost On";
    /**
	 * 
	 */
    String APPLIANCE_ACTION_SUPERFROST_OFF_EN = "SuperFrost Off";
    /**
	 * 
	 */
    String APPLIANCE_ACTION_SUPERCOOLING_ON_EN = "SuperCooling On";
    /**
	 * 
	 */
    String APPLIANCE_ACTION_SUPERCOOLING_OFF_EN = "SuperCooling Off";

    // Action parameter name in URL
    /**
	 * 
	 */
    String APPLIANCE_ACTION_PARAM_START = "start";
    /**
	 * 
	 */
    String APPLIANCE_ACTION_PARAM_STOP = "stop";
    /**
	 * 
	 */
    String APPLIANCE_ACTION_PARAM_SUPERFROST_ON = "startSuperFreezing";
    /**
	 * 
	 */
    String APPLIANCE_ACTION_PARAM_SUPERFROST_OFF = "stopSuperFreezing";
    /**
	 * 
	 */
    String APPLIANCE_ACTION_PARAM_SUPERCOOLING_ON = "SuperCooling On";
    /**
	 * 
	 */
    String APPLIANCE_ACTION_PARAM_SUPERCOOLING_OFF = "SuperCooling Off";

    /*
     * Appliance keys used in the xml information of the gateway appliance info message.
     */

    // Appliance information key (make Enum?)
    /**
	 * 
	 */
    int APPLIANCE_CLASS = 1;
    /**
	 * 
	 */
    int APPLIANCE_STATE = 2;
    /**
	 * 
	 */
    int REFRIGERATOR_STATE = 3;
    /**
	 * 
	 */
    int REFRIGERATOR_TARGET_TEMPERATURE = 4;
    /**
	 * 
	 */
    int REFRIGERATOR_CURRENT_TEMPERATURE = 5;
    /**
	 * 
	 */
    int FREEZER_STATE = 6;
    /**
	 * 
	 */
    int FREEZER_TARGET_TEMPERATURE = 7;
    /**
	 * 
	 */
    int FREEZER_CURRENT_TEMPERATURE = 8;
    /**
	 * 
	 */
    int DISHWASHER_REMAINING_TIME = 9;
    /**
	 * 
	 */
    int DISHWASHER_PROGRAM = 10;
    /**
	 * 
	 */
    int DISHWASHER_PHASE = 11;
    /**
	 * 
	 */
    int DISHWASHER_DURATION = 12;
    /**
	 * 
	 */
    int DISHWASHER_START_TIME = 13;

    /**
	 * 
	 */
    int UNKNOWN_INFORMATION_KEY = 0;

    // Appliance info keys (German)
    /**
	 * 
	 */
    String APPLIANCE_CLASS_DE = "Gerät";
    /**
	 * 
	 */
    String APPLIANCE_STATE_DE = "Gerätestatus";
    /**
	 * 
	 */
    String REFRIGERATOR_STATE_DE = "Kühlstatus";
    /**
	 * 
	 */
    String REFRIGERATOR_TARGET_TEMPERATURE_DE = "Ziel-Kühltemperatur";
    /**
	 * 
	 */
    String REFRIGERATOR_CURRENT_TEMPERATURE_DE = "Aktuelle Kühltemperatur";
    /**
	 * 
	 */
    String FREEZER_STATE_DE = "Gefrierstatus";
    /**
	 * 
	 */
    String FREEZER_TARGET_TEMPERATURE_DE = "Ziel-Gefriertemperatur";
    /**
	 * 
	 */
    String FREEZER_CURRENT_TEMPERATURE_DE = "Aktuelle Gefriertemperatur";
    /**
	 * 
	 */
    String DISHWASHER_START_TIME_DE = "Startzeit";
    /**
	 * 
	 */
    String DISHWASHER_REMAINING_TIME_DE = "Restzeit";
    /**
	 * 
	 */
    String DISHWASHER_PROGRAM_DE = "Programm";
    /**
	 * 
	 */
    String DISHWASHER_PHASE_DE = "Phase";
    /**
	 * 
	 */
    String DISHWASHER_DURATION_DE = "Dauer";

    // Appliance info keys (English)
    /**
	 * 
	 */
    String APPLIANCE_CLASS_EN = "Appliance Type";
    /**
	 * 
	 */
    String APPLIANCE_STATE_EN = "State";
    /**
	 * 
	 */
    String REFRIGERATOR_STATE_EN = "State";
    // FIXME: adjusted for fridge only
    // String REFRIGERATOR_STATE_EN = "Fridge State";
    /**
	 * 
	 */
    String REFRIGERATOR_TARGET_TEMPERATURE_EN = "Target Temperature";
    // FIXME: adjusted for fridge only
    // String REFRIGERATOR_TARGET_TEMPERATURE_EN = "Fridge Target Temperature";

    /**
	 * 
	 */
    String REFRIGERATOR_CURRENT_TEMPERATURE_EN = "Current Temperature";
    // FIXME: adjusted for fridge only
    // String REFRIGERATOR_CURRENT_TEMPERATURE_EN = "Fridge Current Temperature";
    /**
	 * 
	 */
    String FREEZER_STATE_EN = "Freezer State";
    /**
	 * 
	 */
    String FREEZER_TARGET_TEMPERATURE_EN = "Freezer Target Temperature";
    /**
	 * 
	 */
    String FREEZER_CURRENT_TEMPERATURE_EN = "Freezer Current Temperature";
    /**
	 * 
	 */
    String DISHWASHER_START_TIME_EN = "Start Time";
    /**
	 * 
	 */
    String DISHWASHER_REMAINING_TIME_EN = "Remaining Time";
    /**
	 * 
	 */
    String DISHWASHER_PROGRAM_EN = "Program";
    /**
	 * 
	 */
    String DISHWASHER_PHASE_EN = "Phase";
    /**
	 * 
	 */
    String DISHWASHER_DURATION_EN = "Duration";

    /*
     * Appliance states
     */

    // Appliance states
    /**
	 * 
	 */
    int MA_STATE_OFF = 1; // Aus
    /**
	 * 
	 */
    int MA_STATE_READY = 2; // Bereit
    /**
	 * 
	 */
    int MA_STATE_PROGRAM = 3; // Programm gewählt
    /**
	 * 
	 */
    int MA_STATE_WAITING = 4; // Start verzögert
    /**
	 * 
	 */
    int MA_STATE_ON = 5; // In Betrieb
    /**
	 * 
	 */
    int MA_STATE_PAUSED = 6; // Pause
    /**
	 * 
	 */
    int MA_STATE_END = 7; // Ende
    /**
	 * 
	 */
    int MA_STATE_ERROR = 8; // Fehler
    /**
	 * 
	 */
    int MA_STATE_HALTED = 9; // Programm unterbrochen
    /**
	 * 
	 */
    int MA_STATE_SERVICE = 12; // Service
    /**
	 * 
	 */
    int MA_STATE_SUPERFROST = 13; // Superfrost
    /**
	 * 
	 */
    int MA_STATE_SUPERCOOL = 14; // Superkühlen
    /**
	 * 
	 */
    int MA_STATE_DEFAULT = 144; // Default
    /**
	 * 
	 */
    int MA_STATE_LOCKED = 145; // Verriegelt
    /**
	 * 
	 */
    int MA_STATE_UNKNOWN = 0; // Unknown state. Not an
                              // official state according
                              // to Miele documentation.

    // State values (description) German
    /**
	 * 
	 */
    String STATE_OFF_DE = "Aus";
    /**
	 * 
	 */
    String STATE_VALUE_READY_DE = "Bereit";
    /**
	 * 
	 */
    String STATE_VALUE_PROGRAM_DE = "Programm gewählt";
    /**
	 * 
	 */
    String STATE_VALUE_WAITING_DE = "Start verzögert";
    /**
	 * 
	 */
    String STATE_VALUE_ON_DE = "In Betrieb";
    /**
	 * 
	 */
    String STATE_VALUE_PAUSED_DE = "Pause";
    /**
	 * 
	 */
    String STATE_VALUE_END_DE = "Ende";
    /**
	 * 
	 */
    String STATE_VALUE_ERROR_DE = "Fehler";
    /**
	 * 
	 */
    String STATE_VALUE_HALTED_DE = "Abbruch";
    /**
	 * 
	 */
    String STATE_VALUE_SERVICE_DE = "Service";
    /**
	 * 
	 */
    String STATE_VALUE_SUPERFROST_DE = "Superfrost";
    /**
	 * 
	 */
    String STATE_VALUE_SUPERCOOL_DE = "Superkühlen";
    /**
	 * 
	 */
    String STATE_VALUE_DEFAULT_DE = "Default";
    /**
	 * 
	 */
    String STATE_VALUE_LOCKED_DE = "Verriegelt";

    // State values (description) English
    /**
	 * 
	 */
    String STATE_OFF_EN = "Off";;
    /**
	 * 
	 */
    String STATE_VALUE_READY_EN = "On";
    /**
	 * 
	 */
    String STATE_VALUE_PROGRAM_EN = "Programmed";
    /**
	 * 
	 */
    String STATE_VALUE_WAITING_EN = "Waiting to Start";
    /**
	 * 
	 */
    String STATE_VALUE_ON_EN = "Running";
    /**
	 * 
	 */
    String STATE_VALUE_PAUSED_EN = "Paused";
    /**
	 * 
	 */
    String STATE_VALUE_END_EN = "End";
    /**
	 * 
	 */
    String STATE_VALUE_ERROR_EN = "Error";
    /**
	 * 
	 */
    String STATE_VALUE_HALTED_EN = "Abort";
    /**
	 * 
	 */
    String STATE_VALUE_SERVICE_EN = "Service";
    /**
	 * 
	 */
    String STATE_VALUE_SUPERFROST_EN = "Super Freezing";
    /**
	 * 
	 */
    String STATE_VALUE_SUPERCOOL_EN = "Super Cooling";
    /**
	 * 
	 */
    String STATE_VALUE_DEFAULT_EN = "Default";
    /**
	 * 
	 */
    String STATE_VALUE_LOCKED_EN = "Locked";

    /*
     * Appliance class id and descriptions
     */

    // Appliance class ID
    /**
	 * 
	 */
    int DISHWASHER_CLASS_ID = 22017;
    /**
	 * 
	 */
    int DRYER_CLASS_ID = 22018;
    /**
	 * 
	 */
    int WASHING_MACHINE_CLASS_ID = 22019;
    /**
	 * 
	 */
    int CERAMIC_HOB_CLASS_ID = 24067;
    /**
	 * 
	 */
    int HOOD_CLASS_ID = 24068;
    /**
	 * 
	 */
    int OVEN_CLASS_ID = 24070;
    /**
	 * 
	 */
    int STOVE_CLASS_ID = 24071;
    /**
	 * 
	 */
    int STEAMER_CLASS_ID = 24072;
    /**
	 * 
	 */
    int INDUCTION_HOB_CLASS_ID = 24073;
    /**
	 * 
	 */
    int COFFEE_MACHINE_CLASS_ID = 24074;
    /**
	 * 
	 */
    int REFRIGERATOR_FREEZER_CLASS_ID = 26113;
    /**
	 * 
	 */
    int FREEZER_CLASS_ID = 26114;
    /**
	 * 
	 */
    int REFRIGERATOR_CLASS_ID = 26115;
    /**
	 * 
	 */
    int WINE_REFRIGERATOR_CLASS_ID = 26116;

    // Appliance class name (German description)
    /**
	 * 
	 */
    String DISHWASHER_CLASS_NAME_DE = "Geschirrspüler";
    /**
	 * 
	 */
    String DRYER_CLASS_NAME_DE = "Trockenautomat";
    /**
	 * 
	 */
    String WASHING_MACHINE_CLASS_NAME_DE = "Waschvollautomat";
    /**
	 * 
	 */
    String CERAMIC_HOB_CLASS_NAME_DE = "Kochfeld (Highlight)";
    /**
	 * 
	 */
    String HOOD_CLASS_NAME_DE = "Haube";
    /**
	 * 
	 */
    String OVEN_CLASS_NAME_DE = "Oven";
    /**
	 * 
	 */
    String STOVE_CLASS_NAME_DE = "Herd";
    /**
	 * 
	 */
    String STEAMER_CLASS_NAME_DE = "Dampfgarer";
    /**
	 * 
	 */
    String INDUCTION_HOB_CLASS_NAME_DE = "Kochfeld (Induktion)";
    /**
	 * 
	 */
    String COFFEE_MACHINE_CLASS_NAME_DE = "Kaffeevollautomat";
    /**
	 * 
	 */
    String REFRIGERATOR_FREEZER_CLASS_NAME_DE = "Kühl-Gefrierkombi";
    /**
	 * 
	 */
    String FREEZER_CLASS_NAME_DE = "Gefrierschrank";
    /**
	 * 
	 */
    String REFRIGERATOR_CLASS_NAME_DE = "Kühlschrank";
    /**
	 * 
	 */
    String WINE_REFRIGERATOR_CLASS_NAME_DE = "Weinlager";

    // Appliance class name (English description)
    /**
	 * 
	 */
    String DISHWASHER_CLASS_NAME_EN = "Dishwasher";
    /**
	 * 
	 */
    String DRYER_CLASS_NAME_EN = "Dryer";
    /**
	 * 
	 */
    String WASHING_MACHINE_CLASS_NAME_EN = "Washing machine";
    /**
	 * 
	 */
    String CERAMIC_HOB_CLASS_NAME_EN = "Ceramic hob";
    /**
	 * 
	 */
    String HOOD_CLASS_NAME_EN = "Hood";
    /**
	 * 
	 */
    String OVEN_CLASS_NAME_EN = "Oven";
    /**
	 * 
	 */
    String STOVE_CLASS_NAME_EN = "Stove";
    /**
	 * 
	 */
    String STEAMER_CLASS_NAME_EN = "Streamer";
    /**
	 * 
	 */
    String INDUCTION_HOB_CLASS_NAME_EN = "Induction hob";
    /**
	 * 
	 */
    String COFFEE_MACHINE_CLASS_NAME_EN = "Coffee machine";
    /**
	 * 
	 */
    String REFRIGERATOR_FREEZER_CLASS_NAME_EN = "Refrigerator Freezer";
    /**
	 * 
	 */
    String FREEZER_CLASS_NAME_EN = "Freezer";
    /**
	 * 
	 */
    String REFRIGERATOR_CLASS_NAME_EN = "Refrigerator";
    /**
	 * 
	 */
    String WINE_REFRIGERATOR_CLASS_NAME_EN = "Wine refrigerator";
}
