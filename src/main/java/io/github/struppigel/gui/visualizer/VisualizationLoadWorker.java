/**
 * *****************************************************************************
 * Copyright 2026 Karsten Hahn
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
package io.github.struppigel.gui.visualizer;

import io.github.struppigel.tools.visualizer.engine.ByteStatistics;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import javax.swing.*;
import java.util.List;
import java.util.concurrent.ExecutionException;

/**
 * Computes the byte statistics of a file in the background and tells the model about the progress, so that the
 * entropy and byte plot fill in while the statistics are computed.
 */
class VisualizationLoadWorker extends SwingWorker<Void, Long> {

    private static final Logger LOGGER = LogManager.getLogger();
    private static final long PUBLISH_INTERVAL_MS = 250;

    private final VisualizationModel model;
    private final ByteStatistics statistics;
    private final int generation;
    private long lastPublish = 0;

    VisualizationLoadWorker(VisualizationModel model, ByteStatistics statistics, int generation) {
        this.model = model;
        this.statistics = statistics;
        this.generation = generation;
    }

    @Override
    protected Void doInBackground() throws Exception {
        statistics.compute((done, total) -> {
            long now = System.currentTimeMillis();
            if (now - lastPublish >= PUBLISH_INTERVAL_MS) {
                lastPublish = now;
                publish(done);
            }
        });
        return null;
    }

    @Override
    protected void process(List<Long> chunks) {
        if (!isCancelled()) {
            model.statisticsProgressed(generation);
        }
    }

    @Override
    protected void done() {
        if (isCancelled()) {
            return;
        }
        try {
            get();
        } catch (InterruptedException | ExecutionException e) {
            LOGGER.error("Could not compute the byte statistics: " + e.getMessage());
        }
        model.statisticsProgressed(generation);
    }
}
