package org.flexiblepower.smartmeter.manager;

import java.util.Date;
import java.util.Map;

import javax.measure.Measurable;
import javax.measure.Measure;
import javax.measure.quantity.Duration;
import javax.measure.quantity.Energy;
import javax.measure.unit.NonSI;
import javax.measure.unit.SI;

import org.flexiblepower.observation.Observation;
import org.flexiblepower.observation.ObservationProvider;
import org.flexiblepower.rai.Allocation;
import org.flexiblepower.rai.UncontrolledControlSpace;
import org.flexiblepower.rai.values.EnergyProfile;
import org.flexiblepower.ral.ResourceManager;
import org.flexiblepower.ral.drivers.uncontrolled.UncontrolledControlParameters;
import org.flexiblepower.ral.drivers.uncontrolled.UncontrolledDriver;
import org.flexiblepower.ral.drivers.uncontrolled.UncontrolledState;
import org.flexiblepower.ral.ext.AbstractResourceManager;
import org.flexiblepower.smartmeter.driver.interfaces.SmartMeterControlParameters;
import org.flexiblepower.smartmeter.driver.interfaces.SmartMeterDriver;
import org.flexiblepower.smartmeter.driver.interfaces.SmartMeterState;
import org.flexiblepower.ui.Widget;
import org.osgi.framework.BundleContext;
import org.osgi.framework.ServiceRegistration;

import aQute.bnd.annotation.component.Activate;
import aQute.bnd.annotation.component.Component;
import aQute.bnd.annotation.component.Deactivate;
import aQute.bnd.annotation.metatype.Configurable;
import aQute.bnd.annotation.metatype.Meta;

@Component(designateFactory = SmartMeterManager.Config.class, provide = ResourceManager.class, immediate = true)
public class SmartMeterManager
		extends
		AbstractResourceManager<UncontrolledControlSpace, SmartMeterState, SmartMeterControlParameters> {

	@Meta.OCD
	interface Config {
		@Meta.AD(deflt = "smartmeter")
		String resourceId();
	}

	private SmartMeterState lastState;

	public SmartMeterManager() {
		super(SmartMeterDriver.class, UncontrolledControlSpace.class);
	}

	private SmartMeterWidget widget;
	private ServiceRegistration<Widget> widgetRegistration;
	private String resourceId;

	@Activate
	public void activate(BundleContext context,Map<String, Object> properties) {
		
		Config config = Configurable.createConfigurable(Config.class, properties);
		resourceId = config.resourceId();

		try {
			widget = new SmartMeterWidget(this);
			widgetRegistration = context.registerService(Widget.class, widget,
					null);
		} catch (Throwable e) {
			logger.error("Error in SmartMeter Activate", e);
			System.out.println("Error in SmartMeter Activate" + e);
		}

	}

	@Deactivate
	public void deactivate() {
		widgetRegistration.unregister();
	}

	@Override
	public void handleAllocation(Allocation allocation) {
		// Allocation is not handled
	}

	public SmartMeterState getState() {
		return lastState;
	}

	@Override
	public void consume(ObservationProvider<? extends SmartMeterState> source,
			Observation<? extends SmartMeterState> observation) {
		
		 this.lastState = observation.getValue();
		
		 publish(updateControlspace(lastState));
		 logger.debug("observation consumed by smart meter2");
		
	}

	public UncontrolledControlSpace updateControlspace(SmartMeterState state){
		
		UncontrolledControlSpace controlSpace = null;
		
		// Time 
		Date startTime = new Date();
		
		// Calculations
		Measurable<Energy> energy = Measure.valueOf(state.getCurrentPowerConsumptionW().intValueExact() * (15), SI.JOULE);
		Measurable<Duration> duration = Measure.valueOf((15), SI.SECOND); 	
		
		// Energy Values
	    EnergyProfile energyProfile = new EnergyProfile(duration,energy);

	    // Create controlspace
	    controlSpace = new UncontrolledControlSpace(resourceId, startTime, energyProfile );
		
		return controlSpace;
		
	}

}
