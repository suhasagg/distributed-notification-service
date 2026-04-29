package com.example.notification.service;
import com.example.notification.domain.Notification;import com.example.notification.dto.NotificationResponse;
public class NotificationMapper { public static NotificationResponse toResponse(Notification n){ return new NotificationResponse(n.id,n.tenantId,n.userId,n.channel,n.recipient,n.templateId,n.status,n.idempotencyKey,n.createdAt,n.attemptCount,n.maxAttempts,n.failureReason,n.nextRetryAt,n.deadLetteredAt); } }
