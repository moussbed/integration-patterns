package com.mb.integration;

import org.springframework.boot.ApplicationRunner;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.context.annotation.Bean;
import org.springframework.integration.core.GenericHandler;
import org.springframework.integration.core.GenericTransformer;
import org.springframework.integration.core.MessageSource;
import org.springframework.integration.dsl.IntegrationFlow;
import org.springframework.integration.dsl.context.IntegrationFlowContext;
import org.springframework.messaging.Message;
import org.springframework.messaging.support.MessageBuilder;
import org.springframework.stereotype.Component;

import java.time.Instant;
import java.util.Set;

@SpringBootApplication
public class IntegrationApplication {

	public static void main(String[] args) {
		SpringApplication.run(IntegrationApplication.class, args);
	}

	private static String text(){
		return Math.random()> .5 ?
				String.format("Hello World @  %s !", Instant.now()) :
				String.format("Hola todo el mundo @ %s !", Instant.now());
	}

	@Component
	static class MyMessageSource implements MessageSource<String>{
		@Override
		public Message<String> receive() {
			return MessageBuilder.withPayload(text()).build();
		}
	}


	private static IntegrationFlow buildIntegrationFlow(MyMessageSource messageSource, int milliseconds, String filterText, String name){
		return IntegrationFlow
				.from(messageSource,
						sourcePollingChannelAdapterSpec ->
								sourcePollingChannelAdapterSpec.poller(
										pollerFactory -> pollerFactory.fixedRate(milliseconds)))
				.filter(String.class, source -> source.contains(filterText))
				.transform((GenericTransformer<String, String>) String::toUpperCase)
				.handle((GenericHandler<String>) (payload, headers) -> {
					System.out.printf("[%s] The payload is %s%n",name, payload);
					return null;
				})
				.get();
	}

	@Bean
	ApplicationRunner runner (MyMessageSource messageSource, IntegrationFlowContext context){
		return args -> {
			Set<IntegrationFlow> integrationFlows = Set.of(buildIntegrationFlow(messageSource, 1000, "Hola", "Flow-00"),
					buildIntegrationFlow(messageSource, 3000, "Hello","Flow-01" ));

			integrationFlows.forEach(integrationFlow ->
					context.registration(integrationFlow).register().start());

		};
	}


}
