package com.omni.flightaggregator.domain;

import java.math.BigDecimal;
import java.math.RoundingMode;

/** Fare for one passenger type. {@code baseFare} and {@code taxes} are per passenger. */
public record PassengerFare(
		PassengerType type,
		int count,
		BigDecimal baseFare,
		BigDecimal taxes,
		BigDecimal totalPerPassenger,
		BigDecimal totalForType) {

	public static PassengerFare of(PassengerType type, int count, BigDecimal baseFare, BigDecimal taxes) {
		BigDecimal base = baseFare.setScale(2, RoundingMode.HALF_UP);
		BigDecimal tax = taxes.setScale(2, RoundingMode.HALF_UP);
		BigDecimal perPassenger = base.add(tax);
		return new PassengerFare(type, count, base, tax, perPassenger, perPassenger.multiply(BigDecimal.valueOf(count)));
	}
}
