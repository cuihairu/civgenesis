package io.github.cuihairu.civgenesis.dispatcher.runtime;

import io.github.cuihairu.civgenesis.core.error.CivError;

public interface DeferredReply {
    boolean reply(Object resp);

    boolean error(CivError error);

    boolean cancel();
}

