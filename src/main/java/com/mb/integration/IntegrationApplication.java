package com.mb.integration;

import org.springframework.boot.ApplicationRunner;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.context.annotation.Bean;
import org.springframework.integration.core.GenericHandler;
import org.springframework.integration.core.GenericSelector;
import org.springframework.integration.core.GenericTransformer;
import org.springframework.integration.core.MessageSource;
import org.springframework.integration.dsl.IntegrationFlow;
import org.springframework.integration.dsl.MessageChannels;
import org.springframework.messaging.Message;
import org.springframework.messaging.MessageChannel;
import org.springframework.messaging.support.MessageBuilder;

import java.time.Instant;

@SpringBootApplication
public class IntegrationApplication {

	public static void main(String[] args) {
		SpringApplication.run(IntegrationApplication.class, args);
	}

	private String text(){
		return Math.random()> .5 ?
				String.format("Hello World @  %s !", Instant.now()) :
				String.format("hola todo el mundo @ %s !", Instant.now());
	}
	@Bean
	MessageChannel greetings(){
		return MessageChannels.direct().getObject();
	}
	@Bean
	IntegrationFlow flow(){
		return IntegrationFlow
				.from(greetings())
				.filter(String.class, new GenericSelector<String>() {
					@Override
					public boolean accept(String source) {
						return source.contains("hola");
					}
				})
				.transform(new GenericTransformer<String, String>() {
					@Override
					public String transform(String source) {
						return source.toUpperCase();
					}
				})
				.handle((GenericHandler<String>) (payload, headers) -> {
					System.out.printf("The payload is %s%n", payload);
					return null;
				})
				.get();
	}

	@Bean
	ApplicationRunner runner(){
		return  args ->{
			for (var i=0; i<10; i++)
				greetings().send(MessageBuilder.withPayload(text()).build());
		};
	}



}
