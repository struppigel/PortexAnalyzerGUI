package io.github.struppigel.gui.visualizer;

import io.github.struppigel.gui.FullPEData;
import io.github.struppigel.gui.utils.TestPEData;
import io.github.struppigel.gui.utils.WorkerKiller;
import io.github.struppigel.gui.visualizer.ZoomLadder.ZoomLevel;
import io.github.struppigel.testsupport.Swing;
import io.github.struppigel.tools.layout.FileRegion;
import io.github.struppigel.tools.layout.RegionType;
import io.github.struppigel.tools.visualizer.render.Track;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import javax.swing.*;
import java.awt.*;
import java.awt.image.BufferedImage;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.stream.Collectors;

import static io.github.struppigel.testsupport.Swing.onEdt;
import static io.github.struppigel.testsupport.Swing.onEdtGet;
import static io.github.struppigel.testsupport.TestFiles.*;
import static org.junit.jupiter.api.Assertions.*;

class InteractiveVisualizerPanelTest {

    private final List<Throwable> edtErrors = Collections.synchronizedList(new ArrayList<>());
    private Thread.UncaughtExceptionHandler previousHandler;
    private VisualizationModel model;
    private InteractiveVisualizerPanel panel;
    private DetailCanvas canvas;
    private FullPEData data;

    @BeforeEach
    void collectEdtErrors() {
        previousHandler = Thread.getDefaultUncaughtExceptionHandler();
        Thread.setDefaultUncaughtExceptionHandler((thread, e) -> edtErrors.add(e));
    }

    @AfterEach
    void cleanUp() throws Exception {
        WorkerKiller.getInstance().cancelAndDeleteWorkers();
        onEdt(() -> {
            if (model != null) {
                model.dispose();
            }
        });
        onEdt(() -> {}); // let pending events run
        Thread.setDefaultUncaughtExceptionHandler(previousHandler);
        assertTrue(edtErrors.isEmpty(), "exceptions in the event dispatch thread: " + edtErrors);
    }

    private void load(String sample) throws Exception {
        data = TestPEData.load(sample);
        onEdt(() -> {
            model = new VisualizationModel();
            // renders tiles in the calling thread
            panel = new InteractiveVisualizerPanel(model, Runnable::run);
            Swing.layout(panel, 1000, 700);
            model.loadSynchronously(data);
            canvas = panel.getCanvas();
            assertTrue(canvas.relayout(), "the canvas has a size and a file");
        });
    }

    private long peSignature() {
        return data.getPeData().getCOFFFileHeader().getOffset() - 4;
    }

    /** zooms in until the cells have one byte, but no hex */
    private void zoomToBytes() {
        while (canvas.level().bytesPerCell > 1) {
            canvas.zoom(1, null);
        }
    }

    @Test
    void wholeFileFitsAtTheFirstLevel() throws Exception {
        load(DOTNET_PINVOKE);
        onEdt(() -> {
            assertEquals(0, canvas.getLevelIndex());
            assertEquals(0, canvas.getFirstRow());
            assertEquals(data.getFile().length(), model.getVisibleEnd());
            assertTrue(canvas.level().bytesPerCell > 1);
        });
    }

    @Test
    void offsetAtPixelOfIsTheOffset() throws Exception {
        load(NATIVE_X86);
        onEdt(() -> {
            zoomToBytes();
            long peSignature = peSignature();
            canvas.scrollToOffset(peSignature, false);
            for (Track track : new Track[]{Track.BYTEPLOT, Track.ENTROPY, Track.STRUCTURE}) {
                Point p = canvas.pixelOf(peSignature, track);
                Point center = new Point(p.x + canvas.level().cellWidth / 2, p.y + canvas.level().cellHeight / 2);
                assertEquals(peSignature, canvas.offsetAt(center), track.toString());
            }
            assertEquals(-1, canvas.offsetAt(new Point(2, 2)), "the ruler has no offset");
        });
    }

    @Test
    void hoverDescribesTheStructures() throws Exception {
        load(NATIVE_X86);
        onEdt(() -> {
            model.setHoverOffset(peSignature());
            panel.getInfoPane().updateNow();
            String text = panel.getInfoPane().getInfoText();
            assertTrue(text.contains("PE Signature"), text);
            assertTrue(text.contains("Offset   0x" + Long.toHexString(peSignature())), text);
            assertTrue(text.contains("Byte     50"), "the PE signature starts with 'P': " + text);
        });
    }

