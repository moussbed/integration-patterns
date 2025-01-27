package com.mb.integration;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.context.annotation.Bean;
import org.springframework.integration.annotation.IntegrationComponentScan;
import org.springframework.integration.channel.DirectChannel;
import org.springframework.integration.core.GenericHandler;
import org.springframework.integration.core.GenericSelector;
import org.springframework.integration.core.GenericTransformer;
import org.springframework.integration.dsl.IntegrationFlow;
import org.springframework.integration.dsl.MessageChannels;
import org.springframework.integration.file.dsl.FileInboundChannelAdapterSpec;
import org.springframework.integration.file.dsl.FileWritingMessageHandlerSpec;
import org.springframework.integration.file.dsl.Files;
import org.springframework.integration.file.transformer.FileToStringTransformer;
import org.springframework.messaging.MessageChannel;
import org.springframework.messaging.MessageHeaders;
import org.springframework.util.SystemPropertyUtils;

import java.io.File;
import java.io.IOException;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.time.Instant;
import java.util.stream.Collectors;
import java.util.stream.Stream;


@IntegrationComponentScan
@SpringBootApplication
public class IntegrationApplication {

    private static final String PATH = "${HOME}";

    public static void main(String[] args) {
        SpringApplication.run(IntegrationApplication.class, args);
    }

    static String text() {
        return Math.random() > .5 ?
                String.format("Hello World @  %s !", Instant.now()) :
                String.format("Hola todo el mundo @ %s !", Instant.now());
    }

    @Bean
    MessageChannel fileRequests() {
        return MessageChannels.direct().getObject();
    }

    @Bean
    DirectChannel fileReplies() {
        return MessageChannels.direct().getObject();
    }

    @Bean
    IntegrationFlow buildIntegrationFlow() {
        return IntegrationFlow.from(fileRequests())
                  .filter(String.class, source -> source.contains("Hola"))
                  .transform((GenericTransformer<String, String>) String::toUpperCase)
//                .filter(File.class, source -> {
//                    try (final Stream<String> lines = java.nio.file.Files.lines(Paths.get(source.getPath()))) {
//                        return lines.anyMatch(line -> line.contains("Hola"));
//                    } catch (IOException e) {
//                        throw new RuntimeException(e);
//                    }
//                })
//                .transform((GenericTransformer<File, File>) source -> {
//                    try(final Stream<String> lines = java.nio.file.Files.lines(Path.of(source.getPath()))) {
//                        var modifiedLines = lines.map(line -> String.format("%s todo el mundo @ %s !", line, Instant.now()));
//                        final Path tempFile = java.nio.file.Files.createTempFile("tempFile", "txt");
//                        return java.nio.file.Files.write(tempFile, modifiedLines.collect(Collectors.toSet())).toFile();
//                    } catch (IOException e) {
//                        throw new RuntimeException(e);
//                    }
//                })
                .channel(fileReplies()).get();
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
                    headers.forEach((key, value)-> System.out.println(key +"="+ value));
                    return payload;
                })
                .channel(fileRequests()).get();
    }

    @Bean
    IntegrationFlow outboundFileSystemFlow() {
        String path = PATH + "/out";
        var directory = new File(SystemPropertyUtils.resolvePlaceholders(path));
        FileWritingMessageHandlerSpec fileWritingMessageHandler = Files.outboundAdapter(directory).autoCreateDirectory(true);
        return IntegrationFlow.from(fileReplies())
                .handle(fileWritingMessageHandler)
                .get();
    }
}

