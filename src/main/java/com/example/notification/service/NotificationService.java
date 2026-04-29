package com.example.notification.service;
import com.example.notification.domain.*;import com.example.notification.dto.*;import com.example.notification.messaging.NotificationEvent;import com.example.notification.repository.*;import com.fasterxml.jackson.databind.ObjectMapper;import org.springframework.beans.factory.annotation.Value;import org.springframework.kafka.core.KafkaTemplate;import org.springframework.stereotype.Service;import org.springframework.transaction.annotation.Transactional;import java.time.Instant;import java.util.*;
@Service
public class NotificationService{
 private final NotificationRepository repo; private final PreferenceRepository prefRepo; private final KafkaTemplate<String,Object> kafka; private final ObjectMapper mapper; @Value("${app.topics.notification-events}") String topic; @Value("${app.topics.dead-letter-events}") String deadLetterTopic;
 public NotificationService(NotificationRepository r,PreferenceRepository p,KafkaTemplate<String,Object> k,ObjectMapper m){repo=r;prefRepo=p;kafka=k;mapper=m;}
 @Transactional public NotificationResponse enqueue(String tenantId,CreateNotificationRequest req){
  if(req.idempotencyKey()!=null && !req.idempotencyKey().isBlank()){ Optional<Notification> existing=repo.findByTenantIdAndIdempotencyKey(tenantId,req.idempotencyKey()); if(existing.isPresent()) return NotificationMapper.toResponse(existing.get()); }
  Notification n=new Notification(); n.tenantId=tenantId; n.userId=req.userId(); n.channel=req.channel(); n.recipient=req.recipient(); n.templateId=req.templateId()!=null?req.templateId():req.templateKey(); n.idempotencyKey=req.idempotencyKey();
  if(req.payloadJson()!=null && !req.payloadJson().isBlank()){ n.payloadJson=req.payloadJson(); } else { try{n.payloadJson=mapper.writeValueAsString(req.payload()==null?Map.of():req.payload());}catch(Exception e){n.payloadJson="{}";} }
  n.status=NotificationStatus.QUEUED; n.updatedAt=Instant.now(); n=repo.save(n); publish(n); return NotificationMapper.toResponse(n);
 }
 public NotificationResponse get(String tenantId,String id){return repo.findByTenantIdAndId(tenantId,id).map(NotificationMapper::toResponse).orElseThrow();}
 public List<NotificationResponse> history(String tenantId,String userId){return repo.findTop50ByTenantIdAndUserIdOrderByCreatedAtDesc(tenantId,userId).stream().map(NotificationMapper::toResponse).toList();}
 public List<NotificationResponse> deadLetters(String tenantId){return repo.findTop50ByTenantIdAndStatusOrderByUpdatedAtDesc(tenantId,NotificationStatus.DEAD_LETTERED).stream().map(NotificationMapper::toResponse).toList();}
 @Transactional public NotificationResponse retry(String tenantId,String id){
  Notification n=repo.findByTenantIdAndId(tenantId,id).orElseThrow();
  if(n.status==NotificationStatus.SENT) return NotificationMapper.toResponse(n);
  // Make manual retry deterministic for local/API testing. Each retry attempt is processed immediately.
  n.attemptCount++;
  n.updatedAt=Instant.now();
  if(isDeliveryBlockedByPreference(n)){
   n.status=NotificationStatus.SUPPRESSED_DUPLICATE; n.failureReason="USER_PREFERENCE_DISABLED"; n.nextRetryAt=null; n.deadLetteredAt=null; return NotificationMapper.toResponse(repo.save(n));
  }
  if(shouldSimulateProviderFailure(n)){
   n.failureReason="SIMULATED_PROVIDER_FAILURE";
   if(n.attemptCount>=n.maxAttempts){
    n.status=NotificationStatus.DEAD_LETTERED; n.deadLetteredAt=Instant.now(); n.nextRetryAt=null; n=repo.save(n); publishDeadLetter(n); return NotificationMapper.toResponse(n);
   }
   n.status=NotificationStatus.FAILED; n.nextRetryAt=Instant.now().plusSeconds((long)Math.pow(2,n.attemptCount)*30); n.deadLetteredAt=null; return NotificationMapper.toResponse(repo.save(n));
  }
  n.status=NotificationStatus.SENT; n.failureReason=null; n.nextRetryAt=null; n.deadLetteredAt=null; return NotificationMapper.toResponse(repo.save(n));
 }
 @Transactional public NotificationResponse requeueDeadLetter(String tenantId,String id){
  Notification n=repo.findByTenantIdAndId(tenantId,id).orElseThrow();
  if(n.status!=NotificationStatus.DEAD_LETTERED) return NotificationMapper.toResponse(n);
  n.status=NotificationStatus.QUEUED; n.attemptCount=0; n.failureReason=null; n.deadLetteredAt=null; n.nextRetryAt=null; n.updatedAt=Instant.now(); n=repo.save(n); publish(n); return NotificationMapper.toResponse(n);
 }
 @Transactional public PreferenceResponse upsertPreference(String tenantId,String userId,PreferenceRequest req){ NotificationPreference p=prefRepo.findByTenantIdAndUserId(tenantId,userId).orElseGet(NotificationPreference::new); p.tenantId=tenantId;p.userId=userId; if(req.emailEnabled()!=null)p.emailEnabled=req.emailEnabled(); if(req.smsEnabled()!=null)p.smsEnabled=req.smsEnabled(); if(req.pushEnabled()!=null)p.pushEnabled=req.pushEnabled(); p.updatedAt=Instant.now(); prefRepo.save(p); return new PreferenceResponse(userId,p.emailEnabled,p.smsEnabled,p.pushEnabled);}
 public PreferenceResponse getPreference(String tenantId,String userId){ NotificationPreference p=prefRepo.findByTenantIdAndUserId(tenantId,userId).orElseGet(()->{NotificationPreference x=new NotificationPreference();x.tenantId=tenantId;x.userId=userId;return x;}); return new PreferenceResponse(userId,p.emailEnabled,p.smsEnabled,p.pushEnabled);}
 private boolean shouldSimulateProviderFailure(Notification n){ return (n.recipient!=null && n.recipient.toLowerCase().contains("fail")) || (n.payloadJson!=null && n.payloadJson.contains("forceFail")); }
 private boolean isDeliveryBlockedByPreference(Notification n){ return prefRepo.findByTenantIdAndUserId(n.tenantId,n.userId).map(p -> switch(n.channel){case EMAIL -> !p.emailEnabled; case SMS -> !p.smsEnabled; case PUSH -> !p.pushEnabled;}).orElse(false); }
 private void publish(Notification n){ kafka.send(topic,n.tenantId,new NotificationEvent(n.id,n.tenantId,n.userId,n.channel,n.recipient,n.templateId,n.payloadJson)); }
 private void publishDeadLetter(Notification n){ kafka.send(deadLetterTopic,n.tenantId,new NotificationEvent(n.id,n.tenantId,n.userId,n.channel,n.recipient,n.templateId,n.payloadJson)); }
}
