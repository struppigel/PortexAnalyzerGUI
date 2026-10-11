package io.github.struppigel.gui;

import org.junit.jupiter.api.Test;

import java.io.BufferedReader;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;

import static org.junit.jupiter.api.Assertions.*;
import static org.junit.jupiter.api.Assumptions.assumeTrue;

class VersionTest {

    @Test
    void aboutVersionMatchesPomVersion() {
        String pomVersion = System.getProperty("project.version");
        assumeTrue(pomVersion != null, "only set when run by Maven");

        assertEquals(pomVersion, AboutFrame.version);
    }

    /**
     * The update check compares this number with the one on GitHub.
     */
    @Test
    void updateVersionIsANumber() throws Exception {
        try (InputStream in = getClass().getResourceAsStream("/upd_version.txt")) {
            assertNotNull(in);
            BufferedReader reader = new BufferedReader(new InputStreamReader(in, StandardCharsets.UTF_8));
            assertTrue(Integer.parseInt(reader.readLine().trim()) > 0);
        }
    }
}
