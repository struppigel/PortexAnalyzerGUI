/**
 * *****************************************************************************
 * Copyright 2022 Karsten Hahn
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *   <a href="http://www.apache.org/licenses/LICENSE-2.0">http://www.apache.org/licenses/LICENSE-2.0</a>
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
import com.github.struppigel.gui.utils.WorkerKiller;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import javax.swing.*;
import java.awt.*;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.awt.event.ComponentAdapter;
import java.awt.event.ComponentEvent;
import java.awt.image.BufferedImage;
import java.io.File;
import java.io.IOException;
import java.util.concurrent.ExecutionException;

/**
 * Panel with byteplot, entropy and PE structure image
 */
public class VisualizerPanel extends JPanel {

    private static final Logger LOGGER = LogManager.getLogger();

    private javax.swing.Timer waitingTimer;

    private final JLabel visLabel = new JLabel();
    private File pefile;

    private final static int RESIZE_DELAY = 1000;
    private JProgressBar scanRunningBar = new JProgressBar();

    private boolean enableLegend = false;
    private boolean enableByteplot = true;
    private boolean enableEntropy = true;

    private int imageWidth = 50;
    private BufferedImage image;

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
        this.removeAll();
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
        SwingWorker worker = new VisualizerWorker(getHeight(), imageWidth, pefile, enableEntropy, enableByteplot, enableLegend);
        WorkerKiller.getInstance().addWorker(worker);
        worker.execute();
        initProgressBar();
    }

    private void initProgressBar() {
        this.removeAll();
        this.add(scanRunningBar);
        this.setLayout(new FlowLayout());
        scanRunningBar.setIndeterminate(true);
        scanRunningBar.setVisible(true);
        this.repaint();
    }

    public BufferedImage getImage() {
        return image;
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
            if(isCancelled()){return;}
            try {
                image = get();
                if(image == null) return;
                initPanel();
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

    private class ResizeListener extends ComponentAdapter implements ActionListener {
        public void componentResized(ComponentEvent e) {
            if(waitingTimer == null) {
                try {
                    waitingTimer = new Timer(RESIZE_DELAY, this);
                    waitingTimer.start();
                } catch (Exception ex) {
                    LOGGER.error("Visualization update failed " + ex.getMessage());
                    throw new RuntimeException(ex);
                }
            } else {
                waitingTimer.restart();
            }
        }

        public void actionPerformed(ActionEvent ae)
        {
            /* Timer finished? */
            if (ae.getSource()==waitingTimer)
            {
                /* Stop timer */
                waitingTimer.stop();
                waitingTimer = null;
                /* Resize */
                applyResize();
            }
        }

        private void applyResize() {
            SwingWorker worker = new VisualizerWorker(getHeight(), imageWidth, pefile, enableEntropy, enableByteplot, enableLegend);
            WorkerKiller.getInstance().addWorker(worker);
            worker.execute();
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
