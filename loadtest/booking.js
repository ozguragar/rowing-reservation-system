// Exercise the write path with optimistic-lock contention.
// Many VUs attempt to book the same session's boats at once —
// most should succeed, some should see the 409 conflict, none should 500.
import http from 'k6/http';
import { check, group, sleep } from 'k6';
import { Counter } from 'k6/metrics';

const BASE_URL = __ENV.BASE_URL || 'http://localhost:8081/api';
const EMAIL    = __ENV.STUDENT_EMAIL    || 'student1@university.edu';
const PASSWORD = __ENV.STUDENT_PASSWORD || 'student123';

export const options = {
  scenarios: {
    contention: {
      executor: 'ramping-vus',
      startVUs: 0,
      stages: [
        { duration: '15s', target: 10 },
        { duration: '30s', target: 20 },
        { duration: '10s', target: 0  },
      ],
    },
  },
  thresholds: {
    'http_req_duration{op:book}':   ['p(95)<400'],
    'http_req_duration{op:cancel}': ['p(95)<400'],
    http_req_failed:                 ['rate<0.02'],
    conflicts_observed:              ['count>=0'],
  },
};

const conflicts = new Counter('conflicts_observed');

export default function () {
  // Log in with a fresh token each iteration (tokens are 15 min, VU lifetime is short).
  const login = http.post(
    `${BASE_URL}/auth/login`,
    JSON.stringify({ email: EMAIL, password: PASSWORD }),
    { headers: { 'Content-Type': 'application/json' } },
  );
  const token = login.json('accessToken');
  if (!token) return;
  const auth = { Authorization: `Bearer ${token}`, 'Content-Type': 'application/json' };

  const up = http.get(`${BASE_URL}/sessions/upcoming`, { headers: auth });
  const sessions = up.json();
  if (!Array.isArray(sessions) || sessions.length === 0) { sleep(1); return; }
  const boat = sessions.flatMap((s) => s.boats || []).find((b) => (b.currentOccupants || 0) < (b.capacity || 0));
  if (!boat) { sleep(1); return; }

  let bookingId;
  group('book', () => {
    const r = http.post(
      `${BASE_URL}/bookings`,
      JSON.stringify({ boatId: boat.id }),
      { headers: auth, tags: { op: 'book' } },
    );
    if (r.status === 409) conflicts.add(1);
    check(r, { 'book accepted or conflicted': (x) => x.status === 200 || x.status === 409 });
    bookingId = r.status === 200 ? r.json('id') : null;
  });

  if (bookingId) {
    sleep(0.3);
    group('cancel', () => {
      const r = http.del(`${BASE_URL}/bookings/${bookingId}`, null, { headers: auth, tags: { op: 'cancel' } });
      check(r, { 'cancel ok': (x) => x.status === 200 });
    });
  }
  sleep(0.5);
}
