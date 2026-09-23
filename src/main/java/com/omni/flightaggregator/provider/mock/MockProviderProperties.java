package com.omni.flightaggregator.provider.mock;

import java.time.Duration;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.bind.DefaultValue;

/** Simulated network behaviour of each mocked provider. */
@ConfigurationProperties("omni.mock")
public record MockProviderProperties(
		@DefaultValue Behavior skybook,
		@DefaultValue Behavior globaltravel,
		@DefaultValue Behavior aurora,
		@DefaultValue Behavior slowair) {

	/**
	 * @param minLatency lower bound of the simulated response time
	 * @param maxLatency upper bound of the simulated response time
	 * @param failureRate probability (0.0 - 1.0) that a call fails
	 */
	public record Behavior(
			@DefaultValue("100ms") Duration minLatency,
			@DefaultValue("500ms") Duration maxLatency,
			@DefaultValue("0") double failureRate) {

		public static Behavior instant() {
			return new Behavior(Duration.ZERO, Duration.ZERO, 0);
		}
	}
}
