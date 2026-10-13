package io.github.struppigel.settings;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.File;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Arrays;
import java.util.List;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import static io.github.struppigel.settings.PortexSettingsKey.*;
import static org.junit.jupiter.api.Assertions.*;

class PortexSettingsTest {

    @TempDir
    Path tempDir;

    @Test
    void defaultsWithoutSettingsFile() {
        PortexSettings settings = new PortexSettings(settingsFile());

        assertEquals("0", settings.get(DISABLE_UPDATE));
        assertEquals("PORTEX", settings.get(LOOK_AND_FEEL));
        assertEquals("1", settings.get(VALUES_AS_HEX));
        assertEquals("1", settings.get(CONTENT_PREVIEW));
        assertEquals("0", settings.get(HEX_BYTES_PER_LINE));
    }

    @Test
    void writtenSettingsAreReadBack() throws Exception {
        PortexSettings settings = new PortexSettings(settingsFile());
        settings.put(HEX_BYTES_PER_LINE, "16");
        settings.put(VALUES_AS_HEX, "0");
        settings.put(LOOK_AND_FEEL, LookAndFeelSetting.SYSTEM.toString());

        settings.writeSettingsNow();

        assertEquals(settings, new PortexSettings(settingsFile()));
    }

    @Test
    void unknownKeysAreIgnored() throws Exception {
        Files.write(settingsFile().toPath(), Arrays.asList("NOT_A_KEY:::1", "DISABLE_UPDATE:::1"),
                StandardCharsets.UTF_8);

        PortexSettings settings = new PortexSettings(settingsFile());

        assertEquals("1", settings.get(DISABLE_UPDATE));
        assertTrue(settings.valueEquals(DISABLE_UPDATE, "1"));
    }

    @Test
    void settingsOfTheRemovedYaraScanAreIgnored() throws Exception {
        Files.write(settingsFile().toPath(), Arrays.asList("YARA_PATH:::C:\\tools\\yara64.exe",
                "YARA_SIGNATURE_PATH:::C:\\rules\\all.yar", "DISABLE_YARA_WARNINGS:::1", "DISABLE_UPDATE:::1"),
                StandardCharsets.UTF_8);

        PortexSettings settings = new PortexSettings(settingsFile());

        assertEquals("1", settings.get(DISABLE_UPDATE));
        assertEquals(5, settings.size(), settings.toString());
    }

    /**
     * Keys are stored by name, so renaming one silently drops the users' setting.
     */
    @Test
    void storedNamesAreUnchanged() {
        assertEquals(Arrays.asList("DISABLE_UPDATE", "LOOK_AND_FEEL", "VALUES_AS_HEX", "CONTENT_PREVIEW",
                "HEX_BYTES_PER_LINE"), names(PortexSettingsKey.values()));
        assertEquals(Arrays.asList("PORTEX", "SYSTEM"), names(LookAndFeelSetting.values()));
    }

    private File settingsFile() {
        return tempDir.resolve("settings.ini").toFile();
    }

    private static List<String> names(Enum<?>[] values) {
        return Stream.of(values).map(Enum::name).collect(Collectors.toList());
    }
}
