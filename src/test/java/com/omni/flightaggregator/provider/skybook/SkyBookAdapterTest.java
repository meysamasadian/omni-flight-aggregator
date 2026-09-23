package com.omni.flightaggregator.provider.skybook;

import java.math.BigDecimal;
import java.time.Duration;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import java.util.concurrent.atomic.AtomicReference;

import com.omni.flightaggregator.domain.BaggageAllowance;
import com.omni.flightaggregator.domain.CabinClass;
import com.omni.flightaggregator.domain.Direction;
import com.omni.flightaggregator.domain.FlightOffer;
import com.omni.flightaggregator.domain.FlightSearchCriteria;
import com.omni.flightaggregator.domain.PassengerCounts;
import com.omni.flightaggregator.domain.PassengerType;
import com.omni.flightaggregator.domain.TripType;
import com.omni.flightaggregator.provider.skybook.SkyBookApi.Fare;
import com.omni.flightaggregator.provider.skybook.SkyBookApi.Leg;
import com.omni.flightaggregator.provider.skybook.SkyBookApi.PaxFare;
import com.omni.flightaggregator.provider.skybook.SkyBookApi.SearchRequest;
import com.omni.flightaggregator.provider.skybook.SkyBookApi.SearchResponse;
import org.junit.jupiter.api.Test;
import reactor.core.publisher.Mono;

import static org.assertj.core.api.Assertions.assertThat;

class SkyBookAdapterTest {

	private final AtomicReference<SearchRequest> sentRequest = new AtomicReference<>();

	private SearchResponse nativeResponse = singleLegResponse();

	private final SkyBookAdapter adapter = new SkyBookAdapter(request -> {
		sentRequest.set(request);
		return Mono.just(nativeResponse);
	});

	@Test
	void translatesOneWayCriteriaIntoTheNativeRequest() {
		adapter.search(criteria(TripType.ONE_WAY, null, new PassengerCounts(2, 1, 0))).block();

		assertThat(sentRequest.get()).isEqualTo(new SearchRequest("JFK", "LHR", "2026-10-10", null, 2, 1, 0));
	}

	@Test
	void translatesRoundTripCriteriaIntoTheNativeRequest() {
		adapter.search(criteria(TripType.ROUND_TRIP, LocalDate.of(2026, 10, 20), new PassengerCounts(1, 0, 1)))
				.block();

		assertThat(sentRequest.get())
				.isEqualTo(new SearchRequest("JFK", "LHR", "2026-10-10", "2026-10-20", 1, 0, 1));
	}

	@Test
	void mapsTheNativeFareIntoAStandardOffer() {
		List<FlightOffer> offers = adapter.search(criteria(TripType.ONE_WAY, null, new PassengerCounts(2, 1, 0)))
				.block();

		assertThat(offers).hasSize(1);
		FlightOffer offer = offers.getFirst();
		assertThat(offer.id()).isEqualTo("skybook-SB1");
		assertThat(offer.provider()).isEqualTo("skybook");
		assertThat(offer.cabin()).isEqualTo(CabinClass.PREMIUM_ECONOMY);
		assertThat(offer.baggage()).isEqualTo(new BaggageAllowance(2, 1));
		assertThat(offer.refundable()).isTrue();

		assertThat(offer.itineraries()).hasSize(1);
		assertThat(offer.itineraries().getFirst().direction()).isEqualTo(Direction.OUTBOUND);
		assertThat(offer.itineraries().getFirst().stops()).isZero();
		assertThat(offer.itineraries().getFirst().duration()).isEqualTo(Duration.ofHours(7).plusMinutes(5));
		assertThat(offer.itineraries().getFirst().segments()).singleElement().satisfies(segment -> {
			assertThat(segment.carrier()).isEqualTo("BA");
			assertThat(segment.flightNumber()).isEqualTo("BA117");
			assertThat(segment.origin()).isEqualTo("JFK");
			assertThat(segment.destination()).isEqualTo("LHR");
			assertThat(segment.departure()).isEqualTo(LocalDateTime.of(2026, 10, 10, 19, 0));
			assertThat(segment.arrival()).isEqualTo(LocalDateTime.of(2026, 10, 11, 7, 5));
			assertThat(segment.aircraft()).isEqualTo("B777-300");
		});
	}

	@Test
	void journeyDurationAddsFlightTimesAndLayoversInsteadOfComparingLocalClockTimes() {
		// JFK 18:00 -> CDG 07:30 (+1d, six hours ahead) is 7h30 in the air; a 2h layover at CDG;
		// CDG 09:30 -> LHR 09:50 is 1h20 in the air (one hour behind)
		nativeResponse = new SearchResponse("SKB-2", List.of(new Fare("SB2", "Y", "USD", false, 1, List.of(
				new Leg(0, "BA", "303", "JFK", "CDG", "2026-10-10T18:00", "2026-10-11T07:30", "PT7H30M", "A330-300"),
				new Leg(0, "BA", "304", "CDG", "LHR", "2026-10-11T09:30", "2026-10-11T09:50", "PT1H20M", "A320")),
				List.of(new PaxFare("ADT", 1, 50000, 4500)))));

		FlightOffer offer = adapter.search(criteria(TripType.ONE_WAY, null, new PassengerCounts(1, 0, 0)))
				.block().getFirst();

		assertThat(offer.itineraries().getFirst().stops()).isEqualTo(1);
		assertThat(offer.itineraries().getFirst().duration()).isEqualTo(Duration.ofHours(10).plusMinutes(50));
	}

	@Test
	void convertsCentsIntoDecimalAmountsPerPassengerType() {
		FlightOffer offer = adapter.search(criteria(TripType.ONE_WAY, null, new PassengerCounts(2, 1, 0)))
				.block().getFirst();

		assertThat(offer.price().currency()).isEqualTo("USD");
		assertThat(offer.price().passengerFares()).satisfiesExactly(
				adult -> {
					assertThat(adult.type()).isEqualTo(PassengerType.ADULT);
					assertThat(adult.count()).isEqualTo(2);
					assertThat(adult.baseFare()).isEqualByComparingTo("612.38");
					assertThat(adult.taxes()).isEqualByComparingTo("45.00");
					assertThat(adult.totalPerPassenger()).isEqualByComparingTo("657.38");
					assertThat(adult.totalForType()).isEqualByComparingTo("1314.76");
				},
				child -> {
					assertThat(child.type()).isEqualTo(PassengerType.CHILD);
					assertThat(child.count()).isEqualTo(1);
					assertThat(child.totalForType()).isEqualByComparingTo("504.28");
				});
		assertThat(offer.price().total()).isEqualByComparingTo(new BigDecimal("1819.04"));
	}

	private static FlightSearchCriteria criteria(TripType tripType, LocalDate returnDate,
			PassengerCounts passengers) {
		return new FlightSearchCriteria("JFK", "LHR", tripType, LocalDate.of(2026, 10, 10), returnDate, passengers);
	}

	private static SearchResponse singleLegResponse() {
		Leg outbound = new Leg(0, "BA", "117", "JFK", "LHR", "2026-10-10T19:00", "2026-10-11T07:05", "PT7H5M",
				"B777-300");
		Fare fare = new Fare("SB1", "W", "USD", true, 2, List.of(outbound),
				List.of(new PaxFare("ADT", 2, 61238, 4500), new PaxFare("CHD", 1, 45928, 4500)));
		return new SearchResponse("SKB-1", List.of(fare));
	}
}
