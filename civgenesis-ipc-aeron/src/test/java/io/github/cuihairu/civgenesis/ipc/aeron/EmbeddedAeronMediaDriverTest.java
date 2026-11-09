package io.github.cuihairu.civgenesis.ipc.aeron;

import io.aeron.driver.MediaDriver;
import org.junit.jupiter.api.Test;
import org.mockito.MockedStatic;
import org.mockito.Mockito;

import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class EmbeddedAeronMediaDriverTest {

    @Test
    void launchesDriverAndReportsDirectory() {
        try (MockedStatic<MediaDriver> mediaDriver = Mockito.mockStatic(MediaDriver.class)) {
            MediaDriver driver = mock(MediaDriver.class);
            when(MediaDriver.launchEmbedded(any(MediaDriver.Context.class))).thenReturn(driver);
            when(driver.aeronDirectoryName()).thenReturn("/tmp/aeron-test");

            try (EmbeddedAeronMediaDriver embedded = new EmbeddedAeronMediaDriver(Path.of("/tmp/aeron-test"))) {
                assertNotNull(embedded.aeronDirectoryName());
                assertEquals("/tmp/aeron-test", embedded.aeronDirectoryName());
            }
            verify(driver).close();
        }
    }

    @Test
    void requiresNonNullDirectory() {
        assertThrows(NullPointerException.class, () -> new EmbeddedAeronMediaDriver(null));
    }
}