    @Test
    void zoomKeepsTheAnchorRow() throws Exception {
        load(DOTNET_PINVOKE);
        onEdt(() -> {
            zoomToBytes();
            canvas.scrollToOffset(data.getFile().length() / 2, false);
            int x = canvas.trackLayouts().get(0).x + 1;
            Point anchor = new Point(x, canvas.getHeight() / 2);
            long offset = canvas.offsetAt(anchor);
            assertTrue(offset >= 0);

            canvas.zoom(1, anchor);
            assertEquals(anchor.y / canvas.level().cellHeight, canvas.mapping().rowOf(offset) - canvas.getFirstRow());

            canvas.zoom(-1, anchor);
            assertEquals(anchor.y / canvas.level().cellHeight, canvas.mapping().rowOf(offset) - canvas.getFirstRow());
        });
    }

    @Test
    void peSignatureHasItsColor() throws Exception {
        load(NATIVE_X86);
        onEdt(() -> {
            model.setMarkersVisible(false); // anomalies could be drawn on top
            // the largest cells before hex, the marker is drawn inset
            canvas.zoom(canvas.getLadder().size() - 2 - canvas.getLevelIndex(), null);
            assertFalse(canvas.level().hex);
            long peSignature = peSignature();
            canvas.scrollToOffset(peSignature, false);
            FileRegion region = model.getVisualization().getStructureMap().getRegions(RegionType.PE_SIGNATURE).get(0);
            int expected = model.getVisualization().getColorScheme().rgbOf(region);

            BufferedImage image = canvas.renderToImage();
            Point p = canvas.pixelOf(peSignature, Track.STRUCTURE);
            int actual = image.getRGB(p.x + canvas.level().cellWidth / 2, p.y + canvas.level().cellHeight / 2);
            assertEquals(Integer.toHexString(expected & 0xffffff), Integer.toHexString(actual & 0xffffff));
        });
    }

    @Test
    void deepestLevelShowsHexAndAscii() throws Exception {
        load(NATIVE_X86);
        onEdt(() -> {
            canvas.zoom(100, null);
            ZoomLevel level = canvas.level();
            assertTrue(level.hex);
            List<Track> tracks = canvas.trackLayouts().stream().map(t -> t.track).collect(Collectors.toList());
            assertTrue(tracks.contains(Track.HEX), tracks.toString());
            assertTrue(tracks.contains(Track.ASCII), tracks.toString());
            assertFalse(tracks.contains(Track.STRUCTURE));
            DetailCanvas.TrackLayout last = canvas.trackLayouts().get(canvas.trackLayouts().size() - 1);
            assertTrue(last.x + last.width <= canvas.getWidth(), "the tracks fit into the canvas");
            assertNotNull(canvas.renderToImage());
        });
    }

    @Test
    void zoomingInOverATrackPicksItsColorsForTheHexView() throws Exception {
        load(DOTNET_PINVOKE);
        onEdt(() -> {
            assertEquals(Track.STRUCTURE, model.getHexBackground());
            canvas.dispatchEvent(ctrlWheel(pointIn(Track.BYTEPLOT), -1));
            assertEquals(Track.BYTEPLOT, model.getHexBackground());
            assertEquals(1, canvas.getLevelIndex());
            assertEquals(1, panel.getHexColorChooser().getSelectedIndex());

            canvas.dispatchEvent(ctrlWheel(pointIn(Track.ENTROPY), -1));
            assertEquals(Track.ENTROPY, model.getHexBackground());
            canvas.dispatchEvent(ctrlWheel(pointIn(Track.STRUCTURE), 1));
            assertEquals(Track.ENTROPY, model.getHexBackground(), "zooming out keeps the colors");
            assertEquals(2, panel.getHexColorChooser().getSelectedIndex());

            panel.getHexColorChooser().setSelectedIndex(0);
            assertEquals(Track.STRUCTURE, model.getHexBackground());
        });
    }

    @Test
    void hexCellsHaveTheColorsOfThePickedTrack() throws Exception {
        load(NATIVE_X86);
        onEdt(() -> {
            canvas.zoom(100, null);
            canvas.setFirstRow(0);
            assertTrue(canvas.level().hex);
            long zeroByte = 3; // MZ 90 00
            assertEquals(0, model.getVisualization().getSource().byteAt(zeroByte));
            Point p = canvas.pixelOf(zeroByte, Track.HEX);

            int structure = canvas.renderToImage().getRGB(p.x + 1, p.y + 1);
            model.setHexBackground(Track.BYTEPLOT);
            int byteplot = canvas.renderToImage().getRGB(p.x + 1, p.y + 1);

            int expected = model.getVisualization().getColorScheme().bytePlotRgb(0);
            assertEquals(Integer.toHexString(expected & 0xffffff), Integer.toHexString(byteplot & 0xffffff));
            assertNotEquals(structure & 0xffffff, byteplot & 0xffffff);
        });
    }

