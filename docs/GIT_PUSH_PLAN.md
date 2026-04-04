# GitHub: incremental push plan

This repo is split into **several logical commits** (infra → API → worker → UI → docs). You can **push one commit per day** so `main` grows in believable steps.

## One-time setup

1. Create an **empty** repository on GitHub (no README/license if you want a clean first push).

2. In this folder, set **your** identity for this repo (use the same email as your GitHub account, or your GitHub noreply address):

   ```bash
   cd /path/to/your/clone
   git config user.name "Naysha Singh"
   git config user.email "you@example.com"
   ```

3. Add the remote (replace `YOUR_USER` and `YOUR_REPO`):

   ```bash
   git remote add origin git@github.com:YOUR_USER/YOUR_REPO.git
   ```

4. List commits and copy each **full** hash (first push uses commit 1 only):

   ```bash
   git log --oneline --reverse
   ```

## Push one new commit per day

Assume your history is `C1 → C2 → C3 → C4 → C5` (oldest to newest).

| Day | Command (after remote is empty or already has previous commits) |
|-----|----------------------------------------------------------------|
| 1 | `git push -u origin <C1_full_sha>:main` |
| 2 | `git push origin <C2_full_sha>:main` |
| 3 | `git push origin <C3_full_sha>:main` |
| … | Same pattern: always push the **tip** SHA you want `main` to point to. |

Git sends only **new** objects; each push **fast-forwards** `main` by one (or more) commits if you skip a day and push a later SHA.

**If the remote already has commits:** use normal `git push origin main` after you add new commits on top locally.

## If you already pushed everything

To “replay” slowly you would need a **new repo** or **reset remote** (destructive). Easier: from now on, make **small real commits** and push daily—that is the most natural history.

## Optional: backdated author dates

If you need commits to show **different calendar days** on GitHub, you can amend or recommit with:

```bash
GIT_AUTHOR_DATE="2026-04-01T10:00:00" GIT_COMMITTER_DATE="2026-04-01T10:00:00" git commit ...
```

Use **honest** dates that match when you actually did the work when possible; fabricated timelines can backfire in interviews.

## Commit map (this repository)

After `git log --oneline --reverse`, expect messages like:

1. **chore: add Docker Compose stack and repo ignores** — Postgres, Redis, Kafka/ZK; root `.gitignore`.
2. **feat(api): add Spring Boot job scheduler service** — `distributed-job-scheduler/` (REST, JPA, Kafka producer, OpenAPI).
3. **feat(worker): add Kafka job worker with Redis locking** — `job-worker/`.
4. **feat(dashboard): add React job dashboard** — `job-dashboard/` (source + lockfile; no `node_modules`).
5. **docs: add README, architecture notes, and demo script** — `README.md`, `docs/ARCHITECTURE_AND_WORKFLOW.md`, `scripts/`.
