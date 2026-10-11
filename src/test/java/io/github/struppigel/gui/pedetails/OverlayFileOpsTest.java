package io.github.struppigel.gui.pedetails;

import io.github.struppigel.gui.utils.TestPEData;
import io.github.struppigel.parser.PELoader;
import io.github.struppigel.tools.Overlay;
import io.github.struppigel.testsupport.TestFiles;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.File;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Arrays;
import java.util.Random;

import static io.github.struppigel.testsupport.TestFiles.OVERLAY;
import static org.junit.jupiter.api.Assertions.*;

class OverlayFileOpsTest {

    @TempDir
    Path tempDir;

    @Test
    void writeUntilStopsAtSmallOffset() throws Exception {
        byte[] content = randomBytes(6000);
        File out = tempDir.resolve("out").toFile();

        PEDetailsPanel.writeUntil(write(content), 1000, out);

        assertArrayEquals(Arrays.copyOf(content, 1000), Files.readAllBytes(out.toPath()));
    }

    /**
     * Offsets beyond the first 2048 byte buffer must be honored, too.
     */
    @Test
    void writeUntilStopsAtLargeOffset() throws Exception {
        byte[] content = randomBytes(6000);
        File out = tempDir.resolve("out").toFile();

        PEDetailsPanel.writeUntil(write(content), 5000, out);

        assertArrayEquals(Arrays.copyOf(content, 5000), Files.readAllBytes(out.toPath()));
    }

    @Test
    void writeUntilEndOfFileCopiesEverything() throws Exception {
        byte[] content = randomBytes(6000);
        File out = tempDir.resolve("out").toFile();

        PEDetailsPanel.writeUntil(write(content), 6000, out);

        assertArrayEquals(content, Files.readAllBytes(out.toPath()));
    }

    @Test
    void writeFromCopiesTail() throws Exception {
        byte[] content = randomBytes(6000);
        File out = tempDir.resolve("out").toFile();

        PEDetailsPanel.writeFrom(write(content), 5000, out);

        assertArrayEquals(Arrays.copyOfRange(content, 5000, 6000), Files.readAllBytes(out.toPath()));
    }

    @Test
    void removedOverlayIsGone() throws Exception {
        File sample = TestFiles.file(OVERLAY);
        long overlayOffset = TestPEData.load(OVERLAY).getOverlay().getOffset();
        File truncated = tempDir.resolve("truncated").toFile();

        PEDetailsPanel.writeUntil(sample, overlayOffset, truncated);

        assertEquals(overlayOffset, truncated.length());
        assertFalse(new Overlay(PELoader.loadPE(truncated)).exists());
    }

    @Test
    void dumpedOverlayIsTailOfFile() throws Exception {
        File sample = TestFiles.file(OVERLAY);
        long overlayOffset = TestPEData.load(OVERLAY).getOverlay().getOffset();
        File dumped = tempDir.resolve("dumped").toFile();

        PEDetailsPanel.writeFrom(sample, overlayOffset, dumped);

        byte[] content = Files.readAllBytes(sample.toPath());
        assertArrayEquals(Arrays.copyOfRange(content, (int) overlayOffset, content.length),
                Files.readAllBytes(dumped.toPath()));
    }

    private File write(byte[] content) throws Exception {
        File file = tempDir.resolve("in").toFile();
        Files.write(file.toPath(), content);
        return file;
    }

    private static byte[] randomBytes(int size) {
        byte[] bytes = new byte[size];
        new Random(42).nextBytes(bytes);
        return bytes;
    }
}