    @Test
    void theCanvasDrawsTheHexDigitsOnTilesWithoutText() throws Exception {
        load(NATIVE_X86);
        onEdt(() -> {
            assertFalse(canvas.styleOf(Track.HEX).isHexText(), "PortEx draws only the cells");
            assertFalse(canvas.styleOf(Track.ASCII).isHexText());
            assertTrue(model.getHexStyle().isHexText(), "exports keep the digits of PortEx");

            canvas.zoom(100, null);
            canvas.setFirstRow(0);
            BufferedImage image = canvas.renderToImage();
            for (Track track : new Track[]{Track.HEX, Track.ASCII}) {
                Point p = canvas.pixelOf(0, track); // 4D, 'M'
                int cellWidth = canvas.trackLayouts().stream().filter(t -> t.track == track).findFirst().get().cellWidth;
                int background = image.getRGB(p.x, p.y);
                int textPixels = 0;
                for (int y = p.y; y < p.y + canvas.level().cellHeight - 1; y++) {
                    for (int x = p.x; x < p.x + cellWidth - 1; x++) {
                        if (image.getRGB(x, y) != background) {
                            textPixels++;
                        }
                    }
                }
                assertTrue(textPixels > 5, track + " has text: " + textPixels);
            }
        });
    }

    private Point centerOf(long offset, Track track) {
        Point p = canvas.pixelOf(offset, track);
        return new Point(p.x + canvas.level().cellWidth / 2, p.y + canvas.level().cellHeight / 2);
    }

    private void drag(Point from, Point to) {
        long when = System.currentTimeMillis();
        int button = java.awt.event.InputEvent.BUTTON1_DOWN_MASK;
        canvas.dispatchEvent(new java.awt.event.MouseEvent(canvas, java.awt.event.MouseEvent.MOUSE_PRESSED, when,
                button, from.x, from.y, 1, false, java.awt.event.MouseEvent.BUTTON1));
        canvas.dispatchEvent(new java.awt.event.MouseEvent(canvas, java.awt.event.MouseEvent.MOUSE_DRAGGED, when,
                button, to.x, to.y, 0, false, java.awt.event.MouseEvent.BUTTON1));
        canvas.dispatchEvent(new java.awt.event.MouseEvent(canvas, java.awt.event.MouseEvent.MOUSE_RELEASED, when,
                0, to.x, to.y, 1, false, java.awt.event.MouseEvent.BUTTON1));
    }

    @Test
    void draggingInTheHexViewMarksBytesAndCopiesThemWithoutSpaces() throws Exception {
        load(NATIVE_X86);
        onEdt(() -> {
            canvas.zoom(100, null);
            canvas.setFirstRow(0);
            assertTrue(canvas.level().hex);

            drag(centerOf(0, Track.HEX), centerOf(1, Track.HEX));

            assertTrue(model.hasRangeSelection());
            assertEquals(0, model.getRangeStart());
            assertEquals(2, model.getRangeEnd());
            assertEquals(2, canvas.markedByteCount());
            assertEquals("4d5a", canvas.markedBytes(false), "'MZ' without spaces");
            assertEquals("MZ", canvas.markedBytes(true));
        });
    }

    @Test
    void draggingInTheAsciiAreaMarksTheString() throws Exception {
        load(NATIVE_X86);
        onEdt(() -> {
            canvas.zoom(100, null);
            canvas.setFirstRow(0);

            drag(centerOf(1, Track.ASCII), centerOf(0, Track.ASCII));

            assertEquals(0, model.getRangeStart(), "backwards works too");
            assertEquals(2, model.getRangeEnd());
            assertEquals("MZ", canvas.markedBytes(true));
        });
    }

    @Test
    void nothingMarkedCopiesNothing() throws Exception {
        load(NATIVE_X86);
        onEdt(() -> {
            assertEquals(0, canvas.markedByteCount());
            assertNull(canvas.markedBytes(false));
        });
    }

