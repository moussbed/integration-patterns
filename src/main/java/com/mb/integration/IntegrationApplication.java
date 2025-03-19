package com.mb.integration;

import org.springframework.amqp.core.*;
import org.springframework.amqp.rabbit.annotation.RabbitListener;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.boot.ApplicationRunner;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.integration.annotation.IntegrationComponentScan;

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
    ApplicationRunner runner(RabbitTemplate rabbitTemplate) {
        return args -> {
            log.info("Application started");
            rabbitTemplate.convertAndSend(RabbitMQConfiguration.EXCHANGE_NAME, RabbitMQConfiguration.ROUTING_KEY, MessageBuilder.withBody("Hello from RabbitMQ".getBytes()).build());
        };
    }

    @RabbitListener(queues = RabbitMQConfiguration.QUEUE_NAME)
    public void listen(String in) {
        log.info("Message read from RabbitMQ: " + in);
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
}



