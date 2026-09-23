package com.omni.flightaggregator.api;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import reactor.core.publisher.Mono;

import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatusCode;
import org.springframework.http.MediaType;
import org.springframework.http.ProblemDetail;
import org.springframework.http.ResponseEntity;
import org.springframework.validation.FieldError;
import org.springframework.validation.ObjectError;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.bind.support.WebExchangeBindException;
import org.springframework.web.reactive.result.method.annotation.ResponseEntityExceptionHandler;
import org.springframework.web.server.ServerWebExchange;

/** Renders request errors as RFC 9457 problem details, listing each violated field. */
@RestControllerAdvice
class ApiExceptionHandler extends ResponseEntityExceptionHandler {

	@Override
	protected Mono<ResponseEntity<Object>> handleWebExchangeBindException(WebExchangeBindException ex,
			HttpHeaders headers, HttpStatusCode status, ServerWebExchange exchange) {
		ProblemDetail problem = ex.getBody();
		problem.setTitle("Invalid flight search request");
		problem.setProperty("errors", violations(ex));
		return handleExceptionInternal(ex, problem, headers, status, exchange);
	}

	/**
	 * The search endpoint only produces {@code text/event-stream}, and a client asking for that would
	 * otherwise get the error framed as an SSE event. Problem details are always plain JSON.
	 */
	@Override
	protected Mono<ResponseEntity<Object>> handleExceptionInternal(Exception ex, Object body, HttpHeaders headers,
			HttpStatusCode status, ServerWebExchange exchange) {
		HttpHeaders problemHeaders = new HttpHeaders();
		problemHeaders.putAll(headers);
		problemHeaders.setContentType(MediaType.APPLICATION_PROBLEM_JSON);
		return super.handleExceptionInternal(ex, body, problemHeaders, status, exchange);
	}

	private static List<Map<String, String>> violations(WebExchangeBindException ex) {
		List<Map<String, String>> errors = new ArrayList<>();
		for (ObjectError error : ex.getAllErrors()) {
			String field = error instanceof FieldError fieldError ? fieldError.getField() : error.getObjectName();
			errors.add(Map.of("field", field, "message", String.valueOf(error.getDefaultMessage())));
		}
		return errors;
	}
}
