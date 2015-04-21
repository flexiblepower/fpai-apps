package org.flexiblepower.simulation.profile.uncontrolled;

import java.util.Date;

import org.flexiblepower.ral.values.CommodityForecast;

public interface UncontrolledProfileForecaster {

    CommodityForecast getForecast(Date startTime,
                                  int forecastNumberOfElements,
                                  int forecastDurationPerElement,
                                  int forecastRandomnessPercentage);
}
