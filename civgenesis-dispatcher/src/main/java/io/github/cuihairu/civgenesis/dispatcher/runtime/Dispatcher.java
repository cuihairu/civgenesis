package io.github.cuihairu.civgenesis.dispatcher.runtime;

import io.github.cuihairu.civgenesis.core.protocol.Frame;
import io.github.cuihairu.civgenesis.core.transport.Connection;

public interface Dispatcher {
    void onConnect(Connection connection);

    void onDisconnect(Connection connection);

    void handle(Connection connection, Frame frame);
}

