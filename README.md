# Distributed Notification Delivery Service

A multi-tenant, event-driven notification platform designed to send user notifications through multiple channels such as Email, SMS, and Push.

This project implements:

- PostgreSQL as source of truth
- Kafka for asynchronous notification delivery
- Redis-ready design for rate limiting and idempotency
- Spring Boot API layer
- Multi-tenant notification isolation
- User notification preferences
- Retry handling
- Dead-letter queue support
- Notification history APIs

## 1. Problem Statement

Modern products need to send reliable notifications to users.

The system should support:

- Sending notifications through Email, SMS, and Push
- Storing notification history
- Respecting user preferences
- Supporting retry for failed notifications
- Moving repeatedly failed notifications to dead-letter queue
- Supporting multi-tenant isolation
- Scaling delivery workers independently
- Preventing duplicate sends
- Supporting auditability and operational debugging

Typical use cases:

- Welcome email
- OTP SMS
- Order status update
- Payment failure alert
- Marketing campaign
- Push notification
- Billing reminder
- Security alert

## 2. High-Level Architecture
                         +----------------------+
                         |       Clients        |
                         | Admin UI / Backend   |
                         +----------+-----------+
                                    |
                                    v
                         +----------+-----------+
                         |    Spring Boot API   |
                         | Notification APIs    |
                         +----+------------+----+
                              |            |
                    Write path|            |Read path
                              |            |
                              v            v
                       +------+---+   +----+------+
                       |PostgreSQL|   |   Redis   |
                       |Source of |   | Rate limit|
                       |Truth     |   | Idempotency|
                       +------+---+   +----+------+
                              |
                              v
                       +------+----------------+
                       | Kafka                 |
                       | notification-events   |
                       +------+----------------+
                              |
                              v
                    +---------+-------------+
                    | Delivery Worker       |
                    | Email / SMS / Push    |
                    +----+------------+-----+
                         |            |
                         v            v
                 +-------+---+   +----+----------------+
                 | Providers |   | Dead Letter Queue   |
                 | SES/Twilio|   | failed notifications|
                 +-----------+   +---------------------+

## 3. Core Components

## 3.1 Spring Boot API

Responsibilities:

- Accept notification requests
- Validate tenant header
- Store notification in PostgreSQL
- Publish notification event to Kafka
- Expose notification history
- Manage user preferences
- Retry failed notifications
- Requeue dead-lettered notifications

APIs:

- GET  /health
- POST /notifications
- GET  /notifications/{id}
- GET  /users/{userId}/notifications
- PUT  /users/{userId}/preferences
- GET  /users/{userId}/preferences
- POST /notifications/{id}/retry
- GET  /dead-letter
- POST /dead-letter/{id}/requeue

## 3.2 PostgreSQL

PostgreSQL is the source of truth.

Stores:

- Notification request
- Tenant ID
- User ID
- Channel
- Recipient
- Template ID
- Payload
- Status
- Attempt count
- Failure reason
- Dead-letter timestamp
- User preferences

Why PostgreSQL:

- Strong consistency
- Durable history
- Easy querying by user and tenant
- Supports audit/debug workflows
- Reliable canonical store

## 3.3 Kafka

Kafka is used for asynchronous delivery.

Main topic:

notification-events

Dead-letter topic:

notification-dead-letter-events

Why Kafka:

- Decouples API from provider latency
- Allows scalable delivery workers
- Buffers traffic spikes
- Supports retries and replay
- Avoids blocking API while sending email/SMS/push

## 3.4 Redis

Redis is useful for production features such as:

- Tenant-level rate limiting
- Idempotency keys
- Deduplication windows
- Provider throttling
- Temporary delivery locks
- Campaign counters

Example keys:

- rate:{tenantId}:{minute}
- idempotency:{tenantId}:{key}
- dedupe:{tenantId}:{userId}:{templateId}
- provider_limit:{provider}:{minute}

## 3.5 Delivery Worker

The worker consumes Kafka messages and sends notifications.

Responsibilities:

- Fetch notification
- Check user preferences
- Render template
- Send via provider
- Mark as SENT on success
- Mark as FAILED on failure
- Retry if attempts remain
- Move to DEAD_LETTERED after max attempts

## 4. Data Model

## 4.1 Notifications Table
notifications

Fields:

- id
- tenant_id
- user_id
- channel
- recipient
- template_id
- payload_json
- status
- idempotency_key
- created_at
- attempt_count
- max_attempts
- failure_reason
- next_retry_at
- dead_lettered_at

Possible statuses:

