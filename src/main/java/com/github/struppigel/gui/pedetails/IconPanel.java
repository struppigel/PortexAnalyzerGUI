/**
 * *****************************************************************************
 * Copyright 2022 Karsten Hahn
 * <p>
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * <a href="http://www.apache.org/licenses/LICENSE-2.0">http://www.apache.org/licenses/LICENSE-2.0</a>
 * <p>
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 * ****************************************************************************
 */
package com.github.struppigel.gui.pedetails;

import com.github.struppigel.parser.PEData;
import com.github.struppigel.parser.sections.rsrc.icon.IcoFile;
import com.github.struppigel.parser.sections.rsrc.icon.IconParser;
import com.github.struppigel.gui.FullPEData;
import com.github.struppigel.gui.utils.WorkerKiller;
import net.ifok.image.image4j.codec.ico.ICODecoder;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import javax.swing.*;
import java.awt.*;
import java.awt.image.BufferedImage;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ExecutionException;

public class IconPanel extends JPanel {
    private static final Logger LOGGER = LogManager.getLogger();
    private FullPEData peData;

    private List<BufferedImage> icons;

    public void setPeData(FullPEData peData) {
        this.peData = peData;
        SwingWorker worker = new IconUpdateWorker(peData.getPeData());
        WorkerKiller.getInstance().addWorker(worker);
        worker.execute();
    }

    public List<BufferedImage> getIcons() {
        return icons;
    }

    private class IconUpdateWorker extends SwingWorker<List<BufferedImage>, Void> {
        private final PEData data;

        public IconUpdateWorker(PEData data) {
            this.data = data;
        }

        @Override
        protected List<BufferedImage> doInBackground() throws IOException {
            List<IcoFile> icons = IconParser.extractIcons(data);
            List<BufferedImage> images = new ArrayList<>();
            for (IcoFile icon : icons) {
                try {
                    List<BufferedImage> result = ICODecoder.read(icon.getInputStream());
                    images.addAll(result);
                } catch (IOException e) {
                    LOGGER.error(e);
                }
            }
            return images;
        }

        @Override
        protected void done() {
            if (isCancelled()) {
                return;
            }
            try {
                // get images
                icons = get();

                // init Swing components in the icon panel
                GridLayout grid = new GridLayout(0, 2);
                JPanel gridPanel = new JPanel(grid);
                //gridPanel.setPreferredSize(new Dimension(getWidth(), getHeight()));
                IconPanel.this.removeAll();
                IconPanel.this.add(gridPanel);

                // add all icons to the grid
                for (BufferedImage image : icons) {
                    JLabel iconLabel = new JLabel(new ImageIcon(image));
                    gridPanel.add(iconLabel);
                }
            } catch (InterruptedException | ExecutionException e) {
                e.printStackTrace();
                LOGGER.error(e);
            }
        }
    }
}
