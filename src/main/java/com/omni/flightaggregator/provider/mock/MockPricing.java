package com.omni.flightaggregator.provider.mock;

import com.omni.flightaggregator.domain.PassengerType;

/** Fare rules shared by the mocked providers. All amounts are in minor currency units (cents). */
public final class MockPricing {

	private static final long ADULT_TAX_CENTS = 4_500;

	private static final long INFANT_TAX_CENTS = 1_000;

	private MockPricing() {
	}

	/** Per-passenger fare. */
	public record UnitFare(long baseCents, long taxCents) {
	}

	/** A stable base adult fare for a route (60.00 - 660.00), before any per-offer variation. */
	public static long routeBaseCents(String from, String to) {
		String key = from.compareTo(to) < 0 ? from + to : to + from;
		return 6_000 + Math.floorMod(key.hashCode() * 31L, 60_000);
	}

	/** Children pay 75% of the adult base fare and full taxes; infants 10% and reduced taxes. */
	public static UnitFare unitFare(PassengerType type, long adultBaseCents) {
		return switch (type) {
			case ADULT -> new UnitFare(adultBaseCents, ADULT_TAX_CENTS);
			case CHILD -> new UnitFare(adultBaseCents * 75 / 100, ADULT_TAX_CENTS);
			case INFANT -> new UnitFare(adultBaseCents * 10 / 100, INFANT_TAX_CENTS);
		};
	}
}
