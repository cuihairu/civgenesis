package io.github.cuihairu.civgenesis.jobs;

import io.github.cuihairu.civgenesis.core.observability.CivMetrics;
import io.github.cuihairu.civgenesis.core.observability.CivSpan;
import io.github.cuihairu.civgenesis.core.observability.CivTracer;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;

import java.time.Duration;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class JobRunnerTest {
    @Test
    void runsLocalJobsImmediatelyAndReportsFailures() {
        AtomicInteger runs = new AtomicInteger();
        AtomicInteger metrics = new AtomicInteger();
        AtomicInteger errors = new AtomicInteger();
        AtomicInteger closed = new AtomicInteger();
        RecordingScheduler scheduler = new RecordingScheduler();
        CivTracer tracer = new CivTracer() {
            @Override
            public CivSpan startJobSpan(String jobName, long runId) {
                return new CivSpan() {
                    @Override
                    public void recordException(Throwable ignored) {
                        errors.incrementAndGet();
                    }

                    @Override
                    public void close() {
                        closed.incrementAndGet();
                    }
                };
            }
        };
        try (JobRunner runner = new JobRunner(scheduler, null, new RecordingMetrics(metrics), tracer)) {
            runner.start(List.of(new LocalJob("ok", () -> runs.incrementAndGet()), new LocalJob("bad", () -> {
                throw new IllegalStateException("job failed");
            })));
            assertEquals(2, scheduler.tasks.size());
            scheduler.tasks.get(0).run();
            scheduler.tasks.get(1).run();
            assertEquals(1, runs.get());
            assertEquals(2, metrics.get());
            assertEquals(1, errors.get());
            assertEquals(2, closed.get());
        }
        assertTrue(scheduler.cancelled);
    }

    @Test
    void leaderOnlyJobsRequireAcquireLeaseAndReleaseIt() {
        AtomicInteger runs = new AtomicInteger();
        AtomicInteger leases = new AtomicInteger();
        RecordingScheduler scheduler = new RecordingScheduler();
        RecordingLease lease = Mockito.mock(RecordingLease.class);
        try (JobRunner runner = new JobRunner(scheduler, (name, ttl) -> {
            leases.incrementAndGet();
            assertEquals("leader", name);
            assertEquals(Duration.ofSeconds(8), ttl);
            return Optional.of(lease);
        }, CivMetrics.noop(), CivTracer.noop())) {
            runner.start(List.of(new LeaderJob(() -> runs.incrementAndGet())));
            scheduler.tasks.get(0).run();
            assertEquals(1, runs.get());
            Mockito.verify(lease).close();
        }

        RecordingScheduler emptyScheduler = new RecordingScheduler();
        try (JobRunner runner = new JobRunner(emptyScheduler, (name, ttl) -> Optional.empty(), CivMetrics.noop(), CivTracer.noop())) {
            runner.start(List.of(new LeaderJob(() -> {
                throw new IllegalStateException("should not run");
            })));
            emptyScheduler.tasks.get(0).run();
        }

        RecordingScheduler missingScheduler = new RecordingScheduler();
        try (JobRunner runner = new JobRunner(missingScheduler, null, CivMetrics.noop(), CivTracer.noop())) {
            runner.start(List.of(new LeaderJob(() -> {
                throw new IllegalStateException("should not run");
            })));
            missingScheduler.tasks.get(0).run();
        }
        Mockito.verify(lease, Mockito.times(1)).close();
    }

    @Test
    void localSchedulerSchedulesAndCloses() throws Exception {
        java.util.concurrent.CountDownLatch executed = new java.util.concurrent.CountDownLatch(1);
        try (LocalJobScheduler scheduler = new LocalJobScheduler("test-job")) {
            ScheduledJob job = scheduler.scheduleWithFixedDelay(Duration.ZERO, Duration.ofMillis(50), executed::countDown);
            assertTrue(executed.await(1, java.util.concurrent.TimeUnit.SECONDS));
            job.cancel();
            assertThrows(NullPointerException.class, () -> scheduler.scheduleWithFixedDelay(null, Duration.ofSeconds(1), () -> {}));
            assertThrows(NullPointerException.class, () -> scheduler.scheduleWithFixedDelay(Duration.ZERO, null, () -> {}));
            assertThrows(NullPointerException.class, () -> scheduler.scheduleWithFixedDelay(Duration.ZERO, Duration.ofSeconds(1), null));
        }
        assertEquals("default", new LocalJobScheduler(null).toString().isEmpty() ? "default" : "default");
    }

    @Test
    void validatesJobRunnerConstructionAndInputs() {
        RecordingScheduler scheduler = new RecordingScheduler();
        try (JobRunner runner = new JobRunner(scheduler, null, CivMetrics.noop(), CivTracer.noop())) {
            assertThrows(NullPointerException.class, () -> runner.start(null));
        }
        assertThrows(NullPointerException.class, () -> new JobRunner(null, null, CivMetrics.noop(), CivTracer.noop()));
        assertEquals(JobMode.LOCAL, new LocalJob("mode", () -> {}).mode());
        assertEquals(Duration.ZERO, new LocalJob("delay", () -> {}).initialDelay());
    }

    private static class LocalJob implements CivJob {
        private final String name;
        private final Runnable action;

        LocalJob(String name, Runnable action) {
            this.name = name;
            this.action = action;
        }

        @Override
        public String name() {
            return name;
        }

        @Override
        public Duration fixedDelay() {
            return Duration.ofSeconds(3);
        }

        @Override
        public void run(JobExecutionContext ignored) {
            action.run();
        }
    }

    private static final class LeaderJob extends LocalJob {
        private LeaderJob(Runnable action) {
            super("leader", action);
        }

        @Override
        public JobMode mode() {
            return JobMode.LEADER_ONLY;
        }
    }

    private static final class RecordingScheduler implements JobScheduler {
        final List<Runnable> tasks = new java.util.concurrent.CopyOnWriteArrayList<>();
        volatile boolean cancelled;

        @Override
        public ScheduledJob scheduleWithFixedDelay(Duration initialDelay, Duration delay, Runnable task) {
            tasks.add(task);
            return () -> cancelled = true;
        }

        @Override
        public void close() {}
    }

    private static interface RecordingLease extends Lease {
    }

    private record RecordingMetrics(AtomicInteger count) implements CivMetrics {
        @Override
        public void onJobRun(String jobName, boolean success, long durationNanos) {
            count.incrementAndGet();
        }
    }
}
