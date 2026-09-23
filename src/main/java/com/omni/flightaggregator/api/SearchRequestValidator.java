package com.omni.flightaggregator.api;

import com.omni.flightaggregator.domain.PassengerCounts;
import com.omni.flightaggregator.domain.TripType;
import jakarta.validation.ConstraintValidator;
import jakarta.validation.ConstraintValidatorContext;

class SearchRequestValidator implements ConstraintValidator<ValidSearchRequest, FlightSearchRequest> {

	static final int MAX_PASSENGERS = 9;

	@Override
	public boolean isValid(FlightSearchRequest request, ConstraintValidatorContext context) {
		context.disableDefaultConstraintViolation();
		boolean valid = true;

		if (request.origin() != null && request.origin().equals(request.destination())) {
			valid = reject(context, "origin and destination must differ", "destination");
		}

		if (request.tripType() == TripType.ROUND_TRIP) {
			if (request.returnDate() == null) {
				valid = reject(context, "returnDate is required for round trips", "returnDate");
			}
			else if (request.departureDate() != null && request.returnDate().isBefore(request.departureDate())) {
				valid = reject(context, "returnDate must not be before departureDate", "returnDate");
			}
		}
		else if (request.tripType() == TripType.ONE_WAY && request.returnDate() != null) {
			valid = reject(context, "returnDate must not be provided for one-way trips", "returnDate");
		}

		PassengerCounts passengers = request.passengers();
		if (passengers != null) {
			if (passengers.infants() > passengers.adults()) {
				valid = reject(context, "each infant must travel with an adult", "passengers", "infants");
			}
			if (passengers.total() > MAX_PASSENGERS) {
				valid = reject(context, "at most " + MAX_PASSENGERS + " passengers per search", "passengers");
			}
		}
		return valid;
	}

	/** Records a violation on the given property path and returns {@code false} for convenient chaining. */
	private static boolean reject(ConstraintValidatorContext context, String message, String... path) {
		ConstraintValidatorContext.ConstraintViolationBuilder.NodeBuilderCustomizableContext node = context
				.buildConstraintViolationWithTemplate(message)
				.addPropertyNode(path[0]);
		for (int i = 1; i < path.length; i++) {
			node = node.addPropertyNode(path[i]);
		}
		node.addConstraintViolation();
		return false;
	}
}
