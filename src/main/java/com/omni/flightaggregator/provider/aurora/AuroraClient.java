package com.omni.flightaggregator.provider.aurora;

import reactor.core.publisher.Mono;

/** Gateway to the Aurora Air API. The mock implementation is to be replaced by a real HTTP client. */
public interface AuroraClient {

	Mono<AuroraApi.OfferResponse> searchOffers(AuroraApi.OfferRequest request);
}
