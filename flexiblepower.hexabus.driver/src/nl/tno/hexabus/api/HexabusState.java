package nl.tno.hexabus.api;

import org.flexiblepower.ral.ResourceState;

public interface HexabusState extends ResourceState {
    long getCurrentLoad();

    boolean isSwitchedOn();
}
