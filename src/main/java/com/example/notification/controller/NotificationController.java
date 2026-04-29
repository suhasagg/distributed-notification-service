package com.example.notification.controller;
import com.example.notification.dto.*;import com.example.notification.service.NotificationService;import org.springframework.web.bind.annotation.*;import java.util.*;
@RestController
public class NotificationController{
 private final NotificationService service; public NotificationController(NotificationService s){service=s;}
 @GetMapping("/health") public Map<String,Object> health(){return Map.of("status","UP");}
 @PostMapping("/notifications") public NotificationResponse create(@RequestHeader("X-Tenant-ID") String tenantId,@RequestBody CreateNotificationRequest req){return service.enqueue(tenantId,req);}
 @GetMapping("/notifications/{id}") public NotificationResponse get(@RequestHeader("X-Tenant-ID") String tenantId,@PathVariable("id") String id){return service.get(tenantId,id);}
 @PostMapping("/notifications/{id}/retry") public NotificationResponse retry(@RequestHeader("X-Tenant-ID") String tenantId,@PathVariable("id") String id){return service.retry(tenantId,id);}
 @GetMapping("/users/{userId}/notifications") public List<NotificationResponse> history(@RequestHeader("X-Tenant-ID") String tenantId,@PathVariable("userId") String userId){return service.history(tenantId,userId);}
 @PutMapping("/users/{userId}/preferences") public PreferenceResponse upsertPref(@RequestHeader("X-Tenant-ID") String tenantId,@PathVariable("userId") String userId,@RequestBody PreferenceRequest req){return service.upsertPreference(tenantId,userId,req);}
 @GetMapping("/users/{userId}/preferences") public PreferenceResponse getPref(@RequestHeader("X-Tenant-ID") String tenantId,@PathVariable("userId") String userId){return service.getPreference(tenantId,userId);}
 @GetMapping("/dead-letter") public List<NotificationResponse> deadLetters(@RequestHeader("X-Tenant-ID") String tenantId){return service.deadLetters(tenantId);}
 @PostMapping("/dead-letter/{id}/requeue") public NotificationResponse requeueDeadLetter(@RequestHeader("X-Tenant-ID") String tenantId,@PathVariable("id") String id){return service.requeueDeadLetter(tenantId,id);}
}
