package com.omni.flightaggregator.search;

import java.time.Duration;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.bind.DefaultValue;

@ConfigurationProperties("omni.search")
public record SearchProperties(@DefaultValue("3s") Duration providerTimeout) {
}
