package com.omni.flightaggregator.provider.slowair;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.Random;

import com.omni.flightaggregator.domain.PassengerType;
import com.omni.flightaggregator.provider.mock.MockFlightGenerator;
import com.omni.flightaggregator.provider.mock.MockFlightGenerator.MockJourney;
import com.omni.flightaggregator.provider.mock.MockPricing;
import com.omni.flightaggregator.provider.mock.MockPricing.UnitFare;
import com.omni.flightaggregator.provider.mock.MockProviderProperties;
import com.omni.flightaggregator.provider.mock.MockProviderProperties.Behavior;
import com.omni.flightaggregator.provider.mock.SimulatedNetwork;
import com.omni.flightaggregator.provider.slowair.SlowAirApi.Flight;
import com.omni.flightaggregator.provider.slowair.SlowAirApi.Offer;
import com.omni.flightaggregator.provider.slowair.SlowAirApi.PaxFare;
import com.omni.flightaggregator.provider.slowair.SlowAirApi.SearchRequest;
import com.omni.flightaggregator.provider.slowair.SlowAirApi.SearchResponse;
import reactor.core.publisher.Mono;

import org.springframework.boot.autoconfigure.condition.ConditionalOnBooleanProperty;
import org.springframework.stereotype.Component;

@Component
@ConditionalOnBooleanProperty(SlowAirAdapter.ENABLED_PROPERTY)
class MockSlowAirClient implements SlowAirClient {

	static final String PROVIDER = "slowair";

	private static final List<String> CARRIERS = List.of("SL", "SW");

	private final Behavior behavior;

	MockSlowAirClient(MockProviderProperties properties) {
		this.behavior = properties.slowair();
	}

	@Override
	public Mono<SearchResponse> search(SearchRequest request) {
		return SimulatedNetwork.call(PROVIDER, behavior, () -> respond(request));
	}

	private SearchResponse respond(SearchRequest request) {
		LocalDate date = LocalDate.parse(request.date());
		List<MockJourney> outbound = MockFlightGenerator.journeys(PROVIDER, request.origin(), request.destination(),
				date, CARRIERS, 3);
		List<MockJourney> inbound = request.returnDate() == null ? List.of()
				: MockFlightGenerator.journeys(PROVIDER, request.destination(), request.origin(),
						LocalDate.parse(request.returnDate()), CARRIERS, 3);

		Random random = MockFlightGenerator.random(PROVIDER + "-fares", request.origin(), request.destination(), date);
		long routeBase = MockPricing.routeBaseCents(request.origin(), request.destination());
		List<Offer> offers = new ArrayList<>();
		for (int i = 0; i < outbound.size(); i++) {
			boolean business = random.nextInt(4) == 0;
			long adultBase = routeBase * (80 + random.nextInt(41)) / 100 * (business ? 3 : 1);
			offers.add(new Offer("SA" + (i + 1) + "-" + (1000 + random.nextInt(9000)),
					business ? "BUSINESS" : "ECONOMY", "USD", business, business ? 2 : 1,
					toFlights(outbound.get(i)), inbound.isEmpty() ? List.of() : toFlights(inbound.get(i)),
					paxFares(request, adultBase)));
		}
		return new SearchResponse(offers);
	}

	private static List<Flight> toFlights(MockJourney journey) {
		return journey.legs().stream()
				.map(leg -> new Flight(leg.carrier(), leg.flightNumber(), leg.from(), leg.to(),
						leg.departure().toString(), leg.arrival().toString(), leg.duration().toString(),
						leg.aircraft()))
				.toList();
	}

	private static List<PaxFare> paxFares(SearchRequest request, long adultBase) {
		List<PaxFare> paxFares = new ArrayList<>();
		addPaxFare(paxFares, PassengerType.ADULT, request.adults(), adultBase);
		addPaxFare(paxFares, PassengerType.CHILD, request.children(), adultBase);
		addPaxFare(paxFares, PassengerType.INFANT, request.infants(), adultBase);
		return paxFares;
	}

	private static void addPaxFare(List<PaxFare> paxFares, PassengerType type, int count, long adultBase) {
		if (count > 0) {
			UnitFare fare = MockPricing.unitFare(type, adultBase);
			paxFares.add(new PaxFare(type.name(), count, fare.baseCents(), fare.taxCents()));
		}
	}
}
