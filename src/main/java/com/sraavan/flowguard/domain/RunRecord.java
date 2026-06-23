package com.sraavan.flowguard.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Index;
import jakarta.persistence.Table;

import java.time.Instant;

/**
 * An append-only record of every trigger decision flowguard made. Doubles as the
 * audit trail and the source of truth for rate-limit / active-run counting.
 */
@Entity
@Table(name = "run_record",
        indexes = {
                @Index(name = "idx_run_dag", columnList = "dag_id"),
                @Index(name = "idx_run_dag_at", columnList = "dag_id, requested_at")
        })
public class RunRecord {

    public enum Outcome {
        ALLOWED,
        BLOCKED,
        FORWARD_FAILED
    }

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "dag_id", nullable = false)
    private String dagId;

    @Column(nullable = false)
    private String requestedBy;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private Outcome outcome;

    @Column(length = 2000)
    private String detail;

    /** The Airflow dag_run_id assigned when a run is actually triggered (null if blocked). */
    private String dagRunId;

    @Column(name = "requested_at", nullable = false)
    private Instant requestedAt = Instant.now();

    protected RunRecord() {
    }

    public RunRecord(String dagId, String requestedBy, Outcome outcome, String detail, String dagRunId) {
        this.dagId = dagId;
        this.requestedBy = requestedBy;
        this.outcome = outcome;
        this.detail = detail;
        this.dagRunId = dagRunId;
        this.requestedAt = Instant.now();
    }

    public Long getId() {
        return id;
    }

    public String getDagId() {
        return dagId;
    }

    public String getRequestedBy() {
        return requestedBy;
    }

    public Outcome getOutcome() {
        return outcome;
    }

    public String getDetail() {
        return detail;
    }

    public String getDagRunId() {
        return dagRunId;
    }

    public Instant getRequestedAt() {
        return requestedAt;
    }
}
