package com.everdeliver.worker;

import com.everdeliver.common.NotificationRequest;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.mail.SimpleMailMessage;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.stereotype.Service;

@Service
@Slf4j
@RequiredArgsConstructor
public class NotificationConsumer {

    private final JavaMailSender mailSender;

    @KafkaListener(topics = "notification-topic", groupId = "everdeliver-group")
    public void consume(NotificationRequest request) {
        log.info("Processing email for: {}", request.getEmail());

        SimpleMailMessage message = new SimpleMailMessage();
        message.setFrom("noreply@everdeliver.com");
        message.setTo(request.getEmail());
        message.setSubject(request.getSubject());
        message.setText(request.getMessage());

        mailSender.send(message);
        log.info("Email successfully sent to Mailpit!");
    }
}