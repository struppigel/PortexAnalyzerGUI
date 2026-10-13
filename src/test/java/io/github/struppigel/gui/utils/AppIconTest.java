package io.github.struppigel.gui.utils;

import org.junit.jupiter.api.Test;

import javax.swing.*;
import java.awt.*;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class AppIconTest {

    @Test
    void theIconComesInAllSizes() {
        List<Image> images = AppIcon.images();

        assertEquals(8, images.size());
        for (Image image : images) {
            assertEquals(image.getWidth(null), image.getHeight(null));
        }
        assertEquals(16, images.get(0).getWidth(null));
        assertEquals(256, images.get(images.size() - 1).getWidth(null));
    }

    @Test
    void theLogoHasTheWidthAndTheColor() {
        Icon exact = AppIcon.logo(128, Color.green);
        Icon scaled = AppIcon.logo(100, Color.green);

        assertNotNull(exact);
        assertEquals(128, exact.getIconWidth());
        assertEquals(128, exact.getIconHeight());
        assertEquals(100, scaled.getIconWidth());
        assertNull(AppIcon.image(17), "only the sizes that exist");
    }
}
