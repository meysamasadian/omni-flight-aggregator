package com.omni.flightaggregator.api;

import java.time.LocalDate;

import com.omni.flightaggregator.domain.FlightSearchCriteria;
import com.omni.flightaggregator.domain.PassengerCounts;
import com.omni.flightaggregator.domain.TripType;
import jakarta.validation.Valid;
import jakarta.validation.constraints.FutureOrPresent;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;

@ValidSearchRequest
public record FlightSearchRequest(
		@NotNull @Pattern(regexp = "[A-Z]{3}", message = "must be a 3-letter uppercase IATA code") String origin,
		@NotNull @Pattern(regexp = "[A-Z]{3}", message = "must be a 3-letter uppercase IATA code") String destination,
		@NotNull TripType tripType,
		@NotNull @FutureOrPresent LocalDate departureDate,
		LocalDate returnDate,
		@NotNull @Valid PassengerCounts passengers) {

	public FlightSearchCriteria toCriteria() {
		return new FlightSearchCriteria(origin, destination, tripType, departureDate, returnDate, passengers);
	}
}
