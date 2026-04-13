# Concepts in this project — where to learn them

This is a **reading list**, not a tutorial. Everything here is **free on the web**. Start with the sections that match what confuses you first; you do not need to read cover-to-cover.

Links were chosen for **plain explanations** where possible; **official docs** are marked so you know what is canonical.

---

## 1. Apache Kafka (messages, topics, producers, consumers)

**What your project uses:** The API **produces** JSON messages to topics `job-queue-high` and `job-queue-low`. Workers **consume** with `@KafkaListener`, use **consumer groups**, and **acknowledge** messages after processing.

| Read this | Why it helps |
|-----------|----------------|
| [Kafka documentation — Getting started](https://kafka.apache.org/documentation/#gettingStarted) | **Official.** High-level “what is Kafka” and how the docs are organized. |
| [Introduction (Kafka)](https://kafka.apache.org/intro) | Short **official** overview: events, topics, producers, consumers. |
| [Kafka design documentation](https://kafka.apache.org/documentation/#design) | **Official**, deeper: logs, topics, consumer groups (good when you are ready). |

**Spring wiring (your code uses Spring, not raw Kafka clients only):**

| Read this | Why it helps |
|-----------|----------------|
| [Spring Kafka — Quick tour](https://docs.spring.io/spring-kafka/reference/quick-tour.html) | **Official.** `KafkaTemplate`, `@KafkaListener`, minimal examples. |
| [Spring Kafka reference](https://docs.spring.io/spring-kafka/reference/index.html) | **Official** full manual; use the sidebar to jump to listeners, error handling, etc. |

**Delivery semantics (why the same job can be seen twice):**

| Read this | Why it helps |
|-----------|----------------|
| [Confluent — Message delivery guarantees](https://docs.confluent.io/kafka/design/delivery-semantics.html) | Very readable on **at-most-once / at-least-once / exactly-once** (Confluent is a major Kafka vendor; wording matches how people talk in interviews). |

*Your worker aims for “process then ack” behavior, which is the usual path toward **at-least-once** processing — duplicates possible, loss avoided if commits are configured carefully.*

---

## 2. Consumer groups and multiple topics

**What your project uses:** **Two topics** and **two consumer group ids** (HIGH lane vs LOW lane), because one consumer group normally shares one subscription pattern; your `FairPriorityJobIngress` then **merges** both streams in process.

| Read this | Why it helps |
|-----------|----------------|
| [Kafka consumer design — consumer groups](https://kafka.apache.org/documentation/#consumerconfigs) (within design docs) | **Official** context around consumers and configuration. |
| [Spring Kafka — receiving messages](https://docs.spring.io/spring-kafka/reference/kafka/receiving.html) | How `@KafkaListener` ties to groups and topics. |

*For a gentler article-style read, many people use Baeldung’s Kafka section (search “Baeldung Kafka consumer groups”) — not official, but often easier as a first pass.*

---

## 3. Redis and distributed locks

**What your project uses:** Before running a job, the worker tries to **acquire a lock** keyed by `jobId` (default prefix `job-worker:exec:`), with **TTL** so a crashed process does not hold the lock forever. Release uses a **token** so one client does not delete another’s lock.

| Read this | Why it helps |
|-----------|----------------|
| [Redis — SET command](https://redis.io/docs/latest/commands/set/) | **Official.** Explains **NX** (set if not exists) and **EX** (expiry) — the building block for simple locks. |
| [Redis — Distributed locks with Redis](https://redis.io/docs/latest/develop/clients/patterns/distributed-locks/) | **Official** pattern doc: single-instance lock, limitations, when **Redlock** is discussed for multi-node Redis. |

*Read the “limitations” part on the Redis lock page — it matches why this project is a **learning** setup, not a proof you solved distributed locking for every failure mode.*

---

## 4. Spring: transactions and “publish after commit”

**What your project uses:** `JobService` publishes `JobCreatedEvent` inside a transaction; `JobCreatedKafkaPublisher` listens with `@TransactionalEventListener(phase = AFTER_COMMIT)` so Kafka is only called if the DB commit succeeded.

| Read this | Why it helps |
|-----------|----------------|
| [Spring Framework — Transaction-bound events](https://docs.spring.io/spring-framework/reference/data-access/transaction/event.html) | **Official.** Explains `@TransactionalEventListener`, phases (`AFTER_COMMIT`, etc.), and `fallbackExecution`. |
| [`TransactionalEventListener` Javadoc](https://docs.spring.io/spring-framework/docs/current/javadoc-api/org/springframework/transaction/event/TransactionalEventListener.html) | **Official** API details. |

*Related idea people mention in blogs: the **transactional outbox** pattern (separate table + publisher). Your project uses the **lighter** “event after commit” approach; searching “transactional outbox pattern” is useful when you want the next level.*

---

## 5. Spring Data JPA, Hibernate, and `ddl-auto`

**What your project uses:** Scheduler uses **`ddl-auto: update`** (creates/updates schema). Worker uses **`validate`** (expects tables to exist).

| Read this | Why it helps |
|-----------|----------------|
| [Spring Boot — Data access](https://docs.spring.io/spring-boot/how-to/data-access.html) | **Official** how-to; links to JPA and initialization behavior. |
| [Spring Boot application properties — JPA](https://docs.spring.io/spring-boot/appendix/application-properties/index.html) (search `ddl-auto`) | **Official** property reference. |

*Hibernate’s own docs on schema tooling also explain `update` vs `validate` (search “Hibernate hbm2ddl”).*

---

## 6. PostgreSQL (durable job state)

**What your project uses:** Single database, `jobs` table — **source of truth** for status, payload, priority, retries.

| Read this | Why it helps |
|-----------|----------------|
| [PostgreSQL documentation — tutorial](https://www.postgresql.org/docs/current/tutorial.html) | **Official** basics if SQL or Postgres is new. |

*Your project does not require advanced Postgres features; understanding **tables, transactions, and connections** is enough to follow the code.*

---

## 7. Docker Compose (local infra)

**What your project uses:** `docker-compose.yml` for Postgres, Redis, Zookeeper, Kafka.

| Read this | Why it helps |
|-----------|----------------|
| [Docker Compose — Overview](https://docs.docker.com/compose/) | **Official** what Compose is and how to run `docker compose up`. |

---

## 8. REST API and OpenAPI (Swagger)

**What your project uses:** Spring MVC controllers; **springdoc-openapi** exposes `/swagger-ui.html` and OpenAPI JSON.

| Read this | Why it helps |
|-----------|----------------|
| [springdoc-openapi](https://springdoc.org/) | **Project docs** for the library you are using. |

---

## 9. React + Vite (dashboard only)

**What your project uses:** `job-dashboard` — Vite dev server, React, Axios; proxy to the API.

| Read this | Why it helps |
|-----------|----------------|
| [Vite documentation](https://vite.dev/guide/) | **Official** dev server, config, env. |
| [React documentation — Learn](https://react.dev/learn) | **Official** modern React tutorial. |

---

## Suggested order if you are new to almost everything

1. Kafka **introduction** + **Spring Kafka quick tour** (half a day of skimming).  
2. Run the project, watch **one message** in logs from API → Kafka → worker.  
3. Read **delivery semantics** (at-least-once) once you see **duplicate** or **retry** behavior.  
4. Read **Redis SET** + **distributed locks** page when tracing `JobDistributedLockService`.  
5. Read **transaction-bound events** when tracing `JobCreatedKafkaPublisher`.

---

## Keeping this file useful

Official URLs and doc structure **change** sometimes. If a link breaks, search the same site for the page title or use the site’s own search box.

To jump back to **how this repository wires things together**, see [ARCHITECTURE_AND_WORKFLOW.md](./ARCHITECTURE_AND_WORKFLOW.md) — start with **section 0** if you want a **file-by-file** map, then **section 8** for one traced request.
