// Stress the login path. We deliberately exceed the 30-rpm rate-limit
// window from a single IP and count the 429s separately — they should
// account for the bulk of responses once the burst begins.
import http from 'k6/http';
import { check, sleep } from 'k6';
import { Counter, Rate } from 'k6/metrics';

const BASE_URL = __ENV.BASE_URL || 'http://localhost:8081/api';

export const options = {
  scenarios: {
    burst: {
      executor: 'constant-arrival-rate',
      rate: 60,                // 60 logins/minute from this VU pool
      timeUnit: '1m',
      duration: '1m',
      preAllocatedVUs: 10,
      maxVUs: 20,
    },
  },
  thresholds: {
    'http_req_duration{expected:ok}': ['p(95)<300'],
    rate_limited:                       ['count>10'],  // sanity: limiter fired
    login_errors:                       ['rate<0.02'],
  },
};

const rateLimited = new Counter('rate_limited');
const loginErrors = new Rate('login_errors');

const users = [
  { email: 'student1@university.edu', password: 'student123' },
  { email: 'member1@rowingclub.com',   password: 'member123'  },
];

export default function () {
  const u = users[Math.floor(Math.random() * users.length)];
  const res = http.post(
    `${BASE_URL}/auth/login`,
    JSON.stringify(u),
    { headers: { 'Content-Type': 'application/json' }, tags: { expected: 'ok' } },
  );

  if (res.status === 429) {
    rateLimited.add(1);
    loginErrors.add(false);
    return;
  }
  const ok = check(res, {
    'status 200':     (r) => r.status === 200,
    'has accessToken': (r) => !!(r.json() && r.json().accessToken),
  });
  loginErrors.add(!ok);
  sleep(0.1);
}
