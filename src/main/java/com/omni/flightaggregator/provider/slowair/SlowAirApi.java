package com.omni.flightaggregator.provider.slowair;

import java.util.List;

/**
 * SlowAir's native wire model: amounts in cents, ISO-8601 strings for times and durations, and separate
 * outbound and inbound flight lists.
 */
public final class SlowAirApi {

	private SlowAirApi() {
	}

	/** {@code returnDate} is null for a one-way search. */
	public record SearchRequest(String origin, String destination, String date, String returnDate, int adults,
			int children, int infants) {
	}

	public record SearchResponse(List<Offer> offers) {
	}

	/** {@code cabin} is one of ECONOMY, BUSINESS; {@code inbound} is empty for one-way offers. */
	public record Offer(String offerId, String cabin, String currency, boolean refundable, int checkedBags,
			List<Flight> outbound, List<Flight> inbound, List<PaxFare> fares) {
	}

	/** {@code flightNumber} includes the carrier code. */
	public record Flight(String carrier, String flightNumber, String from, String to, String departure,
			String arrival, String duration, String aircraft) {
	}

	/** Per-passenger amounts for one passenger type (ADULT, CHILD, INFANT). */
	public record PaxFare(String type, int count, long baseCents, long taxCents) {
	}
}