- QUEUED
- SENT
- FAILED
- RETRYING
- DEAD_LETTERED

## 4.2 User Preferences Table
user_preferences

Fields:

- id
- tenant_id
- user_id
- email_enabled
- sms_enabled
- push_enabled
- updated_at

Purpose:

- Respect opt-in / opt-out
- Prevent sending on disabled channels
- Keep preferences tenant-specific

## 4.3 Dead Letter Queue

Dead-letter records can either be:

- stored as DEAD_LETTERED rows in PostgreSQL
- published to Kafka DLQ topic
- exported to ops dashboard

DLQ topic:

notification-dead-letter-events

DLQ is used for:

- permanent provider failures
- malformed payloads
- invalid recipients
- exhausted retry attempts
- manual recovery

## 5. Notification Lifecycle
QUEUED
  |
  v
SENT

Failure path:

QUEUED
  |
  v
FAILED
  |
  v
RETRYING
  |
  v
FAILED
  |
  v
RETRYING
  |
  v
DEAD_LETTERED

Requeue path:

DEAD_LETTERED
  |
  v
QUEUED

## 6. Retry and DLQ Strategy

Retry is used for transient failures:

- provider timeout
- temporary provider outage
- network issue
- rate-limit response
- 5xx provider error

DLQ is used for repeated or permanent failures:

- invalid email
- invalid phone number
- malformed template payload
- max retries exceeded

Retry logic:

1. Load notification by tenant and id
2. Increment attempt count
3. Try delivery
4. If success, mark SENT
5. If failure and attempts < maxAttempts, mark FAILED / RETRYING
6. If failure and attempts >= maxAttempts, mark DEAD_LETTERED
7. Publish DLQ event

Recommended production retry policy:

- attempt 1: immediate
- attempt 2: after 1 minute
- attempt 3: after 5 minutes
- attempt 4: after 15 minutes
- attempt 5: dead-letter

Use exponential backoff with jitter:

nextRetryAt = now + baseDelay * 2^attempt + random_jitter

## 7. Consistency Model

The system uses mixed consistency.

Strong consistency:

- notification creation in PostgreSQL
- preference updates
- notification status updates
- dead-letter state transition

Eventual consistency:

- actual provider delivery
- Kafka event processing
- retry processing
- delivery analytics

Why this works:

- API should not wait for external providers
- provider calls may be slow or unreliable
- asynchronous delivery improves availability
- user history remains correct through status updates

## 8. Multi-Tenancy Strategy

Tenant is identified using:

X-Tenant-ID

Tenant isolation applies to:

- notification creation
- notification lookup
- user history
- user preferences
- retry endpoint
- dead-letter listing
- requeue endpoint
- Kafka event payload
- Redis keys
- metrics and logs

Example:

tenant-a:user-123
tenant-b:user-123

Both tenants can have the same user ID safely.

## 9. API List

## 9.1 Health Check

```bash
curl http://localhost:8080/health
```

Response:

```json
{
  "status": "UP"
}
```

## 9.2 Create Notification

```bash
curl -X POST http://localhost:8080/notifications \
  -H "Content-Type: application/json" \
  -H "X-Tenant-ID: tenant-a" \
  -d '{
    "userId": "user-123",
    "channel": "EMAIL",
    "recipient": "user123@example.com",
    "templateKey": "welcome_email",
    "payloadJson": "{\"name\":\"Suhas\",\"product\":\"DemoApp\"}"
  }'
```

Response:

```json
{
  "id": "uuid",
  "tenantId": "tenant-a",
  "userId": "user-123",
  "channel": "EMAIL",
  "recipient": "user123@example.com",
  "templateId": "welcome_email",
  "status": "QUEUED",
  "attemptCount": 0,
  "maxAttempts": 3
}
```

## 9.3 Get Notification

```bash
curl http://localhost:8080/notifications/{id} \
  -H "X-Tenant-ID: tenant-a"
```

Response:

```json
{
  "id": "uuid",
  "tenantId": "tenant-a",
  "userId": "user-123",
  "channel": "EMAIL",
  "recipient": "user123@example.com",
  "templateId": "welcome_email",
  "status": "SENT",
  "attemptCount": 0,
  "maxAttempts": 3
}
```

## 9.4 User Notification History

```bash
curl http://localhost:8080/users/user-123/notifications \
  -H "X-Tenant-ID: tenant-a"
```

Response:

```json
[
  {
    "id": "uuid",
    "tenantId": "tenant-a",
    "userId": "user-123",
    "channel": "EMAIL",
    "status": "SENT"
  }
]
```

