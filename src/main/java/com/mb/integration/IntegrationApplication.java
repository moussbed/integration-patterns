package com.mb.integration;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.context.annotation.Bean;
import org.springframework.integration.annotation.IntegrationComponentScan;
import org.springframework.integration.core.GenericHandler;
import org.springframework.integration.core.GenericSelector;
import org.springframework.integration.dsl.IntegrationFlow;
import org.springframework.integration.dsl.PollerFactory;
import org.springframework.integration.file.FileReadingMessageSource;
import org.springframework.integration.file.FileWritingMessageHandler;
import org.springframework.integration.file.dsl.Files;
import org.springframework.integration.file.support.FileExistsMode;
import org.springframework.messaging.MessageHeaders;

import java.io.File;
import java.time.Duration;
import java.util.logging.Logger;


@IntegrationComponentScan
@SpringBootApplication
public class IntegrationApplication {
    Logger log = Logger.getLogger(IntegrationApplication.class.getName());

    public static void main(String[] args) throws InterruptedException {
        SpringApplication.run(IntegrationApplication.class, args);
		Thread.currentThread().join();
    }

    @Bean
    IntegrationFlow inboundFileFlow(@Value("${HOME}/Desktop/in") File in,
                                    @Value("${HOME}/Desktop/out") File out) {
        FileReadingMessageSource inboundFileAdapter = Files.inboundAdapter(in)
                .autoCreateDirectory(true)
                .recursive(true)
                .getObject();
        FileWritingMessageHandler outboundFileAdapter = Files.outboundAdapter(out)
                .autoCreateDirectory(true)
                .fileNameGenerator(message -> Long.toString(System.currentTimeMillis()))
                .fileExistsMode(FileExistsMode.FAIL)
                .deleteSourceFiles(true)
                .getObject();
        return IntegrationFlow
                .from(inboundFileAdapter, c -> c.poller(p -> PollerFactory.fixedRate(Duration.ofSeconds(1))))
                .filter(File.class, source -> source.isFile() && source.getName().endsWith(".csv"))
                .handle(new GenericHandler<File>() {
                    @Override
                    public Object handle(File payload, MessageHeaders headers) {
                        log.info(String.format("Received: %s", payload.getAbsolutePath()));
                        headers.forEach((k, v) -> log.info(String.format("%s: %s", k, v)));
                        return payload;
                    }
                })
                .handle(outboundFileAdapter)
                .get();
    }


}
