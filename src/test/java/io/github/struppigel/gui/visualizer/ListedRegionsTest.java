package io.github.struppigel.gui.visualizer;

import io.github.struppigel.gui.FullPEData;
import io.github.struppigel.gui.utils.TestPEData;
import io.github.struppigel.gui.utils.WorkerKiller;
import io.github.struppigel.tools.layout.FileRegion;
import io.github.struppigel.tools.visualizer.render.LegendEntry;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

import javax.swing.*;
import java.util.ArrayList;
import java.util.List;

import static io.github.struppigel.testsupport.Swing.findAll;
import static io.github.struppigel.testsupport.Swing.onEdt;
import static io.github.struppigel.testsupport.TestFiles.NATIVE_X86;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ListedRegionsTest {

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

    @SuppressWarnings("unchecked")
    private static List<LegendEntry> entriesOf(LegendPanel panel) {
        List<LegendEntry> entries = new ArrayList<>();
        for (JList<?> list : (List<JList<?>>) (List<?>) findAll(panel, JList.class)) {
            for (int i = 0; i < list.getModel().getSize(); i++) {
                entries.add((LegendEntry) list.getModel().getElementAt(i));
            }
        }
        return entries;
    }

    private static LegendEntry firstWithStructures(LegendPanel panel) {
        for (LegendEntry entry : entriesOf(panel)) {
            if (!entry.getRegions().isEmpty()) {
                return entry;
            }
        }
        throw new AssertionError("no legend entry has structures");
    }

    @Test
    void clickingAnEntryListsItsStructuresAndAnotherClickGoesBack() throws Exception {
        FullPEData data = TestPEData.load(NATIVE_X86);
        onEdt(() -> {
            model = new VisualizationModel();
            LegendPanel legend = new LegendPanel(model);
            InfoPane pane = new InfoPane(model, () -> 1);
            model.loadSynchronously(data);
            long offset = data.getPeData().getCOFFFileHeader().getOffset();
            model.setHoverOffset(offset);
            pane.updateNow();
            int atPointer = pane.getRegionList().getModel().getSize();
            assertTrue(atPointer > 0);

            LegendEntry entry = firstWithStructures(legend);
            legend.entryClicked(entry);
            assertTrue(model.hasListedRegions());
            assertEquals(entry.getLabel(), model.getListedTitle());
            assertEquals(entry.getRegions().size(), model.getListedRegions().size());
            assertEquals(entry.getLabel() + " (" + entry.getRegions().size() + ")", pane.getRegionTitle());
            ListModel<FileRegion> listed = pane.getRegionList().getModel();
            assertEquals(entry.getRegions().size(), listed.getSize());
            for (int i = 1; i < listed.getSize(); i++) {
                assertTrue(listed.getElementAt(i - 1).getStart() <= listed.getElementAt(i).getStart(),
                        "sorted by offset");
            }

            // the pointer does not replace the listed structures
            model.setHoverOffset(0);
            pane.updateNow();
            assertEquals(entry.getRegions().size(), pane.getRegionList().getModel().getSize());

            legend.entryClicked(entry);
            assertFalse(model.hasListedRegions());
            assertEquals("Structures, innermost first", pane.getRegionTitle());
            model.setHoverOffset(offset);
            pane.updateNow();
            assertEquals(atPointer, pane.getRegionList().getModel().getSize());
        });
    }

    @Test
    void anotherEntryReplacesTheListing() throws Exception {
        FullPEData data = TestPEData.load(NATIVE_X86);
        onEdt(() -> {
            model = new VisualizationModel();
            LegendPanel legend = new LegendPanel(model);
            model.loadSynchronously(data);
            List<LegendEntry> withStructures = new ArrayList<>();
            for (LegendEntry entry : entriesOf(legend)) {
                if (!entry.getRegions().isEmpty()) {
                    withStructures.add(entry);
                }
            }
            assertTrue(withStructures.size() >= 2, "entries " + withStructures);
            legend.entryClicked(withStructures.get(0));
            legend.entryClicked(withStructures.get(1));
            assertEquals(withStructures.get(1).getLabel(), model.getListedTitle());
        });
    }

    @Test
    void loadingAnotherFileEndsTheListing() throws Exception {
        FullPEData data = TestPEData.load(NATIVE_X86);
        onEdt(() -> {
            model = new VisualizationModel();
            LegendPanel legend = new LegendPanel(model);
            InfoPane pane = new InfoPane(model, () -> 1);
            model.loadSynchronously(data);
            legend.entryClicked(firstWithStructures(legend));
            assertTrue(model.hasListedRegions());
            model.loadSynchronously(data);
            pane.updateNow();
            assertFalse(model.hasListedRegions());
            assertNotNull(pane.getRegionTitle());
            assertEquals("Structures, innermost first", pane.getRegionTitle());
        });
    }

    @Test
    void clickingAListedStructureSelectsItAndKeepsTheListing() throws Exception {
        FullPEData data = TestPEData.load(NATIVE_X86);
        onEdt(() -> {
            model = new VisualizationModel();
            LegendPanel legend = new LegendPanel(model);
            InfoPane pane = new InfoPane(model, () -> 1);
            model.loadSynchronously(data);
            LegendEntry entry = firstWithStructures(legend);
            legend.entryClicked(entry);
            FileRegion region = model.getListedRegions().get(0);
            model.showRegion(region, region.getStart());
            pane.updateNow();
            assertTrue(model.hasListedRegions());
            assertEquals(region, model.getSelectedRegion());
            assertEquals(entry.getRegions().size(), pane.getRegionList().getModel().getSize());
        });
    }
}