## 9.5 Update User Preferences

```bash
curl -X PUT http://localhost:8080/users/user-123/preferences \
  -H "Content-Type: application/json" \
  -H "X-Tenant-ID: tenant-a" \
  -d '{
    "emailEnabled": true,
    "smsEnabled": false,
    "pushEnabled": true
  }'
```

Response:

```json
{
  "userId": "user-123",
  "emailEnabled": true,
  "smsEnabled": false,
  "pushEnabled": true
}
```

## 9.6 Get User Preferences

```bash
curl http://localhost:8080/users/user-123/preferences \
  -H "X-Tenant-ID: tenant-a"
```

Response:

```json
{
  "userId": "user-123",
  "emailEnabled": true,
  "smsEnabled": false,
  "pushEnabled": true
}
```

## 9.7 Retry Notification

```bash
curl -X POST http://localhost:8080/notifications/{id}/retry \
  -H "X-Tenant-ID: tenant-a"
```

Response:

```json
{
  "id": "uuid",
  "tenantId": "tenant-a",
  "status": "RETRYING",
  "attemptCount": 1,
  "maxAttempts": 3
}
```

After max attempts:

```json
{
  "id": "uuid",
  "tenantId": "tenant-a",
  "status": "DEAD_LETTERED",
  "attemptCount": 3,
  "maxAttempts": 3
}
```

## 9.8 List Dead Letter Queue

```bash
curl http://localhost:8080/dead-letter \
  -H "X-Tenant-ID: tenant-a"
```

Response:

```json
[
  {
    "id": "uuid",
    "tenantId": "tenant-a",
    "userId": "user-999",
    "channel": "EMAIL",
    "recipient": "fail@example.com",
    "status": "DEAD_LETTERED",
    "attemptCount": 3,
    "failureReason": "Simulated provider failure"
  }
]
```

## 9.9 Requeue Dead Letter

```bash
curl -X POST http://localhost:8080/dead-letter/{id}/requeue \
  -H "X-Tenant-ID: tenant-a"
```

Response:

```json
{
  "id": "uuid",
  "tenantId": "tenant-a",
  "status": "QUEUED",
  "attemptCount": 0,
  "failureReason": null,
  "deadLetteredAt": null
}
```

## 10. Production Readiness Analysis

## 10.1 Scalability

API layer:

- stateless Spring Boot services
- horizontal scaling behind load balancer
- autoscale on CPU, RPS, latency
- separate admin APIs from ingestion APIs

Kafka:

- partition notification topic by tenant ID or notification ID
- scale consumers by consumer group
- use separate topics per priority if needed

PostgreSQL:

- index by tenant ID and user ID
- archive old notification history
- partition large tables by time
- use read replicas for history queries

Worker layer:

- independently scalable delivery workers
- channel-specific worker pools
- separate email, SMS, push delivery workers
- priority queues for urgent notifications like OTP

## 10.2 Resilience

Provider failure:

- retry with exponential backoff
- circuit breaker per provider
- fallback provider support
- DLQ after max attempts

Kafka failure:

- API writes to DB first
- use transactional outbox for reliable publish
- retry publisher
- monitor consumer lag

PostgreSQL failure:

- HA primary/replica
- backup and PITR
- connection pool limits
- graceful degradation for history APIs

Redis failure:

- notification creation should still work
- rate limiting can degrade open or closed depending on product
- idempotency fallback can use DB unique constraints

## 10.3 Security

Production system should add:

- JWT authentication
- tenant authorization
- RBAC for admin APIs
- encryption in transit
- encryption at rest
- secrets in Vault or cloud secret manager
- recipient PII masking in logs
- audit logs for manual retry/requeue
- rate limiting per tenant
- request validation
- webhook signature verification

Sensitive fields:

- email
- phone number
- device token
- payload_json

These should not be logged in raw form.

## 10.4 Observability

Key metrics:

- notification_requests_total
- notification_delivery_success_total
- notification_delivery_failure_total
- notification_retry_total
- notification_dead_letter_total
- notification_delivery_latency_ms
- kafka_consumer_lag
- provider_latency_ms
- provider_error_rate
- tenant_rate_limited_total

Dashboards:

- notification volume by tenant
- delivery success rate
- failure rate by provider
- retry count
- DLQ count
- Kafka lag
- p95 delivery latency
- provider SLA dashboard

Tracing:

API request -> PostgreSQL -> Kafka -> Worker -> Provider -> Status update

Logs should include:

- tenantId
- notificationId
- channel
- status
- attemptCount
- provider
- correlationId

