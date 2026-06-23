# flowguard

[![CI](https://github.com/SraavanChevireddy/flowguard/actions/workflows/ci.yml/badge.svg)](https://github.com/SraavanChevireddy/flowguard/actions/workflows/ci.yml)
[![License: MIT](https://img.shields.io/badge/License-MIT-yellow.svg)](LICENSE)
[![Java 21](https://img.shields.io/badge/Java-21-orange.svg)](https://adoptium.net/)
[![Spring Boot 3](https://img.shields.io/badge/Spring%20Boot-3.3-brightgreen.svg)](https://spring.io/projects/spring-boot)

**A policy and governance control-plane for Apache Airflow.** Put flowguard in front of your `triggerDagRun` calls and every run is checked against a policy, rate-limited, and audited — *before* it reaches the scheduler.

> Status: **early-stage (v0.1).** The policy engine and governance workflow are real and fully tested against a stubbed Airflow. The live Airflow transport uses the stable REST API but has had limited real-world mileage — see [Roadmap](#roadmap).

---

## The problem

Airflow's API will trigger anything, anytime, for anyone with the credential. That's fine until:

- An automated job fires a backfill **100 times in a loop** and floods the cluster.
- Someone triggers a heavy DAG **during business hours** when it should only run overnight.
- A run goes out **without the `run_date` conf** it needs and silently processes the wrong partition.
- Three months later nobody can answer **"who triggered this run and why was it allowed?"**

flowguard is a thin control-plane that sits between callers and Airflow and answers one question on every request: *should this run be allowed right now?* — then forwards it if yes, blocks it with reasons if no, and records the decision either way.

## What a policy can enforce

Policies are per-DAG (with a wildcard `*` default). Every rule is optional — set only what you need.

| Rule | Blocks when |
|---|---|
| **DAG allowlist** | the DAG id isn't in the approved set |
| **Required conf keys** | a mandatory `conf` key is missing or null |
| **Required roles** | the requester holds none of the required roles |
| **Max active runs** | the DAG already has N runs active |
| **Rate limit** | the DAG already ran N times in the last *window* minutes |
| **Freeze windows** | the current time falls in a blocked range (e.g. 09:00–17:00, or 22:00–06:00 across midnight) |

A blocked request returns **403** with *every* violated rule, so callers see the full picture, not just the first failure. The engine ([`PolicyEngine`](src/main/java/com/sraavan/flowguard/policy/PolicyEngine.java)) is pure and Spring-free — exhaustively unit-tested.

## Quick start

```bash
docker compose up --build      # starts Postgres + flowguard on :8088
# point flowguard at your Airflow:
#   AIRFLOW_URL=http://your-airflow:8080 AIRFLOW_USER=... AIRFLOW_PASSWORD=...
```

Swagger UI: `http://localhost:8088/swagger-ui.html` · Health: `http://localhost:8088/actuator/health`

## Walkthrough

```bash
# 1. Set a policy for the "orders_etl" DAG:
#    only allowlisted, must pass run_date, no triggering 09:00-17:00, max 3/hour.
curl -XPUT localhost:8088/api/dags/orders_etl/policy -H 'Content-Type: application/json' -d '{
  "updatedBy": "data-platform",
  "policy": {
    "dagAllowlist": ["orders_etl"],
    "requiredConfKeys": ["run_date"],
    "requiredRoles": ["data-eng"],
    "maxActiveRuns": 2,
    "maxRunsPerWindow": 3,
    "windowMinutes": 60,
    "freezeWindows": [
      { "start": "09:00", "end": "17:00", "reason": "business hours" }
    ]
  }
}'

# 2. Dry-run a trigger (no side effects) — great for CI or a UI "can I run this?" check
curl -XPOST localhost:8088/api/dags/orders_etl/evaluate -H 'Content-Type: application/json' -d '{
  "requestedBy": "alice", "conf": {}, "roles": ["data-eng"]
}'
# -> { "allowed": false, "violations": [ { "rule": "MISSING_REQUIRED_CONF", ... } ] }

# 3. Actually trigger — evaluated, then forwarded to Airflow if allowed
curl -XPOST localhost:8088/api/dags/orders_etl/trigger -H 'Content-Type: application/json' -d '{
  "requestedBy": "alice", "conf": { "run_date": "2026-06-23" }, "roles": ["data-eng"]
}'
# -> 202 { "allowed": true, "dagRunId": "flowguard__..." }

# 4. Audit trail
curl localhost:8088/api/dags/orders_etl/history
```

## API

| Method | Path | Purpose |
|---|---|---|
| `PUT` | `/api/dags/{dagId}/policy` | Set/update a policy (use `*` for the default) |
| `GET` | `/api/dags/{dagId}/policy` | View the effective policy for a DAG |
| `POST` | `/api/dags/{dagId}/evaluate` | Dry-run: would this trigger be allowed? |
| `POST` | `/api/dags/{dagId}/trigger` | Evaluate and, if allowed, trigger the run |
| `GET` | `/api/dags/{dagId}/history` | Decision / audit history |

## How it forwards to Airflow

When a request passes policy, flowguard calls Airflow's stable REST API
(`POST /api/v1/dags/{dagId}/dagRuns`) with HTTP Basic auth and returns the assigned
`dag_run_id`. The Airflow client is an [interface](src/main/java/com/sraavan/flowguard/airflow/AirflowClient.java),
so tests run against an in-memory stub and you could add other transports later.

## Architecture

```
policy/    pure policy engine — Policy, PolicyEngine, TriggerRequest, PolicyDecision
airflow/   AirflowClient interface + REST implementation
service/   GuardService — resolves policy, evaluates, forwards, audits (transactional)
domain/    JPA entities: DagPolicy, RunRecord
repo/      Spring Data repositories (rate-limit counting lives here)
web/       REST controller + DTOs
db/migration/  Flyway schema (Postgres)
```

## Tech

Java 21 · Spring Boot 3.3 · Spring Data JPA · Flyway · PostgreSQL · springdoc/OpenAPI · JUnit 5 · Docker.

## Roadmap

Honest v0.1. Natural next steps for a real deployment:

- [ ] AuthN (OIDC) so `requestedBy`/`roles` come from a verified token, not the request body
- [ ] A Python decorator / Airflow plugin so DAGs route triggers through flowguard transparently
- [ ] Approval workflow for one-off overrides of a freeze window
- [ ] Prometheus metrics for allowed/blocked counts per DAG
- [ ] Pluggable policy sources (file/GitOps) in addition to the API
- [ ] Pagination on history

Issues and PRs welcome — see [CONTRIBUTING.md](CONTRIBUTING.md).

## License

[MIT](LICENSE) © Sraavan Chevireddy
