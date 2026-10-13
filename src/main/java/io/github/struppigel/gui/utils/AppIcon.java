/**
 * *****************************************************************************
 * Copyright 2026 Karsten Hahn
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *   <a href="http://www.apache.org/licenses/LICENSE-2.0">http://www.apache.org/licenses/LICENSE-2.0</a>
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 * ****************************************************************************
 */
package io.github.struppigel.gui.utils;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import javax.imageio.ImageIO;
import javax.swing.*;
import java.awt.*;
import java.awt.image.BufferedImage;
import java.io.IOException;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.List;

/**
 * The icon of the application: the hedgehog of the logo with a magnifying glass, white with a thin dark outline that
 * fills the whole icon, in the sizes that windows, task bars and dialogs use. Without the outline it is available in
 * any color for the pages of the GUI.
 */
public final class AppIcon {

    private static final Logger LOGGER = LogManager.getLogger();
    private static final int[] SIZES = {16, 20, 24, 32, 48, 64, 128, 256};
    private static final int[] LOGO_SIZES = {64, 128, 192, 256};

    private AppIcon() {
    }

    /**
     * @return the icon in all sizes, to set as the icons of a window; empty if the files are missing
     */
    public static List<Image> images() {
        List<Image> images = new ArrayList<>();
        for (int size : SIZES) {
            Image image = image(size);
            if (image != null) {
                images.add(image);
            }
        }
        return images;
    }

    /**
     * @param size one of 16, 20, 24, 32, 48, 64, 128, 256 pixels
     * @return the icon in the size, null if there is none
     */
    public static Image image(int size) {
        try (InputStream in = AppIcon.class.getResourceAsStream("/app-icon-" + size + ".png")) {
            return in == null ? null : ImageIO.read(in);
        } catch (IOException e) {
            LOGGER.warn("Could not read the app icon of " + size + " pixels: " + e.getMessage());
            return null;
        }
    }

    /**
     * The hedgehog with the magnifying glass without a background, in one color, so that it sits flat on the page
     * like text.
     *
     * @param width the width and height of the logo in pixels, 64, 128, 192 or 256 for the sharpest picture
     * @param color the color of the logo, e.g., the text color of the theme
     * @return the logo, null if the file is missing
     */
    public static Icon logo(int width, Color color) {
        int source = LOGO_SIZES[LOGO_SIZES.length - 1];
        for (int size : LOGO_SIZES) {
            if (width <= size) {
                source = size;
                break;
            }
        }
        try (InputStream in = AppIcon.class.getResourceAsStream("/app-logo-" + source + ".png")) {
            if (in == null) {
                return null;
            }
            BufferedImage mask = ImageIO.read(in);
            BufferedImage tinted = new BufferedImage(mask.getWidth(), mask.getHeight(), BufferedImage.TYPE_INT_ARGB);
            for (int y = 0; y < mask.getHeight(); y++) {
                for (int x = 0; x < mask.getWidth(); x++) {
                    int alpha = mask.getRGB(x, y) >>> 24;
                    tinted.setRGB(x, y, (alpha << 24) | (color.getRGB() & 0xFFFFFF));
                }
            }
            if (width == source) {
                return new ImageIcon(tinted);
            }
            return new ImageIcon(tinted.getScaledInstance(width, width, Image.SCALE_SMOOTH));
        } catch (IOException e) {
            LOGGER.warn("Could not read the logo: " + e.getMessage());
            return null;
        }
    }

    /**
     * Sets the icon of the window.
     */
    public static void applyTo(Window window) {
        List<Image> images = images();
        if (!images.isEmpty()) {
            window.setIconImages(images);
        }
    }
}
