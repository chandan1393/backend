package com.assignease.config;

import org.springframework.amqp.core.*;
import org.springframework.amqp.rabbit.connection.ConnectionFactory;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.amqp.support.converter.Jackson2JsonMessageConverter;
import org.springframework.amqp.support.converter.MessageConverter;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class RabbitMQConfig {

    public static final String EMAIL_QUEUE = "email.queue";
    public static final String EMAIL_EXCHANGE = "email.exchange";
    public static final String EMAIL_ROUTING_KEY = "email.routing";
    public static final String DLQ = "email.dlq";

    @Bean
    public Queue emailQueue() {
        return QueueBuilder.durable(EMAIL_QUEUE)
                .withArgument("x-dead-letter-exchange", EMAIL_EXCHANGE + ".dlx")
                .build();
    }

    @Bean
    public DirectExchange exchange() {
        return new DirectExchange(EMAIL_EXCHANGE);
    }

    @Bean
    public Binding binding() {
        return BindingBuilder.bind(emailQueue())
                .to(exchange())
                .with(EMAIL_ROUTING_KEY);
    }

    @Bean
    public Queue deadLetterQueue() {
        return new Queue(DLQ);
    }

    @Bean
    public DirectExchange deadLetterExchange() {
        return new DirectExchange(EMAIL_EXCHANGE + ".dlx");
    }

    @Bean
    public Binding dlqBinding() {
        return BindingBuilder.bind(deadLetterQueue())
                .to(deadLetterExchange())
                .with(EMAIL_ROUTING_KEY);
    }

    @Bean
    public MessageConverter jsonMessageConverter() {
        return new Jackson2JsonMessageConverter();
    }

    @Bean
    public AmqpTemplate amqpTemplate(ConnectionFactory connectionFactory) {
        RabbitTemplate template = new RabbitTemplate(connectionFactory);
        template.setMessageConverter(jsonMessageConverter());
        return template;
    }
}