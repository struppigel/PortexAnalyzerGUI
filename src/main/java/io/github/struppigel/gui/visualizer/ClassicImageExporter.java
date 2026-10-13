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
package io.github.struppigel.gui.visualizer;

import io.github.struppigel.tools.visualizer.ColorableItem;
import io.github.struppigel.tools.visualizer.ImageUtil;
import io.github.struppigel.tools.visualizer.Visualizer;
import io.github.struppigel.tools.visualizer.VisualizerBuilder;

import java.awt.*;
import java.awt.image.BufferedImage;
import java.io.File;
import java.io.IOException;

/**
 * Creates the classic picture of the whole file: byte plot, entropy, PE structure and legend, as earlier versions
 * of the PortexAnalyzer GUI showed it.
 */
final class ClassicImageExporter {

    static final int DEFAULT_HEIGHT = 1000;
    private static final int WIDTH = 180;
    private static final int PIXEL_SIZE = 4;

    private ClassicImageExporter() {
    }

    /**
     * Parses the file anew, so that this may run in any thread without sharing the PEData of the GUI.
     *
     * @param file the PE file
     * @param maxHeight the maximum height of the picture, at least {@value #PIXEL_SIZE}
     */
    static BufferedImage render(File file, int maxHeight) throws IOException {
        long length = Math.max(1, file.length());
        int height = Math.max(PIXEL_SIZE, maxHeight);
        long bytesPerPixel = 1;
        while (heightFor(length, bytesPerPixel) > height && bytesPerPixel < Integer.MAX_VALUE / 2) {
            bytesPerPixel *= 2;
        }
        // one visualizer for all pictures, it parses the file once
        Visualizer visualizer = new VisualizerBuilder()
                .setFileWidth(WIDTH)
                .setPixelSize(PIXEL_SIZE)
                .setBytesPerPixel((int) bytesPerPixel, length)
                .setColor(ColorableItem.ENTROPY, Color.cyan)
                .build();
        BufferedImage image = ImageUtil.appendImages(visualizer.createBytePlot(file), visualizer.createEntropyImage(file));
        image = ImageUtil.appendImages(image, visualizer.createImage(file));
        return ImageUtil.appendImages(image, visualizer.createLegendImage(true, true, true));
    }

    private static long heightFor(long length, long bytesPerPixel) {
        double pixels = length / (double) bytesPerPixel;
        double pixelsPerRow = WIDTH / (double) PIXEL_SIZE;
        return (long) Math.ceil(pixels / pixelsPerRow * PIXEL_SIZE);
    }
}
