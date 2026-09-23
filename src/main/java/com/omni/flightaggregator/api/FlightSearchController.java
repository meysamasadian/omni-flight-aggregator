package com.omni.flightaggregator.api;

import com.omni.flightaggregator.search.FlightSearchService;
import com.omni.flightaggregator.search.SearchEvent;
import jakarta.validation.Valid;
import reactor.core.publisher.Flux;

import org.springframework.http.MediaType;
import org.springframework.http.codec.ServerSentEvent;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/flights")
public class FlightSearchController {

	private final FlightSearchService searchService;

	public FlightSearchController(FlightSearchService searchService) {
		this.searchService = searchService;
	}

	/** Streams offers as Server-Sent Events, provider by provider, as each one responds. */
	@PostMapping(path = "/search", produces = MediaType.TEXT_EVENT_STREAM_VALUE)
	public Flux<ServerSentEvent<SearchEvent>> search(@Valid @RequestBody FlightSearchRequest request) {
		return searchService.search(request.toCriteria())
				.map(event -> ServerSentEvent.builder(event).event(event.eventName()).build());
	}
}
