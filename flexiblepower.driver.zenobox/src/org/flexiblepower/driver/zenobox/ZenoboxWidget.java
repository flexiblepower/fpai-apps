package org.flexiblepower.driver.zenobox;

import java.util.Locale;

import org.flexiblepower.ui.Widget;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class ZenoboxWidget implements Widget {

    // Nested class to handle widget updates
    public static class Update {
        private final String mMode;
        private final int mCurrentTemperature;

        // Nested constructor for update class
        public Update(String aMode, int aCurrentTemperature) {
            mMode = aMode;
            mCurrentTemperature = aCurrentTemperature;
        }

        // Getter for mode
        public String getMode() {
            return mMode;
        }

        // Getter for current temperature
        public int getCurrentTemperature() {
            return mCurrentTemperature;
        }
    }

    private final Zenobox mZbx;
    private static final Logger logger = LoggerFactory.getLogger(Zenobox.class);

    /**
     * Constructor to initialize / connect to Zenobox Example
     *
     * @param zbx
     */
    public ZenoboxWidget(Zenobox aZbx) {
        mZbx = aZbx;
    }

    public Update update() {
        ZenoboxState state = mZbx.getCurrentState();
        ZenoboxMode mode = state.getCurrentMode();
        int CurrentTemp = state.getCurrentTemperature();
        logger.info(mode.toString());
        return new Update(mode.toString(), CurrentTemp);
    }

    public Update setLightOn() {
        logger.info("Light ON");
        mZbx.setLightState(true);
        return update();
    }

    public Update setLightOff() {
        logger.info("Light OFF");
        mZbx.setLightState(false);
        return update();
    }

    @Override
    public String getTitle(Locale locale) {
        return "Zenobox Driver";
    }
}
