package com.omni.flightaggregator.provider.globaltravel;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Random;

import com.omni.flightaggregator.domain.PassengerType;
import com.omni.flightaggregator.provider.globaltravel.GlobalTravelApi.FareLine;
import com.omni.flightaggregator.provider.globaltravel.GlobalTravelApi.Flight;
import com.omni.flightaggregator.provider.globaltravel.GlobalTravelApi.Query;
import com.omni.flightaggregator.provider.globaltravel.GlobalTravelApi.Result;
import com.omni.flightaggregator.provider.globaltravel.GlobalTravelApi.TravelPackage;
import com.omni.flightaggregator.provider.globaltravel.GlobalTravelApi.Trip;
import com.omni.flightaggregator.provider.mock.MockFlightGenerator;
import com.omni.flightaggregator.provider.mock.MockFlightGenerator.MockJourney;
import com.omni.flightaggregator.provider.mock.MockPricing;
import com.omni.flightaggregator.provider.mock.MockPricing.UnitFare;
import com.omni.flightaggregator.provider.mock.MockProviderProperties;
import com.omni.flightaggregator.provider.mock.MockProviderProperties.Behavior;
import com.omni.flightaggregator.provider.mock.SimulatedNetwork;
import reactor.core.publisher.Mono;

import org.springframework.stereotype.Component;

@Component
class MockGlobalTravelClient implements GlobalTravelClient {

	static final String PROVIDER = "globaltravel";

	private static final List<String> CARRIERS = List.of("IB", "TK", "EK", "QR");

	private static final List<String> CABINS = List.of("economy", "economy", "economy", "premium_economy",
			"business");

	private final Behavior behavior;

	MockGlobalTravelClient(MockProviderProperties properties) {
		this.behavior = properties.globaltravel();
	}

	@Override
	public Mono<Result> search(Query query) {
		return SimulatedNetwork.call(PROVIDER, behavior, () -> respond(query));
	}

	private Result respond(Query query) {
		LocalDate outboundDate = LocalDate.parse(query.outboundDate(), GlobalTravelApi.DATE);
		List<MockJourney> outbound = MockFlightGenerator.journeys(PROVIDER, query.origin(), query.destination(),
				outboundDate, CARRIERS, 5);
		List<MockJourney> inbound = query.inboundDate() == null ? List.of()
				: MockFlightGenerator.journeys(PROVIDER, query.destination(), query.origin(),
						LocalDate.parse(query.inboundDate(), GlobalTravelApi.DATE), CARRIERS, 5);

		Random random = MockFlightGenerator.random(PROVIDER + "-fares", query.origin(), query.destination(),
				outboundDate);
		long routeBase = MockPricing.routeBaseCents(query.origin(), query.destination());
		List<TravelPackage> packages = new ArrayList<>();
		for (int i = 0; i < outbound.size(); i++) {
			String cabin = CABINS.get(random.nextInt(CABINS.size()));
			long adultBase = routeBase * (85 + random.nextInt(41)) / 100 * cabinMultiplier(cabin) / 10;
			int bags = "economy".equals(cabin) ? random.nextInt(3) : 2;
			packages.add(new TravelPackage("GT-" + (10000 + random.nextInt(90000)), toTrip(outbound.get(i)),
					inbound.isEmpty() ? null : toTrip(inbound.get(i)), "EUR", cabin, bags + "PC",
					bags == 2 ? "REFUNDABLE" : "NON_REFUNDABLE", fareTable(query, adultBase)));
		}
		return new Result("gt-" + Math.abs(random.nextLong()), packages);
	}

	private static Trip toTrip(MockJourney journey) {
		List<Flight> flights = journey.legs().stream()
				.map(leg -> new Flight(leg.carrier(), leg.flightNumber().substring(leg.carrier().length()),
						leg.from(), leg.to(), leg.departure().format(GlobalTravelApi.DATE_TIME),
						leg.arrival().format(GlobalTravelApi.DATE_TIME), (int) leg.duration().toMinutes(),
						leg.aircraft()))
				.toList();
		return new Trip(flights, (int) journey.totalDuration().toMinutes());
	}

	private static Map<String, FareLine> fareTable(Query query, long adultBase) {
		Map<String, FareLine> table = new LinkedHashMap<>();
		addFare(table, query, "adult", PassengerType.ADULT, adultBase);
		addFare(table, query, "child", PassengerType.CHILD, adultBase);
		addFare(table, query, "infant", PassengerType.INFANT, adultBase);
		return table;
	}

	private static void addFare(Map<String, FareLine> table, Query query, String key, PassengerType type,
			long adultBase) {
		if (query.travellers().getOrDefault(key, 0) > 0) {
			UnitFare fare = MockPricing.unitFare(type, adultBase);
			table.put(key, new FareLine(BigDecimal.valueOf(fare.baseCents(), 2).toPlainString(),
					BigDecimal.valueOf(fare.taxCents(), 2).toPlainString()));
		}
	}

	/** Price multiplier in tenths: premium economy 1.6x, business 3.2x. */
	private static long cabinMultiplier(String cabin) {
		return switch (cabin) {
			case "premium_economy" -> 16;
			case "business" -> 32;
			default -> 10;
		};
	}
}
