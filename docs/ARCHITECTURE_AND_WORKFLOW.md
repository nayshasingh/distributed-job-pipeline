# Architecture and end-to-end workflow

This document has **two layers**:

1. **Navigate the codebase** — what each folder and file is for (read this if opening the project feels overwhelming).
2. **How data moves** — HTTP → database → Kafka → worker → Redis → database again.

If you are new to the stack, read **sections 0 → 3** first, then skim the diagrams, then read **section 8** while you have the listed files open in your editor.

---

## 0. How to read this document

| Section | Purpose |
|---------|---------|
| [1 — Repository root](#1-repository-root-what-sits-at-the-top-level) | What `docker-compose.yml`, each app folder, and `docs/` mean. |
| [2 — Scheduler files](#2-distributed-job-scheduler-file-by-file) | Every Java file in the API project, in plain language. |
| [3 — Worker files](#3-job-worker-file-by-file) | Every Java file in the worker project. |
| [4 — Dashboard & scripts](#4-job-dashboard-and-root-scripts) | React UI and helper shell script. |
| [5 — Config files](#5-configuration-files-applicationyml) | What the YAML settings are doing. |
| [6–7](#6-system-overview-diagram) | Diagrams + database columns. |
| [8](#8-follow-one-request-postjobs-with-exact-file-paths) | **Line-by-line story** of one job from curl to completion. |
| [11 — Glossary](#11-glossary) | Short definitions (Kafka topic, consumer group, JPA, …). |

---

## 1. Repository root (what sits at the top level)

```text
.
├── README.md                 # How to run, env vars, troubleshooting
├── docker-compose.yml        # Starts Postgres, Redis, Zookeeper, Kafka locally
├── .gitignore                # Ignores target/, node_modules/, .env, …
├── distributed-job-scheduler/# Spring Boot **API** (Maven)
├── job-worker/               # Spring Boot **worker** (Maven) — no HTTP API
├── job-dashboard/            # React **UI** (npm) — optional
├── docs/                     # This file, learning links, git push notes
└── scripts/
    └── demo-burst-jobs.sh    # Fires many POST /jobs in parallel for demos
```

- **Two JVM apps** (`distributed-job-scheduler`, `job-worker`) share the **same Postgres database** and **same `jobs` table**. Only the **scheduler** auto-creates/updates the table (Hibernate `ddl-auto: update`). The **worker** expects the table to exist (`validate`).
- **Kafka** is the pipe between them for the main demo path: scheduler **writes** messages, worker **reads** them.
- **Redis** is only used by the **worker** for short-lived **locks** (not for storing jobs).

---

## 2. `distributed-job-scheduler` — file by file

Root: `distributed-job-scheduler/src/main/java/com/distributedjob/scheduler/`

| Path (under `scheduler/`) | What it does |
|---------------------------|--------------|
| **`DistributedJobSchedulerApplication.java`** | `main()` entry point. Running this starts Spring Boot and loads every `@Component`, `@Service`, `@RestController`. |
| **`controller/JobController.java`** | Maps **`/jobs`**: `POST` create job, `GET` list, `GET /{id}`, `POST /{id}/retry`. Delegates everything to `JobService`. |
| **`controller/WorkerJobController.java`** | Maps **`/api/workers/jobs/...`**: optional **pull** API (`claim-next`, `complete`) for workers that do **not** use Kafka. |
| **`service/JobService.java`** | Core logic: save job, publish `JobCreatedEvent`, retry failed jobs, claim/complete for REST workers. Annotated with `@Transactional` where DB changes must be atomic. |
| **`repository/JobRepository.java`** | Spring Data JPA interface — Spring generates SQL for `save`, `findById`, custom queries like “next pending job for claim.” |
| **`entity/Job.java`** | JPA entity = one row shape for table **`jobs`** (id, jobType, payload JSON string, status, priority, retryCount, timestamps). |
| **`entity/JobStatus.java`** | Enum: `PENDING`, `RUNNING`, `SUCCESS`, `FAILED`. |
| **`entity/JobPriority.java`** | Enum: `HIGH`, `LOW`. |
| **`dto/JobSubmissionRequest.java`** | JSON body for `POST /jobs` (validated). |
| **`dto/JobResponse.java`** | JSON returned to clients (job id, status, etc.). |
| **`dto/JobCompletionRequest.java`** | JSON for REST worker `complete` endpoint. |
| **`kafka/JobCreatedEvent.java`** | Small **immutable record** passed to Spring’s event bus when a job should be announced to Kafka (holds jobId, jobType, priority). |
| **`kafka/JobCreatedKafkaPublisher.java`** | Listens for `JobCreatedEvent` with **`@TransactionalEventListener(phase = AFTER_COMMIT)`** — calls the producer **only after** the database transaction committed. |
| **`kafka/JobQueueKafkaProducer.java`** | Uses Spring’s **`KafkaTemplate`** to send a JSON string to **`job-queue-high`** or **`job-queue-low`**. |
| **`kafka/JobQueueMessage.java`** | Java record / DTO for what goes on the wire (`jobId`, `jobType`, `priority`). |
| **`config/KafkaTopicConfig.java`** | **`@Bean`** that registers the two Kafka **topics** when the app starts (for local dev). |
| **`config/KafkaTopicsProperties.java`** | Binds `application.yml` keys under `app.kafka` (topic names). |
| **`config/WebCorsConfig.java`** | Allows browsers (e.g. dashboard on port 3000/5173) to call the API. |
| **`config/OpenApiConfig.java`** | Swagger / OpenAPI title and version. |

**Resources:** `distributed-job-scheduler/src/main/resources/application.yml` — ports, datasource, Kafka bootstrap, topic names (see [§5](#5-configuration-files-applicationyml)).

**Build:** `distributed-job-scheduler/pom.xml` — dependencies (web, JPA, Kafka, PostgreSQL driver, springdoc).

---

## 3. `job-worker` — file by file

Root: `job-worker/src/main/java/com/distributedjob/worker/`

| Path (under `worker/`) | What it does |
|------------------------|--------------|
| **`JobWorkerApplication.java`** | `main()` — starts Spring Boot; enables Kafka listeners. |
| **`kafka/FairPriorityJobIngress.java`** | **Heart of consumption:** two **`@KafkaListener`** methods (one topic + consumer group per priority lane). Each puts work on an internal **queue**; a **background thread** takes jobs in a **3 HIGH : 1 LOW** pattern and calls `JobProcessingService`. Kafka **ack** happens only after processing returns. |
| **`kafka/JobQueueKafkaProducer.java`** | On retry, sends another message to the **same** high/low topic as the job’s priority. |
| **`kafka/JobQueueMessage.java`** | Same JSON shape as scheduler (parsed from consumer record). |
| **`service/JobProcessingService.java`** | **`processQueuedJob`**: try **Redis lock** → **`JobStateService.tryClaimPending`** → **`MockJobTaskExecutor.execute`** → success or **`recordExecutionFailure`** (maybe re-publish to Kafka). **`finally`**: release Redis lock. |
| **`service/JobStateService.java`** | All **`@Transactional`** updates to the `jobs` row: claim PENDING→RUNNING, set SUCCESS, or handle failure + retry count. |
| **`service/JobDistributedLockService.java`** | Talks to Redis: **`SET` key with NX + expiry**, unlock with Lua script + token. |
| **`service/MockJobTaskExecutor.java`** | Fake work: log, sleep for `SLOW_JOB`, throw for `FAILING_JOB`, etc. Replace with real integrations in a real system. |
| **`service/ExecutionFailureResult.java`** | Small type used when deciding “requeue vs FAILED.” |
| **`repository/JobRepository.java`** | JPA + one **`@Modifying` query** `claimIfStatus` — updates row **only if** status is still `PENDING` (helps with duplicate messages). |
| **`entity/Job.java`**, **`JobStatus.java`**, **`JobPriority.java`** | Mirror of scheduler’s model so Hibernate can read the **same** table (`validate` mode). |
| **`config/KafkaConsumerConfig.java`** | Factory for listeners: **manual ack**, **`syncCommits=false`** so the thread that processes can ack on another thread (fair dispatcher pattern). |
| **`config/WorkerKafkaTopicsProperties.java`** | Topic names + consumer **group ids** from YAML. |
| **`config/WorkerProperties.java`** | Lock prefix, TTL, max execution attempts. |

**Resources:** `job-worker/src/main/resources/application.yml` — datasource, Kafka consumer/producer, Redis, worker tuning.

**Build:** `job-worker/pom.xml`.

---

## 4. `job-dashboard` and root scripts

| Path | What it does |
|------|--------------|
| **`job-dashboard/src/main.jsx`** | React entry — mounts `<App />`. |
| **`job-dashboard/src/App.jsx`** | Renders the main page (usually `Dashboard`). |
| **`job-dashboard/src/pages/Dashboard.jsx`** | Lists jobs (polling), summary cards, embeds form/table/filters. |
| **`job-dashboard/src/services/api.js`** | Axios client: `fetchJobs`, `createJob` — hits `/jobs` (dev proxy forwards to Spring). |
| **`job-dashboard/src/components/JobForm.jsx`** | Form to POST a new job. |
| **`job-dashboard/src/components/JobTable.jsx`** | Table of jobs. |
| **`job-dashboard/src/components/Filters.jsx`** | Client-side filter UI. |
| **`job-dashboard/vite.config.js`** | Dev server + **`proxy`** using `VITE_API_PROXY_TARGET` so `/jobs` goes to the API. |
| **`job-dashboard/package.json`** | Scripts: `npm run dev`, dependencies (React, Axios, Tailwind, …). |
| **`job-dashboard/.env.example`** | Example env vars (copy to `.env` locally; real `.env` is gitignored). |
| **`scripts/demo-burst-jobs.sh`** | Shell loop / parallel `curl` to stress-test POST /jobs. |

---

## 5. Configuration files (`application.yml`)

You do **not** need to memorize every key — use this as a map when something fails to connect.

### Scheduler (`distributed-job-scheduler/src/main/resources/application.yml`)

Typical blocks:

- **`server.port`** — HTTP port (often `8080`, overridable with `SERVER_PORT`).
- **`spring.datasource.*`** — JDBC URL, username, password → **Postgres**.
- **`spring.jpa.hibernate.ddl-auto: update`** — Hibernate creates/updates **`jobs`** table to match `Job.java`.
- **`spring.kafka.bootstrap-servers`** — where Kafka listens (e.g. `localhost:9092`).
- **`app.kafka.*`** — names of **high** and **low** topics (must match worker).

### Worker (`job-worker/src/main/resources/application.yml`)

- **`spring.datasource.*`** — **same database** as scheduler.
- **`spring.jpa.hibernate.ddl-auto: validate`** — **do not** change schema; fail fast if table missing/wrong.
- **Kafka consumer** — `group-id` differs per lane (`job-worker-high` vs `job-worker-low`); subscribe to high/low topics.
- **Redis** — host/port for **`JobDistributedLockService`**.
- **`app.worker.*`** — lock key prefix, TTL seconds, **`max-execution-attempts`** for retries.

Environment variables in README often **override** these YAML values — same names, different layer.

---

## 6. System overview (diagram)

```mermaid
flowchart TB
  subgraph clients [Clients]
    UI[job-dashboard React]
    CURL[curl / other HTTP clients]
    SW[Swagger UI]
  end

  subgraph api [distributed-job-scheduler]
    JC[JobController]
    WJC[WorkerJobController]
    JS[JobService]
    JR_API[JobRepository]
    KProd[JobQueueKafkaProducer]
    KPub[JobCreatedKafkaPublisher]
  end

  subgraph data [Data stores]
    PG[(PostgreSQL jobs)]
    KH[[Kafka job-queue-high]]
    KL[[Kafka job-queue-low]]
    RD[(Redis locks)]
  end

  subgraph worker [job-worker]
    FPI[FairPriorityJobIngress]
    JPS[JobProcessingService]
    JSS[JobStateService]
    JDL[JobDistributedLockService]
    MJE[MockJobTaskExecutor]
    KProdW[JobQueueKafkaProducer]
    JR_W[JobRepository]
  end

  UI --> JC
  CURL --> JC
  SW --> JC
  JC --> JS
  WJC --> JS
  JS --> JR_API
  JR_API --> PG
  JS --> KPub
  KPub --> KProd
  KProd --> KH
  KProd --> KL
  FPI --> KH
  FPI --> KL
  FPI --> JPS
  JPS --> JDL
  JDL --> RD
  JPS --> JSS
  JSS --> JR_W
  JR_W --> PG
  JPS --> MJE
  JPS --> KProdW
  KProdW --> KH
  KProdW --> KL
```

| Box | Role |
|-----|------|
| **distributed-job-scheduler** | HTTP API, owns DB schema (`ddl-auto: update`), publishes to Kafka **after** DB commit. |
| **job-worker** | Consumes Kafka (two lanes + fair merge), uses Redis + DB to execute jobs and handle retries. |
| **PostgreSQL** | Source of truth for job rows (`jobs` table). |
| **Kafka** | Two topics for priority lanes; buffers work between API and workers. |
| **Redis** | Lock per `jobId` to reduce duplicate execution across JVMs. |
| **job-dashboard** | Optional React UI; proxies `/jobs` to the API in dev. |

---

## 7. Data model (`jobs` table)

| Field | Meaning |
|-------|---------|
| `id` | UUID primary key. |
| `job_type` | Free-form string (e.g. `EMAIL`, `SLOW_JOB`). |
| `payload` | JSON — arbitrary parameters for workers. |
| `status` | `PENDING`, `RUNNING`, `SUCCESS`, `FAILED`. |
| `priority` | `HIGH` or `LOW` (default **LOW**). |
| `retry_count` | Incremented on API retry and on worker failure path. |
| `created_at` / `updated_at` | Timestamps. |

---

## 8. Follow one request: `POST /jobs` (with exact file paths)

Open these files in order while you read. The **bold** names are Java classes.

### Phase 1 — API (scheduler JVM)

1. **HTTP** hits **`JobController.createJob`**  
   `distributed-job-scheduler/src/main/java/com/distributedjob/scheduler/controller/JobController.java`  
   → calls **`jobService.submitJob(request)`**.

2. **`JobService.submitJob`**  
   `.../service/JobService.java`  
   - Builds a **`Job`** entity (`PENDING`, priority from request or `LOW`).  
   - **`jobRepository.save(job)`** → INSERT into Postgres.  
   - **`eventPublisher.publishEvent(new JobCreatedEvent(...))`** — event is **queued** for after-commit.

3. Transaction **commits** (Spring closes the `@Transactional` method successfully).

4. **`JobCreatedKafkaPublisher.onJobQueuedForKafka`**  
   `.../kafka/JobCreatedKafkaPublisher.java`  
   - Runs only **after commit** (`AFTER_COMMIT`).  
   - Calls **`JobQueueKafkaProducer.sendJobQueued(...)`**.

5. **`JobQueueKafkaProducer`**  
   `.../kafka/JobQueueKafkaProducer.java`  
   - Serializes **`JobQueueMessage`** to JSON.  
   - **`kafkaTemplate.send(highOrLowTopic, jobId, json)`** — record lands in Kafka.

6. **`JobController`** returns **`JobResponse`** → client gets **201** with job id and `PENDING`.

### Phase 2 — Worker (worker JVM)

7. **`FairPriorityJobIngress`** (listener on **high** or **low** topic)  
   `job-worker/src/main/java/com/distributedjob/worker/kafka/FairPriorityJobIngress.java`  
   - Kafka delivers a record → pushed to internal **queue** (ack **not** yet sent).  
   - Dispatcher thread dequeues → parses JSON into **`JobQueueMessage`**.

8. **`JobProcessingService.processQueuedJob`**  
   `.../service/JobProcessingService.java`  
   - **`JobDistributedLockService.tryLock(jobId)`** — Redis `SET` if absent.  
   - If lock fails → **return** (another worker may be handling it).  
   - **`JobStateService.tryClaimPending`** — runs UPDATE … WHERE id = ? AND status = PENDING; if 0 rows updated, unlock and return (duplicate/stale message).  
   - **`MockJobTaskExecutor.execute(job)`** — simulate work.  
   - On success: **`JobStateService.markSuccess`**.  
   - On failure: **`JobStateService.recordExecutionFailure`** — maybe **`JobQueueKafkaProducer`** sends another message (retry) or marks **`FAILED`**.  
   - **`finally`**: Redis unlock.

9. Back in **`FairPriorityJobIngress`**, after `processQueuedJob` returns → **`acknowledgment.acknowledge()`** — Kafka marks offset so the message is not redelivered under normal settings.

### Phase 3 — See the result

10. **`GET /jobs` or `/jobs/{id}`** again → **`JobController`** → **`JobService`** → **`JobRepository`** → row now **`SUCCESS`** (or **`FAILED`** / still **`RUNNING`** briefly).

---

## 9. Alternate workflow: REST worker (`WorkerJobController`)

- **`POST /api/workers/jobs/claim-next`** → **`JobService.claimNextPendingJob`** → **`JobRepository.findPendingForClaim`** (HIGH first, then oldest) → status **`RUNNING`**.  
- **`POST /api/workers/jobs/{id}/complete`** → **`JobService.completeJob`** → **`SUCCESS`** or **`FAILED`**.

No Kafka in this path. **Do not** mix heavy Kafka consumption and REST claim on the **same** job set for demos — they can race.

**Files:** `WorkerJobController.java`, `JobService.java`, `JobRepository.java` (scheduler).

---

## 10. API retry (`POST /jobs/{id}/retry`)

1. Only if status is **`FAILED`**.  
2. **`JobService.retryFailedJob`** sets **`PENDING`**, increments **`retryCount`**, saves.  
3. Publishes **`JobCreatedEvent`** again → same after-commit path → Kafka message on the job’s **priority** topic.

---

## 11. Glossary

| Term | One-line meaning |
|------|------------------|
| **Topic** | Named stream in Kafka; producers append records, consumers subscribe. |
| **Consumer group** | Kafka balances partitions across consumers with the **same group id**; each partition is read by one consumer in the group at a time. |
| **Partition** | Shard of a topic; ordering is guaranteed **within** a partition (per key helps stick one job id to one partition). |
| **Ack (acknowledge)** | Consumer tells Kafka “this record is done” — influences whether Kafka will redeliver. |
| **JPA / Hibernate** | Java ORM: **`@Entity`** classes map to SQL tables; **`Repository`** interfaces generate queries. |
| **`ddl-auto: update`** | On startup, Hibernate aligns DB schema with entities (dev-friendly; not typical for prod migrations). |
| **`ddl-auto: validate`** | Fail startup if schema does not match entities. |
| **`@Transactional`** | Method runs inside a database transaction (commit or rollback together). |
| **`TransactionalEventListener`** | Run code **after** commit (or rollback) so side effects match DB outcome. |
| **Redis NX** | “Set this key only if it does not exist” — primitive for a simple lock. |
| **TTL** | Key expires automatically — avoids deadlock if a JVM dies holding a lock. |

---

## 12. Configuration touchpoints (quick reference)

| Concern | Where |
|---------|--------|
| DB URL / user / password | Both `application.yml`, env `DB_*`, `SPRING_DATASOURCE_*` |
| API port | `SERVER_PORT`, `server.port` |
| Kafka bootstrap | `KAFKA_BOOTSTRAP_SERVERS` |
| Topic names | `KAFKA_JOB_QUEUE_HIGH_TOPIC`, `KAFKA_JOB_QUEUE_LOW_TOPIC`, `app.kafka.*` |
| Worker consumer groups | `KAFKA_CONSUMER_GROUP_HIGH`, `LOW`, `app.kafka.consumer-groups` |
| Redis | `REDIS_HOST`, `REDIS_PORT`, `REDIS_PASSWORD` |
| Max worker attempts | `JOB_WORKER_MAX_EXECUTION_ATTEMPTS` |
| Lock TTL / prefix | `JOB_WORKER_LOCK_TTL_SECONDS`, `JOB_WORKER_LOCK_KEY_PREFIX` |
| Dashboard → API | `VITE_API_PROXY_TARGET`, `VITE_API_BASE_URL` |

---

## 13. Behaviour notes (limitations)

- **At-least-once Kafka:** the same message can be delivered more than once; **DB claim** + **Redis lock** reduce double execution but do not magically make every integration idempotent.  
- **Ordering:** only **per partition**, not globally across the whole topic.  
- **Stuck `RUNNING`:** if a worker dies mid-job, the row can stay `RUNNING`; production systems add timeouts or reclaim logic.  
- **Mock executor:** real systems swap in real I/O and idempotency keys.

---

## 14. Related files in repo

| Doc / script | Purpose |
|--------------|---------|
| [README.md](../README.md) | Runbook, API tables, troubleshooting |
| [LEARNING_RESOURCES.md](./LEARNING_RESOURCES.md) | External tutorials and official manuals |
| [scripts/demo-burst-jobs.sh](../scripts/demo-burst-jobs.sh) | Burst `POST /jobs` |

---

*Document matches this repository layout: Spring Boot 3, Java 21 in Maven POMs, dual Kafka topics, fair worker ingress, Redis locks, optional React dashboard.*
