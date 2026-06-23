-- Initial schema for flowguard.

CREATE TABLE dag_policy (
    id          BIGSERIAL PRIMARY KEY,
    dag_id      VARCHAR(255) NOT NULL UNIQUE,
    policy_json TEXT         NOT NULL,
    updated_by  VARCHAR(255) NOT NULL,
    updated_at  TIMESTAMP    NOT NULL
);

CREATE TABLE run_record (
    id           BIGSERIAL PRIMARY KEY,
    dag_id       VARCHAR(255) NOT NULL,
    requested_by VARCHAR(255) NOT NULL,
    outcome      VARCHAR(32)  NOT NULL,
    detail       VARCHAR(2000),
    dag_run_id   VARCHAR(255),
    requested_at TIMESTAMP    NOT NULL
);

CREATE INDEX idx_run_dag ON run_record (dag_id);
CREATE INDEX idx_run_dag_at ON run_record (dag_id, requested_at);
