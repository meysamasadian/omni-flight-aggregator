package com.omni.flightaggregator.provider.mock;

import java.time.Duration;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Objects;
import java.util.Random;

/**
 * Generates plausible, deterministic flight schedules for the mocked providers. The same provider,
 * route and date always yield the same journeys. Times are a naive local reference frame (arrival is
 * departure plus duration, time zones are ignored), which is good enough for mock data.
 */
public final class MockFlightGenerator {

	private static final List<String> HUBS = List.of("FRA", "AMS", "IST", "DXB", "DOH", "CDG");

	private static final List<String> NARROW_BODY = List.of("A320", "A321", "B737-800", "E190");

	private static final List<String> WIDE_BODY = List.of("A350-900", "B787-9", "A330-300");

	private MockFlightGenerator() {
	}

	public record MockLeg(String carrier, String flightNumber, String from, String to, LocalDateTime departure,
			LocalDateTime arrival, Duration duration, String aircraft) {
	}

	public record MockJourney(List<MockLeg> legs) {

		public Duration totalDuration() {
			return Duration.between(legs.getFirst().departure(), legs.getLast().arrival());
		}
	}

	/** A random source seeded from the request, for reproducible per-request variation. */
	public static Random random(String provider, String from, String to, LocalDate date) {
		return new Random(Objects.hash(provider, from, to, date));
	}

	/** {@code count} journeys from {@code from} to {@code to} on {@code date}, earliest departure first. */
	public static List<MockJourney> journeys(String provider, String from, String to, LocalDate date,
			List<String> carriers, int count) {
		Random random = random(provider, from, to, date);
		int routeMinutes = routeMinutes(from, to);
		List<MockJourney> journeys = new ArrayList<>();
		for (int i = 0; i < count; i++) {
			String carrier = carriers.get(random.nextInt(carriers.size()));
			LocalDateTime departure = date.atStartOfDay().plusMinutes(5 * 60 + random.nextInt(17 * 12) * 5L);
			if (random.nextInt(4) == 0) {
				journeys.add(oneStop(random, carrier, from, to, departure, routeMinutes));
			}
			else {
				journeys.add(new MockJourney(List.of(leg(random, carrier, from, to, departure, routeMinutes))));
			}
		}
		journeys.sort(Comparator.comparing(journey -> journey.legs().getFirst().departure()));
		return journeys;
	}

	private static MockJourney oneStop(Random random, String carrier, String from, String to,
			LocalDateTime departure, int routeMinutes) {
		List<String> hubs = HUBS.stream().filter(hub -> !hub.equals(from) && !hub.equals(to)).toList();
		String hub = hubs.get(random.nextInt(hubs.size()));
		MockLeg first = leg(random, carrier, from, hub, departure, routeMinutes * 45 / 100 + 20);
		LocalDateTime nextDeparture = first.arrival().plusMinutes(60 + random.nextInt(121));
		MockLeg second = leg(random, carrier, hub, to, nextDeparture, routeMinutes * 55 / 100 + 20);
		return new MockJourney(List.of(first, second));
	}

	private static MockLeg leg(Random random, String carrier, String from, String to, LocalDateTime departure,
			int minutes) {
		List<String> aircraftTypes = minutes >= 300 ? WIDE_BODY : NARROW_BODY;
		Duration duration = Duration.ofMinutes(minutes);
		return new MockLeg(carrier, carrier + (100 + random.nextInt(8900)), from, to, departure,
				departure.plus(duration), duration, aircraftTypes.get(random.nextInt(aircraftTypes.size())));
	}

	/** Same duration for a route and its reverse. */
	private static int routeMinutes(String from, String to) {
		String key = from.compareTo(to) < 0 ? from + to : to + from;
		return 90 + Math.floorMod(key.hashCode(), 600);
	}
}
