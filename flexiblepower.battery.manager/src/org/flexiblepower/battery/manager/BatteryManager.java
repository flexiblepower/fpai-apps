package org.flexiblepower.battery.manager;

import static javax.measure.unit.SI.JOULE;
import static javax.measure.unit.SI.SECOND;
import static javax.measure.unit.SI.WATT;

import java.util.Map;

import javax.measure.Measurable;
import javax.measure.Measure;
import javax.measure.quantity.Energy;
import javax.measure.quantity.Power;

import org.flexiblepower.battery.manager.BatteryManager.Config;
import org.flexiblepower.observation.Observation;
import org.flexiblepower.observation.ObservationProvider;
import org.flexiblepower.rai.Allocation;
import org.flexiblepower.rai.StorageControlSpace;
import org.flexiblepower.rai.values.ConstraintList;
import org.flexiblepower.rai.values.EnergyProfile;
import org.flexiblepower.ral.ResourceDriver;
import org.flexiblepower.ral.ResourceManager;
import org.flexiblepower.ral.drivers.battery.BatteryControlParameters;
import org.flexiblepower.ral.drivers.battery.BatteryDriver;
import org.flexiblepower.ral.drivers.battery.BatteryMode;
import org.flexiblepower.ral.drivers.battery.BatteryState;
import org.flexiblepower.ral.ext.AbstractResourceManager;
import org.flexiblepower.time.TimeService;
import org.flexiblepower.time.TimeUtil;

import aQute.bnd.annotation.component.Activate;
import aQute.bnd.annotation.component.Component;
import aQute.bnd.annotation.component.Reference;
import aQute.bnd.annotation.metatype.Configurable;
import aQute.bnd.annotation.metatype.Meta;

@Component(designateFactory = Config.class, provide = ResourceManager.class)
public class BatteryManager extends
                           AbstractResourceManager<StorageControlSpace, BatteryState, BatteryControlParameters> {
    interface Config {
        @Meta.AD(deflt = "battery", description = "Resource identifier")
        String resourceId();

        @Meta.AD(deflt = "20", description = "Expiration of the ControlSpaces [s]", required = false)
        int expirationTime();
    }

    private BatteryState lastBatteryState = null;

    public BatteryManager() {
        super(BatteryDriver.class, StorageControlSpace.class);
    }

    private TimeService timeService;
    private Config config;
    private Measure<Integer, javax.measure.quantity.Duration> expirationTime;

    @Reference
    public void setTimeService(TimeService timeService) {
        this.timeService = timeService;
    }

    @Override
    public void consume(ObservationProvider<? extends BatteryState> source,
                        Observation<? extends BatteryState> observation) {
        logger.debug("Observation received from " + source + ": " + observation.getValue());

        BatteryState state = observation.getValue();

        lastBatteryState = state;

        ConstraintList<Power> chargeSpeed = ConstraintList.create(WATT).addSingle(state.getChargeSpeed()).build();
        ConstraintList<Power> dischargeSpeed = ConstraintList.create(WATT).addSingle(state.getDischargeSpeed()).build();

        publish(new StorageControlSpace(config.resourceId(),
                                        timeService.getTime(),
                                        TimeUtil.add(timeService.getTime(), expirationTime),
                                        TimeUtil.add(timeService.getTime(), expirationTime),
                                        state.getTotalCapacity(),
                                        (float) state.getStateOfCharge(),
                                        chargeSpeed,
                                        dischargeSpeed,
                                        state.getSelfDischargeSpeed(),
                                        (float) state.getChargeEfficiency(),
                                        (float) state.getDischargeEfficiency(),
                                        state.getMinimumOnTime(),
                                        state.getMinimumOffTime(),
                                        null,
                                        null));

    }

    @Activate
    public void init(Map<String, ?> properties) {
        config = Configurable.createConfigurable(Config.class, properties);
        expirationTime = Measure.valueOf(config.expirationTime(), SECOND);
    }

    @Override
    public void handleAllocation(Allocation allocation) {
        ResourceDriver<BatteryState, BatteryControlParameters> driver = getDriver();
        if (allocation != null && driver != null) {
            EnergyProfile energyProfile = allocation.getEnergyProfile();
            Measurable<Energy> energyValue = energyProfile.getElementForOffset(Measure.valueOf(0, SECOND)).getEnergy();
            final double energy = energyValue.doubleValue(JOULE);
            logger.debug("Setting energy level to " + energyValue);
            driver.setControlParameters(new BatteryControlParameters() {
                @Override
                public BatteryMode getMode() {
                    if (energy < -1e-9 && (lastBatteryState == null || lastBatteryState.getStateOfCharge() > 0)) {
                        return BatteryMode.DISCHARGE;
                    } else if (energy > 1e-9 && (lastBatteryState == null || lastBatteryState.getStateOfCharge() < 1)) {
                        return BatteryMode.CHARGE;
                    } else {
                        return BatteryMode.IDLE;
                    }
                }
            });
        } else {
            logger.info("Received Allocation Null");
        }
    }
}
