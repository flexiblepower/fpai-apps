package flexiblepower.manager.battery.sony;

import java.util.Locale;

import javax.measure.unit.NonSI;
import javax.measure.unit.SI;

import org.flexiblepower.ui.Widget;

import flexiblepower.manager.genericadvancedbattery.GenericAdvancedBatteryDeviceModel;

public class SonyBatteryWidget implements Widget {
    public static class Update {
        private final double soc;
        private final String mode;
        private final double initialTotalCapacity;
        private final double percentageOfInitialCapacityLeft;
        private final double voltage;
        private final double current;
        private final double chargingPower;

        public Update(double soc,
                      String mode,
                      double initialTotalCapacity,
                      double percentageOfInitialCapacityLeft,
                      double voltage,
                      double current,
                      double chargingPower) {
            super();
            this.soc = soc;
            this.mode = mode;
            this.initialTotalCapacity = initialTotalCapacity;
            this.percentageOfInitialCapacityLeft = percentageOfInitialCapacityLeft;
            this.voltage = voltage;
            this.current = current;
            this.chargingPower = chargingPower;
        }

        public double getSoc() {
            return soc;
        }

        public String getMode() {
            return mode;
        }

        public double getInitialTotalCapacity() {
            return initialTotalCapacity;
        }

        public double getPercentageOfInitialCapacityLeft() {
            return percentageOfInitialCapacityLeft;
        }

        public double getVoltage() {
            return voltage;
        }

        public double getCurrent() {
            return current;
        }

        public double getChargingPower() {
            return chargingPower;
        }
    }

    private final GenericAdvancedBatteryDeviceModel deviceModel;

    public SonyBatteryWidget(GenericAdvancedBatteryDeviceModel model) {
        deviceModel = model;
    }

    public Update update() {
        return new Update(deviceModel.getCurrentFillLevel().doubleValue(NonSI.PERCENT),
                          deviceModel.getCurrentMode().name(),
                          deviceModel.getTotalCapacity().doubleValue(NonSI.KWH),
                          100.0d,
                          deviceModel.getBatteryVolts(),
                          deviceModel.getCurrentInAmps(),
                          deviceModel.getElectricPower().doubleValue(SI.WATT));
    }

    @Override
    public String getTitle(Locale locale) {
        return "Sony Fortelion Battery Simulation";
    }
}
