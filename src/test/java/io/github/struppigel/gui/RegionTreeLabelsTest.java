package io.github.struppigel.gui;

import io.github.struppigel.gui.utils.TestPEData;
import io.github.struppigel.testsupport.Swing;
import io.github.struppigel.testsupport.TestFiles;
import io.github.struppigel.tools.layout.FileRegion;
import io.github.struppigel.tools.layout.RegionType;
import io.github.struppigel.tools.layout.StructureMap;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;

import javax.swing.*;
import java.util.ArrayList;
import java.util.List;

import static io.github.struppigel.testsupport.Swing.onEdtGet;
import static org.junit.jupiter.api.Assertions.*;

class RegionTreeLabelsTest {

    static List<String> allPE() {
        return TestFiles.ALL_PE;
    }

    /**
     * Every structure of the samples has a node in the tree, except unmapped bytes and own regions.
     */
    @ParameterizedTest
    @MethodSource("allPE")
    void everyStructureHasATreeNode(String sample) throws Exception {
        FullPEData data = TestPEData.load(sample);
        StructureMap map = data.getStructureMap();
        List<String> treeLabels = onEdtGet(() -> {
            PEComponentTree tree = new PEComponentTree(null);
            tree.setFileData(data);
            return Swing.treeLabels(Swing.findAll(tree, JTree.class).get(0));
        });
        List<String> clrTableNames = new ArrayList<>(data.getClrTables().keySet());

        for (FileRegion region : map.getRegions()) {
            if (region.getType() == RegionType.UNMAPPED || region.getType() == RegionType.CUSTOM) {
                continue;
            }
            List<String> labels = RegionTreeLabels.labelsFor(region, clrTableNames);
            assertTrue(labels.stream().anyMatch(treeLabels::contains),
                    region.getPath() + " " + region.getType() + ": " + labels + " not in " + treeLabels);
        }
    }
}
