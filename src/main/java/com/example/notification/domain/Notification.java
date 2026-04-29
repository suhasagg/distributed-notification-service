package com.example.notification.domain;
import jakarta.persistence.*;import java.time.Instant;import java.util.UUID;
@Entity @Table(name="notifications", indexes={@Index(name="idx_notification_tenant_user", columnList="tenantId,userId"), @Index(name="idx_notification_status", columnList="status"), @Index(name="idx_notification_tenant_status", columnList="tenantId,status")})
public class Notification {
 @Id public String id=UUID.randomUUID().toString();
 public String tenantId; public String userId; @Enumerated(EnumType.STRING) public NotificationChannel channel; public String recipient; public String templateId;
 @Column(length=4000) public String payloadJson; @Enumerated(EnumType.STRING) public NotificationStatus status=NotificationStatus.QUEUED; public String idempotencyKey;
 public int attemptCount=0; public int maxAttempts=3; @Column(length=1000) public String failureReason; public Instant nextRetryAt; public Instant deadLetteredAt;
 public Instant createdAt=Instant.now(); public Instant updatedAt=Instant.now();
}
