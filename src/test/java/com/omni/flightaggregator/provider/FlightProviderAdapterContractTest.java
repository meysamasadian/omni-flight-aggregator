package com.omni.flightaggregator.provider;

import java.math.BigDecimal;
import java.time.Duration;
import java.time.LocalDate;
import java.util.List;

import com.omni.flightaggregator.domain.Direction;
import com.omni.flightaggregator.domain.FlightOffer;
import com.omni.flightaggregator.domain.FlightSearchCriteria;
import com.omni.flightaggregator.domain.Itinerary;
import com.omni.flightaggregator.domain.PassengerCounts;
import com.omni.flightaggregator.domain.PassengerFare;
import com.omni.flightaggregator.domain.PassengerType;
import com.omni.flightaggregator.domain.TripType;
import org.junit.jupiter.api.Test;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Every {@link FlightProviderAdapter} bean, including any added later, must produce well-formed standard
 * offers, whatever its provider's native format looks like.
 */
@SpringBootTest
@ActiveProfiles("test")
class FlightProviderAdapterContractTest {

	private static final LocalDate DEPART = LocalDate.now().plusDays(30);

	private static final LocalDate RETURN = DEPART.plusDays(10);

	@Autowired
	private List<FlightProviderAdapter> adapters;

	@Test
	void allMockedProvidersAreDiscovered() {
		assertThat(adapters).extracting(FlightProviderAdapter::code)
				.containsExactlyInAnyOrder("skybook", "globaltravel", "aurora");
	}

	@Test
	void oneWaySearchYieldsOutboundOnlyOffers() {
		FlightSearchCriteria criteria = new FlightSearchCriteria("JFK", "LHR", TripType.ONE_WAY, DEPART, null,
				new PassengerCounts(2, 1, 1));

		for (FlightProviderAdapter adapter : adapters) {
			List<FlightOffer> offers = search(adapter, criteria);
			for (FlightOffer offer : offers) {
				assertThat(offer.itineraries()).as(adapter.code()).extracting(Itinerary::direction)
						.containsExactly(Direction.OUTBOUND);
				assertWellFormed(adapter, offer, criteria);
			}
		}
	}

	@Test
	void roundTripSearchYieldsOutboundAndInboundOffers() {
		FlightSearchCriteria criteria = new FlightSearchCriteria("JFK", "LHR", TripType.ROUND_TRIP, DEPART, RETURN,
				new PassengerCounts(1, 0, 0));

		for (FlightProviderAdapter adapter : adapters) {
			List<FlightOffer> offers = search(adapter, criteria);
			for (FlightOffer offer : offers) {
				assertThat(offer.itineraries()).as(adapter.code()).extracting(Itinerary::direction)
						.containsExactly(Direction.OUTBOUND, Direction.INBOUND);
				assertWellFormed(adapter, offer, criteria);
			}
		}
	}

	private static List<FlightOffer> search(FlightProviderAdapter adapter, FlightSearchCriteria criteria) {
		List<FlightOffer> offers = adapter.search(criteria).block(Duration.ofSeconds(5));
		assertThat(offers).as(adapter.code() + " offers").isNotEmpty();
		assertThat(offers).extracting(FlightOffer::id).as(adapter.code() + " offer ids").doesNotHaveDuplicates();
		return offers;
	}

	private static void assertWellFormed(FlightProviderAdapter adapter, FlightOffer offer,
			FlightSearchCriteria criteria) {
		String code = adapter.code();
		assertThat(offer.provider()).as(code).isEqualTo(code);
		assertThat(offer.id()).as(code).startsWith(code + "-");
		assertThat(offer.cabin()).as(code).isNotNull();
		assertThat(offer.baggage()).as(code).isNotNull();

		for (Itinerary itinerary : offer.itineraries()) {
			boolean outbound = itinerary.direction() == Direction.OUTBOUND;
			String from = outbound ? criteria.origin() : criteria.destination();
			String to = outbound ? criteria.destination() : criteria.origin();
			LocalDate date = outbound ? criteria.departureDate() : criteria.returnDate();

			assertThat(itinerary.segments()).as(code).isNotEmpty();
			assertThat(itinerary.stops()).as(code).isEqualTo(itinerary.segments().size() - 1);
			assertThat(itinerary.duration()).as(code).isPositive();
			assertThat(itinerary.segments().getFirst().origin()).as(code).isEqualTo(from);
			assertThat(itinerary.segments().getLast().destination()).as(code).isEqualTo(to);
			assertThat(itinerary.segments().getFirst().departure().toLocalDate()).as(code).isEqualTo(date);
			for (int i = 0; i < itinerary.segments().size(); i++) {
				var segment = itinerary.segments().get(i);
				assertThat(segment.flightNumber()).as(code).startsWith(segment.carrier());
				assertThat(segment.duration()).as(code).isPositive();
				// departure and arrival are airport-local, so only a layover (one airport) can be compared
				if (i > 0) {
					var previous = itinerary.segments().get(i - 1);
					assertThat(segment.origin()).as(code).isEqualTo(previous.destination());
					assertThat(segment.departure()).as(code).isAfter(previous.arrival());
				}
			}
		}

		assertThat(offer.price().currency()).as(code).hasSize(3);
		assertPricing(code, offer, criteria.passengers());
	}

	private static void assertPricing(String code, FlightOffer offer, PassengerCounts passengers) {
		List<PassengerFare> fares = offer.price().passengerFares();
		List<PassengerType> expectedTypes = List.of(PassengerType.values()).stream()
				.filter(type -> passengers.count(type) > 0)
				.toList();
		assertThat(fares).as(code).extracting(PassengerFare::type).containsExactlyElementsOf(expectedTypes);

		BigDecimal sum = BigDecimal.ZERO;
		for (PassengerFare fare : fares) {
			assertThat(fare.count()).as(code).isEqualTo(passengers.count(fare.type()));
			assertThat(fare.totalPerPassenger()).as(code).isEqualByComparingTo(fare.baseFare().add(fare.taxes()));
			assertThat(fare.totalForType()).as(code)
					.isEqualByComparingTo(fare.totalPerPassenger().multiply(BigDecimal.valueOf(fare.count())));
			sum = sum.add(fare.totalForType());
		}
		assertThat(offer.price().total()).as(code).isEqualByComparingTo(sum).isPositive();
	}
}
