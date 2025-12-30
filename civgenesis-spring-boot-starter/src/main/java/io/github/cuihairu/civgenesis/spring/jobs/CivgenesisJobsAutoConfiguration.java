package io.github.cuihairu.civgenesis.spring.jobs;

import io.github.cuihairu.civgenesis.core.observability.CivMetrics;
import io.github.cuihairu.civgenesis.core.observability.CivTracer;
import io.github.cuihairu.civgenesis.jobs.CivJob;
import io.github.cuihairu.civgenesis.jobs.JobRunner;
import io.github.cuihairu.civgenesis.jobs.JobScheduler;
import io.github.cuihairu.civgenesis.jobs.LeaseProvider;
import io.github.cuihairu.civgenesis.jobs.LocalJobScheduler;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.ApplicationContext;
import org.springframework.context.SmartLifecycle;
import org.springframework.context.annotation.Bean;

import java.util.Arrays;
import java.util.List;

@AutoConfiguration
@EnableConfigurationProperties(CivgenesisJobsProperties.class)
@ConditionalOnProperty(prefix = "civgenesis.jobs", name = "enabled", havingValue = "true")
public class CivgenesisJobsAutoConfiguration {
    @Bean
    @ConditionalOnMissingBean
    public JobScheduler civgenesisJobScheduler(CivgenesisJobsProperties props) {
        return new LocalJobScheduler(props.getThreadName());
    }

    @Bean
    @ConditionalOnMissingBean
    public JobRunner civgenesisJobRunner(JobScheduler scheduler, CivMetrics metrics, CivTracer tracer, ApplicationContext ctx) {
        LeaseProvider leaseProvider = ctx.getBeanProvider(LeaseProvider.class).getIfAvailable();
        return new JobRunner(scheduler, leaseProvider, metrics, tracer);
    }

    @Bean
    public SmartLifecycle civgenesisJobRunnerLifecycle(JobRunner runner, ApplicationContext ctx) {
        return new SmartLifecycle() {
            private volatile boolean running = false;

            @Override
            public void start() {
                String[] names = ctx.getBeanNamesForType(CivJob.class);
                List<CivJob> jobs = Arrays.stream(names).map(n -> (CivJob) ctx.getBean(n)).toList();
                runner.start(jobs);
                running = true;
            }

            @Override
            public void stop() {
                running = false;
                runner.close();
            }

            @Override
            public boolean isRunning() {
                return running;
            }

            @Override
            public int getPhase() {
                return Integer.MAX_VALUE - 20;
            }
        };
    }
}

