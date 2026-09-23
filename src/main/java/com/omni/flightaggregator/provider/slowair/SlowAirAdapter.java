package com.omni.flightaggregator.provider.slowair;

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
import com.omni.flightaggregator.provider.slowair.SlowAirApi.Flight;
import com.omni.flightaggregator.provider.slowair.SlowAirApi.Offer;
import com.omni.flightaggregator.provider.slowair.SlowAirApi.PaxFare;
import com.omni.flightaggregator.provider.slowair.SlowAirApi.SearchRequest;
import com.omni.flightaggregator.provider.slowair.SlowAirApi.SearchResponse;
import reactor.core.publisher.Mono;

import org.springframework.boot.autoconfigure.condition.ConditionalOnBooleanProperty;
import org.springframework.stereotype.Component;

/**
 * Adapts the (fake, very slow) SlowAir API to the standard offer model. Off by default; set
 * {@value #ENABLED_PROPERTY} to {@code true} to include it in searches.
 */
@Component
@ConditionalOnBooleanProperty(SlowAirAdapter.ENABLED_PROPERTY)
class SlowAirAdapter implements FlightProviderAdapter {

	static final String CODE = "slowair";

	static final String ENABLED_PROPERTY = "omni.mock.slowair.enabled";

	private final SlowAirClient client;

	SlowAirAdapter(SlowAirClient client) {
		this.client = client;
	}

	@Override
	public String code() {
		return CODE;
	}

	@Override
	public Mono<List<FlightOffer>> search(FlightSearchCriteria criteria) {
		return client.search(toRequest(criteria)).map(SlowAirAdapter::toOffers);
	}

	private static SearchRequest toRequest(FlightSearchCriteria criteria) {
		return new SearchRequest(criteria.origin(), criteria.destination(), criteria.departureDate().toString(),
				criteria.isRoundTrip() ? criteria.returnDate().toString() : null,
				criteria.passengers().adults(), criteria.passengers().children(), criteria.passengers().infants());
	}

	private static List<FlightOffer> toOffers(SearchResponse response) {
		return response.offers().stream().map(SlowAirAdapter::toOffer).toList();
	}

	private static FlightOffer toOffer(Offer offer) {
		List<Itinerary> itineraries = new ArrayList<>();
		addItinerary(itineraries, Direction.OUTBOUND, offer.outbound());
		addItinerary(itineraries, Direction.INBOUND, offer.inbound());
		List<PassengerFare> passengerFares = offer.fares().stream().map(SlowAirAdapter::toPassengerFare).toList();
		return new FlightOffer(CODE + "-" + offer.offerId(), CODE, itineraries,
				Price.of(offer.currency(), passengerFares), CabinClass.valueOf(offer.cabin()),
				new BaggageAllowance(offer.checkedBags(), 1), offer.refundable());
	}

	private static void addItinerary(List<Itinerary> itineraries, Direction direction, List<Flight> flights) {
		if (!flights.isEmpty()) {
			List<Segment> segments = flights.stream().map(SlowAirAdapter::toSegment).toList();
			itineraries.add(Itinerary.of(direction, segments, journeyDuration(segments)));
		}
	}

	/** Flight times plus layovers, since departure and arrival are airport-local clock times. */
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

	private static Segment toSegment(Flight flight) {
		return new Segment(flight.carrier(), flight.flightNumber(), flight.from(), flight.to(),
				LocalDateTime.parse(flight.departure()), LocalDateTime.parse(flight.arrival()),
				Duration.parse(flight.duration()), flight.aircraft());
	}

	private static PassengerFare toPassengerFare(PaxFare fare) {
		return PassengerFare.of(PassengerType.valueOf(fare.type()), fare.count(),
				BigDecimal.valueOf(fare.baseCents(), 2), BigDecimal.valueOf(fare.taxCents(), 2));
	}
}
