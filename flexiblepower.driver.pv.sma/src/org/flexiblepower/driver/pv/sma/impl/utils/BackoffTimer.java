package org.flexiblepower.driver.pv.sma.impl.utils;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class BackoffTimer {

	private final static Logger logger = LoggerFactory.getLogger(BackoffTimer.class);

	private long initialIntervalMillis;
	private double multiplier;
	private long maximumIntervalMillis;
	private long currentIntervalMillis;
	
	public BackoffTimer(long initialIntervalMillis, double multiplier, long maximumIntervalMillis) {
		if ((initialIntervalMillis > maximumIntervalMillis) || (multiplier <= 1)) {
			throw new IllegalArgumentException("Arguments do not adhere to: (initialIntervalMillis <= maximumIntervalMillis) and (multiplier > 1)");
		}
		this.initialIntervalMillis = initialIntervalMillis;
		this.multiplier = multiplier;
		this.maximumIntervalMillis = maximumIntervalMillis;
		currentIntervalMillis = initialIntervalMillis;
	}
	
	public void reset() {
		logger.debug("Resetting backoff timer to {} ms", initialIntervalMillis);
		currentIntervalMillis = initialIntervalMillis;
	}

	public void sleep() {
		logger.debug("Backing off for {} ms", currentIntervalMillis);
		try {
			Thread.sleep(currentIntervalMillis);
		} catch (InterruptedException e) {
			logger.warn("BackoffTimer interrupted", e);
		}
		
		currentIntervalMillis *= multiplier;
		if (currentIntervalMillis > maximumIntervalMillis) {
			currentIntervalMillis = maximumIntervalMillis;
		}
	}
}
