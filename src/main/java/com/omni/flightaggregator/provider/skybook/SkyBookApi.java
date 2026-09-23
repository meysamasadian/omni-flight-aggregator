package com.omni.flightaggregator.provider.skybook;

import java.util.List;

/**
 * SkyBook's native (OTA) wire model: amounts in cents, ISO-8601 strings for times and durations, and a
 * flat list of legs tagged 0 (outbound) or 1 (inbound).
 */
public final class SkyBookApi {

	private SkyBookApi() {
	}

	public record SearchRequest(String from, String to, String departDate, String returnDate, int adt, int chd,
			int inf) {
	}

	public record SearchResponse(String requestRef, List<Fare> fares) {
	}

	/** {@code cabin} is a booking class letter: Y economy, W premium economy, C business, F first. */
	public record Fare(String fareId, String cabin, String cur, boolean refundable, int checkedBags,
			List<Leg> legs, List<PaxFare> paxFares) {
	}

	/** {@code flightNo} is the bare number, without the carrier code. */
	public record Leg(int direction, String carrier, String flightNo, String from, String to, String dep,
			String arr, String elapsed, String equip) {
	}

	/** Per-passenger amounts for one passenger code (ADT, CHD, INF). */
	public record PaxFare(String paxCode, int paxCount, long baseCents, long taxCents) {
	}
}
