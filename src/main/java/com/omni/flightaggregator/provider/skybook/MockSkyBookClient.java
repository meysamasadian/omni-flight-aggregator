package com.omni.flightaggregator.provider.skybook;

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
import com.omni.flightaggregator.provider.skybook.SkyBookApi.Fare;
import com.omni.flightaggregator.provider.skybook.SkyBookApi.Leg;
import com.omni.flightaggregator.provider.skybook.SkyBookApi.PaxFare;
import com.omni.flightaggregator.provider.skybook.SkyBookApi.SearchRequest;
import com.omni.flightaggregator.provider.skybook.SkyBookApi.SearchResponse;
import reactor.core.publisher.Mono;

import org.springframework.stereotype.Component;

@Component
class MockSkyBookClient implements SkyBookClient {

	static final String PROVIDER = "skybook";

	private static final List<String> CARRIERS = List.of("BA", "LH", "AF", "KL");

	private static final List<String> CABINS = List.of("Y", "Y", "Y", "W", "C");

	private final Behavior behavior;

	MockSkyBookClient(MockProviderProperties properties) {
		this.behavior = properties.skybook();
	}

	@Override
	public Mono<SearchResponse> search(SearchRequest request) {
		return SimulatedNetwork.call(PROVIDER, behavior, () -> respond(request));
	}

	private SearchResponse respond(SearchRequest request) {
		LocalDate departDate = LocalDate.parse(request.departDate());
		List<MockJourney> outbound = MockFlightGenerator.journeys(PROVIDER, request.from(), request.to(),
				departDate, CARRIERS, 4);
		List<MockJourney> inbound = request.returnDate() == null ? List.of()
				: MockFlightGenerator.journeys(PROVIDER, request.to(), request.from(),
						LocalDate.parse(request.returnDate()), CARRIERS, 4);

		Random random = MockFlightGenerator.random(PROVIDER + "-fares", request.from(), request.to(), departDate);
		long routeBase = MockPricing.routeBaseCents(request.from(), request.to());
		List<Fare> fares = new ArrayList<>();
		for (int i = 0; i < outbound.size(); i++) {
			String cabin = CABINS.get(random.nextInt(CABINS.size()));
			long adultBase = routeBase * (90 + random.nextInt(41)) / 100 * cabinMultiplier(cabin) / 10;
			List<Leg> legs = new ArrayList<>(toLegs(outbound.get(i), 0));
			if (!inbound.isEmpty()) {
				legs.addAll(toLegs(inbound.get(i), 1));
			}
			fares.add(new Fare("SB" + (1000 + random.nextInt(9000)), cabin, "USD", !"Y".equals(cabin),
					"Y".equals(cabin) ? random.nextInt(2) : 2, legs, paxFares(request, adultBase)));
		}
		return new SearchResponse("SKB-" + Math.abs(random.nextInt()), fares);
	}

	private static List<Leg> toLegs(MockJourney journey, int direction) {
		return journey.legs().stream()
				.map(leg -> new Leg(direction, leg.carrier(), leg.flightNumber().substring(leg.carrier().length()),
						leg.from(), leg.to(), leg.departure().toString(), leg.arrival().toString(),
						leg.duration().toString(), leg.aircraft()))
				.toList();
	}

	private static List<PaxFare> paxFares(SearchRequest request, long adultBase) {
		List<PaxFare> paxFares = new ArrayList<>();
		addPaxFare(paxFares, "ADT", PassengerType.ADULT, request.adt(), adultBase);
		addPaxFare(paxFares, "CHD", PassengerType.CHILD, request.chd(), adultBase);
		addPaxFare(paxFares, "INF", PassengerType.INFANT, request.inf(), adultBase);
		return paxFares;
	}

	private static void addPaxFare(List<PaxFare> paxFares, String code, PassengerType type, int count,
			long adultBase) {
		if (count > 0) {
			UnitFare fare = MockPricing.unitFare(type, adultBase);
			paxFares.add(new PaxFare(code, count, fare.baseCents(), fare.taxCents()));
		}
	}

	/** Price multiplier in tenths: premium economy 1.6x, business 3.2x. */
	private static long cabinMultiplier(String cabin) {
		return switch (cabin) {
			case "W" -> 16;
			case "C" -> 32;
			default -> 10;
		};
	}
}
