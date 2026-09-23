package com.omni.flightaggregator.search;

import java.time.Duration;
import java.time.LocalDate;
import java.util.List;
import java.util.function.Supplier;

import com.omni.flightaggregator.domain.BaggageAllowance;
import com.omni.flightaggregator.domain.CabinClass;
import com.omni.flightaggregator.domain.FlightOffer;
import com.omni.flightaggregator.domain.FlightSearchCriteria;
import com.omni.flightaggregator.domain.PassengerCounts;
import com.omni.flightaggregator.domain.Price;
import com.omni.flightaggregator.domain.TripType;
import com.omni.flightaggregator.provider.FlightProviderAdapter;
import com.omni.flightaggregator.provider.ProviderUnavailableException;
import com.omni.flightaggregator.search.SearchEvent.CompleteEvent;
import com.omni.flightaggregator.search.SearchEvent.OffersEvent;
import com.omni.flightaggregator.search.SearchEvent.ProviderStatusEvent;
import org.junit.jupiter.api.Test;
import reactor.core.publisher.Mono;
import reactor.test.StepVerifier;

import static org.assertj.core.api.Assertions.assertThat;

class FlightSearchServiceTest {

	private static final Duration PROVIDER_TIMEOUT = Duration.ofSeconds(3);

	private static final FlightSearchCriteria CRITERIA = new FlightSearchCriteria("JFK", "LHR", TripType.ONE_WAY,
			LocalDate.now().plusDays(30), null, new PassengerCounts(1, 0, 0));

	@Test
	void emitsEachProviderAsSoonAsItRespondsAndQueriesAllInParallel() {
		FlightSearchService service = service(respondingAfter("fast", Duration.ofSeconds(1)),
				respondingAfter("slow", Duration.ofSeconds(2)));

		// sequential calls would deliver "slow" after 3s; in parallel it arrives 2s in
		StepVerifier.withVirtualTime(() -> service.search(CRITERIA))
				.expectSubscription()
				.expectNoEvent(Duration.ofMillis(999))
				.thenAwait(Duration.ofMillis(1))
				.expectNextMatches(event -> isOffersFrom(event, "fast"))
				.thenAwait(Duration.ofSeconds(1))
				.expectNextMatches(event -> isOffersFrom(event, "slow"))
				.assertNext(event -> assertThat(event).isInstanceOfSatisfying(CompleteEvent.class, complete -> {
					assertThat(complete.providersQueried()).isEqualTo(2);
					assertThat(complete.succeeded()).isEqualTo(2);
					assertThat(complete.failed()).isZero();
					assertThat(complete.totalOffers()).isEqualTo(2);
				}))
				.verifyComplete();
	}

	@Test
	void failingProviderIsReportedWithoutFailingTheSearch() {
		FlightSearchService service = service(respondingAfter("ok", Duration.ofSeconds(1)), failing("bad"));

		StepVerifier.withVirtualTime(() -> service.search(CRITERIA))
				.assertNext(event -> assertThat(event).isInstanceOfSatisfying(ProviderStatusEvent.class, status -> {
					assertThat(status.provider()).isEqualTo("bad");
					assertThat(status.status()).isEqualTo(ProviderStatusEvent.Status.FAILED);
					assertThat(status.message()).contains("upstream exploded");
				}))
				.thenAwait(Duration.ofSeconds(1))
				.expectNextMatches(event -> isOffersFrom(event, "ok"))
				.assertNext(event -> assertThat(event).isInstanceOfSatisfying(CompleteEvent.class, complete -> {
					assertThat(complete.providersQueried()).isEqualTo(2);
					assertThat(complete.succeeded()).isEqualTo(1);
					assertThat(complete.failed()).isEqualTo(1);
					assertThat(complete.totalOffers()).isEqualTo(1);
				}))
				.verifyComplete();
	}

