package com.example.notification.service;
import com.example.notification.domain.*;import com.example.notification.messaging.NotificationEvent;import com.example.notification.repository.*;import org.springframework.beans.factory.annotation.Value;import org.springframework.data.redis.core.StringRedisTemplate;import org.springframework.kafka.annotation.KafkaListener;import org.springframework.kafka.core.KafkaTemplate;import org.springframework.stereotype.Service;import java.time.Duration;import java.time.Instant;
@Service
public class DispatcherService{
 private final NotificationRepository repo; private final PreferenceRepository prefRepo; private final StringRedisTemplate redis; private final KafkaTemplate<String,Object> kafka; @Value("${app.topics.dead-letter-events}") String deadLetterTopic;
 public DispatcherService(NotificationRepository r,PreferenceRepository p,StringRedisTemplate redis,KafkaTemplate<String,Object> kafka){repo=r;prefRepo=p;this.redis=redis;this.kafka=kafka;}
 @KafkaListener(topics="notification-events", groupId="notification-dispatcher")
 public void dispatch(NotificationEvent event){
  Notification n=repo.findByTenantIdAndId(event.tenantId(),event.notificationId()).orElse(null); if(n==null)return;
  try{
   n.attemptCount++; n.updatedAt=Instant.now();
   String rateKey="rl:"+event.tenantId()+":"+event.userId()+":"+event.channel(); Long count=redis.opsForValue().increment(rateKey); if(count!=null && count==1)redis.expire(rateKey, Duration.ofMinutes(1));
   if(count!=null && count>20){ markFailedOrDeadLetter(n,"RATE_LIMIT_EXCEEDED"); return; }
   var pref=prefRepo.findByTenantIdAndUserId(event.tenantId(),event.userId()); boolean allowed=pref.map(p -> switch(event.channel()){case EMAIL -> p.emailEnabled; case SMS -> p.smsEnabled; case PUSH -> p.pushEnabled;}).orElse(true);
   if(!allowed){ n.status=NotificationStatus.SUPPRESSED_DUPLICATE; n.failureReason="USER_PREFERENCE_DISABLED"; n.updatedAt=Instant.now(); repo.save(n); return; }
   if(shouldSimulateProviderFailure(n)){ markFailedOrDeadLetter(n,"SIMULATED_PROVIDER_FAILURE"); return; }
   n.status=NotificationStatus.SENT; n.failureReason=null; n.nextRetryAt=null; n.deadLetteredAt=null; n.updatedAt=Instant.now(); repo.save(n);
  } catch(Exception ex){ markFailedOrDeadLetter(n,ex.getMessage()==null?"DISPATCH_EXCEPTION":ex.getMessage()); }
 }
 private boolean shouldSimulateProviderFailure(Notification n){ return (n.recipient!=null && n.recipient.toLowerCase().contains("fail")) || (n.payloadJson!=null && n.payloadJson.contains("forceFail")); }
 private void markFailedOrDeadLetter(Notification n,String reason){
  n.failureReason=reason; n.updatedAt=Instant.now();
  if(n.attemptCount>=n.maxAttempts){ n.status=NotificationStatus.DEAD_LETTERED; n.deadLetteredAt=Instant.now(); n.nextRetryAt=null; repo.save(n); kafka.send(deadLetterTopic,n.tenantId,new NotificationEvent(n.id,n.tenantId,n.userId,n.channel,n.recipient,n.templateId,n.payloadJson)); }
  else { n.status=NotificationStatus.FAILED; n.nextRetryAt=Instant.now().plusSeconds((long)Math.pow(2,n.attemptCount)*30); repo.save(n); }
 }
}
