package com.omni.flightaggregator.provider.globaltravel;

import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.Map;

/**
 * GlobalTravel's native (travel agency) wire model: packages made of nested trips, {@code yyyyMMdd}
 * request dates, {@code dd/MM/yyyy HH:mm} flight times, decimal-string fares keyed by traveller type.
 */
public final class GlobalTravelApi {

	public static final DateTimeFormatter DATE = DateTimeFormatter.ofPattern("yyyyMMdd");

	public static final DateTimeFormatter DATE_TIME = DateTimeFormatter.ofPattern("dd/MM/yyyy HH:mm");

	private GlobalTravelApi() {
	}

	/** {@code inboundDate} is null for one-way; {@code travellers} is keyed adult / child / infant. */
	public record Query(String origin, String destination, String outboundDate, String inboundDate,
			Map<String, Integer> travellers) {
	}

	public record Result(String searchToken, List<TravelPackage> packages) {
	}

	/**
	 * {@code cabinClass} is lower-case (economy, premium_economy, business); {@code baggage} is a piece
	 * count such as {@code 1PC}; {@code refundPolicy} is REFUNDABLE or NON_REFUNDABLE; {@code fareTable}
	 * holds per-traveller amounts keyed like the query's travellers. {@code inbound} is null for one-way.
	 */
	public record TravelPackage(String code, Trip outbound, Trip inbound, String currency, String cabinClass,
			String baggage, String refundPolicy, Map<String, FareLine> fareTable) {
	}

	public record Trip(List<Flight> flights, int totalMinutes) {
	}

	/** {@code number} is the bare flight number, without the airline code. */
	public record Flight(String airlineCode, String number, String departAirport, String arriveAirport,
			String departLocal, String arriveLocal, int minutes, String aircraft) {
	}

	/** Per-traveller amounts as decimal strings, e.g. {@code "245.50"}. */
	public record FareLine(String base, String tax) {
	}
}
