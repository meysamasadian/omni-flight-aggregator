package com.omni.flightaggregator.domain;

import java.time.Duration;
import java.util.List;

/** One direction of travel: a chain of segments from the origin to the destination. */
public record Itinerary(Direction direction, List<Segment> segments, Duration duration, int stops) {

	public static Itinerary of(Direction direction, List<Segment> segments, Duration duration) {
		return new Itinerary(direction, List.copyOf(segments), duration, segments.size() - 1);
	}
}
