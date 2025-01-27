package com.mb.integration;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.context.annotation.Bean;
import org.springframework.integration.core.GenericHandler;
import org.springframework.integration.core.GenericSelector;
import org.springframework.integration.core.GenericTransformer;
import org.springframework.integration.core.MessageSource;
import org.springframework.integration.dsl.IntegrationFlow;
import org.springframework.messaging.Message;
import org.springframework.messaging.support.MessageBuilder;
import org.springframework.stereotype.Component;

import java.time.Instant;

@SpringBootApplication
public class IntegrationApplication {

	public static void main(String[] args) {
		SpringApplication.run(IntegrationApplication.class, args);
	}

	private static String text(){
		return Math.random()> .5 ?
				String.format("Hello World @  %s !", Instant.now()) :
				String.format("hola todo el mundo @ %s !", Instant.now());
	}

	@Component
	static class MyMessageSource implements MessageSource<String>{
		@Override
		public Message<String> receive() {
			return MessageBuilder.withPayload(text()).build();
		}
	}

	@Bean
	IntegrationFlow flow(MyMessageSource messageSource){
		return IntegrationFlow
				.from(messageSource,
						sourcePollingChannelAdapterSpec ->
								sourcePollingChannelAdapterSpec.poller(
										pollerFactory -> pollerFactory.fixedRate(2000)))
				.filter(String.class, source -> source.contains("hola"))
				.transform((GenericTransformer<String, String>) String::toUpperCase)
				.handle((GenericHandler<String>) (payload, headers) -> {
					System.out.printf("The payload is %s%n", payload);
					return null;
				})
				.get();
	}


}
