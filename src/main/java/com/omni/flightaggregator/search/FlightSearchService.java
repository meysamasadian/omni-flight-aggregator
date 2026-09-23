package com.omni.flightaggregator.search;

import java.time.Duration;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.TimeoutException;

import com.omni.flightaggregator.domain.FlightSearchCriteria;
import com.omni.flightaggregator.provider.FlightProviderAdapter;
import com.omni.flightaggregator.search.SearchEvent.CompleteEvent;
import com.omni.flightaggregator.search.SearchEvent.OffersEvent;
import com.omni.flightaggregator.search.SearchEvent.ProviderStatusEvent;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import org.springframework.stereotype.Service;

/**
 * Fans a search out to every {@link FlightProviderAdapter} at once and emits events in the order the
 * providers finish, followed by a single {@link CompleteEvent}.
 */
@Service
public class FlightSearchService {

	private static final Logger log = LoggerFactory.getLogger(FlightSearchService.class);

	private final List<FlightProviderAdapter> adapters;

	private final Duration providerTimeout;

	public FlightSearchService(List<FlightProviderAdapter> adapters, SearchProperties properties) {
		this.adapters = List.copyOf(adapters);
		this.providerTimeout = properties.providerTimeout();
	}

	public Flux<SearchEvent> search(FlightSearchCriteria criteria) {
		return Flux.defer(() -> {
			String searchId = UUID.randomUUID().toString();
			long startedAt = System.nanoTime();
			Tally tally = new Tally();
			return Flux.fromIterable(adapters)
					// flatMap subscribes to every provider up front and emits as each one completes
					.flatMap(adapter -> query(adapter, criteria, searchId))
					// Reactive Streams signals are serialized, so the tally needs no extra synchronization
					.doOnNext(tally::record)
					.concatWith(Mono.fromSupplier(() -> tally.complete(searchId, adapters.size(),
							Duration.ofNanos(System.nanoTime() - startedAt).toMillis())));
		});
	}

	private Mono<SearchEvent> query(FlightProviderAdapter adapter, FlightSearchCriteria criteria, String searchId) {
		return Mono.defer(() -> adapter.search(criteria))
				.timeout(providerTimeout)
				.<SearchEvent>map(offers -> new OffersEvent(searchId, adapter.code(), offers))
				.onErrorResume(error -> {
					boolean timedOut = error instanceof TimeoutException;
					log.warn("Provider {} {}: {}", adapter.code(), timedOut ? "timed out" : "failed",
							error.getMessage());
					return Mono.just(new ProviderStatusEvent(searchId, adapter.code(),
							timedOut ? ProviderStatusEvent.Status.TIMEOUT : ProviderStatusEvent.Status.FAILED,
							timedOut ? "no response within " + providerTimeout.toMillis() + " ms" : error.getMessage()));
				});
	}

	private static final class Tally {

		private int succeeded;

		private int failed;

		private int totalOffers;

		void record(SearchEvent event) {
			if (event instanceof OffersEvent offers) {
				succeeded++;
				totalOffers += offers.offers().size();
			}
			else {
				failed++;
			}
		}

		CompleteEvent complete(String searchId, int providersQueried, long elapsedMs) {
			return new CompleteEvent(searchId, providersQueried, succeeded, failed, totalOffers, elapsedMs);
		}
	}
}
