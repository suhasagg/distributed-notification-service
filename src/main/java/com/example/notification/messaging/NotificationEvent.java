package com.example.notification.messaging;
import com.example.notification.domain.NotificationChannel;
public record NotificationEvent(String notificationId,String tenantId,String userId,NotificationChannel channel,String recipient,String templateId,String payloadJson){}
