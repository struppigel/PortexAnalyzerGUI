package io.github.struppigel.gui.visualizer;

import io.github.struppigel.gui.utils.TestPEData;
import io.github.struppigel.testsupport.Swing;
import io.github.struppigel.tools.layout.FileRegion;
import io.github.struppigel.tools.visualizer.render.Track;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

import java.awt.*;
import java.awt.event.MouseEvent;
import java.util.ArrayList;
import java.util.List;

import static io.github.struppigel.testsupport.Swing.onEdt;
import static io.github.struppigel.testsupport.TestFiles.DOTNET_PINVOKE;
import static org.junit.jupiter.api.Assertions.*;

class CompactVisualizerPanelTest {

    private VisualizationModel model;

    @AfterEach
    void dispose() throws Exception {
        onEdt(() -> model.dispose());
    }

    @Test
    void singleClickShowsTheStructureAndDoubleClickOpensTheVisualization() throws Exception {
        List<FileRegion> shownInTree = new ArrayList<>();
        List<Long> treeOffsets = new ArrayList<>();
        List<Long> opened = new ArrayList<>();
        onEdt(() -> {
            model = new VisualizationModel();
            CompactVisualizerPanel panel = new CompactVisualizerPanel(model);
            panel.setRegionNavigator((region, offset) -> {
                shownInTree.add(region);
                treeOffsets.add(offset);
            });
            panel.setOpenVisualizationAction(opened::add);
            Swing.layout(panel, panel.getPreferredSize().width, 600);
            model.loadSynchronously(TestPEData.load(DOTNET_PINVOKE));

            OverviewStrip overview = panel.getOverview();
            Point p = new Point(2, 300);
            long offset = overview.offsetAt(p);
            assertTrue(offset > 0);

            overview.dispatchEvent(click(overview, p, 1));
            assertEquals(1, shownInTree.size());
            assertEquals(offset, (long) treeOffsets.get(0));
            assertEquals(offset, model.getSelectedOffset());
            assertTrue(opened.isEmpty());

            overview.dispatchEvent(click(overview, p, 2));
            assertEquals(1, opened.size());
            assertEquals(offset, (long) opened.get(0));
            assertEquals(1, shownInTree.size(), "the double click does not show the tree node again");
            assertEquals(Track.BYTEPLOT, model.getHexBackground(), "the first column is the byte plot");

            Point entropy = new Point(overview.getPreferredSize().width / 2, 300);
            assertEquals(Track.ENTROPY, overview.trackAt(entropy));
            overview.dispatchEvent(click(overview, entropy, 2));
            assertEquals(Track.ENTROPY, model.getHexBackground());
        });
    }

    private static MouseEvent click(Component c, Point p, int count) {
        return new MouseEvent(c, MouseEvent.MOUSE_CLICKED, 0, 0, p.x, p.y, count, false, MouseEvent.BUTTON1);
    }
}