	@Test
	void providerExceedingTheTimeoutIsReportedAsTimedOut() {
		FlightSearchService service = service(respondingAfter("fast", Duration.ofSeconds(1)),
				respondingAfter("stuck", Duration.ofMinutes(10)));

		StepVerifier.withVirtualTime(() -> service.search(CRITERIA))
				.thenAwait(Duration.ofSeconds(1))
				.expectNextMatches(event -> isOffersFrom(event, "fast"))
				.thenAwait(PROVIDER_TIMEOUT.minusSeconds(1))
				.assertNext(event -> assertThat(event).isInstanceOfSatisfying(ProviderStatusEvent.class, status -> {
					assertThat(status.provider()).isEqualTo("stuck");
					assertThat(status.status()).isEqualTo(ProviderStatusEvent.Status.TIMEOUT);
				}))
				.assertNext(event -> assertThat(event).isInstanceOfSatisfying(CompleteEvent.class, complete -> {
					assertThat(complete.succeeded()).isEqualTo(1);
					assertThat(complete.failed()).isEqualTo(1);
				}))
				.verifyComplete();
	}

	@Test
	void providerWithNoOffersStillCountsAsSucceeded() {
		FlightSearchService service = service(new StubAdapter("empty", () -> Mono.just(List.of())));

		StepVerifier.create(service.search(CRITERIA))
				.assertNext(event -> assertThat(event).isInstanceOfSatisfying(OffersEvent.class,
						offers -> assertThat(offers.offers()).isEmpty()))
				.assertNext(event -> assertThat(event).isInstanceOfSatisfying(CompleteEvent.class, complete -> {
					assertThat(complete.succeeded()).isEqualTo(1);
					assertThat(complete.totalOffers()).isZero();
				}))
				.verifyComplete();
	}

	@Test
	void everyEventOfOneSearchSharesASearchIdAndEachSearchGetsItsOwn() {
		FlightSearchService service = service(respondingAfter("a", Duration.ofMillis(10)), failing("b"));

		List<SearchEvent> first = service.search(CRITERIA).collectList().block(Duration.ofSeconds(5));
		List<SearchEvent> second = service.search(CRITERIA).collectList().block(Duration.ofSeconds(5));

		assertThat(first).hasSize(3).extracting(SearchEvent::searchId).containsOnly(first.getFirst().searchId());
		assertThat(second.getFirst().searchId()).isNotEqualTo(first.getFirst().searchId());
	}

	@Test
	void searchWithoutProvidersCompletesImmediately() {
		StepVerifier.create(service().search(CRITERIA))
				.assertNext(event -> assertThat(event).isInstanceOfSatisfying(CompleteEvent.class, complete -> {
					assertThat(complete.providersQueried()).isZero();
					assertThat(complete.totalOffers()).isZero();
				}))
				.verifyComplete();
	}

	private static FlightSearchService service(FlightProviderAdapter... adapters) {
		return new FlightSearchService(List.of(adapters), new SearchProperties(PROVIDER_TIMEOUT));
	}

	private static boolean isOffersFrom(SearchEvent event, String provider) {
		return event instanceof OffersEvent offers && offers.provider().equals(provider);
	}

	private static FlightProviderAdapter respondingAfter(String code, Duration delay) {
		return new StubAdapter(code, () -> Mono.delay(delay).thenReturn(List.of(offer(code))));
	}

	private static FlightProviderAdapter failing(String code) {
		return new StubAdapter(code,
				() -> Mono.error(new ProviderUnavailableException(code, "upstream exploded")));
	}

	private static FlightOffer offer(String provider) {
		return new FlightOffer(provider + "-1", provider, List.of(), Price.of("EUR", List.of()),
				CabinClass.ECONOMY, new BaggageAllowance(0, 1), false);
	}

	/** The response is supplied lazily so delays are created at subscription time, as real clients do. */
	private record StubAdapter(String code, Supplier<Mono<List<FlightOffer>>> response)
			implements FlightProviderAdapter {

		@Override
		public Mono<List<FlightOffer>> search(FlightSearchCriteria criteria) {
			return response.get();
		}
	}
}
