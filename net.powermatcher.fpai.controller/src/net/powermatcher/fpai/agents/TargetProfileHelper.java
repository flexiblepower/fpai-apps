package net.powermatcher.fpai.agents;

import java.util.Collection;
import java.util.Date;

import javax.measure.quantity.Quantity;

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
import org.flexiblepower.ral.values.ConstraintProfile;

public class TargetProfileHelper<Q extends Quantity> {

    private final Date startDate;
    private final ConstraintProfile<Q> profile;
    private final BufferRegistration<Q> bufferRegistration;
    private final BufferSystemDescription bufferSystemDescription;
    private final ActuatorBehaviour actuator;
    private final Buffer<Q> bufferHelper;

    public TargetProfileHelper(BufferTargetProfileUpdate<Q> targetProfile,
                               ConstraintProfile<Q> profile,
                               BufferRegistration<Q> bufferRegistration,
                               BufferSystemDescription bufferSystemDescription,
                               Buffer<Q> bufferHelper) {
        this.profile = profile;
        this.bufferRegistration = bufferRegistration;
        this.bufferSystemDescription = bufferSystemDescription;
        this.startDate = targetProfile.getValidFrom();
        this.bufferHelper = bufferHelper;
        // TODO we assume one actuator for now
        this.actuator = bufferSystemDescription.getActuators().iterator().next();
    }

    private double getTargetLowerBound() {
        return profile.get(0).getValue().getLowerBound().doubleValue(bufferRegistration.getFillLevelUnit());
    }

    private double getTargetUpperBound() {
        return profile.get(0).getValue().getUpperBound().doubleValue(bufferRegistration.getFillLevelUnit());
    }

    public long timeToTarget(Date now) {
        return startDate.getTime() - now.getTime();
    }

    public boolean targetIsValid(Date now) {
        // TODO start looking for the next target
        return timeToTarget(now) >= 0;
    }

    public double calculatePriority(Date now) {
        double minimumFillLevel = getMinimumFillLevelForTarget(now);
        double maximumFillLevel = getMaximumFillLevelForTarget(now);

        if (maximumFillLevel == minimumFillLevel) {
            throw new IllegalStateException("Maximum and Minimum Fill Level may not be the same.");
        } else if (maximumFillLevel < minimumFillLevel) {
            throw new IllegalStateException("Maximum Fill level may not be below Minimum Fill Level.");
        }

        double soc = (bufferHelper.getCurrentFillLevel().doubleValue(bufferRegistration.getFillLevelUnit()) - minimumFillLevel) / (maximumFillLevel - minimumFillLevel);
        return 1 - 2 * soc;
    }

    public double getMinimumFillLevelForTarget(Date now) {
        double bufferMinimum = bufferHelper.getMinimumFillLevel();
        double targetMinimum = getTargetLowerBound();
        if (targetMinimum <= bufferMinimum) {
            // Target is not restrictive
            return bufferMinimum;
        }
        double toCharge = targetMinimum - bufferMinimum;
        RunningMode<FillLevelFunction<RunningModeBehaviour>> rm = getFastestChargingRunningMode(actuator.getRunningModes());
        double fillingRate = netFillingRate(rm, bufferSystemDescription.getBufferLeakage());
        if (fillingRate <= 0) {
            // The "deadline line" is horizontal
            return targetMinimum;
        }
        long chargeTimeMs = (long) ((toCharge / fillingRate) * 1000.0);
        long timeToTargetMs = timeToTarget(now);
        if (timeToTargetMs <= 0) {
            // Target is already here
            return targetMinimum;
        }
        if (timeToTargetMs >= chargeTimeMs) {
            // It is not yet necessary to change the priority in order to reach the target
            return bufferMinimum;
        }
        // Do linear interpolation
        long timeSinceDesiredStart = chargeTimeMs - timeToTargetMs;
        double slope = ((double) timeSinceDesiredStart) / chargeTimeMs;
        double addition = slope * toCharge;
        return bufferMinimum + addition;
    }

    public double getMaximumFillLevelForTarget(Date now) {
        double bufferMaximum = bufferHelper.getMaximumFillLevel();
        double targetMaximum = getTargetUpperBound();
        if (targetMaximum >= bufferMaximum) {
            // Target is not restrictive
            return bufferMaximum;
        }
        double toDischarge = bufferMaximum - targetMaximum;
        RunningMode<FillLevelFunction<RunningModeBehaviour>> rm = getFastestDischargingRunningMode(actuator.getRunningModes());
        double fillingRate = netFillingRate(rm, bufferSystemDescription.getBufferLeakage());
        if (fillingRate >= 0) {
            // The "deadline line" is horizontal
            return targetMaximum;
        }
        long dischargeTimeMs = (long) ((toDischarge / (-fillingRate)) * 1000.0);
        long timeToTargetMs = timeToTarget(now);
        if (timeToTargetMs <= 0) {
            // Target is already here
            return targetMaximum;
        }
        if (timeToTargetMs >= dischargeTimeMs) {
            // It is not yet necessary to change the priority in order to reach the target
            return bufferMaximum;
        }
        // Do linear interpolation
        long timeSinceDesiredStart = dischargeTimeMs - timeToTargetMs;
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
