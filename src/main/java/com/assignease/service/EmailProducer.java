package com.assignease.service;



import com.assignease.config.RabbitMQConfig;
import com.assignease.dto.EmailRequest;
import lombok.RequiredArgsConstructor;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
public class EmailProducer {

    private final RabbitTemplate rabbitTemplate;

    public void sendEmail(EmailRequest request) {
        rabbitTemplate.convertAndSend(
                RabbitMQConfig.EMAIL_EXCHANGE,
                RabbitMQConfig.EMAIL_ROUTING_KEY,
                request
        );
    }
}
