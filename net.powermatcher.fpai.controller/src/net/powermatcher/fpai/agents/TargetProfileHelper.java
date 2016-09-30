package net.powermatcher.fpai.agents;

import java.util.Collection;
import java.util.Date;

import javax.measure.quantity.Quantity;
import javax.measure.unit.SI;

import org.flexiblepower.api.efi.bufferhelper.Buffer;
import org.flexiblepower.efi.buffer.ActuatorBehaviour;
import org.flexiblepower.efi.buffer.BufferRegistration;
import org.flexiblepower.efi.buffer.BufferSystemDescription;
import org.flexiblepower.efi.buffer.BufferTargetProfileUpdate;
import org.flexiblepower.efi.buffer.LeakageRate;
import org.flexiblepower.efi.buffer.RunningModeBehaviour;
import org.flexiblepower.efi.util.FillLevelFunction;
import org.flexiblepower.efi.util.FillLevelFunction.RangeElement;
import org.flexiblepower.efi.util.RunningMode;
import org.flexiblepower.ral.values.Constraint;
import org.flexiblepower.ral.values.ConstraintProfile;
import org.flexiblepower.ral.values.Profile.Element;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class TargetProfileHelper<Q extends Quantity> {

    private final static Logger LOG = LoggerFactory.getLogger(TargetProfileHelper.class);

    private final Date startDate;
    private final ConstraintProfile<Q> profile;
    private final BufferRegistration<Q> bufferRegistration;
    private final BufferSystemDescription bufferSystemDescription;
    private final ActuatorBehaviour actuator;
    private final Buffer<Q> bufferHelper;

    public TargetProfileHelper(BufferTargetProfileUpdate<Q> targetProfile,
                               BufferRegistration<Q> bufferRegistration,
                               BufferSystemDescription bufferSystemDescription,
                               Buffer<Q> bufferHelper) {
        this.profile = targetProfile.getTargetProfile();
        this.bufferRegistration = bufferRegistration;
        this.bufferSystemDescription = bufferSystemDescription;
        this.startDate = targetProfile.getValidFrom();
        this.bufferHelper = bufferHelper;
        // TODO we assume one actuator for now
        this.actuator = bufferSystemDescription.getActuators().iterator().next();
    }

    public double calculatePriority(Date now) {
        // Set default values
        double minimumFillLevel = bufferHelper.getMinimumFillLevel();
        double maximumFillLevel = bufferHelper.getMaximumFillLevel();

        long elementStartMS = startDate.getTime();
        long nowMs = now.getTime();

        // Iterate through Constraints. Constraints in the past will be ignored, the current Constraint (if any) and the
        // first Constraint in the future (in any) will be taken into consideration.
        for (Element<Constraint<Q>> element : profile) {
            long elementEndMS = elementStartMS + element.getDuration().longValue(SI.MILLI(SI.SECOND));
            if (elementEndMS < nowMs) {
                // Element is in the past and can be ignored
            } else if (elementStartMS > nowMs) {
                // Element is in the future
                long timeToConstraintMs = elementStartMS - nowMs;
                double elementMinFillLevel = getMinimumFillLevelForFutureTarget(now,
                                                                                element.getValue(),
                                                                                timeToConstraintMs);
                double elementMaxFillLevel = getMaximumFillLevelForFutureTarget(now,
                                                                                element.getValue(),
                                                                                timeToConstraintMs);
                // Take the most restrictive of the two
                minimumFillLevel = Math.max(minimumFillLevel, elementMinFillLevel);
                maximumFillLevel = Math.min(maximumFillLevel, elementMaxFillLevel);
                // For robustness we only consider one future element. It might depend on the use case what is the best
                // strategy here.
                break;
            } else {
                // Element is currently in use
                double elementMinFillLevel = element.getValue()
                                                    .getLowerBound()
                                                    .doubleValue(bufferRegistration.getFillLevelUnit());
                double elementMaxFillLevel = element.getValue()
                                                    .getUpperBound()
                                                    .doubleValue(bufferRegistration.getFillLevelUnit());
                // Take the most restrictive of the two
                minimumFillLevel = Math.max(minimumFillLevel, elementMinFillLevel);
                maximumFillLevel = Math.min(maximumFillLevel, elementMaxFillLevel);
            }
        }

        double currentFillLevel = bufferHelper.getCurrentFillLevel().doubleValue(bufferRegistration.getFillLevelUnit());

        if (maximumFillLevel <= minimumFillLevel) {
            LOG.warn("The TargetProfile being used is too restrictive. Given the model of the device it is not possible to follow the profile. Using best effort startegy.");
            double avg = (maximumFillLevel + minimumFillLevel) / 2;
            if (avg > currentFillLevel) {
                return -1;
            } else {
                return 1;
            }
        }

        double soc = (currentFillLevel - minimumFillLevel) / (maximumFillLevel - minimumFillLevel);
        return 1 - 2 * soc;
    }

    /**
     * Calculate the minimum fill level at this moment in which it is still possible (given the right Running Mode and
     * considering leakage) to achieve the given Constraint in the future.
     *
     * @param now
     *            Current Date
     * @param constraint
     *            Constraint to consider
     * @param timeToConstraintMs
     *            Time between now and the start of the Constraint
     * @return Theoretical minimum fill level at this moment that would allow the device to achieve the given target
     */
    private double getMinimumFillLevelForFutureTarget(Date now, Constraint<Q> constraint, long timeToConstraintMs) {
        double bufferMinimum = bufferHelper.getMinimumFillLevel();
        double targetMinimum = constraint.getLowerBound().doubleValue(bufferRegistration.getFillLevelUnit());
        if (targetMinimum <= bufferMinimum) {
            // Target is not restrictive
            return bufferMinimum;
        }
        double toCharge = targetMinimum - bufferMinimum;
        RunningMode<FillLevelFunction<RunningModeBehaviour>> rm =
                                                                getFastestChargingRunningMode(actuator.getRunningModes());
        double fillingRate = netFillingRate(rm, bufferSystemDescription.getBufferLeakage());
        if (fillingRate <= 0) {
            // The "deadline line" is horizontal
            return targetMinimum;
        }
        long chargeTimeMs = (long) ((toCharge / fillingRate) * 1000.0);
        if (timeToConstraintMs <= 0) {
            // Target is already here
            return targetMinimum;
        }
        if (timeToConstraintMs >= chargeTimeMs) {
            // It is not yet necessary to change the priority in order to reach the target
            return bufferMinimum;
        }
        // Do linear interpolation
        long timeSinceDesiredStart = chargeTimeMs - timeToConstraintMs;
        double slope = ((double) timeSinceDesiredStart) / chargeTimeMs;
        double addition = slope * toCharge;
        return bufferMinimum + addition;
    }

    /**
     * Calculate the maximum fill level at this moment in which it is still possible (given the right Running Mode and
     * considering leakage) to achieve the given Constraint in the future.
     *
     * @param now
     *            Current Date
     * @param constraint
     *            Constraint to consider
     * @param timeToConstraintMs
     *            Time between now and the start of the Constraint
     * @return Theoretical maximum fill level at this moment that would allow the device to achieve the given target
     */
    private double getMaximumFillLevelForFutureTarget(Date now, Constraint<Q> constraint, long timeToConstraintMs) {
        double bufferMaximum = bufferHelper.getMaximumFillLevel();
        double targetMaximum = constraint.getUpperBound().doubleValue(bufferRegistration.getFillLevelUnit());
        if (targetMaximum >= bufferMaximum) {
            // Target is not restrictive
            return bufferMaximum;
        }
        double toDischarge = bufferMaximum - targetMaximum;
        RunningMode<FillLevelFunction<RunningModeBehaviour>> rm =
                                                                getFastestDischargingRunningMode(actuator.getRunningModes());
        double fillingRate = netFillingRate(rm, bufferSystemDescription.getBufferLeakage());
        if (fillingRate >= 0) {
            // The "deadline line" is horizontal
            return targetMaximum;
        }
        long dischargeTimeMs = (long) ((toDischarge / (-fillingRate)) * 1000.0);
        if (timeToConstraintMs <= 0) {
            // Target is already here
            return targetMaximum;
        }
        if (timeToConstraintMs >= dischargeTimeMs) {
            // It is not yet necessary to change the priority in order to reach the target
            return bufferMaximum;
        }
        // Do linear interpolation
        long timeSinceDesiredStart = dischargeTimeMs - timeToConstraintMs;
        double slope = ((double) timeSinceDesiredStart) / dischargeTimeMs;
        return bufferMaximum - (slope * toDischarge);
    }

    private static double averageFillingRate(RunningMode<FillLevelFunction<RunningModeBehaviour>> rm) {
        double totalRange = 0;
        double sum = 0;
        for (RangeElement<RunningModeBehaviour> re : rm.getValue()) {
            double range = re.getUpperBound() - re.getLowerBound();
            totalRange += range;
            sum += re.getValue().getFillingRate() * range;
        }
        return sum / totalRange;
    }

    private static double averageLeakage(FillLevelFunction<LeakageRate> flf) {
        double totalRange = 0;
        double sum = 0;
        for (RangeElement<LeakageRate> re : flf) {
            double range = re.getUpperBound() - re.getLowerBound();
            totalRange += range;
            sum += re.getValue().getLeakageRate() * range;
        }
        return sum / totalRange;
    }

    private static double netFillingRate(RunningMode<FillLevelFunction<RunningModeBehaviour>> rm,
                                         FillLevelFunction<LeakageRate> leakage) {
        // TODO add bufferForecast?
        return averageFillingRate(rm) + averageLeakage(leakage);
    }

    private RunningMode<FillLevelFunction<RunningModeBehaviour>>
            getFastestChargingRunningMode(Collection<RunningMode<FillLevelFunction<RunningModeBehaviour>>> rms) {
        RunningMode<FillLevelFunction<RunningModeBehaviour>> best = null;
        double bestFillingRate = -Double.MAX_VALUE;
        for (RunningMode<FillLevelFunction<RunningModeBehaviour>> rm : rms) {
            double rate = netFillingRate(rm, bufferSystemDescription.getBufferLeakage());
            if (rate > bestFillingRate) {
                bestFillingRate = rate;
                best = rm;
            }
        }
        return best;
    }

    private RunningMode<FillLevelFunction<RunningModeBehaviour>>
            getFastestDischargingRunningMode(Collection<RunningMode<FillLevelFunction<RunningModeBehaviour>>> rms) {
        RunningMode<FillLevelFunction<RunningModeBehaviour>> best = null;
        double bestFillingRate = Double.MAX_VALUE;
        for (RunningMode<FillLevelFunction<RunningModeBehaviour>> rm : rms) {
            double rate = netFillingRate(rm, bufferSystemDescription.getBufferLeakage());
            if (rate < bestFillingRate) {
                bestFillingRate = rate;
                best = rm;
            }
        }
        return best;
    }

}
