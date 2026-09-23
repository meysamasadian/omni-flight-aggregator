package com.omni.flightaggregator.provider.mock;

import java.time.Duration;
import java.util.concurrent.ThreadLocalRandom;
import java.util.function.Supplier;

import com.omni.flightaggregator.provider.ProviderUnavailableException;
import com.omni.flightaggregator.provider.mock.MockProviderProperties.Behavior;
import reactor.core.publisher.Mono;

/** Wraps a canned response in a non-blocking delay, and occasionally fails, like a real remote call. */
public final class SimulatedNetwork {

	private SimulatedNetwork() {
	}

	public static <T> Mono<T> call(String provider, Behavior behavior, Supplier<T> response) {
		return Mono.defer(() -> {
			ThreadLocalRandom random = ThreadLocalRandom.current();
			long min = behavior.minLatency().toMillis();
			long max = Math.max(min, behavior.maxLatency().toMillis());
			Duration latency = Duration.ofMillis(random.nextLong(min, max + 1));
			boolean fail = random.nextDouble() < behavior.failureRate();
			return Mono.delay(latency).flatMap(tick -> fail
					? Mono.<T>error(new ProviderUnavailableException(provider, "upstream service unavailable"))
					: Mono.fromSupplier(response));
		});
	}
}
