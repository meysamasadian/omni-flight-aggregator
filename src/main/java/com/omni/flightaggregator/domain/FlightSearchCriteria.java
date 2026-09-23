package com.omni.flightaggregator.domain;

import java.time.LocalDate;

/**
 * Provider-agnostic, already validated search criteria handed to every provider adapter.
 * {@code returnDate} is {@code null} for one-way trips.
 */
public record FlightSearchCriteria(
		String origin,
		String destination,
		TripType tripType,
		LocalDate departureDate,
		LocalDate returnDate,
		PassengerCounts passengers) {

	public boolean isRoundTrip() {
		return tripType == TripType.ROUND_TRIP;
	}
}
