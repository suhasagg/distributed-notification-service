# Distributed Notification Delivery Service

A multi-tenant, event-driven notification platform designed to demonstrate production-oriented architecture for large-scale notification systems.

This project implements:

- PostgreSQL as the source of truth
- Kafka for asynchronous notification dispatch
- Redis for per-tenant/user/channel rate limiting
- Spring Boot for the API layer
- Multi-tenant notification isolation
- User notification preferences
- Idempotent notification enqueue
- Notification history APIs

## 1. Problem Statement

Companies need to send reliable notifications across multiple channels such as email, SMS, and push.

The system should support:

- enqueue notifications with low API latency
- dispatch notifications asynchronously
- support multiple channels: EMAIL, SMS, PUSH
- support tenant isolation
- support user-level preferences
- prevent duplicate notifications using idempotency keys
- protect infrastructure with rate limiting
- keep notification history for debugging and compliance
- scale horizontally for high-volume campaigns

Typical use cases:

- welcome emails
- password reset messages
- order updates
- payment reminders
- marketing campaigns
- product alerts
- push notifications

## 2. High-Level Architecture

```text
                         +----------------------+
                         |       Clients        |
                         | Admin UI / Services  |
                         +----------+-----------+
                                    |
                                    v
                         +----------+-----------+
                         |    Spring Boot API   |
                         | Enqueue + History    |
                         +----+------------+----+
                              |            |
                    Write path|            |Preference / History path
                              |            |
                              v            v
                       +------+---+   +----+------+
                       |PostgreSQL|   |   Redis   |
                       |Source of |   | Rate      |
                       |Truth     |   | Limiting  |
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
                    | Notification Consumer |
                    | Dispatch Worker      |
                    +----+--------+--------+
                         |        |
                         v        v
                   Email/SMS/Push Providers
```

## 3. Core Components

### 3.1 Spring Boot API

Responsibilities:

- exposes REST APIs
- validates tenant headers
- accepts notification enqueue requests
- handles idempotency
- stores notification records
- publishes Kafka events
- exposes notification history
- stores user preferences

### 3.2 PostgreSQL

PostgreSQL is the source of truth.

Stores:

- notifications
- tenant ID
- user ID
- channel
- recipient
- template ID
- payload JSON
- delivery status
- idempotency key
- user preferences

Why PostgreSQL:

- strong consistency for enqueue path
- durable notification history
- simple querying for debugging
- transactional updates
- reliable compliance records

### 3.3 Kafka

Kafka is used for asynchronous dispatch.

Topic:

```text
notification-events
```

Events:

```json
{
  "notificationId": "uuid",
  "tenantId": "tenant-a",
  "userId": "user-123",
  "channel": "EMAIL",
  "recipient": "user@example.com",
  "templateId": "welcome-email",
  "payloadJson": "{\"name\":\"Suhas\"}"
}
```

Why Kafka:

- decouples API latency from provider latency
- supports retryable asynchronous dispatch
- allows multiple worker groups
- absorbs traffic spikes
- supports replay of failed events

### 3.4 Redis

Redis is used for:

- per-tenant/user/channel rate limiting
- future deduplication windows
- future provider circuit-breaker counters
- future hot preference caching

Example key:

```text
rl:{tenantId}:{userId}:{channel}
```

Example:

```text
rl:tenant-a:user-123:EMAIL
```

## 4. Data Model

### 4.1 Notifications Table

```text
notifications
```

Fields:

```text
id
tenant_id
user_id
channel
recipient
template_id
payload_json
status
idempotency_key
created_at
updated_at
```

Important indexes:

```text
tenant_id + user_id
tenant_id + idempotency_key
status
```

### 4.2 Notification Preferences Table

```text
notification_preferences
```

Fields:

```text
id
tenant_id
user_id
email_enabled
sms_enabled
push_enabled
updated_at
```

Used for:

- unsubscribe behavior
- user-level channel control
- compliance
- reducing unwanted notifications

## 5. Consistency Model

The system uses a mixed consistency model.

### Strong consistency

Used for:

- notification enqueue record
- idempotency check
- preference writes
- notification history

PostgreSQL remains canonical.

### Eventual consistency

Used for:

- asynchronous dispatch
- status update from QUEUED to SENT or FAILED
- provider delivery confirmation

Why this trade-off works:

- API enqueue should be fast
- external providers can be slow or unreliable
- Kafka provides buffering and retry capability
- users can inspect notification history after async processing

## 6. Notification Flow

```text
1. Client calls POST /notifications
2. API validates X-Tenant-ID
3. API checks idempotency key
4. API writes notification as QUEUED
5. API publishes notification event to Kafka
6. Kafka consumer receives event
7. Consumer checks Redis rate limit
8. Consumer checks user preferences
9. Consumer dispatches to provider
10. Consumer updates notification status
```

## 7. API List

### 7.1 Health Check

```bash
curl http://localhost:8080/health
```

Response:

```json
{
  "status": "UP"
}
```

### 7.2 Enqueue Notification

