package com.omni.flightaggregator.domain;

import java.util.List;

/**
 * The standard offer every provider adapter produces. {@code itineraries} holds one OUTBOUND itinerary,
 * plus one INBOUND itinerary for round trips.
 */
public record FlightOffer(
		String id,
		String provider,
		List<Itinerary> itineraries,
		Price price,
		CabinClass cabin,
		BaggageAllowance baggage,
		boolean refundable) {
}
