package com.example.notification.dto;
import com.example.notification.domain.*;import java.time.Instant;
public record NotificationResponse(String id,String tenantId,String userId,NotificationChannel channel,String recipient,String templateId,NotificationStatus status,String idempotencyKey,Instant createdAt,int attemptCount,int maxAttempts,String failureReason,Instant nextRetryAt,Instant deadLetteredAt){}
