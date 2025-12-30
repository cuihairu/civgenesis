package io.github.cuihairu.civgenesis.core.protocol;

public enum FrameType {
    REQ(1),
    RESP(2),
    PUSH(3),
    ACK(4),
    PING(5),
    PONG(6);

    private final int id;

    FrameType(int id) {
        this.id = id;
    }

    public int id() {
        return id;
    }

    public static FrameType fromId(int id) {
        for (FrameType value : values()) {
            if (value.id == id) {
                return value;
            }
        }
        throw new IllegalArgumentException("Unknown FrameType id: " + id);
    }
}

