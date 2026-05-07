package com.assignease.config;

import org.springframework.amqp.core.*;
import org.springframework.amqp.rabbit.config.RetryInterceptorBuilder;
import org.springframework.amqp.rabbit.config.SimpleRabbitListenerContainerFactory;
import org.springframework.amqp.rabbit.connection.ConnectionFactory;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.amqp.rabbit.retry.RejectAndDontRequeueRecoverer;
import org.springframework.amqp.support.converter.Jackson2JsonMessageConverter;
import org.springframework.amqp.support.converter.MessageConverter;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.retry.interceptor.RetryOperationsInterceptor;

/**
 * RabbitMQ topology — full retry + DLQ + manual ack.
 *
 * ┌─────────────────────────────────────────────────────────────────────────┐
 * │  FLOW                                                                    │
 * │                                                                          │
 * │  Producer → eduassist.email (topic exchange)                            │
 * │           → routing key: email.*                                         │
 * │           → eduassist.email.queue  (main queue)                         │
 * │                                                                          │
 * │  Consumer (EmailConsumer) reads from main queue:                        │
 * │    Success → ack()  → message gone                                      │
 * │    Transient fail → Spring Retry: 3 attempts, 2s/4s/8s backoff         │
 * │    All retries exhausted → nack(requeue=false) → DLX → DLQ             │
 * │                                                                          │
 * │  DlqRetryConsumer reads from DLQ:                                       │
 * │    Waits 5 minutes (x-message-ttl on DLQ)                              │
 * │    Re-publishes to main exchange (up to DLQ_MAX_ATTEMPTS times)         │
 * │    If DLQ_MAX_ATTEMPTS exceeded → nack → dead.letter (final grave)     │
 * │                                                                          │
 * │  AcknowledgeMode.MANUAL:                                                │
 * │    Message is NOT ack'd until consumer explicitly calls channel.basicAck│
 * │    If consumer crashes → message stays unacked → broker re-delivers    │
 * │    Zero message loss even on JVM crash                                  │
 * └─────────────────────────────────────────────────────────────────────────┘
 */
@Configuration
public class RabbitMQConfig {

    // Queue / exchange names
    public static final String EMAIL_EXCHANGE    = "eduassist.email";
    public static final String EMAIL_QUEUE       = "eduassist.email.queue";
    public static final String EMAIL_DLQ         = "eduassist.email.queue.dlq";
    public static final String EMAIL_DEAD        = "eduassist.email.queue.dead"; // final grave
    public static final String DLX_EXCHANGE      = "eduassist.email.dlx";
    public static final String DEAD_EXCHANGE      = "eduassist.email.dead";
    public static final String EMAIL_ROUTING_KEY = "email.#";

    /** How many times the DLQ consumer re-publishes before giving up */
    public static final int DLQ_MAX_ATTEMPTS = 3;

    /** Header stamped on each message to track DLQ retry count */
    public static final String DLQ_RETRY_HEADER = "x-dlq-retry-count";

    // ── Final dead exchange + queue (messages that exhaust DLQ retries) ───────
    @Bean DirectExchange deadExchange() {
        return new DirectExchange(DEAD_EXCHANGE, true, false);
    }
    @Bean Queue deadQueue() {
        return QueueBuilder.durable(EMAIL_DEAD).build();
    }
    @Bean Binding deadBinding() {
        return BindingBuilder.bind(deadQueue()).to(deadExchange()).with(EMAIL_DEAD);
    }

    // ── Dead-letter exchange + DLQ (with TTL — 5 min wait before re-try) ─────
    @Bean DirectExchange dlxExchange() {
        return new DirectExchange(DLX_EXCHANGE, true, false);
    }
    @Bean Queue dlQueue() {
        return QueueBuilder.durable(EMAIL_DLQ)
            // After TTL expires, message moves back to main via dlx-dead-letter settings
            .withArgument("x-message-ttl",            5 * 60 * 1000)  // 5 minutes
            .withArgument("x-dead-letter-exchange",   EMAIL_EXCHANGE)
            .withArgument("x-dead-letter-routing-key", "email.retry")
            .build();
    }
    @Bean Binding dlqBinding() {
        return BindingBuilder.bind(dlQueue()).to(dlxExchange()).with(EMAIL_QUEUE);
    }

