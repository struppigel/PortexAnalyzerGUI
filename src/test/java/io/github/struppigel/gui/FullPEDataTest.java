package io.github.struppigel.gui;

import io.github.struppigel.gui.utils.TestPEData;
import io.github.struppigel.parser.sections.clr.CLRTable;
import io.github.struppigel.testsupport.TestFiles;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;

import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;

class FullPEDataTest {

    static List<String> dotNet() {
        return TestFiles.ALL_DOTNET;
    }

    /**
     * Table names like "0x23 AssemblyRef" contain the names of other tables like "Assembly", so they must not resolve
     * to those.
     */
    @ParameterizedTest
    @MethodSource("dotNet")
    void everyClrTableNameResolvesToItsOwnTable(String sample) throws Exception {
        FullPEData data = TestPEData.load(sample);

        assertFalse(data.getClrTables().isEmpty());
        for (String name : data.getClrTables().keySet()) {
            Optional<CLRTable> table = data.getClrTableForName(name);
            assertTrue(table.isPresent(), name);
            assertEquals(name, String.format("%0#4x %s", table.get().getIndex(), table.get().getTableName()));
        }
    }

    /**
     * The preview shows the table offset, which is where the first row of the table starts.
     */
    @ParameterizedTest
    @MethodSource("dotNet")
    void clrTableOffsetIsOffsetOfFirstRow(String sample) throws Exception {
        FullPEData data = TestPEData.load(sample);

        for (String name : data.getClrTables().keySet()) {
            List<Object> firstRow = data.getClrTables().get(name).get(0);
            Object firstRowOffset = firstRow.get(firstRow.size() - 1);
            assertEquals(firstRowOffset, data.getClrTableOffset(name), name);
        }
    }
}
