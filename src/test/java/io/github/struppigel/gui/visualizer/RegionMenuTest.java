package io.github.struppigel.gui.visualizer;

import io.github.struppigel.gui.FullPEData;
import io.github.struppigel.gui.utils.TestPEData;
import io.github.struppigel.gui.utils.WorkerKiller;
import io.github.struppigel.parser.PhysicalLocation;
import io.github.struppigel.tools.layout.FileRegion;
import io.github.struppigel.tools.layout.RegionType;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

import javax.swing.*;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

import static io.github.struppigel.testsupport.Swing.onEdt;
import static io.github.struppigel.testsupport.TestFiles.NATIVE_X86;
import static org.junit.jupiter.api.Assertions.*;

class RegionMenuTest {

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

    private static List<String> texts(JPopupMenu menu) {
        List<String> texts = new ArrayList<>();
        for (MenuElement element : menu.getSubElements()) {
            texts.add(((JMenuItem) element).getText());
        }
        return texts;
    }

    private static JMenuItem item(JPopupMenu menu, String prefix) {
        for (MenuElement element : menu.getSubElements()) {
            JMenuItem item = (JMenuItem) element;
            if (item.getText().startsWith(prefix)) {
                return item;
            }
        }
        throw new AssertionError("no " + prefix + " in " + texts(menu));
    }

    private static String save(FileRegion region) {
        return "Save " + region.getName() + " as file...";
    }

    @Test
    void offersOnlyTheAvailableActions() throws Exception {
        FullPEData data = TestPEData.load(NATIVE_X86);
        onEdt(() -> {
            model = new VisualizationModel();
            model.loadSynchronously(data);
            FileRegion region = data.getStructureMap().getRoots().get(0);
            JLabel invoker = new JLabel();

            assertEquals(List.of("Zoom to " + region.getName(), "Show in tree", save(region)),
                    texts(RegionMenu.create(invoker, model, region, -1, r -> {}, (r, offset) -> {})),
                    "one location, so no items to go to other locations");
            assertEquals(List.of(save(region)), texts(RegionMenu.create(invoker, model, region, -1, null, null)));
        });
    }

    @Test
    void aStructureWithSeveralLocationsGoesToTheNextAndThePreviousOne() throws Exception {
        FullPEData data = TestPEData.load(NATIVE_X86);
        onEdt(() -> {
            model = new VisualizationModel();
            model.loadSynchronously(data);
            List<Long> requested = new ArrayList<>();
            model.addListener(new VisualizationModel.Listener() {
                @Override
                public void viewRequested(long offset, boolean onlyIfInvisible) {
                    requested.add(offset);
                }
            });
            // an imported function: its lookup table entry, its name and its IAT slot
            FileRegion function = null;
            for (FileRegion region : data.getStructureMap().getRegions(RegionType.IMPORT_FUNCTION)) {
                if (region.getLocations().size() > 2) {
                    function = region;
                    break;
                }
            }
            assertNotNull(function, "an import function with three locations");
            List<PhysicalLocation> locations = new ArrayList<>(function.getLocations());
            locations.sort(Comparator.comparingLong(PhysicalLocation::from));
            int n = locations.size();
            JLabel invoker = new JLabel();

            JPopupMenu menu = RegionMenu.create(invoker, model, function, -1, null, null);
            assertEquals(List.of("Next location (1 of " + n + ")", "Previous location (" + n + " of " + n + ")",
                    save(function)), texts(menu), "nothing shown: the first and the last location");

            menu = RegionMenu.create(invoker, model, function, locations.get(0).from(), null, null);
            assertEquals("Next location (2 of " + n + ")", item(menu, "Next").getText());
            assertEquals("Previous location (" + n + " of " + n + ")", item(menu, "Previous").getText(),
                    "before the first comes the last");
            item(menu, "Next").doClick();
            PhysicalLocation second = locations.get(1);
            assertSame(function, model.getSelectedRegion());
            assertEquals(second.from(), model.getSelectedOffset());
            assertArrayEquals(new long[]{second.from(), second.from() + second.size()}, model.getSelectedBytes(),
                    "the location is the selected part");
            assertEquals(List.of(second.from()), requested, "the views show the location");
            assertEquals(second.from(), RegionMenu.selectedOffsetIn(model, function));
            assertEquals(-1, RegionMenu.selectedOffsetIn(model, data.getStructureMap().getRoots().get(0)));

            long inLast = locations.get(n - 1).from() + locations.get(n - 1).size() - 1;
            menu = RegionMenu.create(invoker, model, function, inLast, null, null);
            assertEquals("Next location (1 of " + n + ")", item(menu, "Next").getText(), "after the last the first");
            assertEquals("Previous location (" + (n - 1) + " of " + n + ")", item(menu, "Previous").getText());
        });
    }
}
