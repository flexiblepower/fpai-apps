package org.flexiblepower.unconstrained.manager;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.Date;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

import javax.measure.Measure;
import javax.measure.quantity.Duration;
import javax.measure.unit.NonSI;
import javax.measure.unit.SI;

import org.flexiblepower.efi.UnconstrainedResourceManager;
import org.flexiblepower.efi.unconstrained.RunningModeBehaviour;
import org.flexiblepower.efi.unconstrained.UnconstrainedAllocation;
import org.flexiblepower.efi.unconstrained.UnconstrainedRegistration;
import org.flexiblepower.efi.unconstrained.UnconstrainedStateUpdate;
import org.flexiblepower.efi.unconstrained.UnconstrainedSystemDescription;
import org.flexiblepower.efi.util.RunningMode;
import org.flexiblepower.efi.util.Timer;
import org.flexiblepower.efi.util.TimerUpdate;
import org.flexiblepower.efi.util.Transition;
import org.flexiblepower.messaging.Cardinality;
import org.flexiblepower.messaging.Endpoint;
import org.flexiblepower.messaging.Port;
import org.flexiblepower.messaging.Ports;
import org.flexiblepower.rai.AllocationRevoke;
import org.flexiblepower.rai.AllocationStatus;
import org.flexiblepower.rai.AllocationStatusUpdate;
import org.flexiblepower.rai.ControlSpaceRevoke;
import org.flexiblepower.rai.ResourceMessage;
import org.flexiblepower.rai.values.CommodityMeasurables;
import org.flexiblepower.rai.values.CommoditySet;
import org.flexiblepower.ral.ext.AbstractResourceManager;
import org.flexiblepower.simulation.generator.GeneratorControlParameters;
import org.flexiblepower.simulation.generator.GeneratorLevel;
import org.flexiblepower.simulation.generator.GeneratorState;
import org.flexiblepower.time.TimeService;
import org.flexiblepower.ui.Widget;
import org.osgi.framework.BundleContext;
import org.osgi.framework.ServiceRegistration;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import aQute.bnd.annotation.component.Activate;
import aQute.bnd.annotation.component.Component;
import aQute.bnd.annotation.component.Deactivate;
import aQute.bnd.annotation.component.Reference;
import aQute.bnd.annotation.metatype.Configurable;
import aQute.bnd.annotation.metatype.Meta;

@Component(designateFactory = UnconstrainedManager.Config.class, provide = Endpoint.class, immediate = true)
@Ports({@Port(name = "driver", sends = GeneratorControlParameters.class, accepts = GeneratorState.class),
		@Port(name = "controller",
			accepts = { UnconstrainedAllocation.class, AllocationRevoke.class },
			sends = { UnconstrainedRegistration.class,
         UnconstrainedStateUpdate.class,
         AllocationStatusUpdate.class,
         ControlSpaceRevoke.class },
         cardinality = Cardinality.SINGLE)})