```bash
curl -X POST http://localhost:8080/notifications \
  -H "Content-Type: application/json" \
  -H "X-Tenant-ID: tenant-a" \
  -d '{
    "userId": "user-123",
    "channel": "EMAIL",
    "recipient": "user@example.com",
    "templateId": "welcome-email",
    "payload": {
      "name": "Suhas",
      "plan": "premium"
    },
    "idempotencyKey": "welcome-user-123-v1"
  }'
```

Response:

```json
{
  "id": "generated-id",
  "tenantId": "tenant-a",
  "userId": "user-123",
  "channel": "EMAIL",
  "recipient": "user@example.com",
  "templateId": "welcome-email",
  "status": "QUEUED",
  "idempotencyKey": "welcome-user-123-v1"
}
```

### 7.3 Get Notification by ID

```bash
curl http://localhost:8080/notifications/{id} \
  -H "X-Tenant-ID: tenant-a"
```

Response:

```json
{
  "id": "generated-id",
  "tenantId": "tenant-a",
  "userId": "user-123",
  "channel": "EMAIL",
  "recipient": "user@example.com",
  "templateId": "welcome-email",
  "status": "SENT",
  "idempotencyKey": "welcome-user-123-v1"
}
```

### 7.4 User Notification History

```bash
curl http://localhost:8080/users/user-123/notifications \
  -H "X-Tenant-ID: tenant-a"
```

Response:

```json
[
  {
    "id": "generated-id",
    "tenantId": "tenant-a",
    "userId": "user-123",
    "channel": "EMAIL",
    "status": "SENT"
  }
]
```

### 7.5 Update User Preferences

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

### 7.6 Get User Preferences

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

## 8. Multi-Tenancy Strategy

Tenant is identified using:

```text
X-Tenant-ID
```

Tenant isolation applies to:

- notification enqueue
- notification retrieval
- user history
- preferences
- Redis rate-limit key
- Kafka event payload

Example:

```text
tenant-a:user-123:EMAIL
tenant-b:user-123:EMAIL
```

Both tenants can notify the same user ID safely without cross-tenant leakage.

## 9. Production Readiness Analysis

### 9.1 Scalability

#### API layer

The API is stateless.

Scale horizontally using:

```text
multiple Spring Boot instances behind load balancer
```

Scale signals:

- CPU usage
- request latency
- p95/p99 latency
- enqueue rate
- Kafka producer latency
- PostgreSQL connection pool utilization

#### Dispatch scalability

Notification dispatch is worker-driven.

Optimization:

- scale Kafka consumers horizontally
- partition topic by tenant ID or user ID
- isolate high-volume tenants
- separate topics by channel
- use provider-specific worker pools

Target:

```text
p95 enqueue latency < 100 ms
Kafka consumer lag < 30 seconds during normal traffic
```

#### Database scalability

Writes are append-heavy.

Optimization:

- partition notification table by month
- archive old notifications
- index tenant/user/history paths
- avoid storing large unbounded payloads
- move analytics to warehouse

### 9.2 Resilience

#### PostgreSQL failure

Impact:

- enqueue fails
- history and preference APIs fail

Mitigation:

- multi-AZ primary
- automated failover
- backups
- connection pool limits
- circuit breaker

#### Kafka failure

Impact:

- enqueue may write DB but fail to publish event

Mitigation:

- transactional outbox pattern
- retry publisher
- dead-letter topic
- idempotent consumers

Recommended production improvement:

```text
Write DB transaction:
1. insert notification
2. insert outbox_event

Separate worker:
1. read outbox
2. publish to Kafka
3. mark event published
```

This prevents losing Kafka events after DB commit.

#### Provider failure

Impact:

- messages may fail or be delayed

Mitigation:

- retry with exponential backoff
- provider circuit breakers
- fallback provider routing
- DLQ for permanent failures
- provider-specific throttling

### 9.3 Security

Production system should add:

- JWT authentication
- tenant authorization
- RBAC
- service-to-service authentication
- encrypted provider credentials
- encryption in transit
- encryption at rest
- secrets in Vault/AWS Secrets Manager
- PII masking in logs
- rate limiting
- audit trail for admin actions

Example roles:

```text
NOTIFICATION_ADMIN
NOTIFICATION_SENDER
NOTIFICATION_VIEWER
TENANT_ADMIN
```

### 9.4 Observability

Add metrics:

```text
notification_enqueue_requests_total
notification_enqueue_latency_ms
notification_dispatch_latency_ms
notification_sent_total
notification_failed_total
notification_suppressed_total
notification_rate_limited_total
kafka_consumer_lag
postgres_query_latency
redis_latency
provider_error_rate
```

Dashboards:

- API latency
- enqueue QPS
- Kafka lag
- dispatch success rate
- failure rate by provider
- Redis rate-limit counters
- PostgreSQL connections
- tenant-level traffic

Tracing:

```text
API request -> PostgreSQL -> Kafka -> Consumer -> Provider -> Status update
```

Use:

- OpenTelemetry
- Prometheus
- Grafana
- Loki/ELK
- Jaeger/Tempo

