package com.example.notification.domain;
import jakarta.persistence.*;import java.time.Instant;import java.util.UUID;
@Entity @Table(name="notification_preferences", indexes=@Index(name="idx_pref_tenant_user", columnList="tenantId,userId"))
public class NotificationPreference { @Id public String id=UUID.randomUUID().toString(); public String tenantId; public String userId; public boolean emailEnabled=true; public boolean smsEnabled=true; public boolean pushEnabled=true; public Instant updatedAt=Instant.now(); }
