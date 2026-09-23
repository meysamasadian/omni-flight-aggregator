package com.omni.flightaggregator.domain;

import java.time.Duration;
import java.time.LocalDateTime;

/** One flight. Departure and arrival are local to the respective airport. */
public record Segment(
		String carrier,
		String flightNumber,
		String origin,
		String destination,
		LocalDateTime departure,
		LocalDateTime arrival,
		Duration duration,
		String aircraft) {
}
