package com.omni.flightaggregator.provider;

import java.util.List;

import com.omni.flightaggregator.domain.FlightOffer;
import com.omni.flightaggregator.domain.FlightSearchCriteria;
import reactor.core.publisher.Mono;

/**
 * Adapter between the aggregator's standard model and one third-party source (OTA, agency or airline).
 * An implementation translates the criteria into the provider's native request, calls the provider and
 * translates the native response back into standard offers.
 * <p>
 * Implementations must not block the calling thread: return a lazy {@link Mono} (schedule blocking
 * clients on {@code Schedulers.boundedElastic()}). Failures should surface as an error signal; the
 * search service isolates them so one provider can never fail the whole search.
 */
public interface FlightProviderAdapter {

	/** Stable, lowercase provider identifier used in responses and logs. */
	String code();

	Mono<List<FlightOffer>> search(FlightSearchCriteria criteria);
}
