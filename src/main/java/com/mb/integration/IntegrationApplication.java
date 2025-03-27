package com.mb.integration;

import com.rabbitmq.stream.Environment;
import org.springframework.beans.factory.InitializingBean;
import org.springframework.boot.ApplicationRunner;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.autoconfigure.amqp.RabbitProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.integration.amqp.dsl.RabbitStream;
import org.springframework.integration.annotation.IntegrationComponentScan;
import org.springframework.integration.core.GenericHandler;
import org.springframework.integration.dsl.IntegrationFlow;
import org.springframework.integration.dsl.MessageChannels;
import org.springframework.integration.support.MessageBuilder;
import org.springframework.messaging.Message;
import org.springframework.messaging.MessageChannel;
import org.springframework.messaging.MessageHeaders;
import org.springframework.rabbit.stream.producer.RabbitStreamTemplate;
import org.springframework.scheduling.annotation.EnableScheduling;

import java.util.Map;


@IntegrationComponentScan
@SpringBootApplication
@EnableScheduling
public class IntegrationApplication {


    public static void main(String[] args) throws InterruptedException {
        SpringApplication.run(IntegrationApplication.class, args);
        Thread.currentThread().join();
    }

    @Bean
    InitializingBean initializingBean(RabbitProperties rabbitProperties, Environment environment) {
        return () -> environment.streamCreator().stream(rabbitProperties.getStream().getName()).create();
    }

    @Bean
    MessageChannel streamMessageChannel() {
        return MessageChannels.direct().getObject();
    }

    static Map<String, String> payload(String name) {
        return  Map.of("message", "Hello "+name + "!");
    }

    @Bean
    ApplicationRunner producer(){
        return args -> {
            var message = MessageBuilder.withPayload(payload("streams")).build();
            streamMessageChannel().send(message);
        };
    }

    @Bean
    IntegrationFlow outbound(RabbitStreamTemplate rabbitStreamTemplate) {
        return IntegrationFlow.from(this.streamMessageChannel())
                .handle(RabbitStream.outboundStreamAdapter(rabbitStreamTemplate))
                .get();
    }

    @Bean
    IntegrationFlow inbound(Environment environment, RabbitProperties rabbitProperties) {
        return IntegrationFlow.from(RabbitStream.inboundAdapter(environment).streamName(rabbitProperties.getStream().getName()))
                .handle((GenericHandler<Map<String, String>>) (payload, headers) -> {
                    System.out.println("payload: "+payload);
                    headers.forEach((key, value) -> System.out.println(key+ " : "+value));
                    return null;
                })
                .get();
    }

}
