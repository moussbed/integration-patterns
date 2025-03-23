package com.mb.integration;

import com.fasterxml.jackson.annotation.JsonProperty;
import org.springframework.amqp.core.*;
import org.springframework.amqp.rabbit.annotation.RabbitListener;
import org.springframework.amqp.rabbit.connection.ConnectionFactory;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.amqp.support.converter.Jackson2JsonMessageConverter;
import org.springframework.boot.ApplicationRunner;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.integration.amqp.dsl.Amqp;
import org.springframework.integration.annotation.IntegrationComponentScan;
import org.springframework.integration.core.GenericHandler;
import org.springframework.integration.dsl.IntegrationFlow;
import org.springframework.integration.dsl.MessageChannels;
import org.springframework.integration.support.MessageBuilder;
import org.springframework.messaging.MessageChannel;
import org.springframework.messaging.MessageHeaders;
import org.springframework.messaging.handler.annotation.Payload;
import org.springframework.scheduling.annotation.EnableScheduling;
import org.springframework.scheduling.annotation.Scheduled;

import java.io.Serializable;
import java.io.UnsupportedEncodingException;
import java.nio.charset.StandardCharsets;
import java.util.Map;
import java.util.Random;
import java.util.logging.Logger;


@IntegrationComponentScan
@SpringBootApplication
@EnableScheduling
public class IntegrationApplication {

    record CustomMessage(@JsonProperty("message")String message, @JsonProperty("priority") int priority, @JsonProperty("secret")boolean secret) implements Serializable { }

    Logger log = Logger.getLogger(IntegrationApplication.class.getName());

    private final AmqpTemplate amqpTemplate;

    public IntegrationApplication(AmqpTemplate amqpTemplate) {
        this.amqpTemplate = amqpTemplate;
    }

    public static void main(String[] args) throws InterruptedException {
        SpringApplication.run(IntegrationApplication.class, args);
        Thread.currentThread().join();
    }




//    @Bean
//    ApplicationRunner runner(RabbitTemplate rabbitTemplate) {
//        return args -> {
//            log.info("Application started");
//            rabbitTemplate.convertAndSend(RabbitMQConfiguration.EXCHANGE_NAME, RabbitMQConfiguration.ROUTING_KEY, MessageBuilder.withBody("Hello from RabbitMQ".getBytes()).build());
//        };
//    }
//
//    @RabbitListener(queues = RabbitMQConfiguration.QUEUE_NAME)
//    public void listen(@Payload  Map<String,String> payload) {
//        log.info("Message read from RabbitMQ: " + payload);
//    }

    @Bean
    MessageChannel requests() {
        return MessageChannels.direct().getObject();
    }

    @Bean
    ApplicationRunner runner (MessageChannel requests) {
        return args -> {
            log.info("Application started");
            var data = Map.of("name", "John", "age", "25");
           requests.send(MessageBuilder.withPayload(data).build());
        };
    }
    @Scheduled(fixedDelay = 5000L)
    public void sendMessage() {
        final int priority = new Random().nextInt(50);
        final boolean secret = priority > 25 ;
        final var message = new CustomMessage("Hello there!", priority, secret);
        log.info("Sending message...");
        amqpTemplate.convertAndSend(RabbitMQConfiguration.EXCHANGE_NAME, RabbitMQConfiguration.ROUTING_KEY, message);
    }

    @Bean
    IntegrationFlow rabbitProducerFlow(AmqpTemplate amqpTemplate) {
        return IntegrationFlow
                .from(requests())
                .handle(Amqp.outboundAdapter(amqpTemplate)
                        .exchangeName(RabbitMQConfiguration.EXCHANGE_NAME)
                        .routingKey(RabbitMQConfiguration.ROUTING_KEY))
                .get();
    }

    @Bean
    IntegrationFlow integrationFlow(ConnectionFactory connectionFactory, Queue queue) {
        return IntegrationFlow
                .from(Amqp.inboundAdapter(connectionFactory, queue)
                        .configureContainer(c -> c.queueName(RabbitMQConfiguration.QUEUE_NAME)))
                .handle(new GenericHandler<Object>() {
                    @Override
                    public Object handle(Object payload, MessageHeaders headers) {
                        log.info("Message read from RabbitMQ: " + new String((byte[]) payload, StandardCharsets.UTF_8));
                        headers.forEach((k, v) -> log.info(k + ":" + v));
                        return null;
                    }
                })
                .get();
    }


}

@Configuration
class RabbitMQConfiguration {
    public static final String QUEUE_NAME = "integration.queue";
    public static final String EXCHANGE_NAME = "integration.exchange";
    public static final String ROUTING_KEY = "integration.routingKey";

    @Bean
    Exchange exchange() {
        return ExchangeBuilder.directExchange(EXCHANGE_NAME).durable(true).build();
    }

    @Bean
    Queue queue() {
        return QueueBuilder.durable(QUEUE_NAME).build();
    }

    @Bean
    Binding binding() {
        return BindingBuilder.bind(queue()).to(exchange()).with(ROUTING_KEY).noargs();
    }

    @Bean
    AmqpTemplate amqpTemplate(ConnectionFactory connectionFactory) {
        final RabbitTemplate rabbitTemplate = new RabbitTemplate(connectionFactory);
        rabbitTemplate.setMessageConverter(producerJackson2MessageConverter());
        return rabbitTemplate;
    }

    @Bean
    public Jackson2JsonMessageConverter producerJackson2MessageConverter() {
        return new Jackson2JsonMessageConverter();
    }
}
