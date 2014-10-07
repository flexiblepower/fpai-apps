package org.flexiblepower.smartmeter.driver.interfaces;

import org.flexiblepower.messaging.Port;
import org.flexiblepower.ral.ResourceDriver;

@Port(name = "manager", sends = SmartMeterState.class)
public interface SmartMeterDriver extends ResourceDriver {
}
