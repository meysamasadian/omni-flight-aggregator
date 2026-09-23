package com.omni.flightaggregator.domain;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.List;

/** Total price of an offer for the whole party, in the provider's currency, with the per-type breakdown. */
public record Price(String currency, BigDecimal total, List<PassengerFare> passengerFares) {

	public static Price of(String currency, List<PassengerFare> passengerFares) {
		BigDecimal total = passengerFares.stream()
				.map(PassengerFare::totalForType)
				.reduce(BigDecimal.ZERO, BigDecimal::add)
				.setScale(2, RoundingMode.HALF_UP);
		return new Price(currency, total, List.copyOf(passengerFares));
	}
}
