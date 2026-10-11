package io.github.struppigel.gui;

import io.github.struppigel.gui.pedetails.FileContentPreviewPanel;
import org.junit.jupiter.api.Test;

import static io.github.struppigel.testsupport.Swing.onEdt;
import static org.junit.jupiter.api.Assertions.*;

class PEFieldsTableTest {

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
