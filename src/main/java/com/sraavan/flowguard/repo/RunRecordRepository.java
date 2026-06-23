package com.sraavan.flowguard.repo;

import com.sraavan.flowguard.domain.RunRecord;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.Instant;
import java.util.List;

public interface RunRecordRepository extends JpaRepository<RunRecord, Long> {

    List<RunRecord> findByDagIdOrderByRequestedAtDesc(String dagId);

    /** Count runs we actually allowed for a DAG since a cutoff — drives rate limiting. */
    @Query("""
            select count(r) from RunRecord r
            where r.dagId = :dagId and r.outcome = com.sraavan.flowguard.domain.RunRecord$Outcome.ALLOWED
              and r.requestedAt >= :since
            """)
    long countAllowedSince(@Param("dagId") String dagId, @Param("since") Instant since);
}
