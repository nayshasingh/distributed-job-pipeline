import http from 'k6/http';
import { check } from 'k6';

/**
 * Submits 5,000 EMAIL jobs at 50 requests/second (about 100 seconds).
 * That is 50 concurrent in-flight requests on average, not 5,000 concurrent clients.
 *
 *   k6 run load-tests/submit-jobs.js
 *
 * Override: BASE_URL=http://127.0.0.1:8080 k6 run load-tests/submit-jobs.js
 */
export const options = {
  scenarios: {
    submit_5k: {
      executor: 'constant-arrival-rate',
      rate: 50,
      timeUnit: '1s',
      duration: '100s',
      preAllocatedVUs: 20,
      maxVUs: 60,
    },
  },
  thresholds: {
    http_req_failed: ['rate<0.01'],
    http_req_duration: ['p(95)<500'],
  },
};

const BASE_URL = __ENV.BASE_URL || 'http://127.0.0.1:8080';

export default function () {
  const high = Math.random() < 0.25;
  const res = http.post(
    `${BASE_URL}/jobs`,
    JSON.stringify({
      jobType: 'EMAIL',
      priority: high ? 'HIGH' : 'LOW',
      payload: { source: 'k6', subject: 'load-test' },
    }),
    { headers: { 'Content-Type': 'application/json' } }
  );
  check(res, { 'status is 201': (r) => r.status === 201 });
}