## 10.5 Performance

Optimizations:

- async delivery through Kafka
- batch provider calls where supported
- Redis rate limiting
- idempotency keys
- DB indexes
- compact payloads
- avoid synchronous provider calls in API
- connection pooling
- separate hot and cold history storage

Recommended indexes:

CREATE INDEX idx_notifications_tenant_user_created
ON notifications (tenant_id, user_id, created_at DESC);

CREATE INDEX idx_notifications_status_retry
ON notifications (status, next_retry_at);

CREATE INDEX idx_notifications_tenant_status
ON notifications (tenant_id, status);

## 10.6 Operations

Deployment strategy:

- Docker for local
- Kubernetes for production
- Helm chart
- readiness and liveness probes
- rolling deployment
- blue-green deployment
- canary deployment

Operational jobs:

- retry scheduler
- DLQ reprocessor
- archive old notifications
- provider health checker
- template validator
- stuck message detector

Rollback strategy:

- keep previous app version
- schema backward compatibility
- Kafka event versioning
- canary workers
- feature flags around new providers

## 10.7 SLA Considerations

Target SLA:

99.95% API availability

Delivery SLO examples:

- 99% transactional notifications delivered within 30 seconds
- 99.9% OTP notifications delivered within 5 seconds
- DLQ count below threshold
- Kafka lag under 10 seconds
- Provider error rate below 1%

To achieve:

- multi-AZ deployment
- HA Kafka
- PostgreSQL HA
- provider fallback
- circuit breakers
- queue buffering
- autoscaling workers
- alerting on SLO burn rate

## 11. Cost Optimization

## 11.1 API Layer

Cost controls:

- stateless autoscaling
- use smaller instances for API
- reserve baseline capacity
- use spot instances for workers where acceptable
- separate ingestion from admin traffic

## 11.2 Kafka

Cost controls:

- avoid excessive partitions
- tune retention period
- compact only topics that need compaction
- separate high-priority and bulk topics
- batch producer sends
- monitor broker disk usage

## 11.3 PostgreSQL

Cost controls:

- archive old notification history
- partition by month
- store large payloads in object storage if needed
- keep only indexed query fields in hot table
- move old history to cheaper cold storage
- avoid over-indexing

## 11.4 Redis

Cost controls:

- short TTL for rate-limit keys
- short TTL for idempotency keys
- avoid storing full payloads
- use Redis only for hot operational data
- monitor memory usage

## 11.5 Provider Costs

Notification providers are often the biggest cost.

Cost optimizations:

- avoid duplicate sends using idempotency
- suppress notifications when preferences disabled
- batch low-priority notifications
- use cheaper providers for non-critical messages
- choose channel based on urgency
- use push instead of SMS where possible
- dedupe campaign notifications
- enforce tenant quotas

Example policy:

- OTP/security alert -> SMS or Push
- Marketing update -> Email or Push
- Low-priority reminder -> Email digest

## 11.6 Storage Cost

Optimize storage by:

- keeping recent 30–90 days in PostgreSQL
- archiving older history to S3/GCS
- storing compressed JSON payloads
- retaining DLQ longer than normal successful notifications
- applying per-tenant retention policies

## 12. Advanced Production Enhancements

## 12.1 Idempotency

Use an idempotency key to prevent duplicate sends.

Header:

Idempotency-Key: request-123

DB constraint:

tenant_id + idempotency_key

If same request is retried by client, return the existing notification instead of creating a duplicate.

## 12.2 Rate Limiting

Rate limit by:

- tenant
- user
- channel
- provider
- campaign

Examples:

- tenant-a: 1000 notifications/minute
- user-123: 5 SMS/hour
- provider-sms: 500 requests/minute

## 12.3 Priority Queues

Use separate topics:

- notification-high-priority
- notification-normal-priority
- notification-bulk-priority

Use cases:

- high: OTP, security alert
- normal: order update
- bulk: marketing campaign

## 12.4 Provider Fallback

If primary provider fails:

- SES -> SendGrid
- Twilio -> MessageBird
- FCM -> APNS fallback not direct, but push provider abstraction helps

Provider abstraction:

NotificationProvider.send(notification)

## 12.5 Template System

Production system should include:

- template storage
- template versioning
- localization
- preview
- validation
- approval workflow
- variable substitution
- fallback template

## 12.6 Compliance

Important compliance features:

- unsubscribe handling
- opt-in / opt-out tracking
- GDPR delete support
- PII masking
- audit logs
- retention policies
- consent tracking
