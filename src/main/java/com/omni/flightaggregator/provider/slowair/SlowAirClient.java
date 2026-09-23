package com.omni.flightaggregator.provider.slowair;

import reactor.core.publisher.Mono;

/** Gateway to the SlowAir API, a fake provider that is always too slow to answer in time. */
public interface SlowAirClient {

	Mono<SlowAirApi.SearchResponse> search(SlowAirApi.SearchRequest request);
}
