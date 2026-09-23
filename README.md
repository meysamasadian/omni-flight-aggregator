# Omni Flight Aggregator

A Spring Boot (WebFlux) service that searches flights across several sources at once and streams the
results back as each source answers.

On every search request the service queries all providers (OTAs, travel agencies, airlines)
**concurrently**, translates each provider's native response into one **standard offer model**, and
pushes results to the client as **Server-Sent Events** the moment they are ready. A slow or failing
provider never delays or breaks the others.

There are no real integrations yet: the three providers are mocks behind the same adapter interface a real
integration would use.

| Provider       | Kind   | Mocked behaviour (see `application.yml`)     |
|----------------|--------|----------------------------------------------|
| `skybook`      | OTA    | fast (150-600 ms)                            |
| `globaltravel` | agency | slow (1.0-3.2 s), occasionally times out     |
| `aurora`       | airline| medium (0.4-1.5 s), fails 25% of the time    |

## Run

Requires Java 21. Maven is bundled through the wrapper.

```bash
./mvnw spring-boot:run          # http://localhost:8080
./mvnw test
```

## API

`POST /api/v1/flights/search` (`Content-Type: application/json`, response is `text/event-stream`)

```bash
curl -N -X POST localhost:8080/api/v1/flights/search \
  -H 'Content-Type: application/json' -H 'Accept: text/event-stream' \
  -d '{
        "origin": "JFK",
        "destination": "LHR",
        "tripType": "ROUND_TRIP",
        "departureDate": "2026-10-10",
        "returnDate": "2026-10-20",
        "passengers": { "adults": 2, "children": 1, "infants": 1 }
      }'
```

| Field | Rules |
|---|---|
| `origin`, `destination` | 3 uppercase letters (IATA), must differ |
| `tripType` | `ONE_WAY` or `ROUND_TRIP` |
| `departureDate` | required, today or later |
| `returnDate` | required for `ROUND_TRIP` (not before `departureDate`); must be absent for `ONE_WAY` |
| `passengers.adults` | at least 1 |
| `passengers.children`, `passengers.infants` | optional, default 0; infants must not outnumber adults |
| party size | at most 9 passengers in total |

Invalid requests get `400` with an `application/problem+json` body listing each violated field.

### Response stream

Every event carries the `searchId` of the search.

| SSE event | Sent when | Payload |
|---|---|---|
| `offers` | a provider answered | `provider`, `offers[]` (standard model) |
| `provider-status` | a provider failed or timed out | `provider`, `status` (`FAILED` / `TIMEOUT`), `message` |
| `complete` | all providers have settled (always last) | `providersQueried`, `succeeded`, `failed`, `totalOffers`, `elapsedMs` |

A standard `FlightOffer` has an `id`, the `provider`, one `OUTBOUND` itinerary (plus one `INBOUND` for round
trips) made of segments, `cabin`, `baggage`, `refundable`, and a `price` with the total and a per-passenger-type
breakdown (`ADULT` / `CHILD` / `INFANT`: count, base fare, taxes, total per passenger, total for the type).
Prices are in each provider's own currency; there is no currency conversion yet.

## How it works

```
POST /search ─► FlightSearchService ──┬─► SkyBookAdapter ──► SkyBookClient (mock)      ─┐
                (flatMap over all     ├─► GlobalTravelAdapter ► GlobalTravelClient (mock) ├─► SSE events
                 adapters, per-       └─► AuroraAdapter ───► AuroraClient (mock)        ─┘   as each completes
                 provider timeout)
```

- `provider/FlightProviderAdapter` is the adapter contract: standard criteria in, standard offers out.
- Each provider package holds the provider's **native wire model** (`*Api`), a **client** interface with a
  mock implementation, and the **adapter** that translates between the native model and the standard one.
- `search/FlightSearchService` subscribes to every adapter at once (`flatMap`), applies
  `omni.search.provider-timeout` to each, turns errors and timeouts into `provider-status` events, and ends
  with `complete`. A client disconnect cancels the in-flight provider calls.

## Adding a provider

1. Create a package under `provider/` with the provider's native model, a client interface and an adapter.
2. Make the adapter a `@Component` implementing `FlightProviderAdapter` (non-blocking; schedule blocking
   clients on `Schedulers.boundedElastic()`).
3. Done: the search service picks up every adapter bean, and `FlightProviderAdapterContractTest` checks the
   new adapter's offers automatically.

To replace a mock with a real integration, implement the provider's client interface with a real HTTP client
and remove the `Mock*Client`; the adapter stays as it is.
