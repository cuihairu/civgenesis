package io.github.cuihairu.civgenesis.registry;

import java.io.Closeable;

public interface ServiceRegistry extends Closeable {
    void register(ServiceRegistration registration) throws Exception;

    void deregister(ServiceRegistration registration) throws Exception;

    @Override
    default void close() {}
}

