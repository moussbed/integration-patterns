package com.mb.integration;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.context.annotation.Bean;
import org.springframework.integration.annotation.IntegrationComponentScan;
import org.springframework.integration.annotation.ServiceActivator;
import org.springframework.integration.core.GenericHandler;
import org.springframework.integration.dsl.IntegrationFlow;
import org.springframework.integration.file.dsl.FileInboundChannelAdapterSpec;
import org.springframework.integration.file.dsl.FileWritingMessageHandlerSpec;
import org.springframework.integration.file.dsl.Files;
import org.springframework.integration.file.transformer.FileToStringTransformer;
import org.springframework.messaging.MessageHeaders;
import org.springframework.messaging.handler.annotation.Header;
import org.springframework.messaging.handler.annotation.Headers;
import org.springframework.messaging.handler.annotation.Payload;
import org.springframework.util.SystemPropertyUtils;

import java.io.File;
import java.util.Map;


@IntegrationComponentScan
@SpringBootApplication
public class IntegrationApplication {

    static final String REQUESTS_CHANNEL = "requests";
    private static final String PATH = "${HOME}";
    private static final String UPPERCASE_IN = "uin";
    private static final String UPPERCASE_OUT = "uout";

    public static void main(String[] args) {
        SpringApplication.run(IntegrationApplication.class, args);
    }


    @Bean
    IntegrationFlow buildIntegrationFlow() {
        return IntegrationFlow
                .from(REQUESTS_CHANNEL)
                .filter(String.class, source -> source.contains("Hola"))
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
                .channel(REQUESTS_CHANNEL)
                .get();
    }

    @ServiceActivator(inputChannel = UPPERCASE_IN, outputChannel = UPPERCASE_OUT)
    public String upperCase(@Headers Map<String, Object> headers,
                            @Header(MessageHeaders.ID) String contentType,
                            @Payload String payload) {
        System.out.printf(" The ID is %s%n", contentType);
        headers.forEach((k, v) -> System.out.println(k + "=>" + v));
        return payload.toUpperCase();
    }

    @Bean
    IntegrationFlow outboundFileSystemFlow() {
        String path = PATH + "/out";
        var directory = new File(SystemPropertyUtils.resolvePlaceholders(path));
        FileWritingMessageHandlerSpec fileWritingMessageHandler = Files.outboundAdapter(directory).autoCreateDirectory(true);
        return IntegrationFlow
                .from(UPPERCASE_OUT)
                .handle(fileWritingMessageHandler)
                .get();
    }
}
