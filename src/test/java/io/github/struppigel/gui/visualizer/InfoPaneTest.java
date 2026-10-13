package io.github.struppigel.gui.visualizer;

import com.formdev.flatlaf.FlatLaf;
import io.github.struppigel.gui.FullPEData;
import io.github.struppigel.gui.Starter;
import io.github.struppigel.gui.utils.TestPEData;
import io.github.struppigel.gui.utils.WorkerKiller;
import io.github.struppigel.testsupport.Swing;
import io.github.struppigel.tools.layout.FileRegion;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

import javax.swing.*;
import java.awt.*;
import java.awt.image.BufferedImage;

import static io.github.struppigel.testsupport.Swing.onEdt;
import static io.github.struppigel.testsupport.TestFiles.NATIVE_X86;
import static org.junit.jupiter.api.Assertions.assertTrue;

class InfoPaneTest {

    private VisualizationModel model;

    @AfterEach
    void cleanUp() throws Exception {
        WorkerKiller.getInstance().cancelAndDeleteWorkers();
        onEdt(() -> {
            if (model != null) {
                model.dispose();
            }
        });
    }

    @Test
    void aSelectedStructureIsReadableInThePortexTheme() throws Exception {
        FullPEData data = TestPEData.load(NATIVE_X86);
        LookAndFeel previous = UIManager.getLookAndFeel();
        try {
            onEdt(() -> {
                Starter.setDarkLookAndFeel();
                model = new VisualizationModel();
                InfoPane pane = new InfoPane(model, () -> 1);
                model.loadSynchronously(data);
                model.setHoverOffset(data.getPeData().getCOFFFileHeader().getOffset());
                pane.updateNow();
                JList<FileRegion> list = pane.getRegionList();
                list.setSelectedIndex(0);
                Swing.layout(pane, 300, 400);

                BufferedImage image = new BufferedImage(list.getWidth(), list.getHeight(), BufferedImage.TYPE_INT_RGB);
                Graphics g = image.getGraphics();
                list.paint(g);
                g.dispose();

                Rectangle cell = list.getCellBounds(0, 0);
                int background = list.getSelectionBackground().getRGB() & 0xffffff;
                int backgroundPixels = 0;
                int textPixels = 0;
                // right of the color swatch, only the background and the texts
                for (int y = cell.y; y < cell.y + cell.height; y++) {
                    for (int x = cell.x + 30; x < cell.x + cell.width; x++) {
                        int rgb = image.getRGB(x, y) & 0xffffff;
                        if (rgb == background) {
                            backgroundPixels++;
                        } else if (Math.abs(luminance(rgb) - luminance(background)) > 0.3) {
                            textPixels++;
                        }
                    }
                }
                assertTrue(backgroundPixels > textPixels, "the selection color is the background: "
                        + backgroundPixels + " of " + cell.width * cell.height + " pixels");
                assertTrue(textPixels > 20, "the text stands out from the background: " + textPixels + " pixels");
            });
        } finally {
            onEdt(() -> {
                FlatLaf.setGlobalExtraDefaults(null);
                UIManager.setLookAndFeel(previous);
            });
        }
    }

    private static double luminance(int rgb) {
        return (0.299 * (rgb >> 16 & 0xff) + 0.587 * (rgb >> 8 & 0xff) + 0.114 * (rgb & 0xff)) / 255;
    }
}
