package io.github.cuihairu.civgenesis.jobs;

import java.time.Duration;
import java.util.Optional;

public interface LeaseProvider {
    Optional<Lease> tryAcquire(String name, Duration ttl);
}

