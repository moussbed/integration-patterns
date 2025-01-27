package com.mb.integration;

import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.context.annotation.Bean;
import org.springframework.integration.annotation.IntegrationComponentScan;
import org.springframework.integration.annotation.MessagingGateway;
import org.springframework.integration.core.GenericHandler;
import org.springframework.integration.core.GenericTransformer;
import org.springframework.integration.dsl.IntegrationFlow;
import org.springframework.integration.dsl.MessageChannels;
import org.springframework.messaging.MessageChannel;
import org.springframework.stereotype.Component;

import java.time.Instant;


@IntegrationComponentScan
@SpringBootApplication
public class IntegrationApplication {

	public static void main(String[] args) {
		SpringApplication.run(IntegrationApplication.class, args);
	}

	 static String text(){
		return Math.random()> .5 ?
				String.format("Hello World @  %s !", Instant.now()) :
				String.format("Hola todo el mundo @ %s !", Instant.now());
	}

	@Bean
	 MessageChannel greetings(){
		return MessageChannels.direct().getObject();
	}

	 @Bean
	 IntegrationFlow buildIntegrationFlow(){
		return IntegrationFlow
				.from(greetings())
				.filter(String.class, source -> source.contains("Hola"))
				.transform((GenericTransformer<String, String>) String::toUpperCase)
				.handle((GenericHandler<String>) (payload, headers) -> {
					System.out.printf("The payload is %s%n", payload);
					return null;
				})
				.get();
	}
}

@Component
class Runner implements ApplicationRunner{

	private final GreetingsClient greetingsClient;

	Runner(GreetingsClient greetingsClient) {
		this.greetingsClient = greetingsClient;
	}

	@Override
	public void run(ApplicationArguments args) throws Exception {
		for (int i = 0; i < 100; i++) {
			greetingsClient.greet(IntegrationApplication.text());
		}
	}
}

@MessagingGateway(defaultRequestChannel = "greetings")
interface GreetingsClient{
	void greet(String text);
}

