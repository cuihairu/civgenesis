package io.github.cuihairu.civgenesis.jobs;

import io.github.cuihairu.civgenesis.core.observability.CivMetrics;
import io.github.cuihairu.civgenesis.core.observability.CivSpan;
import io.github.cuihairu.civgenesis.core.observability.CivTracer;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicLong;

public final class JobRunner implements AutoCloseable {
    private static final Logger log = LoggerFactory.getLogger(JobRunner.class);

    private final JobScheduler scheduler;
    private final LeaseProvider leaseProvider;
    private final CivMetrics metrics;
    private final CivTracer tracer;

    private final AtomicLong runIds = new AtomicLong(1);
    private final List<ScheduledJob> scheduled = new ArrayList<>();

    public JobRunner(JobScheduler scheduler, LeaseProvider leaseProvider, CivMetrics metrics, CivTracer tracer) {
        this.scheduler = Objects.requireNonNull(scheduler, "scheduler");
        this.leaseProvider = leaseProvider;
        this.metrics = Objects.requireNonNullElse(metrics, CivMetrics.noop());
        this.tracer = Objects.requireNonNullElse(tracer, CivTracer.noop());
    }

    public void start(List<CivJob> jobs) {
        Objects.requireNonNull(jobs, "jobs");
        for (CivJob job : jobs) {
            if (job == null) {
                continue;
            }
            ScheduledJob scheduledJob = scheduler.scheduleWithFixedDelay(job.initialDelay(), job.fixedDelay(), () -> runOnce(job));
            scheduled.add(scheduledJob);
            log.info("job scheduled name={} mode={} delay={}", job.name(), job.mode(), job.fixedDelay());
        }
    }

    private void runOnce(CivJob job) {
        long runId = runIds.getAndIncrement();
        long startedAtMs = System.currentTimeMillis();
        long startNanos = System.nanoTime();

        Optional<Lease> lease = Optional.empty();
        if (job.mode() == JobMode.LEADER_ONLY) {
            if (leaseProvider == null) {
                log.warn("job skipped (missing leaseProvider) name={}", job.name());
                return;
            }
            Duration ttl = job.fixedDelay().plusSeconds(5);
            lease = leaseProvider.tryAcquire(job.name(), ttl);
            if (lease.isEmpty()) {
                return;
            }
        }

        CivSpan span = tracer.startJobSpan(job.name(), runId);
        Lease acquired = lease.orElse(null);
        try {
            job.run(new JobExecutionContext(runId, startedAtMs));
            metrics.onJobRun(job.name(), true, System.nanoTime() - startNanos);
        } catch (Throwable t) {
            span.recordException(t);
            span.setError("job failed");
            metrics.onJobRun(job.name(), false, System.nanoTime() - startNanos);
            log.error("job failed name={} runId={}", job.name(), runId, t);
        } finally {
            try {
                if (acquired != null) {
                    acquired.close();
                }
            } catch (Exception ignore) {
            }
            span.close();
        }
    }

    @Override
    public void close() {
        for (ScheduledJob j : scheduled) {
            try {
                j.cancel();
            } catch (Exception ignore) {
            }
        }
        scheduled.clear();
        scheduler.close();
    }
}