public class UnconstrainedManager extends
			AbstractResourceManager<GeneratorState,GeneratorControlParameters> implements UnconstrainedResourceManager{

    private static final Logger log = LoggerFactory.getLogger(UnconstrainedManager.class);

    @Meta.OCD
    interface Config {
        @Meta.AD(deflt = "unconstrained", description = "Resource identifier")
        String resourceId();

        @Meta.AD(deflt = "20", description = "Expiration of the ControlSpaces [s]", required = false)
        int expirationTime();

        @Meta.AD(deflt = "false", description = "Show simple widget")
        boolean showWidget();
    }

    private Config config;
	private UnconstrainedManagerWidget widget;
	private ServiceRegistration<Widget> widgetRegistration;
	private Date changedStateAt;
	private TimeService timeService;
	private Measure<Integer, Duration> allocationDelay;
    private ScheduledExecutorService scheduler;	
	
    @Activate
    public void activate(BundleContext bundleContext, Map<String, Object> properties) {
        config = Configurable.createConfigurable(Config.class, properties);
        if (config.showWidget()) {
            widget = new UnconstrainedManagerWidget(this);
            widgetRegistration = bundleContext.registerService(Widget.class, widget, null);
        }
    };
    
    @Deactivate
    public void deactivate() {
        if (widgetRegistration != null) {
            widgetRegistration.unregister();
            widgetRegistration = null;
        }
    }
    
	@Override
	protected List<? extends ResourceMessage> startRegistration(
			GeneratorState state) {
        changedStateAt = timeService.getTime();
        allocationDelay = Measure.valueOf(5, SI.SECOND);
        // Make the registration.
        UnconstrainedRegistration reg = new UnconstrainedRegistration(getResourceId(),
                                                                    changedStateAt,
                                                                    allocationDelay,
                                                                    CommoditySet.onlyElectricity);
        
        // Make the system description.
        UnconstrainedSystemDescription sysDescription = createUnconstrainedSystemDescription();
        
        UnconstrainedStateUpdate update = createUnconstrainedUpdate(state);
        return Arrays.asList(reg, sysDescription, update);
	}

	private UnconstrainedSystemDescription createUnconstrainedSystemDescription() 
	{		
		final List<Integer> powerList = Arrays.asList(0,-1000,-1100,-1200,-1300,-1400,-1500,-1600,-1700,-1800,-1900,-2000);
		
		List<RunningMode<RunningModeBehaviour>> rmList = new ArrayList<RunningMode<RunningModeBehaviour>>();
		for (int power : powerList)
		{
			Set<Integer> transitionSet = new HashSet<Integer>(powerList);
			transitionSet.remove(power);
			Set<Transition> transitions = new HashSet<Transition>();
			for (int transition : transitionSet)
			{
				transitions.add(new Transition(transition, Collections.<Timer>emptySet(), Collections.<Timer>emptySet(),Measure.valueOf(0,NonSI.EUR), Measure.valueOf(0, SI.SECOND)));
			}
			
			rmList.add(new RunningMode<RunningModeBehaviour>(power, 
															"rm_" + power,
															new RunningModeBehaviour(CommodityMeasurables.electricity(Measure.valueOf(power,SI.WATT)), Measure.valueOf(0,NonSI.EUR_PER_HOUR)),
															transitions));
		}
		
        return new UnconstrainedSystemDescription(getResourceId(),
                changedStateAt,
                changedStateAt,
                rmList);
	}

	private UnconstrainedStateUpdate createUnconstrainedUpdate(GeneratorState state) {
        return new UnconstrainedStateUpdate(getResourceId(),
        									timeService.getTime(),
        									timeService.getTime(),
        									state.getGeneratorLevel().getIntLevel(),
        									Collections.<TimerUpdate>emptySet());
	}

	
	private String getResourceId() {
		return config.resourceId();
	}
	

	@Override
	protected List<? extends ResourceMessage> updatedState(GeneratorState state) {
		return Arrays.asList(createUnconstrainedUpdate(state));
	}

	@Override
	protected GeneratorControlParameters receivedAllocation(ResourceMessage message) {
		if (message instanceof UnconstrainedAllocation) {
            UnconstrainedAllocation allocation = (UnconstrainedAllocation) message;
            log.debug("Received allocation " + allocation);
            // determine running mode and delay.
            long delay = allocation.getRunningModeSelectors().iterator().next().getStartTime().getTime() - timeService.getCurrentTimeMillis();
            delay = (delay <= 0 ? 1 : delay);

            // set up the scheduler switch to the running mode after the specified delay
            scheduleSwitchToRunningMode(delay, allocation.getRunningModeSelectors().iterator().next().getRunningModeId());
            // Send to attached energy app the acceptance of the allocation request
            allocationStatusUpdate(new AllocationStatusUpdate(timeService.getTime(),
                                                                  allocation,
                                                                  AllocationStatus.ACCEPTED,
                                                                  ""));
            // the running mode change is scheduled at time, so nothing to return
            return null;
        } else {
            log.warn("Unexpected resource (" + message.toString() + ") message type (" + message.getClass().getName()
                     + ")received");
            return null;
        }
	}
	
    private void scheduleSwitchToRunningMode(final long delay, final int runningModePower) {
        final Runnable allocationHelper = new Runnable() {
            @Override
            public void run() {
                sendControlParameters(new GeneratorControlParameters() {
					@Override
					public GeneratorLevel getLevel() {
						GeneratorLevel level = new GeneratorLevel();
						level.setLevel(runningModePower);
						return level;
					}
                });
                log.debug("Has set up allocation at " + delay + "ms for running mode with Power value=" + runningModePower);
            }
        };
        scheduler.schedule(allocationHelper, delay, TimeUnit.MILLISECONDS);
    }
    
    @Reference
    public void setScheduledExecutorService(ScheduledExecutorService scheduler) {
        this.scheduler = scheduler;
    }
    
    @Reference
    public void setTimeService(TimeService timeService) {
        this.timeService = timeService;
    }
}
