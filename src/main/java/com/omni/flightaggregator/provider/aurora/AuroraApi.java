package com.omni.flightaggregator.provider.aurora;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;

/**
 * Aurora Air's native (airline, NDC-style) wire model: one journey query per direction, offers made of
 * fare families, and price lines per passenger type. Journeys are listed outbound first, then inbound.
 */
public final class AuroraApi {

	private AuroraApi() {
	}

	public record OfferRequest(List<JourneyQuery> journeys, List<PaxQuantity> passengers) {
	}

	public record JourneyQuery(String origin, String destination, LocalDate date) {
	}

	/** {@code type} is ADT, CHD or INF. */
	public record PaxQuantity(String type, int quantity) {
	}

	public record OfferResponse(String responseId, List<Offer> offers) {
	}

	/** {@code fareFamily} is LIGHT, CLASSIC or FLEX. */
	public record Offer(String offerId, String fareFamily, String currency, List<Journey> journeys,
			List<PriceLine> priceLines) {
	}

	public record Journey(String journeyRef, int durationMinutes, List<FlightSegment> segments) {
	}

	/** {@code flightNumber} is the bare number, without the carrier code. */
	public record FlightSegment(String carrier, String flightNumber, String origin, String destination,
			LocalDateTime departure, LocalDateTime arrival, int durationMinutes, String equipment) {
	}

	/** Per-passenger amounts for {@code quantity} passengers of type {@code paxType}. */
	public record PriceLine(String paxType, int quantity, BigDecimal baseAmount, BigDecimal taxAmount) {
	}
}
