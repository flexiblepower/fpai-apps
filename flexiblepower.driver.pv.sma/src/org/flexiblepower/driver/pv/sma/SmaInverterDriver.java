package org.flexiblepower.driver.pv.sma;

import org.flexiblepower.ral.drivers.uncontrolled.UncontrollableDriver;

public interface SmaInverterDriver extends UncontrollableDriver {
    SmaInverterState getCurrentState();
}
