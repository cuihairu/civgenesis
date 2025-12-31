package io.github.cuihairu.civgenesis.jobs.lease.redis;

import io.github.cuihairu.civgenesis.jobs.Lease;
import io.github.cuihairu.civgenesis.jobs.LeaseProvider;
import io.lettuce.core.RedisClient;
import io.lettuce.core.ScriptOutputType;
import io.lettuce.core.SetArgs;
import io.lettuce.core.api.StatefulRedisConnection;
import io.lettuce.core.api.sync.RedisCommands;

import java.time.Duration;
import java.util.Objects;
import java.util.Optional;

public final class RedisLeaseProvider implements LeaseProvider, AutoCloseable {
    private static final String UNLOCK_LUA = """
            if redis.call("get", KEYS[1]) == ARGV[1] then
              return redis.call("del", KEYS[1])
            else
              return 0
            end
            """;

    private final RedisClient client;
    private final StatefulRedisConnection<String, String> connection;
    private final RedisCommands<String, String> commands;
    private final String keyPrefix;

    public RedisLeaseProvider(String redisUri) {
        this(RedisClient.create(Objects.requireNonNull(redisUri, "redisUri")), "civgenesis:lease:");
    }

    public RedisLeaseProvider(RedisClient client, String keyPrefix) {
        this.client = Objects.requireNonNull(client, "client");
        this.connection = client.connect();
        this.commands = connection.sync();
        this.keyPrefix = Objects.requireNonNullElse(keyPrefix, "civgenesis:lease:");
    }

    @Override
    public Optional<Lease> tryAcquire(String name, Duration ttl) {
        Objects.requireNonNull(name, "name");
        Objects.requireNonNull(ttl, "ttl");
        long ttlMillis = Math.max(1, ttl.toMillis());

        String tokenKey = keyPrefix + name + ":token";
        String lockKey = keyPrefix + name + ":lock";

        long token = commands.incr(tokenKey);
        String tokenStr = Long.toString(token);

        String ok = commands.set(lockKey, tokenStr, SetArgs.Builder.nx().px(ttlMillis));
        if (!"OK".equals(ok)) {
            return Optional.empty();
        }
        return Optional.of(new RedisLease(lockKey, token, tokenStr));
    }

    @Override
    public void close() {
        try {
            connection.close();
        } finally {
            client.shutdown();
        }
    }

    private final class RedisLease implements Lease {
        private final String lockKey;
        private final long token;
        private final String tokenStr;
        private volatile boolean closed;

        private RedisLease(String lockKey, long token, String tokenStr) {
            this.lockKey = lockKey;
            this.token = token;
            this.tokenStr = tokenStr;
        }

        @Override
        public long fencingToken() {
            return token;
        }

        @Override
        public void close() {
            if (closed) {
                return;
            }
            closed = true;
            try {
                commands.eval(UNLOCK_LUA, ScriptOutputType.INTEGER, new String[] { lockKey }, tokenStr);
            } catch (Exception ignore) {
            }
        }
    }
}

