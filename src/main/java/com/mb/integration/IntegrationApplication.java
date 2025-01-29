package com.mb.integration;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.annotation.Bean;
import org.springframework.context.event.EventListener;
import org.springframework.integration.annotation.Gateway;
import org.springframework.integration.annotation.IntegrationComponentScan;
import org.springframework.integration.annotation.MessagingGateway;
import org.springframework.integration.annotation.ServiceActivator;
import org.springframework.integration.channel.DirectChannel;
import org.springframework.integration.core.GenericHandler;
import org.springframework.integration.core.GenericTransformer;
import org.springframework.integration.dsl.HeaderEnricherSpec;
import org.springframework.integration.dsl.IntegrationFlow;
import org.springframework.integration.dsl.MessageChannels;
import org.springframework.integration.file.dsl.FileInboundChannelAdapterSpec;
import org.springframework.integration.file.dsl.FileWritingMessageHandlerSpec;
import org.springframework.integration.file.dsl.Files;
import org.springframework.integration.file.transformer.FileToStringTransformer;
import org.springframework.messaging.MessageChannel;
import org.springframework.messaging.MessageHeaders;
import org.springframework.messaging.handler.annotation.Header;
import org.springframework.messaging.handler.annotation.Headers;
import org.springframework.messaging.handler.annotation.Payload;
import org.springframework.stereotype.Component;
import org.springframework.util.SystemPropertyUtils;

import java.io.File;
import java.time.Instant;
import java.util.Map;
import java.util.function.Consumer;


@IntegrationComponentScan
@SpringBootApplication
public class IntegrationApplication {

    private static final String PATH = "${HOME}";
    static final String REQUESTS_CHANNEL = "requests";
    //static final String ERRORS_CHANNEL = "errors";
    private static final String UPPERCASE_IN = "uin";
    private static final String UPPERCASE_OUT = "uout";

    public static void main(String[] args) {
        SpringApplication.run(IntegrationApplication.class, args);
    }

    @Bean(name = REQUESTS_CHANNEL)
    MessageChannel requests(){
        return MessageChannels.direct().getObject();
    }

    @Bean
    MessageChannel errorChannel(){
        return MessageChannels.direct().getObject();
    }

    @Bean
    IntegrationFlow buildIntegrationFlow() {
        return IntegrationFlow.from(REQUESTS_CHANNEL)
                //.filter(String.class, source -> source.contains("Hola"))
                .channel(UPPERCASE_IN)
                .get();
    }


    @Bean
    IntegrationFlow inboundFileSystemFlow() {
        String path = PATH + "/in";
        var directory = new File(SystemPropertyUtils.resolvePlaceholders(path));
        final FileInboundChannelAdapterSpec fileInboundChannelAdapter = Files.inboundAdapter(directory).autoCreateDirectory(true);
        return IntegrationFlow
                .from(fileInboundChannelAdapter, p -> p.poller(pm -> pm.fixedRate(1000)))
                .transform(new FileToStringTransformer())
                .handle((GenericHandler<String>) (payload, headers) -> {
                    headers.forEach((key, value) -> System.out.println(key + "=" + value));
                    return payload;
                })
                .channel(REQUESTS_CHANNEL).get();
    }

    @Bean
   IntegrationFlow upperFlow(){
        return IntegrationFlow.from(UPPERCASE_IN)
                .handle((GenericHandler<String>) (payload, headers) -> {
                    if (payload.chars().anyMatch(Character::isDigit))
                           throw new IllegalArgumentException(" You must provide some letters");
                    return payload;
                })
                .transform((GenericTransformer<String, String>) String::toUpperCase)
                .channel(UPPERCASE_OUT)
                .get();
    }

    @Bean
    IntegrationFlow errorFlow(){
        return IntegrationFlow
                .from(errorChannel())
                .handle(new GenericHandler<Object>() {
                    @Override
                    public Object handle(Object payload, MessageHeaders headers) {
                        System.out.println("Inside the error handling flow " + payload);
                        headers.forEach((k,v)-> System.out.println(k+ "=="+ v));
                        return null;
                    }
                })
                .get();
    }

    @Bean
    IntegrationFlow outboundFileSystemFlow() {
        String path = PATH + "/out";
        var directory = new File(SystemPropertyUtils.resolvePlaceholders(path));
        FileWritingMessageHandlerSpec fileWritingMessageHandler = Files.outboundAdapter(directory).autoCreateDirectory(true);
        return IntegrationFlow.from(UPPERCASE_OUT)
                .handle(fileWritingMessageHandler)
                .get();
    }
}
