package com.omni.flightaggregator.domain;

import jakarta.validation.constraints.Min;

/**
 * Number of travellers per passenger type. Cross-field rules (infants per adult, maximum party size)
 * are enforced by the request-level validator.
 */
public record PassengerCounts(
		@Min(value = 1, message = "at least one adult is required") int adults,
		@Min(value = 0, message = "must not be negative") int children,
		@Min(value = 0, message = "must not be negative") int infants) {

	public int total() {
		return adults + children + infants;
	}

	public int count(PassengerType type) {
		return switch (type) {
			case ADULT -> adults;
			case CHILD -> children;
			case INFANT -> infants;
		};
	}
}
