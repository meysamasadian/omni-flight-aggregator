package com.omni.flightaggregator.search;

import java.util.List;

import com.omni.flightaggregator.domain.FlightOffer;

/** One message of the streamed search response. {@link #eventName()} is used as the SSE event name. */
public sealed interface SearchEvent {

	String searchId();

	String eventName();

	/** A provider answered; {@code offers} may be empty if it had nothing for the route. */
	record OffersEvent(String searchId, String provider, List<FlightOffer> offers) implements SearchEvent {

		@Override
		public String eventName() {
			return "offers";
		}
	}

	/** A provider could not deliver offers. The search continues with the remaining providers. */
	record ProviderStatusEvent(String searchId, String provider, Status status, String message)
			implements SearchEvent {

		public enum Status {
			FAILED,
			TIMEOUT
		}

		@Override
		public String eventName() {
			return "provider-status";
		}
	}

	/** Always the last event: every provider has either answered, failed or timed out. */
	record CompleteEvent(String searchId, int providersQueried, int succeeded, int failed, int totalOffers,
			long elapsedMs) implements SearchEvent {

		@Override
		public String eventName() {
			return "complete";
		}
	}
}
