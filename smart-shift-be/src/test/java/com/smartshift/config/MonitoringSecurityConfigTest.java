package com.smartshift.config;

import java.nio.file.*;
import java.io.IOException;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import static org.junit.jupiter.api.Assertions.*;

class MonitoringSecurityConfigTest {
    @TempDir Path folder;
    @Test void missingSecretStopsStartup() {
        assertThrows(IOException.class, () -> MonitoringSecurityConfig.readPassword(folder.resolve("missing")));
    }
    @Test void shortSecretStopsStartup() throws Exception {
        Path file = folder.resolve("short"); Files.writeString(file, "weak-password");
        assertThrows(IllegalStateException.class, () -> MonitoringSecurityConfig.readPassword(file));
    }
    @Test void secretFileCanEndWithNewline() throws Exception {
        Path file = folder.resolve("strong"); String password = "long-secret-for-monitoring-at-least-24";
        Files.writeString(file, password + "\n");
        assertEquals(password, MonitoringSecurityConfig.readPassword(file));
    }
}
