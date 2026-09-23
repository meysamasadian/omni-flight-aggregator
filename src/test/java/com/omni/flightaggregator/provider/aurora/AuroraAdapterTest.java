package com.omni.flightaggregator.provider.aurora;

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
import com.omni.flightaggregator.provider.aurora.AuroraApi.FlightSegment;
import com.omni.flightaggregator.provider.aurora.AuroraApi.Journey;
import com.omni.flightaggregator.provider.aurora.AuroraApi.JourneyQuery;
import com.omni.flightaggregator.provider.aurora.AuroraApi.Offer;
import com.omni.flightaggregator.provider.aurora.AuroraApi.OfferRequest;
import com.omni.flightaggregator.provider.aurora.AuroraApi.OfferResponse;
import com.omni.flightaggregator.provider.aurora.AuroraApi.PaxQuantity;
import com.omni.flightaggregator.provider.aurora.AuroraApi.PriceLine;
import org.junit.jupiter.api.Test;
import reactor.core.publisher.Mono;

import static org.assertj.core.api.Assertions.assertThat;

class AuroraAdapterTest {

	private static final LocalDate DEPART = LocalDate.of(2026, 10, 10);

	private static final LocalDate RETURN = LocalDate.of(2026, 10, 20);

	private final AtomicReference<OfferRequest> sentRequest = new AtomicReference<>();

	private final AuroraAdapter adapter = new AuroraAdapter(request -> {
		sentRequest.set(request);
		return Mono.just(response());
	});

	@Test
	void sendsOneJourneyQueryPerDirection() {
		adapter.search(new FlightSearchCriteria("JFK", "LHR", TripType.ROUND_TRIP, DEPART, RETURN,
				new PassengerCounts(1, 2, 0))).block();

		assertThat(sentRequest.get().journeys()).containsExactly(new JourneyQuery("JFK", "LHR", DEPART),
				new JourneyQuery("LHR", "JFK", RETURN));
		assertThat(sentRequest.get().passengers()).containsExactly(new PaxQuantity("ADT", 1),
				new PaxQuantity("CHD", 2), new PaxQuantity("INF", 0));
	}

	@Test
	void sendsASingleJourneyQueryForOneWay() {
		adapter.search(new FlightSearchCriteria("JFK", "LHR", TripType.ONE_WAY, DEPART, null,
				new PassengerCounts(1, 0, 0))).block();

		assertThat(sentRequest.get().journeys()).containsExactly(new JourneyQuery("JFK", "LHR", DEPART));
	}

	@Test
	void mapsJourneysToOutboundThenInboundItineraries() {
		FlightOffer offer = adapter.search(roundTrip()).block().getFirst();

		assertThat(offer.id()).isEqualTo("aurora-AU-1");
		assertThat(offer.provider()).isEqualTo("aurora");
		assertThat(offer.cabin()).isEqualTo(CabinClass.ECONOMY);
		assertThat(offer.itineraries()).satisfiesExactly(
				outbound -> {
					assertThat(outbound.direction()).isEqualTo(Direction.OUTBOUND);
					assertThat(outbound.duration()).isEqualTo(Duration.ofMinutes(420));
					assertThat(outbound.segments()).singleElement().satisfies(segment -> {
						assertThat(segment.flightNumber()).isEqualTo("AU2401");
						assertThat(segment.departure()).isEqualTo(LocalDateTime.of(2026, 10, 10, 9, 0));
						assertThat(segment.duration()).isEqualTo(Duration.ofMinutes(420));
						assertThat(segment.aircraft()).isEqualTo("A350-900");
					});
				},
				inbound -> assertThat(inbound.direction()).isEqualTo(Direction.INBOUND));
	}

	@Test
	void derivesBaggageAndRefundabilityFromTheFareFamily() {
		List<FlightOffer> offers = adapter.search(roundTrip()).block();

		assertThat(offers).extracting(FlightOffer::baggage)
				.containsExactly(new BaggageAllowance(2, 1), new BaggageAllowance(0, 1));
		assertThat(offers).extracting(FlightOffer::refundable).containsExactly(true, false);
	}

	@Test
	void mapsPriceLinesToPassengerFares() {
		FlightOffer offer = adapter.search(roundTrip()).block().getFirst();

		assertThat(offer.price().currency()).isEqualTo("EUR");
		assertThat(offer.price().passengerFares()).satisfiesExactly(
				adult -> {
					assertThat(adult.type()).isEqualTo(PassengerType.ADULT);
					assertThat(adult.totalForType()).isEqualByComparingTo("345.00");
				},
				child -> {
					assertThat(child.type()).isEqualTo(PassengerType.CHILD);
					assertThat(child.count()).isEqualTo(2);
					assertThat(child.totalForType()).isEqualByComparingTo("540.00");
				});
		assertThat(offer.price().total()).isEqualByComparingTo("885.00");
	}

	private static FlightSearchCriteria roundTrip() {
		return new FlightSearchCriteria("JFK", "LHR", TripType.ROUND_TRIP, DEPART, RETURN,
				new PassengerCounts(1, 2, 0));
	}

	private static OfferResponse response() {
		Journey outbound = new Journey("J1", 420, List.of(new FlightSegment("AU", "2401", "JFK", "LHR",
				LocalDateTime.of(2026, 10, 10, 9, 0), LocalDateTime.of(2026, 10, 10, 16, 0), 420, "A350-900")));
		Journey inbound = new Journey("J2", 480, List.of(new FlightSegment("AU", "2402", "LHR", "JFK",
				LocalDateTime.of(2026, 10, 20, 11, 0), LocalDateTime.of(2026, 10, 20, 19, 0), 480, "A350-900")));
		List<PriceLine> priceLines = List.of(
				new PriceLine("ADT", 1, new BigDecimal("300.00"), new BigDecimal("45.00")),
				new PriceLine("CHD", 2, new BigDecimal("225.00"), new BigDecimal("45.00")));
		return new OfferResponse("AUR-1", List.of(
				new Offer("AU-1", "FLEX", "EUR", List.of(outbound, inbound), priceLines),
				new Offer("AU-2", "LIGHT", "EUR", List.of(outbound, inbound), priceLines)));
	}
}
