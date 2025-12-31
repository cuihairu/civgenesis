package io.github.cuihairu.civgenesis.registry;

public final class InstanceId {
    private InstanceId() {}

    private static final int REGION_BITS = 12;
    private static final int HOST_BITS = 20;
    private static final int PROCESS_TYPE_BITS = 8;
    private static final int INDEX_BITS = 12;
    private static final int RESERVED_BITS = 12;

    private static final int RESERVED_SHIFT = 0;
    private static final int INDEX_SHIFT = RESERVED_SHIFT + RESERVED_BITS;
    private static final int PROCESS_TYPE_SHIFT = INDEX_SHIFT + INDEX_BITS;
    private static final int HOST_SHIFT = PROCESS_TYPE_SHIFT + PROCESS_TYPE_BITS;
    private static final int REGION_SHIFT = HOST_SHIFT + HOST_BITS;

    private static final long MASK_12 = (1L << 12) - 1;
    private static final long MASK_20 = (1L << 20) - 1;
    private static final long MASK_8 = (1L << 8) - 1;

    public static long encode(int region, int host, int processType, int index, int reserved) {
        checkRange("region", region, REGION_BITS);
        checkRange("host", host, HOST_BITS);
        checkRange("processType", processType, PROCESS_TYPE_BITS);
        checkRange("index", index, INDEX_BITS);
        checkRange("reserved", reserved, RESERVED_BITS);

        long id = 0;
        id |= ((long) reserved & MASK_12) << RESERVED_SHIFT;
        id |= ((long) index & MASK_12) << INDEX_SHIFT;
        id |= ((long) processType & MASK_8) << PROCESS_TYPE_SHIFT;
        id |= ((long) host & MASK_20) << HOST_SHIFT;
        id |= ((long) region & MASK_12) << REGION_SHIFT;
        return id;
    }

    public static int region(long instanceId) {
        return (int) ((instanceId >>> REGION_SHIFT) & MASK_12);
    }

    public static int host(long instanceId) {
        return (int) ((instanceId >>> HOST_SHIFT) & MASK_20);
    }

    public static int processType(long instanceId) {
        return (int) ((instanceId >>> PROCESS_TYPE_SHIFT) & MASK_8);
    }

    public static int index(long instanceId) {
        return (int) ((instanceId >>> INDEX_SHIFT) & MASK_12);
    }

    public static int reserved(long instanceId) {
        return (int) ((instanceId >>> RESERVED_SHIFT) & MASK_12);
    }

    public static boolean sameHost(long a, long b) {
        return region(a) == region(b) && host(a) == host(b);
    }

    private static void checkRange(String name, int v, int bits) {
        int max = (1 << bits) - 1;
        if (v < 0 || v > max) {
            throw new IllegalArgumentException(name + " out of range: " + v + " (0.." + max + ")");
        }
    }
}

