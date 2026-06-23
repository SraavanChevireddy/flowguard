# Changelog

All notable changes to this project are documented here.
The format is based on [Keep a Changelog](https://keepachangelog.com/en/1.1.0/),
and this project adheres to [Semantic Versioning](https://semver.org/spec/v2.0.0.html).

## [0.1.0] - 2026-06-23

### Added
- Initial public release.
- Policy engine with allowlist, required-conf, required-role, max-active-runs,
  rate-limit, and freeze-window rules (including windows crossing midnight).
- Per-DAG and wildcard default policies, stored as JSON.
- Evaluate (dry-run) and trigger endpoints; allowed triggers forwarded to Airflow's
  stable REST API, blocked ones rejected with the full list of violations.
- Append-only run/decision history backing both audit and rate limiting.
- REST API with OpenAPI/Swagger UI; Docker + docker-compose.
