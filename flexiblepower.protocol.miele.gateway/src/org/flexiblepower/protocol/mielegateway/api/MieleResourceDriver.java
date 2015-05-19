package org.flexiblepower.protocol.mielegateway.api;

import java.util.Calendar;
import java.util.Date;
import java.util.Map;
import java.util.concurrent.CopyOnWriteArrayList;

import javax.measure.Measurable;
import javax.measure.Measure;
import javax.measure.quantity.Temperature;
import javax.measure.unit.SI;

import org.flexiblepower.context.FlexiblePowerContext;
import org.flexiblepower.observation.Observation;
import org.flexiblepower.observation.ObservationConsumer;
import org.flexiblepower.observation.ObservationProvider;
import org.flexiblepower.ral.ResourceControlParameters;
import org.flexiblepower.ral.ResourceState;
import org.flexiblepower.ral.ext.AbstractResourceDriver;

public abstract class MieleResourceDriver<RS extends ResourceState, RCP extends ResourceControlParameters> extends
                                                                                                           AbstractResourceDriver<RS, RCP> implements
                                                                                                                                          ObservationProvider<RS> {

    public static Integer parseTime(String value) {
        if (value != null) {
            String[] parts = value.replace("h", "").split(":");
            try {
                if (parts.length == 1) {
                    return Integer.parseInt(parts[0]);
                } else if (parts.length == 2) {
                    int hours = Integer.parseInt(parts[0]);
                    int minutes = Integer.parseInt(parts[1]);

                    minutes = (hours * 60) + minutes;

                    return minutes;
                }
            } catch (NumberFormatException ex) {
                // Ignore, just return null, we don't understand it
            }
        }
        return null;
    }

    public Date parseDate(String value) {
        Integer time = parseTime(value);
        if (time != null) {
            Calendar current = Calendar.getInstance();
            current.setTimeInMillis(context.currentTimeMillis());

            // Clone the current time and set the time from the time string
            Calendar cal = (Calendar) current.clone();
            cal.set(Calendar.HOUR_OF_DAY, 0);
            cal.set(Calendar.MINUTE, 0);
            cal.set(Calendar.SECOND, 0);
            cal.add(Calendar.MINUTE, time);

            // Is current time already after start time then set it to tomorrow
            if (current.after(cal)) {
                cal.add(Calendar.DATE, 1);
            }

            // Return the start time
            return cal.getTime();
        }
        return null;
    }

    public static Measurable<Temperature> parseTemperature(String value) {
        if (value != null && value.endsWith(" °C")) {
            try {
                float temp = Float.parseFloat(value.substring(0, value.length() - 3));
                return Measure.valueOf(temp, SI.CELSIUS);
            } catch (NumberFormatException ex) {
                // Ignore
            }
        }
        return null;
    }

    private final ActionPerformer actionPerformer;
    protected final FlexiblePowerContext context;
    private final CopyOnWriteArrayList<ObservationConsumer<? super RS>> subscriptions = new CopyOnWriteArrayList<ObservationConsumer<? super RS>>();
    private RS lastState;
    private Observation<RS> observation;

    public MieleResourceDriver(ActionPerformer actionPerformer, FlexiblePowerContext context) {
        this.actionPerformer = actionPerformer;
        this.context = context;
    }

    public abstract void updateState(Map<String, String> information);

    public final ActionResult performAction(String action) {
        return actionPerformer.performAction(action);
    }

    public void close() {
    }

    protected void publishMieleState(RS state) {
        publishState(state);
        observation = new Observation<RS>(context.currentTime(), state);
        for (ObservationConsumer<? super RS> consumer : subscriptions) {
            consumer.consume(this, observation);
        }
        lastState = state;
    }

    @Override
    public void subscribe(ObservationConsumer<? super RS> consumer) {
        subscriptions.add(consumer);
    }

    @Override
    public void unsubscribe(ObservationConsumer<? super RS> consumer) {
        subscriptions.remove(consumer);
    }

    @Override
    public Observation<? extends RS> getLastObservation() {
        return observation;
    }

}
