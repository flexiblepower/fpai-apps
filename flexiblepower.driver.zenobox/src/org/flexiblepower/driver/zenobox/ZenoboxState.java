package org.flexiblepower.driver.zenobox;

import org.flexiblepower.ral.ResourceState;

public interface ZenoboxState extends ResourceState {

    /**
     * @return The current mode.
     */
    ZenoboxMode getCurrentMode();

    /**
     * @return The current temperature.
     */
    int getCurrentTemperature();
}
