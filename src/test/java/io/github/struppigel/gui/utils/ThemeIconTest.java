package io.github.struppigel.gui.utils;

import io.github.struppigel.settings.LookAndFeelSetting;
import io.github.struppigel.settings.PortexSettings;
import io.github.struppigel.settings.PortexSettingsKey;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;

import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.io.InputStream;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.*;

class ThemeIconTest {

    @TempDir
    Path tempDir;

    /**
     * The icons for the dark theme are light and the ones for the bright theme are dark, so that they are visible.
     */
    @ParameterizedTest
    @EnumSource(ThemeIcon.class)
    void iconsContrastWithTheirTheme(ThemeIcon icon) throws Exception {
        assertTrue(meanLuminance(icon.path(true)) > 0.8, icon + " is light for the dark theme");
        assertTrue(meanLuminance(icon.path(false)) < 0.2, icon + " is dark for the bright theme");
        assertNotNull(icon.load(true).getImage());
        assertNotNull(icon.load(false).getImage());
    }

    @Test
    void theDarkThemeIsThePortexTheme() {
        PortexSettings settings = new PortexSettings(tempDir.resolve("settings.ini").toFile());
        settings.put(PortexSettingsKey.LOOK_AND_FEEL, LookAndFeelSetting.PORTEX.toString());
        assertTrue(ThemeIcon.isDarkTheme(settings));
        settings.put(PortexSettingsKey.LOOK_AND_FEEL, LookAndFeelSetting.SYSTEM.toString());
        assertFalse(ThemeIcon.isDarkTheme(settings));
    }

    /** mean luminance of the visible pixels, from 0 to 1 */
    private static double meanLuminance(String resource) throws Exception {
        try (InputStream in = ThemeIcon.class.getResourceAsStream(resource)) {
            assertNotNull(in, "missing icon " + resource);
            BufferedImage image = ImageIO.read(in);
            double sum = 0;
            int count = 0;
            for (int y = 0; y < image.getHeight(); y++) {
                for (int x = 0; x < image.getWidth(); x++) {
                    int argb = image.getRGB(x, y);
                    if ((argb >>> 24) > 128) {
                        sum += (0.299 * ((argb >> 16) & 0xff) + 0.587 * ((argb >> 8) & 0xff) + 0.114 * (argb & 0xff)) / 255;
                        count++;
                    }
                }
            }
            assertTrue(count > 0, "no visible pixels in " + resource);
            return sum / count;
        }
    }
}
