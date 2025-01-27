package com.mb.integration;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.context.annotation.Bean;
import org.springframework.integration.core.GenericHandler;
import org.springframework.integration.core.MessageSource;
import org.springframework.integration.dsl.IntegrationFlow;
import org.springframework.integration.dsl.MessageChannels;
import org.springframework.messaging.Message;
import org.springframework.messaging.MessageChannel;
import org.springframework.messaging.MessageHeaders;
import org.springframework.messaging.support.MessageBuilder;

import java.time.Instant;

@SpringBootApplication
public class IntegrationApplication {

	public static void main(String[] args) {
		SpringApplication.run(IntegrationApplication.class, args);
	}

	@Bean
	MessageChannel atob(){
		return MessageChannels.direct().getObject();
	}
	@Bean
	IntegrationFlow flow0(){
		return IntegrationFlow
				.from(new MessageSource<String>() {
					@Override
					public Message<String> receive() {
						return MessageBuilder.withPayload(String.format("Hello World @  %s |", Instant.now())).build();
					}
				}, poller-> poller.poller(pm-> pm.fixedRate(100)))
				.channel(atob())
				.get();
	}

	@Bean
	IntegrationFlow flow1(){
		return IntegrationFlow
				.from(atob())
				.handle((GenericHandler<String>) (payload, headers) -> {
                    System.out.printf("The payload is %s%n", payload);
                    return null;
                })
				.get();
	}



}
