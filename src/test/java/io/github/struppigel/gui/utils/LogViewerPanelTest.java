package io.github.struppigel.gui.utils;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.File;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class LogViewerPanelTest {

    @TempDir
    File folder;

    @Test
    void showsTheWholeLog() throws Exception {
        File log = new File(folder, "test.log");
        Files.write(log.toPath(), "first line\nsecond line\n".getBytes(StandardCharsets.UTF_8));
        LogViewerPanel panel = new LogViewerPanel(log, "An error");
        assertEquals("first line\nsecond line\n", panel.getText());
    }

    @Test
    void showsLaterMessagesAfterRefresh() throws Exception {
        File log = new File(folder, "test.log");
        Files.write(log.toPath(), "before\n".getBytes(StandardCharsets.UTF_8));
        LogViewerPanel panel = new LogViewerPanel(log, "An error");
        Files.write(log.toPath(), "before\nafter\n".getBytes(StandardCharsets.UTF_8));
        panel.refresh();
        assertEquals("before\nafter\n", panel.getText());
    }

    @Test
    void largeLogsAreCutAtALineStartAndSayso() throws Exception {
        File log = new File(folder, "test.log");
        StringBuilder content = new StringBuilder();
        for (int i = 0; i < 2000; i++) {
            content.append("line number ").append(i).append('\n');
        }
        Files.write(log.toPath(), content.toString().getBytes(StandardCharsets.UTF_8));
        String shown = LogViewerPanel.readTail(log, 1024);
        assertTrue(shown.startsWith("[Only the last 1 KiB of the log are shown]\nline number "), shown);
        assertTrue(shown.endsWith("line number 1999\n"), shown);
        assertTrue(shown.length() < 1100, "length " + shown.length());
    }

    @Test
    void anUnreadableLogIsReportedInsteadOfThrowing() {
        String shown = LogViewerPanel.readTail(new File(folder, "missing.log"), 1024);
        assertTrue(shown.startsWith("Could not read the log file"), shown);
    }
}
