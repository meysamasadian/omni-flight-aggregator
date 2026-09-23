package com.omni.flightaggregator.provider;

public class ProviderUnavailableException extends RuntimeException {

	public ProviderUnavailableException(String provider, String message) {
		super(provider + ": " + message);
	}
}
