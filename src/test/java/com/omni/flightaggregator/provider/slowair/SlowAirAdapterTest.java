package com.omni.flightaggregator.provider.slowair;

import java.time.Duration;
import java.time.LocalDate;
import java.util.List;
import java.util.concurrent.atomic.AtomicReference;

import com.omni.flightaggregator.domain.BaggageAllowance;
import com.omni.flightaggregator.domain.CabinClass;
import com.omni.flightaggregator.domain.Direction;
import com.omni.flightaggregator.domain.FlightOffer;
import com.omni.flightaggregator.domain.FlightSearchCriteria;
import com.omni.flightaggregator.domain.Itinerary;
import com.omni.flightaggregator.domain.PassengerCounts;
import com.omni.flightaggregator.domain.PassengerType;
import com.omni.flightaggregator.domain.TripType;
import com.omni.flightaggregator.provider.slowair.SlowAirApi.Flight;
import com.omni.flightaggregator.provider.slowair.SlowAirApi.Offer;
import com.omni.flightaggregator.provider.slowair.SlowAirApi.PaxFare;
import com.omni.flightaggregator.provider.slowair.SlowAirApi.SearchRequest;
import com.omni.flightaggregator.provider.slowair.SlowAirApi.SearchResponse;
import org.junit.jupiter.api.Test;
import reactor.core.publisher.Mono;

import static org.assertj.core.api.Assertions.assertThat;

class SlowAirAdapterTest {

	private final AtomicReference<SearchRequest> sentRequest = new AtomicReference<>();

	private final SlowAirAdapter adapter = new SlowAirAdapter(request -> {
		sentRequest.set(request);
		return Mono.just(roundTripResponse());
	});

	@Test
	void translatesCriteriaIntoTheNativeRequest() {
		adapter.search(criteria(TripType.ROUND_TRIP, LocalDate.of(2026, 10, 20), new PassengerCounts(2, 1, 1)))
				.block();

		assertThat(sentRequest.get())
				.isEqualTo(new SearchRequest("JFK", "LHR", "2026-10-10", "2026-10-20", 2, 1, 1));
	}

	@Test
	void mapsTheNativeOfferIntoAStandardOffer() {
		FlightOffer offer = adapter
				.search(criteria(TripType.ROUND_TRIP, LocalDate.of(2026, 10, 20), new PassengerCounts(1, 0, 0)))
				.block().getFirst();

		assertThat(offer.id()).isEqualTo("slowair-SA1");
		assertThat(offer.provider()).isEqualTo("slowair");
		assertThat(offer.cabin()).isEqualTo(CabinClass.BUSINESS);
		assertThat(offer.baggage()).isEqualTo(new BaggageAllowance(2, 1));
		assertThat(offer.refundable()).isTrue();
		assertThat(offer.itineraries()).extracting(Itinerary::direction)
				.containsExactly(Direction.OUTBOUND, Direction.INBOUND);
		assertThat(offer.itineraries().getFirst().duration()).isEqualTo(Duration.ofHours(7));
		assertThat(offer.itineraries().getFirst().segments()).singleElement()
				.satisfies(segment -> assertThat(segment.flightNumber()).isEqualTo("SL100"));
		assertThat(offer.price().currency()).isEqualTo("USD");
		assertThat(offer.price().passengerFares()).singleElement().satisfies(adult -> {
			assertThat(adult.type()).isEqualTo(PassengerType.ADULT);
			assertThat(adult.baseFare()).isEqualByComparingTo("500.00");
			assertThat(adult.taxes()).isEqualByComparingTo("45.00");
		});
		assertThat(offer.price().total()).isEqualByComparingTo("545.00");
	}

	private static FlightSearchCriteria criteria(TripType tripType, LocalDate returnDate,
			PassengerCounts passengers) {
		return new FlightSearchCriteria("JFK", "LHR", tripType, LocalDate.of(2026, 10, 10), returnDate, passengers);
	}

	private static SearchResponse roundTripResponse() {
		Flight outbound = new Flight("SL", "SL100", "JFK", "LHR", "2026-10-10T19:00", "2026-10-11T07:00", "PT7H",
				"A350-900");
		Flight inbound = new Flight("SL", "SL101", "LHR", "JFK", "2026-10-20T10:00", "2026-10-20T13:00", "PT8H",
				"A350-900");
		return new SearchResponse(List.of(new Offer("SA1", "BUSINESS", "USD", true, 2, List.of(outbound),
				List.of(inbound), List.of(new PaxFare("ADULT", 1, 50000, 4500)))));
	}
}
