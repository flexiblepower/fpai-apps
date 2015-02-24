package org.flexiblepower.driver.generator;

import javax.measure.Measurable;
import javax.measure.Measure;
import javax.measure.quantity.Power;
import javax.measure.unit.SI;

public class GeneratorLevel {

    protected Measurable<Power> level = Measure.valueOf(0, SI.WATT);

    /**
     * Generator level must be 0, or between -1000 and -2000 with steps of 100
     *
     * @param intLevel
     */
    public void setLevel(int intLevel) {
        int generatorLevel;
        if (intLevel == 0) {
            generatorLevel = 0;
        } else {
            generatorLevel = (intLevel / 100) * 100;
            if (generatorLevel > -1000) {
                generatorLevel = -1000;
            } else if (generatorLevel < -2000) {
                generatorLevel = -2000;
            }
        }

        level = Measure.valueOf(generatorLevel, SI.WATT);
    }

    public Measurable<Power> getLevel() {
        return level;
    }

    public int getIntLevel() {
        return (int) level.longValue(SI.WATT);
    }
}
