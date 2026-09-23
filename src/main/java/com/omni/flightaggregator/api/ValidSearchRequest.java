package com.omni.flightaggregator.api;

import java.lang.annotation.Documented;
import java.lang.annotation.Retention;
import java.lang.annotation.Target;

import jakarta.validation.Constraint;
import jakarta.validation.Payload;

import static java.lang.annotation.ElementType.TYPE;
import static java.lang.annotation.RetentionPolicy.RUNTIME;

/** Cross-field rules of a search request: trip dates, distinct airports and the passenger mix. */
@Documented
@Constraint(validatedBy = SearchRequestValidator.class)
@Target(TYPE)
@Retention(RUNTIME)
public @interface ValidSearchRequest {

	String message() default "invalid search request";

	Class<?>[] groups() default {};

	Class<? extends Payload>[] payload() default {};
}
