package com.omni.flightaggregator.provider.aurora;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;
import java.util.Random;

import com.omni.flightaggregator.domain.PassengerType;
import com.omni.flightaggregator.provider.aurora.AuroraApi.FlightSegment;
import com.omni.flightaggregator.provider.aurora.AuroraApi.Journey;
import com.omni.flightaggregator.provider.aurora.AuroraApi.JourneyQuery;
import com.omni.flightaggregator.provider.aurora.AuroraApi.Offer;
import com.omni.flightaggregator.provider.aurora.AuroraApi.OfferRequest;
import com.omni.flightaggregator.provider.aurora.AuroraApi.OfferResponse;
import com.omni.flightaggregator.provider.aurora.AuroraApi.PaxQuantity;
import com.omni.flightaggregator.provider.aurora.AuroraApi.PriceLine;
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
class MockAuroraClient implements AuroraClient {

	static final String PROVIDER = "aurora";

	private static final List<String> CARRIERS = List.of("AU");

	private static final List<String> FARE_FAMILIES = List.of("LIGHT", "CLASSIC", "FLEX");

	private final Behavior behavior;

	MockAuroraClient(MockProviderProperties properties) {
		this.behavior = properties.aurora();
	}

	@Override
	public Mono<OfferResponse> searchOffers(OfferRequest request) {
		return SimulatedNetwork.call(PROVIDER, behavior, () -> respond(request));
	}

	private OfferResponse respond(OfferRequest request) {
		JourneyQuery first = request.journeys().getFirst();
		List<List<MockJourney>> journeysPerQuery = request.journeys().stream()
				.map(query -> MockFlightGenerator.journeys(PROVIDER, query.origin(), query.destination(),
						query.date(), CARRIERS, 3))
				.toList();

		Random random = MockFlightGenerator.random(PROVIDER + "-fares", first.origin(), first.destination(),
				first.date());
		long routeBase = MockPricing.routeBaseCents(first.origin(), first.destination());
		List<Offer> offers = new ArrayList<>();
		for (int i = 0; i < journeysPerQuery.getFirst().size(); i++) {
			String family = FARE_FAMILIES.get(i % FARE_FAMILIES.size());
			long adultBase = routeBase * (90 + random.nextInt(31)) / 100 * familyMultiplier(family) / 100;
			List<Journey> journeys = new ArrayList<>();
			for (List<MockJourney> perQuery : journeysPerQuery) {
				journeys.add(toJourney(perQuery.get(i), random));
			}
			offers.add(new Offer("AU-" + (100000 + random.nextInt(900000)), family, "EUR", journeys,
					priceLines(request.passengers(), adultBase)));
		}
		return new OfferResponse("AUR-" + Math.abs(random.nextInt()), offers);
	}

	private static Journey toJourney(MockJourney journey, Random random) {
		List<FlightSegment> segments = journey.legs().stream()
				.map(leg -> new FlightSegment(leg.carrier(), leg.flightNumber().substring(leg.carrier().length()),
						leg.from(), leg.to(), leg.departure(), leg.arrival(), (int) leg.duration().toMinutes(),
						leg.aircraft()))
				.toList();
		return new Journey("J" + (1000 + random.nextInt(9000)), (int) journey.totalDuration().toMinutes(), segments);
	}

	private static List<PriceLine> priceLines(List<PaxQuantity> passengers, long adultBase) {
		List<PriceLine> lines = new ArrayList<>();
		for (PaxQuantity pax : passengers) {
			if (pax.quantity() > 0) {
				UnitFare fare = MockPricing.unitFare(passengerType(pax.type()), adultBase);
				lines.add(new PriceLine(pax.type(), pax.quantity(), BigDecimal.valueOf(fare.baseCents(), 2),
						BigDecimal.valueOf(fare.taxCents(), 2)));
			}
		}
		return lines;
	}

	private static PassengerType passengerType(String code) {
		return switch (code) {
			case "ADT" -> PassengerType.ADULT;
			case "CHD" -> PassengerType.CHILD;
			default -> PassengerType.INFANT;
		};
	}

	/** Price multiplier in percent. */
	private static long familyMultiplier(String family) {
		return switch (family) {
			case "LIGHT" -> 85;
			case "FLEX" -> 135;
			default -> 100;
		};
	}
}
