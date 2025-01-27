package com.mb.integration;

import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.context.annotation.Bean;
import org.springframework.integration.annotation.IntegrationComponentScan;
import org.springframework.integration.annotation.MessagingGateway;
import org.springframework.integration.channel.DirectChannel;
import org.springframework.integration.core.GenericTransformer;
import org.springframework.integration.dsl.IntegrationFlow;
import org.springframework.integration.dsl.MessageChannels;
import org.springframework.messaging.MessageChannel;
import org.springframework.stereotype.Component;

import java.time.Instant;
import java.util.stream.Stream;


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
	 MessageChannel greetingsRequests(){
		return MessageChannels.direct().getObject();
	}

	@Bean
	DirectChannel greetingsReplies(){
		return MessageChannels.direct().getObject();
	}

	 @Bean
	 IntegrationFlow buildIntegrationFlow(){
		return IntegrationFlow
				.from(greetingsRequests())
				.filter(String.class, source -> source.contains("Hola"))
				.transform((GenericTransformer<String, String>) String::toUpperCase)
				.channel(greetingsReplies())
				.get();
	}
}

@Component
class Runner implements ApplicationRunner{

	private final GreetingsClient greetingsClient;
	private final DirectChannel greetingsReplies ;

	Runner(GreetingsClient greetingsClient, DirectChannel greetingsReplies) {
		this.greetingsClient = greetingsClient;
        this.greetingsReplies = greetingsReplies;
    }

	@Override
	public void run(ApplicationArguments args) throws Exception {
		this.subscriber();
		this.producer();

	}

	private  void producer(){
		Stream.generate(()-> IntegrationApplication.text())
						.forEach(text-> greetingsClient.greet(text));
	}

	private void subscriber(){
		greetingsReplies.subscribe(message -> System.out.printf("The payload is %s%n", message.getPayload()));
	}
}

@MessagingGateway(defaultRequestChannel = "greetingsRequests")
interface GreetingsClient{
	void greet(String text);
}

