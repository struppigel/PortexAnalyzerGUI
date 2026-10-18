package io.github.struppigel.gui.visualizer;

import com.formdev.flatlaf.FlatLaf;
import io.github.struppigel.gui.FullPEData;
import io.github.struppigel.gui.Starter;
import io.github.struppigel.gui.utils.TestPEData;
import io.github.struppigel.gui.utils.WorkerKiller;
import io.github.struppigel.parser.coffheader.COFFFileHeader;
import io.github.struppigel.parser.coffheader.COFFHeaderKey;
import io.github.struppigel.testsupport.Swing;
import io.github.struppigel.tools.layout.FileRegion;
import io.github.struppigel.tools.layout.RegionType;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

import javax.swing.*;
import java.awt.*;
import java.awt.event.MouseEvent;
import java.awt.image.BufferedImage;
import java.util.ArrayList;
import java.util.List;

import static io.github.struppigel.testsupport.Swing.onEdt;
import static io.github.struppigel.testsupport.Swing.onEdtGet;
import static io.github.struppigel.testsupport.TestFiles.NATIVE_X86;
import static io.github.struppigel.testsupport.TestFiles.OVERLAY;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertSame;
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
    void theTabsListTheStructuresAtThePointerAndInTheSelectionAndTakeNoFocus() throws Exception {
        onEdt(() -> {
            model = new VisualizationModel();
            InfoPane pane = new InfoPane(model, () -> 1);
            JTabbedPane tabs = pane.getTabs();

            assertEquals(3, tabs.getTabCount());
            assertEquals("At pointer", tabs.getTitleAt(0));
            assertSame(pane.getRegionList(), ((JScrollPane) tabs.getComponentAt(0)).getViewport().getView());
            assertEquals("In selection", tabs.getTitleAt(1));
            assertTrue(Swing.findAll(tabs.getComponentAt(1), JList.class).contains(pane.getSelectionList()));
            assertEquals("Bytes", tabs.getTitleAt(2));
            assertTrue(Swing.findAll(tabs.getComponentAt(2), InfoGrid.class).contains(pane.getBytesGrid()));
            assertEquals(JTabbedPane.SCROLL_TAB_LAYOUT, tabs.getTabLayoutPolicy(), "one row of tabs");
            assertEquals(0, tabs.getSelectedIndex(), "the structures at the pointer are shown first");
            assertFalse(tabs.isFocusable(), "the arrow keys keep scrolling the visualization");
        });
    }

    private InfoPane load(String sample) throws Exception {
        FullPEData data = TestPEData.load(sample);
        return onEdtGet(() -> {
            model = new VisualizationModel();
            InfoPane pane = new InfoPane(model, () -> 16);
            model.loadSynchronously(data);
            return pane;
        });
    }

    private static List<FileRegion> elements(JList<FileRegion> list) {
        List<FileRegion> elements = new ArrayList<>();
        for (int i = 0; i < list.getModel().getSize(); i++) {
            elements.add(list.getModel().getElementAt(i));
        }
        return elements;
    }

    @Test
    void theStatisticsOfTheCellAreNotShown() throws Exception {
        InfoPane pane = load(NATIVE_X86);
        onEdt(() -> {
            model.setHoverOffset(0x40);
            pane.updateNow();
            String text = pane.getInfoText();
            assertTrue(text.contains("Cell"), text);
            assertFalse(text.contains("mean,"), text);
            assertFalse(text.contains("zero "), text);
            assertFalse(text.contains("control "), text);
        });
    }

    @Test
    void markedBytesAndASelectedFieldShowTheirValues() throws Exception {
        InfoPane pane = load(NATIVE_X86);
        onEdt(() -> {
            COFFFileHeader coff = model.getFileData().asPE().get().getPeData().getCOFFFileHeader();
            long timeDate = coff.get(COFFHeaderKey.TIME_DATE);
            long start = coff.getOffset() + 4;

            model.selectRange(start, start + 4);
            pane.updateNow();
            String text = pane.getInfoText();
            assertTrue(text.contains("Range    0x" + Long.toHexString(start)), text);
            assertTrue(text.contains("LE       " + timeDate), text);
            assertTrue(text.contains("Unix time"), text);

            FileRegion field = null;
            for (FileRegion region : model.getVisualization().getStructureMap().regionsAt(start)) {
                if (region.getType() == RegionType.HEADER_FIELD && region.getName().equals("TIME_DATE")) {
                    field = region;
                }
            }
            assertNotNull(field);
            model.selectRegion(field, field.getStart());
            pane.updateNow();
            text = pane.getInfoText();
            assertTrue(text.contains("Range    0x" + Long.toHexString(start) + " - 0x" + Long.toHexString(start + 3)),
                    "the range of the structure: " + text);
            assertTrue(text.contains("TIME_DATE"), "the selected structure: " + text);
            assertTrue(text.contains("LE       " + timeDate), "the values of the structure: " + text);
        });
    }

    @Test
    void theSelectionTabListsTheStructuresOfTheSelectionAndKeepsThemWhenOneIsClicked() throws Exception {
        InfoPane pane = load(NATIVE_X86);
        onEdt(() -> {
            pane.getTabs().setSelectedIndex(1);
            assertEquals(InfoPane.SELECTION_HINT, pane.getSelectionCaption());
            long start = model.getFileData().asPE().get().getPeData().getCOFFFileHeader().getOffset();
            long end = start + COFFFileHeader.HEADER_SIZE;

            model.selectRange(start, end);
            pane.updateNow();
            List<FileRegion> expected = model.getVisualization().getStructureMap().regionsOverlapping(start, end);
            JList<FileRegion> list = pane.getSelectionList();
            assertEquals(expected, elements(list));
            String caption = pane.getSelectionCaption();
            assertTrue(caption.startsWith("0x" + Long.toHexString(start) + " - "), caption);
            assertTrue(caption.endsWith("(20 bytes), " + expected.size() + " structures"), caption);

            Swing.layout(pane, 300, 900);
            Rectangle cell = list.getCellBounds(0, 0);
            list.dispatchEvent(new MouseEvent(list, MouseEvent.MOUSE_CLICKED, 0, 0, cell.x + 5, cell.y + 5, 1,
                    false, MouseEvent.BUTTON1));
            assertSame(expected.get(0), model.getSelectedRegion(), "the click marks the structure");
            pane.updateNow();
            assertEquals(expected, elements(list), "the list keeps the structures of the marked bytes");

            model.clearSelection();
            pane.updateNow();
            assertEquals(0, list.getModel().getSize());
            assertEquals(InfoPane.SELECTION_HINT, pane.getSelectionCaption());
        });
    }

    @Test
    void theSelectionTabIsFilledWhenItIsShown() throws Exception {
        InfoPane pane = load(NATIVE_X86);
        onEdt(() -> {
            long start = model.getFileData().asPE().get().getPeData().getCOFFFileHeader().getOffset();
            model.selectRange(start, start + 4);
            pane.updateNow();
            assertEquals(0, pane.getSelectionList().getModel().getSize(), "not filled while it is hidden");

            pane.getTabs().setSelectedIndex(1);
            assertEquals(model.getVisualization().getStructureMap().regionsOverlapping(start, start + 4),
                    elements(pane.getSelectionList()));
        });
    }

    @Test
    void onlyTheOffsetAndTheRangeAreAlwaysVisibleTheRestIsInTheBytesTab() throws Exception {
        InfoPane pane = load(NATIVE_X86);
        onEdt(() -> {
            long start = model.getFileData().asPE().get().getPeData().getCOFFFileHeader().getOffset();
            model.selectRange(start, start + 4);
            model.setHoverOffset(start + 0x10);
            pane.updateNow();

            List<String> labels = new ArrayList<>();
            for (String line : pane.getGrid().getText().split("\n")) {
                labels.add(line.split(" ")[0]);
            }
            assertEquals(List.of("Offset", "Range"), labels, pane.getGrid().getText());
            model.setHoverOffset(-1);
            pane.updateNow();
            String[] offsetRow = pane.getGrid().getRows().get(0);
            assertTrue(offsetRow[1].contains("(selected)"), offsetRow[1]);
            assertEquals("0x" + Long.toHexString(start), InfoGrid.copiedText(offsetRow), "only the offset");
            model.setHoverOffset(start + 0x10);
            pane.updateNow();
            String bytes = pane.getBytesGrid().getText();
            assertTrue(bytes.startsWith("Selected bytes"), bytes);
            for (String expected : new String[]{"Entropy", "LE ", "Unix time", "ASCII", "At the pointer", "Byte "}) {
                assertTrue(bytes.contains(expected), expected + " in " + bytes);
            }
            assertTrue(bytes.indexOf("Unix time") < bytes.indexOf("At the pointer"),
                    "the selected bytes come first, so that they stay where they are while the pointer moves");
        });
    }

    private static List<String> menuTexts(InfoGrid grid, String label) {
        for (String[] row : grid.getRows()) {
            if (java.util.Objects.equals(label, row[0])) {
                List<String> texts = new ArrayList<>();
                for (MenuElement element : grid.createMenu(row).getSubElements()) {
                    texts.add(((JMenuItem) element).getText());
                }
                return texts;
            }
        }
        throw new AssertionError("no row " + label + " in " + grid.getText());
    }

    private static int rowIndex(InfoGrid grid, String label) {
        for (int i = 0; i < grid.getRows().size(); i++) {
            if (label.equals(grid.getRows().get(i)[0])) {
                return i;
            }
        }
        throw new AssertionError("no row " + label);
    }

    @Test
    void copyingAllInTheBytesTabCopiesAllSelectedBytesInTheFormatOfTheRow() throws Exception {
        InfoPane pane = load(NATIVE_X86);
        onEdt(() -> {
            InfoGrid bytes = pane.getBytesGrid();
            pane.updateNow();
            assertEquals(List.of(), menuTexts(bytes, null), "no bytes are selected");

            model.selectRange(0, 0x200);
            pane.updateNow();
            assertTrue(menuTexts(bytes, "ASCII").contains("Copy all as ASCII (512 bytes)"),
                    "" + menuTexts(bytes, "ASCII"));
            assertTrue(menuTexts(bytes, "UTF-16").contains("Copy all as UTF-16 (512 bytes)"));
            assertTrue(menuTexts(bytes, "Hex").contains("Copy all as hex (512 bytes)"));
            assertTrue(menuTexts(bytes, "").contains("Copy all as hex (512 bytes)"), "the second hex row");
            assertTrue(menuTexts(bytes, "Byte").contains("Copy all as hex (512 bytes)"), "the bytes at the pointer");
            List<String> asciiRows = new ArrayList<>();
            int ascii = rowIndex(bytes, "ASCII");
            String[] continued = bytes.getRows().get(ascii + 1);
            assertEquals("", continued[0], "the text has several rows");
            for (MenuElement element : bytes.createMenu(continued).getSubElements()) {
                asciiRows.add(((JMenuItem) element).getText());
            }
            assertTrue(asciiRows.contains("Copy all as ASCII (512 bytes)"), "" + asciiRows);
            assertEquals(List.of("Copy 0x0", "Copy all"), menuTexts(pane.getGrid(), "Offset"),
                    "the offset and the range are copied as before");
        });
    }

    @Test
    void aMarkedValueOfTheBytesTabStaysMarkedWhileThePointerMoves() throws Exception {
        InfoPane pane = load(NATIVE_X86);
        onEdt(() -> {
            InfoGrid bytes = pane.getBytesGrid();
            model.selectRange(0, 0x40);
            pane.updateNow();
            int ascii = rowIndex(bytes, "ASCII");
            bytes.markValue(ascii);
            assertTrue(bytes.isMarked(ascii));
            assertTrue(bytes.isMarked(ascii + 1), "all rows of the text");

            model.setHoverOffset(0x300);
            pane.updateNow();
            assertTrue(bytes.isMarked(ascii));
            model.selectRange(0x40, 0x80);
            pane.updateNow();
            assertFalse(bytes.isMarked(ascii), "other bytes");
        });
    }

    @Test
    void theDescriptionGetsShorterAgainWhenTheSelectionIsCleared() throws Exception {
        InfoPane pane = load(NATIVE_X86);
        onEdt(() -> {
            long start = model.getFileData().asPE().get().getPeData().getCOFFFileHeader().getOffset();
            pane.updateNow();
            int before = pane.getGrid().getPreferredSize().height;
            int bytesBefore = pane.getBytesGrid().getPreferredSize().height;
            model.selectRange(start, start + 8);
            pane.updateNow();
            assertTrue(pane.getGrid().getPreferredSize().height > before, "the range takes room");
            int selected = pane.getBytesGrid().getPreferredSize().height;
            assertTrue(selected > bytesBefore, "the values take room: " + bytesBefore + ", " + selected);

            model.clearSelection();
            pane.updateNow();
            assertEquals(before, pane.getGrid().getPreferredSize().height);
            assertEquals(bytesBefore, pane.getBytesGrid().getPreferredSize().height);
        });
    }

    @Test
    void tooltipsOfTheStructuresShowHtmlAsText() throws Exception {
        onEdt(() -> {
            model = new VisualizationModel();
            JToolTip tip = new InfoPane(model, () -> 1).getRegionList().createToolTip();
            assertEquals(Boolean.TRUE, tip.getClientProperty("html.disable"));
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

    @Test
    void aSelectedStructureShowsTheEntropyOfTheOverlayNode() throws Exception {
        FullPEData data = TestPEData.load(OVERLAY);
        InfoPane[] pane = new InfoPane[1];
        FileRegion[] overlay = new FileRegion[1];
        onEdt(() -> {
            model = new VisualizationModel();
            pane[0] = new InfoPane(model, () -> 1);
            model.loadSynchronously(data);
            overlay[0] = model.getVisualization().getStructureMap().getRegions(RegionType.OVERLAY).get(0);
            model.selectRegion(overlay[0], overlay[0].getStart());
            pane[0].updateNow();
        });
        String expected = String.format("%.2f bits per byte", data.getOverlayEntropy() * 8);
        long end = System.currentTimeMillis() + 5000;
        while (!onEdtGet(() -> pane[0].getInfoText().contains(expected))) {
            assertTrue(System.currentTimeMillis() < end, "timed out, shown: " + onEdtGet(() -> pane[0].getInfoText()));
            Thread.sleep(10);
        }
    }

    private static double luminance(int rgb) {
        return (0.299 * (rgb >> 16 & 0xff) + 0.587 * (rgb >> 8 & 0xff) + 0.114 * (rgb & 0xff)) / 255;
    }
}