    @Test
    void bytesAreCopiedAsLowerCaseHexOrPrintableCharacters() {
        byte[] bytes = {0x5a, 0x5d, 0x00, (byte) 0xff, 0x41, 0x20};

        assertEquals("5a5d00ff4120", DetailCanvas.toHex(bytes, bytes.length));
        assertEquals("5a5d", DetailCanvas.toHex(bytes, 2));
        assertEquals("Z]..A ", DetailCanvas.toText(bytes, bytes.length));
    }

    private Point pointIn(Track track) {
        for (DetailCanvas.TrackLayout t : canvas.trackLayouts()) {
            if (t.track == track) {
                return new Point(t.x + t.width / 2, canvas.getHeight() / 2);
            }
        }
        throw new AssertionError("no track " + track);
    }

    private java.awt.event.MouseWheelEvent ctrlWheel(Point p, int rotation) {
        return new java.awt.event.MouseWheelEvent(canvas, java.awt.event.MouseEvent.MOUSE_WHEEL, 0,
                java.awt.event.InputEvent.CTRL_DOWN_MASK, p.x, p.y, 0, false,
                java.awt.event.MouseWheelEvent.WHEEL_UNIT_SCROLL, 1, rotation);
    }

    @Test
    void trackTogglesChangeTheTracks() throws Exception {
        load(NATIVE_X86);
        onEdt(() -> {
            model.setByteplotVisible(false);
            List<Track> tracks = canvas.trackLayouts().stream().map(t -> t.track).collect(Collectors.toList());
            assertEquals(java.util.Arrays.asList(Track.ENTROPY, Track.STRUCTURE), tracks);
            model.setEntropyVisible(false);
            model.setStructureVisible(false);
            tracks = canvas.trackLayouts().stream().map(t -> t.track).collect(Collectors.toList());
            assertEquals(Collections.singletonList(Track.STRUCTURE), tracks, "at least one track is shown");
            assertNotNull(canvas.renderToImage());
        });
    }

    /**
     * The byte plot and the entropy are not shown next to the hex values, however wide the view is, the hex cells
     * have their colors.
     */
    @Test
    void hexViewShowsNoSideTracks() throws Exception {
        load(NATIVE_X86);
        onEdt(() -> {
            canvas.zoom(100, null);
            assertTrue(canvas.level().hex);
            canvas.setSize(3000, canvas.getHeight());
            assertTrue(canvas.relayout());
            assertEquals(Arrays.asList(Track.HEX, Track.ASCII), tracks());
            DetailCanvas.TrackLayout hex = canvas.trackLayouts().get(0);
            assertEquals(DetailCanvas.RULER_WIDTH, hex.x);
        });
    }

    private List<Track> tracks() {
        return canvas.trackLayouts().stream().map(t -> t.track).collect(Collectors.toList());
    }

    @Test
    void goToShowsAndSelectsTheOffset() throws Exception {
        load(DOTNET_PINVOKE);
        onEdt(() -> {
            zoomToBytes();
            long target = data.getFile().length() - 10;
            List<Long> clicked = new ArrayList<>();
            panel.setOffsetClickListener(clicked::add);

            canvas.goTo(target);
            assertTrue(canvas.viewport().isVisible(target));
            assertEquals(target, model.getSelectedOffset());
            assertEquals(Collections.singletonList(target), clicked);
        });
    }

    @Test
    void zoomToOffsetCentersItInTheHexView() throws Exception {
        load(DOTNET_PINVOKE);
        onEdt(() -> {
            long target = data.getFile().length() / 2 + 3;
            panel.zoomToOffset(target);

            assertTrue(canvas.level().hex);
            assertTrue(canvas.viewport().isVisible(target));
            assertEquals(target, model.getSelectedOffset());
            assertNotNull(model.getSelectedRegion());
            int visibleRows = canvas.getHeight() / canvas.level().cellHeight;
            long screenRow = canvas.mapping().rowOf(target) - canvas.getFirstRow();
            assertTrue(Math.abs(screenRow - visibleRows / 2) <= 1, "centered: row " + screenRow + " of " + visibleRows);
        });
    }

    @Test
    void doubleClickIntoTheOverviewZoomsIn() throws Exception {
        load(DOTNET_PINVOKE);
        onEdt(() -> {
            OverviewStrip overview = panel.getOverview();
            Point p = new Point(2, overview.getHeight() / 2);
            long offset = overview.offsetAt(p);
            overview.dispatchEvent(new java.awt.event.MouseEvent(overview, java.awt.event.MouseEvent.MOUSE_CLICKED,
                    0, 0, p.x, p.y, 2, false, java.awt.event.MouseEvent.BUTTON1));

            assertTrue(canvas.level().hex);
            assertEquals(offset, model.getSelectedOffset());
            assertTrue(canvas.viewport().isVisible(offset));
        });
    }

