package io.github.struppigel.gui;

import io.github.struppigel.gui.pedetails.FileContentPreviewPanel;
import org.junit.jupiter.api.Test;

import javax.swing.*;
import javax.swing.plaf.basic.BasicHTML;
import java.awt.*;
import java.awt.event.MouseEvent;

import static io.github.struppigel.testsupport.Swing.layout;
import static io.github.struppigel.testsupport.Swing.onEdt;
import static io.github.struppigel.testsupport.Swing.onEdtGet;
import static org.junit.jupiter.api.Assertions.*;

class PEFieldsTableTest {

    private static final String LONG_TEXT = "Certificate Table: signature 1: the computed SHA-1 image digest "
            + "79da1b80964d0ef4e661b74d09e4d01dfb71fa95 differs from the signed digest "
            + "e51fed3f2ef8c6c099c5f9f9bd1789c33de64680, the file was modified after signing or the signature "
            + "belongs to another file";

    @Test
    void wrappedColumnGrowsRowsWithLongText() throws Exception {
        PEFieldsTable table = new PEFieldsTable(true);
        JScrollPane pane = new JScrollPane(table);

        onEdt(() -> {
            table.setModel(model(new Object[]{LONG_TEXT, "type"}, new Object[]{"short", "type"}));
            table.setWrappedColumn(0);
            layout(pane, 300, 400);
        });

        int oneLine = table.getRowHeight(1);
        assertTrue(oneLine >= table.getRowHeight(), "height " + oneLine);
        assertTrue(table.getRowHeight(0) > 2 * oneLine, "height " + table.getRowHeight(0));

        // wide enough for one line
        onEdt(() -> layout(pane, 8000, 400));
        assertEquals(oneLine, table.getRowHeight(0));
    }

    @Test
    void wrappedRowHeightsFollowSorting() throws Exception {
        PEFieldsTable table = new PEFieldsTable(true);
        JScrollPane pane = new JScrollPane(table);
        onEdt(() -> {
            table.setModel(model(new Object[]{LONG_TEXT, "b"}, new Object[]{"short", "a"}));
            table.setWrappedColumn(0);
            layout(pane, 300, 400);
        });

        int oneLine = table.getRowHeight(1);

        onEdt(() -> table.getRowSorter().toggleSortOrder(1));

        assertEquals("short", table.getValueAt(0, 0));
        assertEquals(oneLine, table.getRowHeight(0));
        assertTrue(table.getRowHeight(1) > 2 * oneLine, "height " + table.getRowHeight(1));
    }

    @Test
    void toolTipShowsCutOffText() throws Exception {
        PEFieldsTable table = new PEFieldsTable(true);
        JScrollPane pane = new JScrollPane(table);
        onEdt(() -> {
            table.setModel(model(new Object[]{LONG_TEXT, "short"}));
            layout(pane, 300, 400);
        });

        String cutOff = onEdtGet(() -> table.getToolTipText(mouseAt(table, 0, 0)));
        String fits = onEdtGet(() -> table.getToolTipText(mouseAt(table, 0, 1)));

        assertNotNull(cutOff);
        assertTrue(cutOff.contains(LONG_TEXT), cutOff);
        assertNull(fits);
    }

    /**
     * Text from the file must not be rendered as HTML, which could, e.g., load remote images.
     */
    @Test
    void toolTipEscapesHtml() throws Exception {
        PEFieldsTable table = new PEFieldsTable(true);
        JScrollPane pane = new JScrollPane(table);
        onEdt(() -> {
            table.setModel(model(new Object[]{"<img src='x'> & " + LONG_TEXT}));
            layout(pane, 300, 400);
        });

        String toolTip = onEdtGet(() -> table.getToolTipText(mouseAt(table, 0, 0)));

        assertTrue(toolTip.contains("&lt;img src='x'&gt; &amp; Certificate"), toolTip);
        assertFalse(toolTip.contains("<img"), toolTip);
    }

    /**
     * Strings from the file, e.g., export names, could start with &lt;html&gt;.
     */
    @Test
    void htmlInCellsIsShownAsText() throws Exception {
        String html = "<html><b>bold</b></html>";
        // a new table, so that this is the first cell its renderer shows
        PEFieldsTable table = new PEFieldsTable(true);
        onEdt(() -> table.setModel(model(new Object[]{html, ""}, new Object[]{"b", 16L})));

        JLabel cell = onEdtGet(() -> (JLabel) table.prepareRenderer(table.getCellRenderer(0, 0), 0, 0));

        assertNull(cell.getClientProperty(BasicHTML.propertyKey));
        assertEquals(html, cell.getText());
        // a label without the protection renders it
        assertNotNull(onEdtGet(() -> new JLabel(html).getClientProperty(BasicHTML.propertyKey)));
    }

    @Test
    void wrappedCellsHaveNoToolTip() throws Exception {
        PEFieldsTable table = new PEFieldsTable(true);
        JScrollPane pane = new JScrollPane(table);
        onEdt(() -> {
            table.setModel(model(new Object[]{LONG_TEXT}));
            table.setWrappedColumn(0);
            layout(pane, 300, 400);
        });

        assertNull(onEdtGet(() -> table.getToolTipText(mouseAt(table, 0, 0))));
    }

    /**
     * Offsets of entries without an offset are empty strings, so the column has the class Object.
     */
    @Test
    void numbersInMixedColumnsAreHex() throws Exception {
        PEFieldsTable hex = new PEFieldsTable(true);
        PEFieldsTable decimal = new PEFieldsTable(false);
        onEdt(() -> {
            hex.setModel(model(new Object[]{"a", 16L}, new Object[]{"b", ""}));
            decimal.setModel(model(new Object[]{"a", 16L}, new Object[]{"b", ""}));
        });

        assertEquals("0x10", renderedText(hex, 0, 1));
        assertEquals("", renderedText(hex, 1, 1));
        assertEquals("16", renderedText(decimal, 0, 1));
    }

