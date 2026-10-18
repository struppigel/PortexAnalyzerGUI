package io.github.struppigel.gui.visualizer;

import io.github.struppigel.tools.visualizer.render.LegendEntry;
import org.junit.jupiter.api.Test;

import javax.swing.*;
import javax.swing.plaf.basic.BasicHTML;
import java.awt.*;

import static io.github.struppigel.testsupport.Swing.findAll;
import static io.github.struppigel.testsupport.Swing.onEdtGet;
import static org.junit.jupiter.api.Assertions.*;

class LegendPanelTest {

    /**
     * Labels can come from the file, e.g., names of resource types, and could start with &lt;html&gt;.
     */
    @Test
    void htmlInLabelsIsShownAsText() throws Exception {
        String html = "<html><b>bold</b></html>";

        JLabel label = onEdtGet(() -> {
            LegendPanel panel = new LegendPanel(new VisualizationModel());
            JTree tree = findAll(panel, JTree.class).get(0);
            Object node = LegendPanel.categoryNode(LegendEntry.entry(html, Color.red, false), LegendPanel.MAX_LISTED);
            return (JLabel) tree.getCellRenderer().getTreeCellRendererComponent(tree, node, false, false, true, 0,
                    false);
        });

        assertNull(label.getClientProperty(BasicHTML.propertyKey));
        assertEquals(html, label.getText());
    }

    @Test
    void tooltipsShowHtmlAsText() throws Exception {
        JToolTip tip = onEdtGet(() -> findAll(new LegendPanel(new VisualizationModel()), JTree.class).get(0)
                .createToolTip());

        assertEquals(Boolean.TRUE, tip.getClientProperty("html.disable"));
    }
}
