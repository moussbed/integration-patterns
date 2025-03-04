package com.mb.integration;

import org.springframework.boot.ApplicationRunner;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.context.annotation.Bean;
import org.springframework.integration.annotation.IntegrationComponentScan;
import org.springframework.integration.core.GenericHandler;
import org.springframework.integration.dsl.IntegrationFlow;
import org.springframework.integration.dsl.MessageChannels;
import org.springframework.integration.splitter.AbstractMessageSplitter;
import org.springframework.integration.support.MessageBuilder;
import org.springframework.messaging.Message;
import org.springframework.messaging.MessageChannel;
import org.springframework.messaging.MessageHeaders;

import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;


@IntegrationComponentScan
@SpringBootApplication
public class IntegrationApplication {

    private final Map<Long, Order> ordersDb = new ConcurrentHashMap<>();

    public static void main(String[] args) throws InterruptedException {
        SpringApplication.run(IntegrationApplication.class, args);
		Thread.currentThread().join();
    }

    @Bean
    ApplicationRunner runner(MessageChannel orderChannel) {
        return args -> {
            var order = new Order(1L,
                    Set.of(new LineItem("sku1"), new LineItem("sku2"), new LineItem("sku3")));
            ordersDb.put(order.id(), order);
            orderChannel.send(MessageBuilder.withPayload(order).setHeader("orderId", order.id()).build());

        };
    }

    @Bean
    MessageChannel orderChannel() {
        return MessageChannels.direct().getObject();
    }

    @Bean
    MessageChannel loggingChannel() {
        return MessageChannels.direct().getObject();
    }

    @Bean
    IntegrationFlow orderFlow() {
        return IntegrationFlow.from(orderChannel())
                .split(new AbstractMessageSplitter() {
                    @Override
                    protected Object splitMessage(Message<?> message) {
                        Order order = (Order) message.getPayload();
                        System.out.println("Got the order = " + order);
                        return order.lineItems();
                    }
                })
                .handle(new GenericHandler<LineItem>() {
                    @Override
                    public Object handle(LineItem lineItem, MessageHeaders headers) {
                        headers.forEach((k, v) -> System.out.println(k + " = " + v));
                        return lineItem;
                    }
                })
                .wireTap(loggingChannel())
                .aggregate()
                .handle(new GenericHandler<Object>() {
                    @Override
                    public Object handle(Object payload, MessageHeaders headers) {
                        System.out.println("Aggregated payload = " + payload);
                        return null;
                    }
                })
                .get();
    }

    @Bean
    IntegrationFlow loggingFlow() {
        return IntegrationFlow.from(loggingChannel())
                .handle(message -> {
                    System.out.println("Logging message = " + message);
                })
                .get();
    }

}

record Order(Long id, Set<LineItem> lineItems) {}
record LineItem(String sku) {}
