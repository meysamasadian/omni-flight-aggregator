package com.omni.flightaggregator.provider.slowair;

import java.time.Duration;
import java.time.LocalDate;
import java.util.List;

import com.omni.flightaggregator.domain.FlightSearchCriteria;
import com.omni.flightaggregator.domain.PassengerCounts;
import com.omni.flightaggregator.domain.TripType;
import com.omni.flightaggregator.search.FlightSearchService;
import com.omni.flightaggregator.search.SearchEvent;
import com.omni.flightaggregator.search.SearchEvent.CompleteEvent;
import com.omni.flightaggregator.search.SearchEvent.OffersEvent;
import com.omni.flightaggregator.search.SearchEvent.ProviderStatusEvent;
import org.junit.jupiter.api.Test;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * With SlowAir enabled at its real 20 s latency, it always times out while the other providers still
 * deliver. The provider timeout is shortened so the test does not wait the default 3 s.
 */
@SpringBootTest(properties = { SlowAirAdapter.ENABLED_PROPERTY + "=true", "omni.mock.slowair.min-latency=20s",
		"omni.mock.slowair.max-latency=20s", "omni.search.provider-timeout=500ms" })
@ActiveProfiles("test")
class SlowAirTimeoutTest {

	@Autowired
	private FlightSearchService searchService;

	@Test
	void slowAirTimesOutWithoutHoldingUpTheOtherProviders() {
		FlightSearchCriteria criteria = new FlightSearchCriteria("JFK", "LHR", TripType.ONE_WAY,
				LocalDate.now().plusDays(30), null, new PassengerCounts(1, 0, 0));

		List<SearchEvent> events = searchService.search(criteria).collectList().block(Duration.ofSeconds(5));

		assertThat(events).filteredOn(OffersEvent.class::isInstance).map(event -> ((OffersEvent) event).provider())
				.containsExactlyInAnyOrder("skybook", "globaltravel", "aurora");
		assertThat(events).filteredOn(ProviderStatusEvent.class::isInstance).singleElement()
				.isInstanceOfSatisfying(ProviderStatusEvent.class, status -> {
					assertThat(status.provider()).isEqualTo("slowair");
					assertThat(status.status()).isEqualTo(ProviderStatusEvent.Status.TIMEOUT);
				});
		assertThat(events.getLast()).isInstanceOfSatisfying(CompleteEvent.class, complete -> {
			assertThat(complete.providersQueried()).isEqualTo(4);
			assertThat(complete.succeeded()).isEqualTo(3);
			assertThat(complete.failed()).isEqualTo(1);
			assertThat(complete.elapsedMs()).isLessThan(Duration.ofSeconds(20).toMillis());
		});
	}
}