### 9.5 Performance

Key optimizations:

- asynchronous dispatch
- Kafka buffering
- Redis rate limiting
- PostgreSQL indexes
- provider connection pooling
- batch provider sends
- template pre-rendering
- channel-specific worker pools

Recommended DB index:

```sql
CREATE INDEX idx_notifications_tenant_user
ON notifications (tenant_id, user_id, created_at DESC);
```

Idempotency index:

```sql
CREATE UNIQUE INDEX idx_notifications_idempotency
ON notifications (tenant_id, idempotency_key)
WHERE idempotency_key IS NOT NULL;
```

### 9.6 Operations

Deployment strategy:

- Docker for local
- Kubernetes for production
- Helm chart
- readiness/liveness probes
- rolling deployment
- blue-green deployment
- canary deployment

Health checks:

```text
/health
/ready
/live
```

Rollback strategy:

- keep previous app version
- database migrations backward compatible
- Kafka event schema versioning
- provider adapter compatibility tests

### 9.7 SLA Considerations

Target SLA:

```text
99.95% enqueue API availability
```

To achieve:

- multi-AZ deployment
- PostgreSQL HA
- Kafka replication
- Redis cluster
- load balancer health checks
- graceful degradation
- DLQ handling
- provider fallback
- automated rollback
- alerting on SLO burn rate

Example SLOs:

```text
99.9% enqueue requests < 100 ms
99.95% API availability
Kafka propagation lag < 30 seconds
Provider dispatch success > 99%
```

## 10. Cost Optimization

### 10.1 API Layer

Cost controls:

- stateless autoscaling
- right-size CPU/memory
- use spot instances for non-critical workers
- autoscale based on RPS and latency
- separate enqueue API from dispatch workers

### 10.2 PostgreSQL

Cost controls:

- keep payloads compact
- archive old notification history
- partition notification table by month
- use read replicas only when needed
- avoid over-indexing
- move analytics queries to warehouse

### 10.3 Redis

Cost controls:

- use TTL-based counters
- avoid long-lived unnecessary keys
- keep rate-limit windows short
- monitor memory fragmentation
- use local in-process counters for low-risk limits if acceptable

### 10.4 Kafka

Cost controls:

- avoid excessive partitions
- tune retention period
- use compact events
- separate topics only when operationally justified
- use managed Kafka when operational cost exceeds infra cost

### 10.5 Provider Cost Strategy

Use:

- provider routing based on cost and reliability
- batch sends where possible
- suppress duplicate notifications
- honor preferences to avoid wasted sends
- rate-limit noisy tenants
- avoid sending low-value marketing messages during peak cost windows

## 11. Advanced Production Enhancements

### 11.1 Outbox Pattern

In production, the enqueue path should use an outbox table.

Benefits:

- prevents lost Kafka events
- enables replay
- improves auditability
- gives operational visibility into stuck events

### 11.2 Dead Letter Queue

Failed notifications should move to a DLQ after retry exhaustion.

DLQ fields:

- notification ID
- tenant ID
- channel
- provider error
- retry count
- last failure time

### 11.3 Template Service

Production systems usually separate template rendering.

Template service supports:

- versioned templates
- localization
- preview
- approval workflow
- dynamic variables

### 11.4 Provider Abstraction

Use provider adapters:

```text
EmailProvider
SmsProvider
PushProvider
```

Benefits:

- provider swap
- fallback routing
- testing with fake provider
- channel-specific throttling

### 11.5 Campaign Support

Add campaign tables:

```text
campaigns
campaign_recipients
campaign_batches
campaign_metrics
```

Support:

- bulk sends
- segmentation
- batch scheduling
- progress tracking
- campaign-level throttling


## 13. Local Setup and Run Instructions

### Prerequisites

- Docker
- Docker Compose

### Run

```bash
docker compose up --build
```

### Reset state

```bash
docker compose down -v
docker compose up --build
```

### Health check

```bash
curl http://localhost:8080/health
```

## 14. Full Demo Flow

### 14.1 Create user preferences

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

### 14.2 Enqueue email notification

```bash
curl -X POST http://localhost:8080/notifications \
  -H "Content-Type: application/json" \
  -H "X-Tenant-ID: tenant-a" \
  -d '{
    "userId": "user-123",
    "channel": "EMAIL",
    "recipient": "user@example.com",
    "templateId": "welcome-email",
    "payload": {
      "name": "Suhas"
    },
    "idempotencyKey": "welcome-user-123-v1"
  }'
```

### 14.3 Check history

```bash
curl http://localhost:8080/users/user-123/notifications \
  -H "X-Tenant-ID: tenant-a"
```

### 14.4 Idempotency test

Run the same enqueue request again with the same idempotencyKey.

Expected:

```text
same notification record returned
no duplicate notification created
```

### 14.5 Tenant isolation test

```bash
curl http://localhost:8080/users/user-123/notifications \
  -H "X-Tenant-ID: tenant-b"
```

Expected:

```json
[]
```
