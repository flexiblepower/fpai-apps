package org.flexiblepower.ledstrip.manager;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.Date;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.NavigableSet;
import java.util.Set;
import java.util.TreeSet;

import javax.measure.Measurable;
import javax.measure.Measure;
import javax.measure.quantity.Duration;
import javax.measure.unit.NonSI;
import javax.measure.unit.SI;
import javax.measure.unit.Unit;

import org.flexiblepower.context.FlexiblePowerContext;
import org.flexiblepower.driver.ledstrip.LedstripControlParameters;
import org.flexiblepower.driver.ledstrip.LedstripLevel;
import org.flexiblepower.driver.ledstrip.LedstripState;
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
import org.flexiblepower.ral.ext.AbstractResourceManager;
import org.flexiblepower.ral.messages.AllocationRevoke;
import org.flexiblepower.ral.messages.AllocationStatus;
import org.flexiblepower.ral.messages.AllocationStatusUpdate;
import org.flexiblepower.ral.messages.ControlSpaceRevoke;
import org.flexiblepower.ral.messages.ResourceMessage;
import org.flexiblepower.ral.values.CommodityMeasurables;
import org.flexiblepower.ral.values.CommoditySet;
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

/**
 * The LedstripManager receives a state and sends commands to the unconstrained driver. From the higher EFI layer it
 * receives allocations and sends the registration description and update messages to inform the EnergyApp of the state
 * of the device.
 */
@Component(designateFactory = LedstripManager.Config.class, provide = Endpoint.class, immediate = true)
@Ports({ @Port(name = "driver", sends = LedstripControlParameters.class, accepts = LedstripState.class),
         @Port(name = "controller",
               accepts = { UnconstrainedAllocation.class, AllocationRevoke.class },
               sends = { UnconstrainedRegistration.class,
                         UnconstrainedStateUpdate.class,
                         AllocationStatusUpdate.class,
                         ControlSpaceRevoke.class },
               cardinality = Cardinality.SINGLE) })
