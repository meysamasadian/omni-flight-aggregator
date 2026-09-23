package com.omni.flightaggregator.provider.globaltravel;

import java.math.BigDecimal;
import java.time.Duration;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

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
import com.omni.flightaggregator.provider.globaltravel.GlobalTravelApi.FareLine;
import com.omni.flightaggregator.provider.globaltravel.GlobalTravelApi.Flight;
import com.omni.flightaggregator.provider.globaltravel.GlobalTravelApi.Query;
import com.omni.flightaggregator.provider.globaltravel.GlobalTravelApi.Result;
import com.omni.flightaggregator.provider.globaltravel.GlobalTravelApi.TravelPackage;
import com.omni.flightaggregator.provider.globaltravel.GlobalTravelApi.Trip;
import reactor.core.publisher.Mono;

import org.springframework.stereotype.Component;

/** Adapts the GlobalTravel agency API to the standard offer model. */
@Component
class GlobalTravelAdapter implements FlightProviderAdapter {

	static final String CODE = "globaltravel";

	private final GlobalTravelClient client;

	GlobalTravelAdapter(GlobalTravelClient client) {
		this.client = client;
	}

	@Override
	public String code() {
		return CODE;
	}

	@Override
	public Mono<List<FlightOffer>> search(FlightSearchCriteria criteria) {
		return client.search(toQuery(criteria))
				.map(result -> toOffers(result, criteria));
	}

	private static Query toQuery(FlightSearchCriteria criteria) {
		Map<String, Integer> travellers = new LinkedHashMap<>();
		travellers.put("adult", criteria.passengers().adults());
		travellers.put("child", criteria.passengers().children());
		travellers.put("infant", criteria.passengers().infants());
		return new Query(criteria.origin(), criteria.destination(),
				criteria.departureDate().format(GlobalTravelApi.DATE),
				criteria.isRoundTrip() ? criteria.returnDate().format(GlobalTravelApi.DATE) : null, travellers);
	}

	private static List<FlightOffer> toOffers(Result result, FlightSearchCriteria criteria) {
		return result.packages().stream().map(pkg -> toOffer(pkg, criteria)).toList();
	}

	private static FlightOffer toOffer(TravelPackage pkg, FlightSearchCriteria criteria) {
		List<Itinerary> itineraries = new ArrayList<>();
		itineraries.add(toItinerary(Direction.OUTBOUND, pkg.outbound()));
		if (pkg.inbound() != null) {
			itineraries.add(toItinerary(Direction.INBOUND, pkg.inbound()));
		}
		return new FlightOffer(CODE + "-" + pkg.code(), CODE, itineraries,
				Price.of(pkg.currency(), toPassengerFares(pkg.fareTable(), criteria)),
				CabinClass.valueOf(pkg.cabinClass().toUpperCase(Locale.ROOT)),
				new BaggageAllowance(Integer.parseInt(pkg.baggage().replace("PC", "")), 1),
				"REFUNDABLE".equals(pkg.refundPolicy()));
	}

	private static Itinerary toItinerary(Direction direction, Trip trip) {
		return Itinerary.of(direction, trip.flights().stream().map(GlobalTravelAdapter::toSegment).toList(),
				Duration.ofMinutes(trip.totalMinutes()));
	}

	private static Segment toSegment(Flight flight) {
		return new Segment(flight.airlineCode(), flight.airlineCode() + flight.number(), flight.departAirport(),
				flight.arriveAirport(), LocalDateTime.parse(flight.departLocal(), GlobalTravelApi.DATE_TIME),
				LocalDateTime.parse(flight.arriveLocal(), GlobalTravelApi.DATE_TIME),
				Duration.ofMinutes(flight.minutes()), flight.aircraft());
	}

	/** The fare table carries no counts, so they come from the search criteria. */
	private static List<PassengerFare> toPassengerFares(Map<String, FareLine> fareTable,
			FlightSearchCriteria criteria) {
		List<PassengerFare> fares = new ArrayList<>();
		for (PassengerType type : PassengerType.values()) {
			int count = criteria.passengers().count(type);
			FareLine line = fareTable.get(type.name().toLowerCase(Locale.ROOT));
			if (count > 0 && line != null) {
				fares.add(PassengerFare.of(type, count, new BigDecimal(line.base()), new BigDecimal(line.tax())));
			}
		}
		return fares;
	}
}
