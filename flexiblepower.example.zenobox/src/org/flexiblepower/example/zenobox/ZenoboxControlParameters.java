package org.flexiblepower.example.zenobox;

import org.flexiblepower.ral.ResourceControlParameters;

public interface ZenoboxControlParameters extends ResourceControlParameters {
    /**
     * @return The mode
     */
    ZenoboxMode getMode();
}