public class LedstripManager extends
                             AbstractResourceManager<LedstripState, LedstripControlParameters>implements
                             UnconstrainedResourceManager {

    private static final Logger logger = LoggerFactory.getLogger(LedstripManager.class);
    private static final Unit<Duration> MS = SI.MILLI(SI.SECOND);

    @Meta.OCD
    interface Config {
        @Meta.AD(deflt = "ledstrip", description = "Resource identifier")
               String resourceId();

        @Meta.AD(deflt = "20", description = "Expiration of the ControlSpaces [s]", required = false)
            int expirationTime();

        @Meta.AD(deflt = "true", description = "Show simple widget")
                boolean showWidget();

        @Meta.AD(deflt = "15", description = "Power in Watt when the generator is producing at maximum.")
            int minProduction();

        @Meta.AD(deflt = "0",
                 description = "Step size: power that will be added to min Production to generate the running modes.")
            int stepSize();

        @Meta.AD(deflt = "30", description = "Number of steps between minimum and maximum")
            int numberOfSteps();

        @Meta.AD(deflt = "1",
                 description = "Minimum time in seconds before a new running mode may be selected.",
                 min = "0")
            int minimumTimeBeforeSwitchingRunningModeInSeconds();
    }

    private Config config;
    private ServiceRegistration<Widget> widgetRegistration;
    private Date changedStateAt;
    private FlexiblePowerContext context;
    private Measure<Integer, Duration> allocationDelay;
    private LedstripState mostRecentState;
    private NavigableSet<Integer> powerValues;

    public NavigableSet<Integer> getPowerValues() {
        return powerValues;
    }

    public LedstripState getMostRecentState() {
        return mostRecentState;
    }

    @Activate
    public void activate(BundleContext bundleContext, Map<String, Object> properties) {
        config = Configurable.createConfigurable(Config.class, properties);
        if (config.showWidget()) {
            LedstripManagerWidget widget = new LedstripManagerWidget(this);
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
                                                                LedstripState state) {
        changedStateAt = context.currentTime();
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

    private UnconstrainedSystemDescription createUnconstrainedSystemDescription() {
        powerValues = new TreeSet<Integer>();

        for (int i = 0; i < config.numberOfSteps(); i++) {
            powerValues.add(config.minProduction() + i * config.stepSize());
        }
        powerValues.add(0);
        final Measurable<Duration> transitionTime = Measure.valueOf(5, SI.SECOND);

        List<RunningMode<RunningModeBehaviour>> rmList = new ArrayList<RunningMode<RunningModeBehaviour>>();
        Timer theTimer = new Timer(1, "theOne", transitionTime);

        for (int power : powerValues) {
            Set<Integer> targetStates = new HashSet<Integer>(powerValues);
            targetStates.remove(power);
            Set<Transition> transitions = new HashSet<Transition>();

            for (int targetState : targetStates) {
                transitions.add(new Transition(targetState,
                                               Collections.<Timer> singleton(theTimer),
                                               Collections.<Timer> singleton(theTimer),
                                               Measure.valueOf(0, NonSI.EUR),
                                               Measure.valueOf(0, SI.SECOND)));
            }

            rmList.add(new RunningMode<RunningModeBehaviour>(power,
                                                             "rm_" + power,
                                                             new RunningModeBehaviour(CommodityMeasurables.electricity(Measure.valueOf(power,
                                                                                                                                       SI.WATT)),
                                                                                      Measure.valueOf(0,
                                                                                                      NonSI.EUR_PER_HOUR)),
                                                             transitions));
        }

        return new UnconstrainedSystemDescription(getResourceId(),
                                                  changedStateAt,
                                                  changedStateAt,
                                                  rmList);
    }

    private UnconstrainedStateUpdate createUnconstrainedUpdate(LedstripState state) {
        return new UnconstrainedStateUpdate(getResourceId(),
                                            context.currentTime(),
                                            context.currentTime(),
                                            state.getLedstripLevel().getIntLevel(),
                                            Collections.<TimerUpdate> emptySet());
    }

    private String getResourceId() {
        return config.resourceId();
    }

    @Override
    protected List<? extends ResourceMessage> updatedState(LedstripState state) {
        mostRecentState = state;
        return Arrays.asList(createUnconstrainedUpdate(state));
    }

    @Override
    protected LedstripControlParameters receivedAllocation(ResourceMessage message) {
        if (message instanceof UnconstrainedAllocation) {
            UnconstrainedAllocation allocation = (UnconstrainedAllocation) message;
            logger.debug("Received allocation " + allocation);
            // determine running mode and delay.
            long delay = allocation.getRunningModeSelectors().iterator().next().getStartTime().getTime()
                         - context.currentTimeMillis();
            delay = (delay <= 0 ? 1 : delay);

            // set up the scheduler switch to the running mode after the specified delay
            scheduleSwitchToRunningMode(Measure.valueOf(delay, MS), allocation.getRunningModeSelectors()
                                                                              .iterator()
                                                                              .next()
                                                                              .getRunningModeId());
            // Send to attached energy app the acceptance of the allocation request
            allocationStatusUpdate(new AllocationStatusUpdate(context.currentTime(),
                                                              allocation,
                                                              AllocationStatus.ACCEPTED,
                                                              ""));
            // the running mode change is scheduled at time, so nothing to return
            return null;
        } else {
            logger.warn("Unexpected resource (" + message.toString()
                        + ") message type ("
                        + message.getClass().getName()
                        + ")received");
            return null;
        }
    }

    private void scheduleSwitchToRunningMode(final Measurable<Duration> delay, final int runningModePower) {
        final Runnable allocationHelper = new Runnable() {
            @Override
            public void run() {
                // Send control parameters to Zenobox
                sendControlParameters(new LedstripControlParameters() {
                    @Override
                    public LedstripLevel getLevel() {
                        LedstripLevel level = new LedstripLevel();
                        level.setLevel(runningModePower);
                        return level;
                    }
                });
                logger.debug("Has set up allocation at " + delay
                             + " for running mode with Power value="
                             + runningModePower);
            }
        };
        context.schedule(allocationHelper, delay);
    }

    @Override
    protected ControlSpaceRevoke createRevokeMessage() {
        return new ControlSpaceRevoke(config.resourceId(), context.currentTime());
    }

    @Reference
    public void setContext(FlexiblePowerContext context) {
        this.context = context;
    }
}