    // ── Main exchange + queue ─────────────────────────────────────────────────
    @Bean TopicExchange emailExchange() {
        return new TopicExchange(EMAIL_EXCHANGE, true, false);
    }
    @Bean Queue emailQueue() {
        return QueueBuilder.durable(EMAIL_QUEUE)
            .withArgument("x-dead-letter-exchange",    DLX_EXCHANGE)
            .withArgument("x-dead-letter-routing-key", EMAIL_QUEUE)
            .build();
    }
    @Bean Binding emailBinding() {
        return BindingBuilder.bind(emailQueue()).to(emailExchange()).with(EMAIL_ROUTING_KEY);
    }
    // Also bind the retry routing key so DLQ re-published messages are received
    @Bean Binding emailRetryBinding() {
        return BindingBuilder.bind(emailQueue()).to(emailExchange()).with("email.retry");
    }

    // ── JSON converter ────────────────────────────────────────────────────────
    @Bean MessageConverter jsonMessageConverter() {
        return new Jackson2JsonMessageConverter();
    }

    @Bean RabbitTemplate rabbitTemplate(ConnectionFactory cf) {
        RabbitTemplate t = new RabbitTemplate(cf);
        t.setMessageConverter(jsonMessageConverter());
        return t;
    }

    // ── Main consumer factory — MANUAL ack + Spring Retry ────────────────────
    /**
     * MANUAL ack: broker holds the message as "unacked" until consumer
     * explicitly calls channel.basicAck(). If the JVM crashes, the broker
     * re-delivers to the next available consumer. Zero loss.
     *
     * Spring Retry interceptor: 3 attempts with exponential backoff
     *   attempt 1 → immediate
     *   attempt 2 → wait 2s
     *   attempt 3 → wait 4s
     * After all 3 fail → RejectAndDontRequeueRecoverer → nack(requeue=false) → DLQ
     */
    @Bean
    public SimpleRabbitListenerContainerFactory rabbitListenerContainerFactory(
            ConnectionFactory cf) {

        RetryOperationsInterceptor retryInterceptor = RetryInterceptorBuilder.stateless()
            .maxAttempts(3)
            .backOffOptions(2_000, 2.0, 8_000)   // initial=2s, multiplier=2, max=8s
            .recoverer(new RejectAndDontRequeueRecoverer())
            .build();

        SimpleRabbitListenerContainerFactory f = new SimpleRabbitListenerContainerFactory();
        f.setConnectionFactory(cf);
        f.setMessageConverter(jsonMessageConverter());
        f.setAcknowledgeMode(AcknowledgeMode.MANUAL);
        f.setDefaultRequeueRejected(false);   // exhausted retries → DLQ, not requeue
        f.setConcurrentConsumers(2);
        f.setMaxConcurrentConsumers(5);
        f.setAdviceChain(retryInterceptor);
        return f;
    }

    // ── DLQ consumer factory — MANUAL ack, NO retry interceptor ──────────────
    /**
     * The DLQ consumer does its own retry-count logic.
     * We don't want Spring Retry here — it would re-send 3 more times
     * before routing back to DLQ, creating an infinite loop.
     */
    @Bean
    public SimpleRabbitListenerContainerFactory dlqListenerContainerFactory(
            ConnectionFactory cf) {
        SimpleRabbitListenerContainerFactory f = new SimpleRabbitListenerContainerFactory();
        f.setConnectionFactory(cf);
        f.setMessageConverter(jsonMessageConverter());
        f.setAcknowledgeMode(AcknowledgeMode.MANUAL);
        f.setDefaultRequeueRejected(false);
        f.setConcurrentConsumers(1);
        return f;
    }
}