    private static String renderedText(JTable table, int row, int column) throws Exception {
        return onEdtGet(() -> ((JLabel) table.prepareRenderer(table.getCellRenderer(row, column), row, column))
                .getText());
    }

    private static MouseEvent mouseAt(JTable table, int row, int column) {
        Rectangle cell = table.getCellRect(row, column, false);
        return new MouseEvent(table, MouseEvent.MOUSE_MOVED, 0, 0, cell.x + 2, cell.y + 2, 0, false);
    }

    @Test
    void hexRendererShowsHexOrDecimal() {
        PEFieldsTable.HexValueRenderer hex = new PEFieldsTable.HexValueRenderer(true);
        PEFieldsTable.HexValueRenderer dec = new PEFieldsTable.HexValueRenderer(false);

        hex.setValue(255L);
        assertEquals("0xff", hex.getText());
        hex.setValue(255);
        assertEquals("0xff", hex.getText());
        dec.setValue(255L);
        assertEquals("255", dec.getText());
        dec.setValue(255);
        assertEquals("255", dec.getText());
    }

    /**
     * The renderer is reused for every cell, so other values must not keep the text of the previous cell.
     */
    @Test
    void hexRendererReplacesTextForOtherValues() {
        PEFieldsTable.HexValueRenderer renderer = new PEFieldsTable.HexValueRenderer(true);

        renderer.setValue(255L);
        renderer.setValue("");
        assertEquals("", renderer.getText());
        renderer.setValue(255L);
        renderer.setValue(null);
        assertEquals("", renderer.getText());
        renderer.setValue("text");
        assertEquals("text", renderer.getText());
    }

    @Test
    void columnClassIsTypeOfValues() {
        PEFieldsTable.PETableModel model = model(new Object[]{"name", 1L, 2}, new Object[]{"other", 3L, 4});

        assertEquals(String.class, model.getColumnClass(0));
        assertEquals(Long.class, model.getColumnClass(1));
        assertEquals(Integer.class, model.getColumnClass(2));
    }

    @Test
    void columnClassOfMixedValuesIsObject() {
        PEFieldsTable.PETableModel model = model(new Object[]{"name", 1L, 2}, new Object[]{"other", 3L, ""});

        assertEquals(Long.class, model.getColumnClass(1));
        assertEquals(Object.class, model.getColumnClass(2));
    }

    @Test
    void columnClassChangesWhenRowsAreAdded() {
        PEFieldsTable.PETableModel model = model(new Object[]{"name", 1L, 2});
        assertEquals(Integer.class, model.getColumnClass(2));

        model.addRow(new Object[]{"other", 3L, ""});

        assertEquals(Object.class, model.getColumnClass(2));
    }

    @Test
    void columnClassOfEmptyModelIsObject() {
        PEFieldsTable.PETableModel model = new PEFieldsTable.PETableModel();
        model.setColumnIdentifiers(new String[]{"a"});

        assertEquals(Object.class, model.getColumnClass(0));
    }

    @Test
    void columnClassOutOfRangeIsObject() {
        PEFieldsTable.PETableModel model = model(new Object[]{"name", 1L, 2});

        assertEquals(Object.class, model.getColumnClass(3));
    }

    @Test
    void columnClassOfNullValueIsObject() {
        PEFieldsTable.PETableModel model = model(new Object[]{null, 1L, 2}, new Object[]{null, null, 3});

        assertEquals(Object.class, model.getColumnClass(0));
        assertEquals(Long.class, model.getColumnClass(1));
    }

    /**
     * The import table has Integer hints for name imports and empty strings for ordinal imports.
     */
    @Test
    void sortingColumnWithMixedTypesWorks() throws Exception {
        PEFieldsTable table = new PEFieldsTable(true);
        PEFieldsTable.PETableModel model = model(new Object[]{"a", 2L, 5}, new Object[]{"b", 1L, ""});

        onEdt(() -> {
            table.setModel(model);
            table.getRowSorter().toggleSortOrder(2);
        });

        assertEquals(2, table.getRowCount());
        assertEquals("b", table.getValueAt(0, 0));
    }

    @Test
    void selectingRowWithoutOffsetDoesNotFail() throws Exception {
        PEFieldsTable table = new PEFieldsTable(true);
        table.setPreviewOffsetColumn(3, new FileContentPreviewPanel());
        PEFieldsTable.PETableModel model = model(new Object[]{"no matches found", "", "", ""});

        onEdt(() -> {
            table.setModel(model);
            table.setRowSelectionInterval(0, 0);
        });

        assertEquals(0, table.getSelectedRow());
    }

    @Test
    void selectingRowWithOffsetDoesNotFail() throws Exception {
        PEFieldsTable table = new PEFieldsTable(true);
        table.setPreviewOffsetColumn(3, new FileContentPreviewPanel());
        PEFieldsTable.PETableModel model = model(new Object[]{"rule", "pattern", "content", 16L});

        onEdt(() -> {
            table.setModel(model);
            table.setRowSelectionInterval(0, 0);
        });

        assertEquals(0, table.getSelectedRow());
    }

    private static PEFieldsTable.PETableModel model(Object[]... rows) {
        PEFieldsTable.PETableModel model = new PEFieldsTable.PETableModel();
        String[] headers = new String[rows[0].length];
        for (int i = 0; i < headers.length; i++) {
            headers[i] = "column " + i;
        }
        model.setColumnIdentifiers(headers);
        for (Object[] row : rows) {
            model.addRow(row);
        }
        return model;
    }
}
