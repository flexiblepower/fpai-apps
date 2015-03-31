package org.flexiblepower.driver.pv.sma;

import java.util.Date;

import javax.measure.Measurable;
import javax.measure.quantity.Energy;

import org.flexiblepower.ral.drivers.uncontrolled.PowerState;

public interface SmaInverterState extends PowerState {

    Measurable<Energy> getLifetimeProduction();

    Measurable<Energy> getTodayProduction();

    boolean isSunUp();

    Date getTime();
}
