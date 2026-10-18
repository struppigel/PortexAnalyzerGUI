package io.github.struppigel.gui.utils;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.File;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class LoggingTest {

    @TempDir
    Path folder;

    @Test
    void logFileIsInTheConfiguredFolder() {
        String previous = System.getProperty("portex.log.dir");
        try {
            System.setProperty("portex.log.dir", folder.toString());
            LogSetup.init();
            assertEquals(folder.toString(), System.getProperty("portex.log.dir"));
            assertEquals(new File(folder.toFile(), "portexanalyzer.log"), LogSetup.logFile());
        } finally {
            restore(previous);
        }
    }

    @Test
    void theDefaultFolderIsWritable() throws Exception {
        File directory = LogSetup.chooseDirectory();
        assertTrue(Files.isDirectory(directory.toPath()), directory.toString());
        Files.delete(Files.createTempFile(directory.toPath(), "probe", ".tmp"));
    }

    @Test
    void exceptionsAreWrittenToTheLogFileWithTheirStackTrace() throws Exception {
        Logger logger = LogManager.getLogger("io.github.struppigel.test.LoggingTest");
        logger.info("info message of the logging test");
        logger.error("error message of the logging test", new IllegalStateException("boom of the logging test"));
        String log = new String(Files.readAllBytes(LogSetup.logFile().toPath()), StandardCharsets.UTF_8);
        assertTrue(log.contains("info message of the logging test"), "INFO messages are logged");
        assertTrue(log.contains("java.lang.IllegalStateException: boom of the logging test"), "the exception");
        assertTrue(log.contains("at io.github.struppigel.gui.utils.LoggingTest.exceptionsAreWrittenToTheLogFile"),
                "the stack trace");
    }

    @Test
    void messagesOfOtherLibrariesBelowErrorAreNotLogged() throws Exception {
        LogManager.getLogger("some.other.library").info("message of another library");
        LogManager.getLogger("io.github.struppigel.test.LoggingTest").error("flush marker");
        String log = new String(Files.readAllBytes(LogSetup.logFile().toPath()), StandardCharsets.UTF_8);
        assertFalse(log.contains("message of another library"));
    }

    private static void restore(String previous) {
        if (previous == null) {
            System.clearProperty("portex.log.dir");
        } else {
            System.setProperty("portex.log.dir", previous);
        }
    }
}
