package com.omni.flightaggregator.api;

import java.time.Duration;
import java.time.LocalDate;
import java.util.List;
import java.util.stream.Stream;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webtestclient.autoconfigure.AutoConfigureWebTestClient;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.http.MediaType;
import org.springframework.http.codec.ServerSentEvent;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.reactive.server.WebTestClient;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@AutoConfigureWebTestClient
@ActiveProfiles("test")
class FlightSearchApiTest {

	private static final String SEARCH_URI = "/api/v1/flights/search";

	private static final LocalDate DEPART = LocalDate.now().plusDays(30);

	private static final LocalDate RETURN = DEPART.plusDays(10);

	private static final JsonMapper MAPPER = JsonMapper.builder().build();

	@Autowired
	private WebTestClient client;

	@Test
	void oneWaySearchStreamsOneOffersEventPerProviderThenComplete() {
		List<ServerSentEvent<String>> events = search(oneWay(2, 1, 1));

		assertThat(events).extracting(ServerSentEvent::event)
				.containsExactlyInAnyOrder("offers", "offers", "offers", "complete")
				.endsWith("complete");

		List<JsonNode> offerEvents = events.stream()
				.filter(event -> "offers".equals(event.event()))
				.map(event -> json(event.data()))
				.toList();
		assertThat(offerEvents).extracting(node -> node.get("provider").asString())
				.containsExactlyInAnyOrder("skybook", "globaltravel", "aurora");
		for (JsonNode offerEvent : offerEvents) {
			assertThat(offerEvent.get("offers").size()).isPositive();
			for (JsonNode offer : offerEvent.get("offers")) {
				assertThat(offer.get("itineraries")).hasSize(1);
				assertThat(offer.get("itineraries").get(0).get("direction").asString()).isEqualTo("OUTBOUND");
			}
		}

		JsonNode complete = json(events.getLast().data());
		assertThat(complete.get("providersQueried").asInt()).isEqualTo(3);
		assertThat(complete.get("succeeded").asInt()).isEqualTo(3);
		assertThat(complete.get("failed").asInt()).isZero();
		assertThat(complete.get("totalOffers").asInt())
				.isEqualTo(offerEvents.stream().mapToInt(node -> node.get("offers").size()).sum());
		assertThat(events).extracting(event -> json(event.data()).get("searchId").asString()).doesNotContainNull()
				.containsOnly(complete.get("searchId").asString());
	}

	@Test
	void roundTripOffersCarryBothItinerariesAndPerPassengerTypePricing() {
		List<ServerSentEvent<String>> events = search(roundTrip(2, 1, 1));

		JsonNode offers = events.stream()
				.filter(event -> "offers".equals(event.event()))
				.map(event -> json(event.data()).get("offers").get(0))
				.findFirst()
				.orElseThrow();
		assertThat(offers.get("itineraries")).hasSize(2);
		assertThat(offers.get("itineraries").get(0).get("direction").asString()).isEqualTo("OUTBOUND");
		assertThat(offers.get("itineraries").get(1).get("direction").asString()).isEqualTo("INBOUND");

		JsonNode fares = offers.get("price").get("passengerFares");
		assertThat(fares).extracting(fare -> fare.get("type").asString()).containsExactly("ADULT", "CHILD",
				"INFANT");
		assertThat(fares).extracting(fare -> fare.get("count").asInt()).containsExactly(2, 1, 1);
		assertThat(offers.get("price").get("total").decimalValue())
				.isEqualByComparingTo(fares.get(0).get("totalForType").decimalValue()
						.add(fares.get(1).get("totalForType").decimalValue())
						.add(fares.get(2).get("totalForType").decimalValue()));
	}

	@Test
	void omittedPassengerCountsDefaultToZero() {
		String body = "{\"origin\":\"JFK\",\"destination\":\"LHR\",\"tripType\":\"ONE_WAY\",\"departureDate\":\""
				+ DEPART + "\",\"passengers\":{\"adults\":2}}";

		List<ServerSentEvent<String>> events = search(body);

		assertThat(events.getLast().event()).isEqualTo("complete");
		JsonNode fares = json(events.getFirst().data()).get("offers").get(0).get("price").get("passengerFares");
		assertThat(fares).extracting(fare -> fare.get("type").asString()).containsExactly("ADULT");
		assertThat(fares.get(0).get("count").asInt()).isEqualTo(2);
	}

