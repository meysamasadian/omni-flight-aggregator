package com.omni.flightaggregator.provider.skybook;

import java.math.BigDecimal;
import java.time.Duration;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

import com.omni.flightaggregator.domain.BaggageAllowance;
import com.omni.flightaggregator.domain.CabinClass;
import com.omni.flightaggregator.domain.Direction;
import com.omni.flightaggregator.domain.FlightOffer;
import com.omni.flightaggregator.domain.FlightSearchCriteria;
import com.omni.flightaggregator.domain.Itinerary;
import com.omni.flightaggregator.domain.PassengerFare;
import com.omni.flightaggregator.domain.PassengerType;
import com.omni.flightaggregator.domain.Price;
import com.omni.flightaggregator.domain.Segment;
import com.omni.flightaggregator.provider.FlightProviderAdapter;
import com.omni.flightaggregator.provider.skybook.SkyBookApi.Fare;
import com.omni.flightaggregator.provider.skybook.SkyBookApi.Leg;
import com.omni.flightaggregator.provider.skybook.SkyBookApi.PaxFare;
import com.omni.flightaggregator.provider.skybook.SkyBookApi.SearchRequest;
import com.omni.flightaggregator.provider.skybook.SkyBookApi.SearchResponse;
import reactor.core.publisher.Mono;

import org.springframework.stereotype.Component;

/** Adapts the SkyBook OTA API to the standard offer model. */
@Component
class SkyBookAdapter implements FlightProviderAdapter {

	static final String CODE = "skybook";

	private final SkyBookClient client;

	SkyBookAdapter(SkyBookClient client) {
		this.client = client;
	}

	@Override
	public String code() {
		return CODE;
	}

	@Override
	public Mono<List<FlightOffer>> search(FlightSearchCriteria criteria) {
		return client.search(toRequest(criteria)).map(SkyBookAdapter::toOffers);
	}

	private static SearchRequest toRequest(FlightSearchCriteria criteria) {
		return new SearchRequest(criteria.origin(), criteria.destination(), criteria.departureDate().toString(),
				criteria.isRoundTrip() ? criteria.returnDate().toString() : null,
				criteria.passengers().adults(), criteria.passengers().children(), criteria.passengers().infants());
	}

	private static List<FlightOffer> toOffers(SearchResponse response) {
		return response.fares().stream().map(SkyBookAdapter::toOffer).toList();
	}

	private static FlightOffer toOffer(Fare fare) {
		List<Itinerary> itineraries = new ArrayList<>();
		addItinerary(itineraries, Direction.OUTBOUND, fare.legs(), 0);
		addItinerary(itineraries, Direction.INBOUND, fare.legs(), 1);
		List<PassengerFare> passengerFares = fare.paxFares().stream().map(SkyBookAdapter::toPassengerFare).toList();
		return new FlightOffer(CODE + "-" + fare.fareId(), CODE, itineraries,
				Price.of(fare.cur(), passengerFares), toCabin(fare.cabin()),
				new BaggageAllowance(fare.checkedBags(), 1), fare.refundable());
	}

	private static void addItinerary(List<Itinerary> itineraries, Direction direction, List<Leg> legs,
			int nativeDirection) {
		List<Segment> segments = legs.stream()
				.filter(leg -> leg.direction() == nativeDirection)
				.map(SkyBookAdapter::toSegment)
				.toList();
		if (!segments.isEmpty()) {
			itineraries.add(Itinerary.of(direction, segments, journeyDuration(segments)));
		}
	}

	/**
	 * SkyBook reports no journey total. Departure and arrival are airport-local clock times, so the total
	 * cannot be read off the first departure and last arrival across time zones. Instead, add up the flight
	 * times and the layovers, whose two times are both local to the connecting airport.
	 */
	private static Duration journeyDuration(List<Segment> segments) {
		Duration total = Duration.ZERO;
		for (int i = 0; i < segments.size(); i++) {
			total = total.plus(segments.get(i).duration());
			if (i > 0) {
				total = total.plus(Duration.between(segments.get(i - 1).arrival(), segments.get(i).departure()));
			}
		}
		return total;
	}

	private static Segment toSegment(Leg leg) {
		return new Segment(leg.carrier(), leg.carrier() + leg.flightNo(), leg.from(), leg.to(),
				LocalDateTime.parse(leg.dep()), LocalDateTime.parse(leg.arr()), Duration.parse(leg.elapsed()),
				leg.equip());
	}

	private static PassengerFare toPassengerFare(PaxFare paxFare) {
		return PassengerFare.of(toPassengerType(paxFare.paxCode()), paxFare.paxCount(),
				BigDecimal.valueOf(paxFare.baseCents(), 2), BigDecimal.valueOf(paxFare.taxCents(), 2));
	}

	private static PassengerType toPassengerType(String code) {
		return switch (code) {
			case "ADT" -> PassengerType.ADULT;
			case "CHD" -> PassengerType.CHILD;
			case "INF" -> PassengerType.INFANT;
			default -> throw new IllegalArgumentException("Unknown SkyBook passenger code: " + code);
		};
	}

	private static CabinClass toCabin(String bookingClass) {
		return switch (bookingClass) {
			case "W" -> CabinClass.PREMIUM_ECONOMY;
			case "C" -> CabinClass.BUSINESS;
			case "F" -> CabinClass.FIRST;
			default -> CabinClass.ECONOMY;
		};
	}
}
