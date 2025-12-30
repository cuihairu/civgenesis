package io.github.cuihairu.civgenesis.jobs;

import java.time.Duration;

public interface CivJob {
    String name();

    default JobMode mode() {
        return JobMode.LOCAL;
    }

    default Duration initialDelay() {
        return Duration.ZERO;
    }

    Duration fixedDelay();

    void run(JobExecutionContext ctx) throws Exception;
}

