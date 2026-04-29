package com.example.notification.repository;
import com.example.notification.domain.NotificationPreference;import org.springframework.data.jpa.repository.JpaRepository;import java.util.*;
public interface PreferenceRepository extends JpaRepository<NotificationPreference,String>{ Optional<NotificationPreference> findByTenantIdAndUserId(String tenantId,String userId); }
