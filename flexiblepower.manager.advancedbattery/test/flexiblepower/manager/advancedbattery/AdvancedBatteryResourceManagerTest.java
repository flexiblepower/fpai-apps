package flexiblepower.manager.advancedbattery;

import static org.junit.Assert.assertEquals;

import java.util.Date;
import java.util.List;
import java.util.UUID;

import org.flexiblepower.context.FlexiblePowerContext;
import org.flexiblepower.ral.messages.ResourceMessage;
import org.junit.Before;
import org.junit.Test;
import org.mockito.Mockito;

public class AdvancedBatteryResourceManagerTest {
	AdvancedBatteryResourceManager manager = new AdvancedBatteryResourceManager();
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
		manager.setContext(context);
	}
	
	@Test
	public void startRegistrationTest() {
		List<? extends ResourceMessage> messages = manager.startRegistration();
		//assertEquals(3, messages.size());
	}
	
	
}