    @Test
    void zoomToRegionShowsItsBeginningInHex() throws Exception {
        load(NATIVE_X86);
        onEdt(() -> {
            FileRegion coff = model.getVisualization().getStructureMap()
                    .getRegions(RegionType.COFF_FILE_HEADER).get(0);
            canvas.zoomToRegion(coff);
            assertSame(coff, model.getSelectedRegion());
            assertTrue(canvas.level().hex, canvas.level().toString());
            assertEquals(canvas.mapping().rowOf(coff.getStart()), canvas.getFirstRow());
            assertTrue(canvas.viewport().isVisible(coff.getEnd() - 1));
        });
    }

    @Test
    void overviewShowsTheWholeFile() throws Exception {
        load(DOTNET_PINVOKE);
        onEdt(() -> {
            OverviewStrip overview = panel.getOverview();
            Swing.layout(overview, overview.getPreferredSize().width, 600);
            assertEquals(0, overview.offsetAt(new Point(0, 0)));
            assertEquals(data.getFile().length(), overview.mapping().getFileLength());
            BufferedImage image = new BufferedImage(overview.getWidth(), overview.getHeight(), BufferedImage.TYPE_INT_ARGB);
            Graphics2D g = image.createGraphics();
            overview.paint(g); // schedules the rendering
            g.dispose();
        });
        onEdtGet(() -> {
            // the rendered overview was delivered by now
            BufferedImage image = new BufferedImage(10, 10, BufferedImage.TYPE_INT_ARGB);
            Graphics2D g = image.createGraphics();
            panel.getOverview().paint(g);
            g.dispose();
            return null;
        });
    }

    @Test
    void hexViewHasTheChosenBytesPerLine() throws Exception {
        load(NATIVE_X86);
        onEdt(() -> {
            panel.setHexBytesPerLine(8);
            canvas.zoom(100, null);
            assertTrue(canvas.level().hex);
            assertEquals(8, canvas.level().columns);
            assertEquals(8, canvas.mapping().getColumns());
            assertEquals(1, panel.getHexWidthChooser().getSelectedIndex());

            Swing.layout(panel, 2600, 700);
            canvas.relayout();
            panel.setHexBytesPerLine(24);
            assertEquals(24, canvas.level().columns);
            assertTrue(canvas.level().hex, "stays at the hex level");
            DetailCanvas.TrackLayout last = canvas.trackLayouts().get(canvas.trackLayouts().size() - 1);
            assertTrue(last.x + last.width <= canvas.getWidth(), "the tracks fit into the canvas");
        });
    }

    @Test
    void tooManyBytesPerLineAreLimitedToTheWidth() throws Exception {
        load(NATIVE_X86);
        onEdt(() -> {
            panel.setHexBytesPerLine(64);
            canvas.zoom(100, null);
            assertTrue(canvas.level().columns < 64, "columns " + canvas.level().columns);
            DetailCanvas.TrackLayout last = canvas.trackLayouts().get(canvas.trackLayouts().size() - 1);
            assertTrue(last.x + last.width <= canvas.getWidth(), "the tracks fit into the canvas");
        });
    }

    @Test
    void autoHexWidthFollowsTheSizeOfTheView() throws Exception {
        load(NATIVE_X86);
        onEdt(() -> {
            canvas.zoom(100, null);
            assertEquals(0, panel.getHexWidthChooser().getSelectedIndex(), "auto is the default");
            int narrow = canvas.level().columns;
            int perByte = 30;
            int room = canvas.getWidth() - DetailCanvas.RULER_WIDTH - DetailCanvas.GAP;
            assertEquals(room / perByte, narrow, "uses all the room");

            Swing.layout(panel, 2600, 700);
            canvas.relayout();
            assertTrue(canvas.level().hex, "stays at the hex level");
            assertTrue(canvas.level().columns > narrow, canvas.level().columns + " after " + narrow);
            room = canvas.getWidth() - DetailCanvas.RULER_WIDTH - DetailCanvas.GAP;
            assertEquals(room / perByte, canvas.level().columns, "uses all the room");
            DetailCanvas.TrackLayout last = canvas.trackLayouts().get(canvas.trackLayouts().size() - 1);
            assertTrue(last.x + last.width <= canvas.getWidth(), "the tracks fit into the canvas");
        });
    }

