/**
 * *****************************************************************************
 * Copyright 2022 Karsten Hahn
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *   http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 * ****************************************************************************
 */
package com.github.struppigel.gui;

import com.github.katjahahn.tools.visualizer.ImageUtil;
import com.github.katjahahn.tools.visualizer.Visualizer;
import com.github.katjahahn.tools.visualizer.VisualizerBuilder;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import javax.swing.*;
import java.awt.*;
import java.awt.event.ComponentAdapter;
import java.awt.event.ComponentEvent;
import java.awt.image.BufferedImage;
import java.io.File;
import java.io.IOException;
import java.util.concurrent.ExecutionException;

public class VisualizerPanel extends JPanel {

    private static final Logger LOGGER = LogManager.getLogger();

    private final JLabel visLabel = new JLabel();
    private File pefile;

    private long timeSinceLastEventManaged = 0;
    private final static long MINIMUM_TIME = 100;

    private boolean enableLegend = false;
    private boolean enableByteplot = true;
    private boolean enableEntropy = true;

    private int imageWidth = 50;

    public VisualizerPanel() {
        super();
        initPanel();
    }

    public VisualizerPanel(boolean enableLegend, boolean enableByteplot, boolean enableEntropy, int imageWidth) {
        super();
        this.enableByteplot = enableByteplot;
        this.enableEntropy = enableEntropy;
        this.enableLegend = enableLegend;
        this.imageWidth = imageWidth;
        initPanel();
    }

    private void initPanel(){
        setLayout(new BorderLayout());
        add(visLabel, BorderLayout.CENTER);
        addComponentListener(new ResizeListener());

       /*JToolBar toolBar = new JToolBar();
        ImageIcon ico = new ImageIcon(getClass().getResource("/science-icon.png"));
        toolBar.add(new JToggleButton(ico));

        add(toolBar, BorderLayout.PAGE_START);*/
    }

    public void visualizePE(File pefile) throws IOException {
        this.pefile = pefile;
        new VisualizerWorker(getHeight(), imageWidth, pefile, enableEntropy, enableByteplot, enableLegend).execute();
    }

    private class VisualizerWorker extends SwingWorker<BufferedImage, Void> {

        private final int height;
        private final File file;
        private final boolean showEntropy;
        private final boolean showByteplot;
        private final boolean showLegend;
        private final int width;

        public VisualizerWorker(int height, int width, File file, boolean showEntropy, boolean showByteplot, boolean showLegend) {
            this.height = height;
            this.width = width;
            this.file = file;
            this.showEntropy = showEntropy;
            this.showByteplot = showByteplot;
            this.showLegend = showLegend;
        }

        @Override
        protected BufferedImage doInBackground() throws Exception {
            if(pefile == null) return null;
            Visualizer visualizer = new VisualizerBuilder()
                    .setHeight(height)
                    .setFileWidth(width)
                    .build();
            BufferedImage peImage = visualizer.createImage(file);

            if(showEntropy){
                BufferedImage entropyImg = visualizer.createEntropyImage(file);
                peImage = ImageUtil.appendImages(entropyImg, peImage);
            }
            if(showByteplot) {
                BufferedImage bytePlot = visualizer.createBytePlot(file);
                peImage = ImageUtil.appendImages(bytePlot, peImage);
            }
            if(showLegend) {
                BufferedImage legendImage = visualizer.createLegendImage(showByteplot, showEntropy, true);
                peImage = ImageUtil.appendImages(peImage, legendImage);
            }
            return peImage;
        }

        @Override
        protected void done() {
            try {
                BufferedImage image = get();
                if(image == null) return;
                visLabel.setIcon(new ImageIcon(image));
                visLabel.repaint();
            } catch (InterruptedException e) {
                String message = "Problem with visualizer! Reason: " + e.getMessage();
                LOGGER.error(message);
                e.printStackTrace(); // no dialog necessary
            } catch (ExecutionException e) {
                LOGGER.error(e);
                e.printStackTrace();
            }
        }
    }

    private class ResizeListener extends ComponentAdapter {
        public void componentResized(ComponentEvent e) {
            long timeSystem = System.currentTimeMillis();
            if(timeSystem - timeSinceLastEventManaged > MINIMUM_TIME) {
                try {
                    timeSinceLastEventManaged  = timeSystem;
                    new VisualizerWorker(getHeight(), imageWidth, pefile, enableEntropy, enableByteplot, enableLegend).execute();
                } catch (Exception ex) {
                    LOGGER.error("Visualization update failed " + ex.getMessage());
                    throw new RuntimeException(ex);
                }
            }
        }
    }


    public void setEnableLegend(boolean enableLegend) {
        this.enableLegend = enableLegend;
    }

    public void setEnableByteplot(boolean enableByteplot) {
        this.enableByteplot = enableByteplot;
    }

    public void setEnableEntropy(boolean enableEntropy) {
        this.enableEntropy = enableEntropy;
    }

    public void setImageWidth(int imageWidth) {
        this.imageWidth = imageWidth;
    }

}
