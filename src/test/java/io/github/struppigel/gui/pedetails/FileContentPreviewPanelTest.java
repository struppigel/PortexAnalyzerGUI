package io.github.struppigel.gui.pedetails;

import io.github.struppigel.gui.utils.TestPEData;
import io.github.struppigel.testsupport.Swing;
import org.junit.jupiter.api.Test;

import javax.swing.*;
import java.awt.*;
import java.awt.event.MouseEvent;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.Callable;

import static io.github.struppigel.testsupport.Swing.onEdt;
import static io.github.struppigel.testsupport.Swing.onEdtGet;
import static io.github.struppigel.testsupport.TestFiles.NATIVE_X86;
import static org.junit.jupiter.api.Assertions.*;

class FileContentPreviewPanelTest {

    @Test
    void oneCharacterPerByte() {
        byte[] bytes = {0x4d, 0x5a, (byte) 0x90, 0, 0x20, 0x7e, 0x7f, 0x0a};
        assertEquals("MZ.. ~..", FileContentPreviewPanel.printable(bytes));
    }

    @Test
    void showsTheBytesAtTheOffset() throws Exception {
        FileContentPreviewPanel preview = loadedPreview();
        String text = onEdtGet(preview::getText);
        assertTrue(text.startsWith("Offset: 0x0\nMZ"), text.substring(0, 20));
        assertFalse(text.contains("4D 5A"), "no hex column");
    }

    @Test
    void highlightsTheBytesOfTheRange() throws Exception {
        FileContentPreviewPanel preview = loadedPreview();

        onEdt(() -> preview.setHighlight(6, 10));
        assertEquals(1, (int) onEdtGet(preview::highlightCount));
        onEdt(() -> preview.setHighlight(0x10_0000, 0x10_0010));
        assertEquals(0, (int) onEdtGet(preview::highlightCount), "not in the shown bytes");
        onEdt(() -> preview.setHighlight(-1, -1));
        assertEquals(0, (int) onEdtGet(preview::highlightCount));
    }

    @Test
    void textPositionsMapToTheirBytes() throws Exception {
        FileContentPreviewPanel preview = loadedPreview();
        onEdt(() -> {
            String text = preview.getText();
            int firstByte = text.indexOf("MZ");
            assertEquals(0, preview.offsetAtTextPosition(0), "the header shows the first byte");
            assertEquals(0, preview.offsetAtTextPosition(firstByte));
            assertEquals(1, preview.offsetAtTextPosition(firstByte + 1));
            assertEquals(0x100, preview.offsetAtTextPosition(firstByte + 0x100));
            assertEquals(text.length() - firstByte - 1, preview.offsetAtTextPosition(text.length() + 5),
                    "after the end: the last byte");
        });
    }

    @Test
    void doubleClickOnAByteOpensTheVisualization() throws Exception {
        List<Long> opened = new ArrayList<>();
        FileContentPreviewPanel preview = loadedPreview();
        onEdt(() -> {
            preview.setOpenVisualizationAction(opened::add);
            Swing.layout(preview, preview.getPreferredSize().width, 400);
            JTextArea area = Swing.findAll(preview, JTextArea.class).get(0);
            Rectangle z = area.modelToView(preview.getText().indexOf("MZ") + 1);
            area.dispatchEvent(new MouseEvent(area, MouseEvent.MOUSE_CLICKED, 0, 0,
                    z.x + 2, z.y + z.height / 2, 2, false, MouseEvent.BUTTON1));
        });

        assertEquals(Collections.singletonList(1L), opened);
    }

    @Test
    void highlightIsReadableWithLightAndDarkText() {
        // the dark PortEx look and feel has white text
        Color forWhiteText = FileContentPreviewPanel.highlightColorFor(Color.white);
        Color forBlackText = FileContentPreviewPanel.highlightColorFor(Color.black);
        assertTrue(contrast(Color.white, forWhiteText) >= 4.5, "contrast " + contrast(Color.white, forWhiteText));
        assertTrue(contrast(Color.black, forBlackText) >= 4.5, "contrast " + contrast(Color.black, forBlackText));
    }

    @Test
    void offsetListenersLearnTheSource() throws Exception {
        FileContentPreviewPanel preview = onEdtGet(FileContentPreviewPanel::new);
        List<Object[]> calls = new ArrayList<>();
        Object source = new Object();
        onEdt(() -> {
            preview.addOffsetListener((offset, from) -> calls.add(new Object[]{offset, from}));
            // also without a file, so that listeners hear about navigation while a file is set up
            preview.showContentAtOffset(0x40);
            preview.showContentAtOffset(0x80, source);
        });

        assertEquals(2, calls.size());
        assertEquals(0x40L, calls.get(0)[0]);
        assertNull(calls.get(0)[1]);
        assertSame(source, calls.get(1)[1]);
        assertEquals(0x80, (long) onEdtGet(preview::getCurrentOffset));
    }

    @Test
    void isNarrowWithAFixedWidth() throws Exception {
        FileContentPreviewPanel empty = onEdtGet(FileContentPreviewPanel::new);
        int width = onEdtGet(() -> empty.getPreferredSize().width);
        FileContentPreviewPanel preview = loadedPreview();

        assertEquals(width, (int) onEdtGet(() -> preview.getPreferredSize().width), "independent of the content");
        assertTrue(width > 100 && width < 260, "about " + FileContentPreviewPanel.COLUMNS + " characters: " + width);
    }

    private static FileContentPreviewPanel loadedPreview() throws Exception {
        FileContentPreviewPanel preview = onEdtGet(FileContentPreviewPanel::new);
        onEdt(() -> preview.setFileData(TestPEData.load(NATIVE_X86)));
        waitFor(() -> preview.getText().startsWith("Offset: 0x0\nMZ"));
        return preview;
    }

    /** contrast ratio of WCAG 2 */
    private static double contrast(Color a, Color b) {
        double la = luminance(a);
        double lb = luminance(b);
        return (Math.max(la, lb) + 0.05) / (Math.min(la, lb) + 0.05);
    }

    private static double luminance(Color c) {
        double[] rgb = {c.getRed() / 255.0, c.getGreen() / 255.0, c.getBlue() / 255.0};
        for (int i = 0; i < 3; i++) {
            rgb[i] = rgb[i] <= 0.03928 ? rgb[i] / 12.92 : Math.pow((rgb[i] + 0.055) / 1.055, 2.4);
        }
        return 0.2126 * rgb[0] + 0.7152 * rgb[1] + 0.0722 * rgb[2];
    }

    private static void waitFor(Callable<Boolean> condition) throws Exception {
        long end = System.currentTimeMillis() + 5000;
        while (!onEdtGet(condition)) {
            assertTrue(System.currentTimeMillis() < end, "timed out");
            Thread.sleep(10);
        }
    }
}
