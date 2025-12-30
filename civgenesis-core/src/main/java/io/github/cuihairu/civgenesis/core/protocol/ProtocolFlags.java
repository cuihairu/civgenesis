package io.github.cuihairu.civgenesis.core.protocol;

public final class ProtocolFlags {
    private ProtocolFlags() {}

    public static final long ERROR = 0x01;
    public static final long COMPRESS = 0x02;
    public static final long ENCRYPT = 0x04;
    public static final long ACK_REQUIRED = 0x08;
}

