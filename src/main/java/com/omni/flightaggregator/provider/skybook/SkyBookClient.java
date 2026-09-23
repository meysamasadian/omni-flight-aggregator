package com.omni.flightaggregator.provider.skybook;

import reactor.core.publisher.Mono;

/** Gateway to the SkyBook API. The mock implementation is to be replaced by a real HTTP client. */
public interface SkyBookClient {

	Mono<SkyBookApi.SearchResponse> search(SkyBookApi.SearchRequest request);
}
