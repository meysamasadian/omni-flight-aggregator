package com.omni.flightaggregator.provider.aurora;

import java.time.Duration;
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
import com.omni.flightaggregator.provider.aurora.AuroraApi.FlightSegment;
import com.omni.flightaggregator.provider.aurora.AuroraApi.Journey;
import com.omni.flightaggregator.provider.aurora.AuroraApi.JourneyQuery;
import com.omni.flightaggregator.provider.aurora.AuroraApi.Offer;
import com.omni.flightaggregator.provider.aurora.AuroraApi.OfferRequest;
import com.omni.flightaggregator.provider.aurora.AuroraApi.OfferResponse;
import com.omni.flightaggregator.provider.aurora.AuroraApi.PaxQuantity;
import com.omni.flightaggregator.provider.aurora.AuroraApi.PriceLine;
import reactor.core.publisher.Mono;

import org.springframework.stereotype.Component;

/** Adapts the Aurora Air airline API to the standard offer model. */
@Component
class AuroraAdapter implements FlightProviderAdapter {

	static final String CODE = "aurora";

	private final AuroraClient client;

	AuroraAdapter(AuroraClient client) {
		this.client = client;
	}

	@Override
	public String code() {
		return CODE;
	}

	@Override
	public Mono<List<FlightOffer>> search(FlightSearchCriteria criteria) {
		return client.searchOffers(toRequest(criteria)).map(AuroraAdapter::toOffers);
	}

	private static OfferRequest toRequest(FlightSearchCriteria criteria) {
		List<JourneyQuery> journeys = new ArrayList<>();
		journeys.add(new JourneyQuery(criteria.origin(), criteria.destination(), criteria.departureDate()));
		if (criteria.isRoundTrip()) {
			journeys.add(new JourneyQuery(criteria.destination(), criteria.origin(), criteria.returnDate()));
		}
		return new OfferRequest(journeys, List.of(
				new PaxQuantity("ADT", criteria.passengers().adults()),
				new PaxQuantity("CHD", criteria.passengers().children()),
				new PaxQuantity("INF", criteria.passengers().infants())));
	}

	private static List<FlightOffer> toOffers(OfferResponse response) {
		return response.offers().stream().map(AuroraAdapter::toOffer).toList();
	}

	private static FlightOffer toOffer(Offer offer) {
		List<Itinerary> itineraries = new ArrayList<>();
		for (int i = 0; i < offer.journeys().size(); i++) {
			itineraries.add(toItinerary(i == 0 ? Direction.OUTBOUND : Direction.INBOUND, offer.journeys().get(i)));
		}
		List<PassengerFare> fares = offer.priceLines().stream().map(AuroraAdapter::toPassengerFare).toList();
		return new FlightOffer(CODE + "-" + offer.offerId(), CODE, itineraries, Price.of(offer.currency(), fares),
				CabinClass.ECONOMY, baggageFor(offer.fareFamily()), "FLEX".equals(offer.fareFamily()));
	}

	private static Itinerary toItinerary(Direction direction, Journey journey) {
		return Itinerary.of(direction, journey.segments().stream().map(AuroraAdapter::toSegment).toList(),
				Duration.ofMinutes(journey.durationMinutes()));
	}

	private static Segment toSegment(FlightSegment segment) {
		return new Segment(segment.carrier(), segment.carrier() + segment.flightNumber(), segment.origin(),
				segment.destination(), segment.departure(), segment.arrival(),
				Duration.ofMinutes(segment.durationMinutes()), segment.equipment());
	}

	private static PassengerFare toPassengerFare(PriceLine line) {
		return PassengerFare.of(toPassengerType(line.paxType()), line.quantity(), line.baseAmount(),
				line.taxAmount());
	}

	private static PassengerType toPassengerType(String code) {
		return switch (code) {
			case "ADT" -> PassengerType.ADULT;
			case "CHD" -> PassengerType.CHILD;
			case "INF" -> PassengerType.INFANT;
			default -> throw new IllegalArgumentException("Unknown Aurora passenger type: " + code);
		};
	}

	private static BaggageAllowance baggageFor(String fareFamily) {
		return switch (fareFamily) {
			case "LIGHT" -> new BaggageAllowance(0, 1);
			case "FLEX" -> new BaggageAllowance(2, 1);
			default -> new BaggageAllowance(1, 1);
		};
	}
}
