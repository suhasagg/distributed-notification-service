package com.example.notification.config;
import org.apache.kafka.clients.admin.NewTopic;import org.springframework.context.annotation.*;import org.springframework.kafka.config.TopicBuilder;
@Configuration public class KafkaConfig { @Bean NewTopic notificationEvents(){return TopicBuilder.name("notification-events").partitions(3).replicas(1).build();} @Bean NewTopic deadLetterEvents(){return TopicBuilder.name("notification-dead-letter-events").partitions(3).replicas(1).build();}}
