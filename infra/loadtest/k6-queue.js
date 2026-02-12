import http from 'k6/http';
import { check } from 'k6';

const GATE_BASE = __ENV.GATE_BASE || 'http://localhost:8010/gate';
const EVENT_ID = __ENV.EVENT_ID || 'a0000000-0000-0000-0000-000000000001';
const SCHEDULE_ID = __ENV.SCHEDULE_ID || 'b0000000-0000-0000-0000-000000000001';
const LOAD_TEST_HEADER = __ENV.LOAD_TEST_HEADER || 'true';

const RATE = Number(__ENV.RATE || 1000);
const DURATION = __ENV.DURATION || '5s';

export const options = {
  scenarios: {
    join_burst: {
      executor: 'constant-arrival-rate',
      rate: RATE,
      timeUnit: '1s',
      duration: DURATION,
      preAllocatedVUs: 300,
      maxVUs: 3000,
    },
  },
  thresholds: {
    http_req_failed: ['rate<0.05'],
    http_req_duration: ['p(95)<1000'],
  },
};

export default function () {
  const res = http.post(
    `${GATE_BASE}/join`,
    JSON.stringify({ eventId: EVENT_ID, scheduleId: SCHEDULE_ID }),
    {
      headers: {
        'Content-Type': 'application/json',
        'X-Load-Test': LOAD_TEST_HEADER,
      },
    },
  );

  check(res, {
    'join status 200': (r) => r.status === 200,
    'join has queueToken': (r) => !!r.json('queueToken'),
  });
}
