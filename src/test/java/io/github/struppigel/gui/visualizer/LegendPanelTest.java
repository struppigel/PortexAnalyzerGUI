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
    @SuppressWarnings("unchecked")
    void htmlInLabelsIsShownAsText() throws Exception {
        String html = "<html><b>bold</b></html>";

        JLabel label = onEdtGet(() -> {
            LegendPanel panel = new LegendPanel(new VisualizationModel());
            JList<LegendEntry> list = (JList<LegendEntry>) findAll(panel, JList.class).get(0);
            return (JLabel) list.getCellRenderer().getListCellRendererComponent(list,
                    LegendEntry.entry(html, Color.red, false), 0, false, false);
        });

        assertNull(label.getClientProperty(BasicHTML.propertyKey));
        assertEquals(html, label.getText());
    }
}
