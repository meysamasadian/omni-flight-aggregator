package com.omni.flightaggregator.provider.globaltravel;

import java.time.Duration;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicReference;

import com.omni.flightaggregator.domain.BaggageAllowance;
import com.omni.flightaggregator.domain.CabinClass;
import com.omni.flightaggregator.domain.Direction;
import com.omni.flightaggregator.domain.FlightOffer;
import com.omni.flightaggregator.domain.FlightSearchCriteria;
import com.omni.flightaggregator.domain.PassengerCounts;
import com.omni.flightaggregator.domain.PassengerType;
import com.omni.flightaggregator.domain.TripType;
import com.omni.flightaggregator.provider.globaltravel.GlobalTravelApi.FareLine;
import com.omni.flightaggregator.provider.globaltravel.GlobalTravelApi.Flight;
import com.omni.flightaggregator.provider.globaltravel.GlobalTravelApi.Query;
import com.omni.flightaggregator.provider.globaltravel.GlobalTravelApi.Result;
import com.omni.flightaggregator.provider.globaltravel.GlobalTravelApi.TravelPackage;
import com.omni.flightaggregator.provider.globaltravel.GlobalTravelApi.Trip;
import org.junit.jupiter.api.Test;
import reactor.core.publisher.Mono;

import static org.assertj.core.api.Assertions.assertThat;

class GlobalTravelAdapterTest {

	private static final FlightSearchCriteria ROUND_TRIP = new FlightSearchCriteria("JFK", "LHR",
			TripType.ROUND_TRIP, LocalDate.of(2026, 10, 10), LocalDate.of(2026, 10, 20), new PassengerCounts(1, 0, 1));

	private final AtomicReference<Query> sentQuery = new AtomicReference<>();

	private final GlobalTravelAdapter adapter = new GlobalTravelAdapter(query -> {
		sentQuery.set(query);
		return Mono.just(result());
	});

	@Test
	void translatesRoundTripCriteriaIntoTheNativeQuery() {
		adapter.search(ROUND_TRIP).block();

		assertThat(sentQuery.get()).isEqualTo(new Query("JFK", "LHR", "20261010", "20261020",
				Map.of("adult", 1, "child", 0, "infant", 1)));
	}

	@Test
	void omitsTheInboundDateForOneWay() {
		adapter.search(new FlightSearchCriteria("JFK", "LHR", TripType.ONE_WAY, LocalDate.of(2026, 10, 10), null,
				new PassengerCounts(1, 0, 0))).block();

		assertThat(sentQuery.get().inboundDate()).isNull();
	}

	@Test
	void mapsBothTripsIntoOutboundAndInboundItineraries() {
		FlightOffer offer = adapter.search(ROUND_TRIP).block().getFirst();

		assertThat(offer.id()).isEqualTo("globaltravel-GT-1");
		assertThat(offer.provider()).isEqualTo("globaltravel");
		assertThat(offer.itineraries()).satisfiesExactly(
				outbound -> {
					assertThat(outbound.direction()).isEqualTo(Direction.OUTBOUND);
					assertThat(outbound.stops()).isEqualTo(1);
					assertThat(outbound.duration()).isEqualTo(Duration.ofMinutes(985));
					assertThat(outbound.segments()).hasSize(2);
					assertThat(outbound.segments().getFirst().flightNumber()).isEqualTo("IB3171");
					assertThat(outbound.segments().getFirst().departure())
							.isEqualTo(LocalDateTime.of(2026, 10, 10, 18, 30));
					assertThat(outbound.segments().getFirst().arrival())
							.isEqualTo(LocalDateTime.of(2026, 10, 11, 6, 35));
					assertThat(outbound.segments().getFirst().duration()).isEqualTo(Duration.ofMinutes(425));
				},
				inbound -> {
					assertThat(inbound.direction()).isEqualTo(Direction.INBOUND);
					assertThat(inbound.stops()).isZero();
					assertThat(inbound.segments().getFirst().origin()).isEqualTo("LHR");
				});
	}

	@Test
	void mapsFareAttributesAndPricesOnlyTheTravellersInTheSearch() {
		FlightOffer offer = adapter.search(ROUND_TRIP).block().getFirst();

		assertThat(offer.cabin()).isEqualTo(CabinClass.PREMIUM_ECONOMY);
		assertThat(offer.baggage()).isEqualTo(new BaggageAllowance(2, 1));
		assertThat(offer.refundable()).isTrue();
		assertThat(offer.price().currency()).isEqualTo("EUR");
		assertThat(offer.price().passengerFares()).satisfiesExactly(
				adult -> {
					assertThat(adult.type()).isEqualTo(PassengerType.ADULT);
					assertThat(adult.totalForType()).isEqualByComparingTo("290.50");
				},
				infant -> {
					assertThat(infant.type()).isEqualTo(PassengerType.INFANT);
					assertThat(infant.totalForType()).isEqualByComparingTo("34.55");
				});
		assertThat(offer.price().total()).isEqualByComparingTo("325.05");
	}

	private static Result result() {
		Trip outbound = new Trip(List.of(
				new Flight("IB", "3171", "JFK", "MAD", "10/10/2026 18:30", "11/10/2026 06:35", 425, "A330-300"),
				new Flight("IB", "3166", "MAD", "LHR", "11/10/2026 09:00", "11/10/2026 10:55", 175, "A320")), 985);
		Trip inbound = new Trip(List.of(
				new Flight("IB", "3200", "LHR", "JFK", "20/10/2026 11:00", "20/10/2026 14:00", 480, "B787-9")), 480);
		TravelPackage travelPackage = new TravelPackage("GT-1", outbound, inbound, "EUR", "premium_economy", "2PC",
				"REFUNDABLE", Map.of("adult", new FareLine("245.50", "45.00"), "infant", new FareLine("24.55", "10.00")));
		return new Result("gt-token", List.of(travelPackage));
	}
}