	@ParameterizedTest(name = "{0}")
	@MethodSource("invalidRequests")
	void invalidRequestsAreRejectedWithAProblemDetailNamingTheField(String name, String body, String field) {
		client.post().uri(SEARCH_URI)
				.contentType(MediaType.APPLICATION_JSON)
				.accept(MediaType.TEXT_EVENT_STREAM)
				.bodyValue(body)
				.exchange()
				.expectStatus().isBadRequest()
				.expectHeader().contentType(MediaType.APPLICATION_PROBLEM_JSON)
				.expectBody(String.class)
				.value(problem -> {
					JsonNode json = json(problem);
					assertThat(json.get("status").asInt()).isEqualTo(400);
					assertThat(json.get("errors")).extracting(error -> error.get("field").asString())
							.contains(field);
				});
	}

	static Stream<Arguments> invalidRequests() {
		return Stream.of(
				Arguments.of("round trip without return date",
						body("JFK", "LHR", "ROUND_TRIP", DEPART, null, 1, 0, 0), "returnDate"),
				Arguments.of("return before departure",
						body("JFK", "LHR", "ROUND_TRIP", DEPART, DEPART.minusDays(1), 1, 0, 0), "returnDate"),
				Arguments.of("one way with a return date",
						body("JFK", "LHR", "ONE_WAY", DEPART, RETURN, 1, 0, 0), "returnDate"),
				Arguments.of("lowercase IATA code",
						body("jfk", "LHR", "ONE_WAY", DEPART, null, 1, 0, 0), "origin"),
				Arguments.of("IATA code too long",
						body("JFK", "LHRX", "ONE_WAY", DEPART, null, 1, 0, 0), "destination"),
				Arguments.of("same origin and destination",
						body("LHR", "LHR", "ONE_WAY", DEPART, null, 1, 0, 0), "destination"),
				Arguments.of("departure in the past",
						body("JFK", "LHR", "ONE_WAY", LocalDate.now().minusDays(1), null, 1, 0, 0), "departureDate"),
				Arguments.of("no adults", body("JFK", "LHR", "ONE_WAY", DEPART, null, 0, 1, 0),
						"passengers.adults"),
				Arguments.of("negative children", body("JFK", "LHR", "ONE_WAY", DEPART, null, 1, -1, 0),
						"passengers.children"),
				Arguments.of("more infants than adults", body("JFK", "LHR", "ONE_WAY", DEPART, null, 1, 0, 2),
						"passengers.infants"),
				Arguments.of("more than nine passengers", body("JFK", "LHR", "ONE_WAY", DEPART, null, 6, 4, 0),
						"passengers"),
				Arguments.of("missing passengers",
						"{\"origin\":\"JFK\",\"destination\":\"LHR\",\"tripType\":\"ONE_WAY\",\"departureDate\":\""
								+ DEPART + "\"}",
						"passengers"),
				Arguments.of("missing trip type",
						"{\"origin\":\"JFK\",\"destination\":\"LHR\",\"departureDate\":\"" + DEPART
								+ "\",\"passengers\":{\"adults\":1}}",
						"tripType"));
	}

	@Test
	void malformedBodiesAreRejectedAsProblemDetails() {
		for (String body : List.of("{oops", "{\"tripType\":\"SIDEWAYS\"}")) {
			client.post().uri(SEARCH_URI)
					.contentType(MediaType.APPLICATION_JSON)
					.accept(MediaType.TEXT_EVENT_STREAM)
					.bodyValue(body)
					.exchange()
					.expectStatus().isBadRequest()
					.expectHeader().contentType(MediaType.APPLICATION_PROBLEM_JSON);
		}
	}

	private List<ServerSentEvent<String>> search(String body) {
		return client.post().uri(SEARCH_URI)
				.contentType(MediaType.APPLICATION_JSON)
				.accept(MediaType.TEXT_EVENT_STREAM)
				.bodyValue(body)
				.exchange()
				.expectStatus().isOk()
				.expectHeader().contentTypeCompatibleWith(MediaType.TEXT_EVENT_STREAM)
				.returnResult(new ParameterizedTypeReference<ServerSentEvent<String>>() {
				})
				.getResponseBody()
				.collectList()
				.block(Duration.ofSeconds(10));
	}

	private static String oneWay(int adults, int children, int infants) {
		return body("JFK", "LHR", "ONE_WAY", DEPART, null, adults, children, infants);
	}

	private static String roundTrip(int adults, int children, int infants) {
		return body("JFK", "LHR", "ROUND_TRIP", DEPART, RETURN, adults, children, infants);
	}

	private static String body(String origin, String destination, String tripType, LocalDate departureDate,
			LocalDate returnDate, int adults, int children, int infants) {
		return """
				{"origin":"%s","destination":"%s","tripType":"%s","departureDate":"%s",%s
				 "passengers":{"adults":%d,"children":%d,"infants":%d}}"""
				.formatted(origin, destination, tripType, departureDate,
						returnDate == null ? "" : "\"returnDate\":\"" + returnDate + "\",", adults, children, infants);
	}

	private static JsonNode json(String content) {
		return MAPPER.readTree(content);
	}
}
