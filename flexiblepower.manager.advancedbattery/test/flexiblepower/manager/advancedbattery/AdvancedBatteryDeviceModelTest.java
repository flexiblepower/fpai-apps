package flexiblepower.manager.advancedbattery;

import static org.junit.Assert.assertEquals;

import java.util.Date;
import java.util.UUID;

import javax.measure.Measure;
import javax.measure.unit.SI;

import org.flexiblepower.context.FlexiblePowerContext;
import org.junit.Before;
import org.junit.Test;
import org.mockito.Mockito;

public class AdvancedBatteryDeviceModelTest {

	AdvancedBatteryDeviceModel model;
	FlexiblePowerContext context;
	
	@Before
	public void setUp() {
		AdvancedBatteryConfig config = new AdvancedBatteryConfig() {
			
			@Override
			public long updateIntervalSeconds() {
				return 10;
			}
			
			@Override
			public String resourceId() {
				return UUID.randomUUID().toString();
			}
			
			@Override
			public int nrOfmodules() {
				return 1;
			}
			
			@Override
			public double minimumFillLevelPercent() {
				return 0;
			}
			
			@Override
			public double maximumFillLevelPercent() {
				return 100;
			}
			
			@Override
			public double initialSocRatio() {
				return 5;
			}
		};
		context = Mockito.mock(FlexiblePowerContext.class);
		Mockito.when(context.currentTimeMillis()).thenReturn(new Date().getTime());
		
		model = new AdvancedBatteryDeviceModel(config, context);
	}
	
	@Test
	public void testGetChargeEfficiency() {
		//TODO check values...
		//Efficiency at full charging speed
		assertEquals(96.47285,model.getChargeEfficiency(model.getMaximumChargeSpeed()),0.0001);
		//Efficiency at zero speed
		assertEquals(0d, model.getChargeEfficiency(Measure.valueOf(0d, SI.WATT)),0.0001);
		//Efficiency at half speed
		assertEquals(97.1002,model.getChargeEfficiency(Measure.valueOf(.5 * model.getMaximumChargeSpeed().doubleValue(SI.WATT),SI.WATT)),0.0001);
	}
	
	@Test
	public void testGetDischargeEfficiency() {
		//TODO check values...
		//Efficiency at full charging speed
		assertEquals(88.48533,model.getDischargeEfficiency(model.getMaximumDischargeSpeed()),0.0001);
		//Efficiency at zero speed
		assertEquals(0.0173d,model.getDischargeEfficiency(Measure.valueOf(0d, SI.WATT)),0.0001);
		//Efficiency at half speed
		assertEquals(94.24067,model.getDischargeEfficiency(Measure.valueOf(.5 * model.getMaximumDischargeSpeed().doubleValue(SI.WATT),SI.WATT)),0.0001);
	}
}
