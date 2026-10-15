package io.github.struppigel.gui.visualizer;

import io.github.struppigel.gui.FullPEData;
import io.github.struppigel.gui.utils.HexFont;
import io.github.struppigel.gui.utils.TestPEData;
import io.github.struppigel.parser.sections.idata.ImportDLL;
import io.github.struppigel.tools.layout.FileRegion;
import io.github.struppigel.tools.layout.RegionType;
import io.github.struppigel.tools.visualizer.engine.ByteSourceClosedException;
import io.github.struppigel.tools.visualizer.engine.FileVisualization;
import io.github.struppigel.tools.visualizer.render.Track;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;

import static io.github.struppigel.testsupport.TestFiles.*;
import static org.junit.jupiter.api.Assertions.*;

class VisualizationModelTest {

    @Test
    void loadCreatesVisualizationWithStatistics() throws Exception {
        FullPEData data = TestPEData.load(OVERLAY);
        VisualizationModel model = new VisualizationModel();
        model.loadSynchronously(data);

        FileVisualization vis = model.getVisualization();
        assertNotNull(vis);
        assertEquals(data.getFile().length(), vis.getFileLength());
        assertTrue(vis.getStatistics().isComplete());
        assertSame(data.getStructureMap(), vis.getStructureMap());
        model.dispose();
    }

    @Test
    void disposeAndReloadReleaseTheFile() throws Exception {
        VisualizationModel model = new VisualizationModel();
        model.loadSynchronously(TestPEData.load(OVERLAY));
        FileVisualization first = model.getVisualization();
        int generation = model.getGeneration();

        model.loadSynchronously(TestPEData.load(RESOURCES));
        assertNotSame(first, model.getVisualization());
        assertNotEquals(generation, model.getGeneration());
        assertThrows(ByteSourceClosedException.class, () -> first.getSource().byteAt(0));

        FileVisualization second = model.getVisualization();
        model.dispose();
        assertNull(model.getVisualization());
        assertThrows(ByteSourceClosedException.class, () -> second.getSource().byteAt(0));
    }

    @Test
    void loadResetsTheSelection() throws Exception {
        VisualizationModel model = new VisualizationModel();
        model.loadSynchronously(TestPEData.load(OVERLAY));
        model.select(0x10);
        assertNotNull(model.getSelectedRegion());

        model.loadSynchronously(TestPEData.load(RESOURCES));
        assertNull(model.getSelectedRegion());
        assertEquals(-1, model.getSelectedOffset());
        model.dispose();
    }

    @Test
    void revealSelectsTheStructureInsteadOfItsFirstField() throws Exception {
        FullPEData data = TestPEData.load(NATIVE_X86);
        VisualizationModel model = new VisualizationModel();
        model.loadSynchronously(data);
        List<Long> requested = new ArrayList<>();
        model.addListener(new VisualizationModel.Listener() {
            @Override
            public void viewRequested(long offset, boolean onlyIfInvisible) {
                assertTrue(onlyIfInvisible);
                requested.add(offset);
            }
        });

        long coff = data.getPeData().getCOFFFileHeader().getOffset();
        model.reveal(coff);
        assertEquals(RegionType.COFF_FILE_HEADER, model.getSelectedRegion().getType());
        assertEquals(coff, model.getSelectedOffset());

        model.reveal(0);
        assertEquals(RegionType.MSDOS_HEADER, model.getSelectedRegion().getType());

        model.reveal(coff - 4);
        assertEquals(RegionType.PE_SIGNATURE, model.getSelectedRegion().getType());
        assertEquals(3, requested.size());
        model.dispose();
    }

    @Test
    void revealSelectsTheImportFunctionAtItsLookupTableEntry() throws Exception {
        FullPEData data = TestPEData.load(NATIVE_X86);
        VisualizationModel model = new VisualizationModel();
        model.loadSynchronously(data);
        ImportDLL dll = data.getPeData().loadImports().stream().max(
                java.util.Comparator.comparingInt((ImportDLL d) -> d.getAllImports().size())).get();
        assertTrue(dll.getAllImports().size() > 1);

        // the first entry starts like the lookup table, it must not select the whole table
        for (int index : new int[]{0, dll.getAllImports().size() - 1}) {
            long offset = dll.getAllImports().get(index).getLocations().get(0).from();
            model.reveal(offset);
            FileRegion region = model.getSelectedRegion();
            assertNotEquals(RegionType.IMPORT_LOOKUP_TABLE, region.getType());
            assertTrue(region.getLocations().size() <= 3, region.toString());
            assertTrue(region.getLocations().stream().anyMatch(l -> l.from() == offset), region.toString());
        }
        model.dispose();
    }

    @Test
    void revealFindsFieldsThatDoNotStartTheStructure() throws Exception {
        FullPEData data = TestPEData.load(NATIVE_X86);
        VisualizationModel model = new VisualizationModel();
        model.loadSynchronously(data);

        // e_lfanew is the last field of the MS-DOS header
        model.reveal(0x3c);
        FileRegion region = model.getSelectedRegion();
        assertEquals(RegionType.HEADER_FIELD, region.getType());
        assertEquals(0x3c, region.getStart());
        model.dispose();
    }

    @Test
    void revealCanBeSuppressed() throws Exception {
        VisualizationModel model = new VisualizationModel();
        model.loadSynchronously(TestPEData.load(NATIVE_X86));
        model.select(0x10);
        FileRegion selected = model.getSelectedRegion();

        model.withoutReveal(() -> model.reveal(0x200));
        assertSame(selected, model.getSelectedRegion());
        model.dispose();
    }

    @Test
    void hexBackgroundOnlyChangesTheHexStyle() {
        VisualizationModel model = new VisualizationModel();
        List<String> events = new ArrayList<>();
        model.addListener(new VisualizationModel.Listener() {
            @Override
            public void styleChanged() {
                events.add("style");
            }
        });
        assertEquals(Track.STRUCTURE, model.getHexBackground());

        model.setHexBackground(Track.BYTEPLOT);
        model.setHexBackground(Track.BYTEPLOT);
        assertEquals(1, events.size(), "only real changes are events");
        assertEquals(Track.BYTEPLOT, model.getHexStyle().getHexBackground());
        assertEquals(Track.STRUCTURE, model.getStyle().getHexBackground(), "the other tracks keep their tiles");
        assertEquals(HexFont.family(), model.getHexStyle().getHexFontFamily(), "the font of the hex preview");
        assertThrows(IllegalArgumentException.class, () -> model.setHexBackground(Track.HEX));
    }

    @Test
    void markerToggleChangesTheStyle() {
        VisualizationModel model = new VisualizationModel();
        assertTrue(model.getStyle().paints(RegionType.SIGNATURE_MATCH));

        model.setMarkersVisible(false);
        assertFalse(model.getStyle().paints(RegionType.SIGNATURE_MATCH));
        assertFalse(model.getStyle().paints(RegionType.ANOMALY));
        assertTrue(model.getStyle().paints(RegionType.PE_SIGNATURE));
    }
}
