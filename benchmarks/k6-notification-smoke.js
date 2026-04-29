import http from 'k6/http';
import { check } from 'k6';
export const options = { vus: 5, duration: '20s' };
export default function () {
  const payload = JSON.stringify({ userId: 'user-' + (__VU % 10), channel: 'EMAIL', recipient: 'user@example.com', templateId: 'welcome-email', payload: { name: 'Suhas' }, idempotencyKey: `${__VU}-${__ITER}` });
  const res = http.post(`${__ENV.BASE_URL || 'http://localhost:8080'}/notifications`, payload, { headers: { 'Content-Type': 'application/json', 'X-Tenant-ID': 'tenant-a' } });
  check(res, { 'created': r => r.status === 200 });
}
