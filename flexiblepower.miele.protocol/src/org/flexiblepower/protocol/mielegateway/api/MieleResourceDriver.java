package org.flexiblepower.protocol.mielegateway.api;

import java.util.Calendar;
import java.util.Date;
import java.util.Map;

import javax.measure.Measurable;
import javax.measure.Measure;
import javax.measure.quantity.Temperature;
import javax.measure.unit.SI;

import org.flexiblepower.ral.ResourceControlParameters;
import org.flexiblepower.ral.ResourceState;
import org.flexiblepower.ral.ext.AbstractResourceDriver;
import org.flexiblepower.time.TimeService;

public abstract class MieleResourceDriver<RS extends ResourceState, RCP extends ResourceControlParameters>
		extends AbstractResourceDriver<RS, RCP> {

	public static Integer parseTime(String value) {
		if (value != null) {
			String[] parts = value.replace('H', ' ').split(":");
			try {
				if (parts.length == 1) {
					return Integer.parseInt(parts[0]);
				} else if (parts.length == 2) {
					return Integer.parseInt(parts[0]) * 60
							+ Integer.parseInt(parts[1]);
				}
			} catch (NumberFormatException ex) {
				// Ignore, just return null, we don't understand it
			}
		}
		return null;
	}

	public static Date parseDate(String value) {
		Integer time = parseTime(value);
		if (time != null) {
			Calendar current = Calendar.getInstance();

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
				float temp = Float.parseFloat(value.substring(0,
						value.length() - 3));
				return Measure.valueOf(temp, SI.CELSIUS);
			} catch (NumberFormatException ex) {
				// Ignore
			}
		}
		return null;
	}

	private final ActionPerformer actionPerformer;
	protected final TimeService timeService;

	public MieleResourceDriver(ActionPerformer actionPerformer,
			TimeService timeService) {
		this.actionPerformer = actionPerformer;
		this.timeService = timeService;
	}

	public abstract void updateState(Map<String, String> information);

	public final ActionResult performAction(String action) {
		return actionPerformer.performAction(action);
	}
}
