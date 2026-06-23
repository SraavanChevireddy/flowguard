package com.sraavan.flowguard.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Lob;
import jakarta.persistence.Table;

import java.time.Instant;

/**
 * A stored policy bound to a DAG id (or the special id {@code *} for a default policy).
 * The policy itself is persisted as JSON so the rule shape can evolve without schema
 * migrations.
 */
@Entity
@Table(name = "dag_policy")
public class DagPolicy {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "dag_id", nullable = false, unique = true)
    private String dagId;

    @Lob
    @Column(name = "policy_json", nullable = false)
    private String policyJson;

    @Column(nullable = false)
    private String updatedBy;

    @Column(nullable = false)
    private Instant updatedAt = Instant.now();

    protected DagPolicy() {
    }

    public DagPolicy(String dagId, String policyJson, String updatedBy) {
        this.dagId = dagId;
        this.policyJson = policyJson;
        this.updatedBy = updatedBy;
        this.updatedAt = Instant.now();
    }

    public void update(String policyJson, String updatedBy) {
        this.policyJson = policyJson;
        this.updatedBy = updatedBy;
        this.updatedAt = Instant.now();
    }

    public Long getId() {
        return id;
    }

    public String getDagId() {
        return dagId;
    }

    public String getPolicyJson() {
        return policyJson;
    }

    public String getUpdatedBy() {
        return updatedBy;
    }

    public Instant getUpdatedAt() {
        return updatedAt;
    }
}
