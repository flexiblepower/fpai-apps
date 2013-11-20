package nl.tno.hexabus.api;

import org.flexiblepower.ral.ResourceControlParameters;

public interface HexabusControlParameters extends ResourceControlParameters {
    boolean isSwitchedOn();
}
