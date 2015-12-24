package flexiblepower.manager.genericadvancedbattery;

import static org.junit.Assert.assertEquals;

import java.util.Date;
import java.util.UUID;

import javax.measure.Measure;
import javax.measure.unit.SI;

import org.flexiblepower.context.FlexiblePowerContext;
import org.junit.Before;
import org.junit.Test;
import org.mockito.Mockito;

import flexiblepower.manager.genericadvancedbattery.GenericAdvancedBatteryConfig;
import flexiblepower.manager.genericadvancedbattery.GenericAdvancedBatteryDeviceModel;

public class AdvancedBatteryDeviceModelTest {

	GenericAdvancedBatteryDeviceModel model;
	FlexiblePowerContext context;

	@Before
	public void setUp() {
		GenericAdvancedBatteryConfig config = new GenericAdvancedBatteryConfig() {

			@Override
			public int updateIntervalSeconds() {
				return 30;
			}

			@Override
			public String resourceId() {
				return UUID.randomUUID().toString();
			}

			@Override
			public double minimumFillLevelPercent() {
				return 20;
			}

			@Override
			public double maximumFillLevelPercent() {
				return 90;
			}

			@Override
			public double initialSocRatio() {
				return .5;
			}

			@Override
			public double totalCapacityKWh() {
				return 5;
			}

			@Override
			public double maximumChargingRateWatts() {
				return 1500;
			}

			@Override
			public double maximumDischargingRateWatts() {
				return 1500;
			}

			@Override
			public int nrOfCyclesBeforeEndOfLife() {
				return 6000;
			}

			@Override
			public double ratedVoltage() {
				return 52.6793;
			}

			@Override
			public double KValue() {
				return 0.011;
			}

			@Override
			public double QAmpereHours() {
				return 24;
			}

			@Override
			public double constantA() {
				return 3;
			}

			@Override
			public double constantB() {
				return 2.8;
			}

			@Override
			public double internalResistanceOhms() {
				return 0.036;
			}

			@Override
			public int nrOfModulationSteps() {
				return 0;
			}

		};
		context = Mockito.mock(FlexiblePowerContext.class);
		Mockito.when(context.currentTimeMillis()).thenReturn(new Date().getTime());

		model = new GenericAdvancedBatteryDeviceModel(config, context);
	}

	@Test
	public void testGetChargeEfficiency() {
		// TODO check values...
		// Efficiency at full charging speed
		assertEquals(96.47285, model.getChargeEfficiency(model.getMaximumChargeSpeed()), 0.0001);
		// Efficiency at zero speed
		assertEquals(0d, model.getChargeEfficiency(Measure.valueOf(0d, SI.WATT)), 0.0001);
		// Efficiency at half speed
		assertEquals(97.1002, model.getChargeEfficiency(
				Measure.valueOf(.5 * model.getMaximumChargeSpeed().doubleValue(SI.WATT), SI.WATT)), 0.0001);
	}

	@Test
	public void testGetDischargeEfficiency() {
		// TODO check values...
		// Efficiency at full charging speed
		assertEquals(94.38403, model.getDischargeEfficiency(model.getMaximumDischargeSpeed()), 0.0001);
		// Efficiency at zero speed
		assertEquals(0.0173d, model.getDischargeEfficiency(Measure.valueOf(0d, SI.WATT)), 0.0001);
		// Efficiency at half speed
		assertEquals(94.9225486, model.getDischargeEfficiency(
				Measure.valueOf(.5 * model.getMaximumDischargeSpeed().doubleValue(SI.WATT), SI.WATT)), 0.0001);
	}
}
