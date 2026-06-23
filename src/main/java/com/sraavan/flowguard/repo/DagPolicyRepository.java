package com.sraavan.flowguard.repo;

import com.sraavan.flowguard.domain.DagPolicy;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface DagPolicyRepository extends JpaRepository<DagPolicy, Long> {
    Optional<DagPolicy> findByDagId(String dagId);
}
