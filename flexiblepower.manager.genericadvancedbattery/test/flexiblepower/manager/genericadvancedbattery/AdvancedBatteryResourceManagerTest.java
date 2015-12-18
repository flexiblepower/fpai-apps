package flexiblepower.manager.genericadvancedbattery;

import java.util.Date;
import java.util.UUID;

import org.flexiblepower.context.FlexiblePowerContext;
import org.junit.Before;
import org.junit.Test;
import org.mockito.Mockito;

import flexiblepower.manager.genericadvancedbattery.GenericAdvancedBatteryConfig;
import flexiblepower.manager.genericadvancedbattery.GenericAdvancedBatteryDeviceModel;
import flexiblepower.manager.genericadvancedbattery.GenericAdvancedBatteryResourceManager;

public class AdvancedBatteryResourceManagerTest {
	GenericAdvancedBatteryResourceManager manager = new GenericAdvancedBatteryResourceManager();
	GenericAdvancedBatteryDeviceModel model;
	FlexiblePowerContext context;
	
	@Before
	public void setUp() {
		GenericAdvancedBatteryConfig config = new GenericAdvancedBatteryConfig() {
			
			@Override
			public long updateIntervalSeconds() {
				return 10;
			}
			
			@Override
			public String resourceId() {
				return UUID.randomUUID().toString();
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

			@Override
			public double totalCapacityKWh() {
				// TODO Auto-generated method stub
				return 0;
			}

			@Override
			public double maximumChargingRateWatts() {
				// TODO Auto-generated method stub
				return 0;
			}

			@Override
			public double maximumDischargingRateWatts() {
				// TODO Auto-generated method stub
				return 0;
			}

			@Override
			public int numberOfCyclesBeforeEndOfLife() {
				// TODO Auto-generated method stub
				return 0;
			}
		};
		context = Mockito.mock(FlexiblePowerContext.class);
		Mockito.when(context.currentTimeMillis()).thenReturn(new Date().getTime());
		
		model = new GenericAdvancedBatteryDeviceModel(config, context);
		manager.setContext(context);
		//TODO get the configuration in there.
	}
	
	@Test
	public void startRegistrationTest() {
		//List<? extends ResourceMessage> messages = manager.startRegistration();
		//assertEquals(3, messages.size());
	}

	@Test
	public void handleMessageTest() {
		//manager.handleMessage(new );
	}
	
}
