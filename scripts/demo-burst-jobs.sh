#!/usr/bin/env bash
# Fire many POST /jobs quickly to mimic a traffic spike (orders, webhooks, etc.).
# Run API + worker + Redis + Kafka first. Then:
#   ./scripts/demo-burst-jobs.sh
# Optional: API_BASE=http://localhost:8081 COUNT=30 ./scripts/demo-burst-jobs.sh
#
# While it runs / right after:
# - Worker terminal: multiple "Processing …" / "completed" lines (async pickup)
# - redis-cli KEYS 'job-worker:exec:*'  (locks appear briefly during SLOW_JOB)
# - kafka-topics / console consumer (see messages on job-queue-high / job-queue-low)

set -euo pipefail
API_BASE="${API_BASE:-http://localhost:8081}"
COUNT="${COUNT:-25}"
# SLOW_JOB keeps each unit of work visible ~2s so RUNNING and Redis locks are easier to spot
DELAY_MS="${DELAY_MS:-2000}"

echo "Posting $COUNT jobs to $API_BASE/jobs (mix HIGH/LOW, type SLOW_JOB, delayMs=$DELAY_MS)..."
for i in $(seq 1 "$COUNT"); do
  prio="LOW"
  if (( i % 3 == 0 )); then prio="HIGH"; fi
  curl -s -X POST "$API_BASE/jobs" \
    -H "Content-Type: application/json" \
    -d "{\"jobType\":\"SLOW_JOB\",\"priority\":\"$prio\",\"payload\":{\"delayMs\":$DELAY_MS,\"batchIndex\":$i}}" \
    > /dev/null &
  # tiny stagger so you don't choke the shell; Kafka/API still get a burst
  if (( i % 10 == 0 )); then wait; fi
done
wait
echo "Done. Check GET $API_BASE/jobs and worker logs."
