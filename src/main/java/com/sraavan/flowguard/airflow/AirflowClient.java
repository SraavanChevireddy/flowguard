package com.sraavan.flowguard.airflow;

import java.util.Map;

/**
 * Minimal abstraction over the Apache Airflow stable REST API. flowguard only needs a
 * few operations; keeping this an interface lets tests run without a live Airflow and
 * lets the transport (HTTP today) change later.
 */
public interface AirflowClient {

    /**
     * Trigger a DAG run.
     *
     * @return the assigned {@code dag_run_id}
     */
    String triggerDagRun(String dagId, Map<String, Object> conf);

    /** @return the number of currently active (queued or running) runs for a DAG. */
    int countActiveRuns(String dagId);
}
