package com.example.notification.dto;
import com.example.notification.domain.NotificationChannel;import java.util.Map;
public record CreateNotificationRequest(String userId, NotificationChannel channel, String recipient, String templateId, String templateKey, Map<String,Object> payload, String payloadJson, String idempotencyKey){}
