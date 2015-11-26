package org.flexiblepower.driver.ledstrip;

import org.flexiblepower.ral.ResourceState;

public interface LedstripState extends ResourceState {

    public LedstripLevel getLedstripLevel();
}
