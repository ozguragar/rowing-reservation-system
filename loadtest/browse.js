// Read-heavy dashboard traffic. Logs in once per VU, then hits the
// endpoints the logged-in dashboard loads in a loop.
import http from 'k6/http';
import { check, sleep, group } from 'k6';

const BASE_URL = __ENV.BASE_URL || 'http://localhost:8081/api';

export const options = {
  stages: [
    { duration: '20s', target: 10 },
    { duration: '30s', target: 20 },
    { duration: '10s', target: 0  },
  ],
  thresholds: {
    'http_req_duration{kind:read}':  ['p(95)<150'],
    http_req_failed:                  ['rate<0.01'],
  },
};

const creds = [
  { email: 'student1@university.edu', password: 'student123' },
  { email: 'student2@university.edu', password: 'student123' },
  { email: 'member1@rowingclub.com',  password: 'member123'  },
];

export function setup() {
  // Warm-up: prime whatever caches the backend keeps hot.
  http.get(`${BASE_URL}/sessions/upcoming`);
  return {};
}

export default function () {
  const u = creds[(__VU - 1) % creds.length];

  let token;
  group('login', () => {
    const r = http.post(
      `${BASE_URL}/auth/login`,
      JSON.stringify(u),
      { headers: { 'Content-Type': 'application/json' } },
    );
    check(r, { 'login ok': (x) => x.status === 200 });
    token = r.json('accessToken');
  });
  if (!token) return;

  const auth = { headers: { Authorization: `Bearer ${token}` }, tags: { kind: 'read' } };

  for (let i = 0; i < 5; i++) {
    group('dashboard-burst', () => {
      const res = http.batch([
        ['GET', `${BASE_URL}/sessions/upcoming`, null, auth],
        ['GET', `${BASE_URL}/users/me`,           null, auth],
        ['GET', `${BASE_URL}/ledger/report`,      null, auth],
      ]);
      res.forEach((r) => check(r, { 'read 200': (x) => x.status === 200 }));
    });
    sleep(0.5);
  }
}
