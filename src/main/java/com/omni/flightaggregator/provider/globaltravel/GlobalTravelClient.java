package com.omni.flightaggregator.provider.globaltravel;

import reactor.core.publisher.Mono;

/** Gateway to the GlobalTravel API. The mock implementation is to be replaced by a real HTTP client. */
public interface GlobalTravelClient {

	Mono<GlobalTravelApi.Result> search(GlobalTravelApi.Query query);
}
