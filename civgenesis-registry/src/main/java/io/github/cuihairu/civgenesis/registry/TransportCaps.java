package io.github.cuihairu.civgenesis.registry;

public final class TransportCaps {
    private TransportCaps() {}

    public static final long TCP = 1L << 0;
    public static final long UDS = 1L << 1;
    public static final long SHM_AERON_IPC = 1L << 2;
    public static final long SHM_MMAP_QUEUE = 1L << 3;
    public static final long GRPC = 1L << 4;
}