    @Test
    void pickingAHexWidthIsReported() throws Exception {
        load(NATIVE_X86);
        onEdt(() -> {
            List<Integer> reported = new ArrayList<>();
            panel.setHexBytesPerLineListener(reported::add);

            panel.setHexBytesPerLine(32); // not the user
            assertTrue(reported.isEmpty());

            panel.getHexWidthChooser().setSelectedIndex(2);
            panel.getHexWidthChooser().setSelectedIndex(0);
            assertEquals(Arrays.asList(16, 0), reported);
            assertEquals(0, canvas.getHexBytesPerLine());
        });
    }

    @Test
    void clickingAStructureInTheListMarksItInTheViews() throws Exception {
        load(NATIVE_X86);
        onEdt(() -> {
            model.select(peSignature());
            InfoPane info = panel.getInfoPane();
            info.updateNow();
            JList<FileRegion> list = info.getRegionList();
            assertTrue(list.getModel().getSize() > 1);
            int shown = list.getModel().getSize();
            // the outermost structure, the innermost one is already selected
            FileRegion first = list.getModel().getElementAt(shown - 1);
            long selectedBefore = model.getSelectedOffset();
            BufferedImage before = canvas.renderToImage();
            Rectangle cell = list.getCellBounds(shown - 1, shown - 1);

            list.dispatchEvent(new java.awt.event.MouseEvent(list, java.awt.event.MouseEvent.MOUSE_CLICKED, 0, 0,
                    cell.x + 5, cell.y + 5, 1, false, java.awt.event.MouseEvent.BUTTON1));

            assertSame(first, model.getSelectedRegion());
            assertEquals(selectedBefore, model.getSelectedOffset(), "the list keeps its offset");
            info.updateNow();
            assertEquals(shown, list.getModel().getSize(), "the list keeps its content");
            BufferedImage after = canvas.renderToImage();
            boolean changed = false;
            for (int y = 0; y < before.getHeight() && !changed; y++) {
                for (int x = 0; x < before.getWidth(); x++) {
                    if (before.getRGB(x, y) != after.getRGB(x, y)) {
                        changed = true;
                        break;
                    }
                }
            }
            assertTrue(changed, "the structure is marked in the detail view");
        });
    }

    @Test
    void legendListsPresentStructures() throws Exception {
        load(OVERLAY);
        onEdt(() -> assertTrue(panel.getLegendPanel().entryCount() > 0));
    }

    @Test
    void layerCheckBoxesSwitchTheLayersAndTheSectionsStay() throws Exception {
        load(OVERLAY);
        onEdt(() -> {
            LegendPanel legend = panel.getLegendPanel();
            int entries = legend.entryCount();
            List<JCheckBox> boxes = Swing.findAll(legend, JCheckBox.class);
            assertEquals(Arrays.asList("Byte plot", "Entropy", "PE structure", "Markers"),
                    boxes.stream().map(AbstractButton::getText).collect(Collectors.toList()));

            boxes.get(0).doClick();
            boxes.get(1).doClick();
            boxes.get(3).doClick();

            assertFalse(model.isByteplotVisible());
            assertFalse(model.isEntropyVisible());
            assertFalse(model.isMarkersVisible());
            assertTrue(model.isStructureVisible());
            assertEquals(entries, legend.entryCount(), "the colors stay when the layers are off");
            assertFalse(boxes.get(0).isSelected());

            boxes.get(0).doClick();
            assertTrue(model.isByteplotVisible());
        });
    }

    @Test
    void layerCheckBoxesFollowTheModel() throws Exception {
        load(OVERLAY);
        onEdt(() -> {
            List<JCheckBox> boxes = Swing.findAll(panel.getLegendPanel(), JCheckBox.class);
            model.setEntropyVisible(false);
            assertFalse(boxes.get(1).isSelected());
            model.setEntropyVisible(true);
            assertTrue(boxes.get(1).isSelected());
        });
    }

    @Test
    void parseOffset() {
        assertEquals(0x1a40, DetailCanvas.parseOffset("0x1A40"));
        assertEquals(0x1a40, DetailCanvas.parseOffset(" 1a40 "));
        assertEquals(0x1a40, DetailCanvas.parseOffset("1A40h"));
        assertEquals(1234, DetailCanvas.parseOffset("1234"));
        assertEquals(-1, DetailCanvas.parseOffset("xyz"));
        assertEquals(-1, DetailCanvas.parseOffset(""));
    }
}
