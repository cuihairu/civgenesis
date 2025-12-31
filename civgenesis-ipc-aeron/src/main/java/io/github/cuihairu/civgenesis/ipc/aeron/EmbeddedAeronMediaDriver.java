package io.github.cuihairu.civgenesis.ipc.aeron;

import io.aeron.driver.MediaDriver;

import java.io.Closeable;
import java.nio.file.Path;
import java.util.Objects;

public final class EmbeddedAeronMediaDriver implements Closeable {
    private final MediaDriver driver;

    public EmbeddedAeronMediaDriver(Path aeronDir) {
        Objects.requireNonNull(aeronDir, "aeronDir");
        MediaDriver.Context ctx = new MediaDriver.Context()
                .aeronDirectoryName(aeronDir.toString())
                .dirDeleteOnStart(true)
                .dirDeleteOnShutdown(true);
        this.driver = MediaDriver.launchEmbedded(ctx);
    }

    public String aeronDirectoryName() {
        return driver.aeronDirectoryName();
    }

    @Override
    public void close() {
        driver.close();
    }
}

