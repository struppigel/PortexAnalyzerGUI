package io.github.struppigel.gui.visualizer;

import io.github.struppigel.parser.PhysicalLocation;
import io.github.struppigel.tools.visualizer.engine.CachedByteSource;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.File;
import java.nio.file.Files;
import java.util.Arrays;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class RegionSaverTest {

    @TempDir
    File folder;

    @Test
    void rangesAreClippedToTheFile() {
        List<long[]> ranges = RegionSaver.rangesOf(Arrays.asList(
                new PhysicalLocation(10, 20),
                new PhysicalLocation(90, 50),
                new PhysicalLocation(100, 5),
                new PhysicalLocation(5, 0)), 100);
        assertEquals(2, ranges.size());
        assertArrayEquals(new long[]{10, 30}, ranges.get(0));
        assertArrayEquals(new long[]{90, 100}, ranges.get(1));
    }

    @Test
    void writesTheRangesOneAfterTheOther() throws Exception {
        byte[] content = new byte[300_000];
        for (int i = 0; i < content.length; i++) {
            content[i] = (byte) (i * 7);
        }
        File input = new File(folder, "input.bin");
        Files.write(input.toPath(), content);
        File out = new File(folder, "out.bin");
        try (CachedByteSource source = new CachedByteSource(input)) {
            long written = RegionSaver.write(source, Arrays.asList(new long[]{3, 150_000}, new long[]{299_990, 300_000}), out);
            assertEquals(150_007, written);
        }
        byte[] expected = new byte[150_007];
        System.arraycopy(content, 3, expected, 0, 149_997);
        System.arraycopy(content, 299_990, expected, 149_997, 10);
        assertArrayEquals(expected, Files.readAllBytes(out.toPath()));
    }

    @Test
    void suggestedFileNameIsSafeAndShort() {
        String name = RegionSaver.suggestedFileName(new File("sample.exe"), "Padding: <bad/name> " + "x".repeat(100), 0x2f400);
        assertTrue(name.endsWith("_0x2f400.bin"), name);
        assertTrue(new File(name).getName().startsWith("sample.exe.Padding_bad_name_x"), name);
        assertTrue(new File(name).getName().length() < 90, name);
    }
}
