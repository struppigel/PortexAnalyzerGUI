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

import io.github.struppigel.tools.visualizer.engine.ByteSourceClosedException;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import javax.swing.*;
import java.util.HashSet;
import java.util.Set;
import java.util.concurrent.Executor;
import java.util.function.Consumer;
import java.util.function.Predicate;
import java.util.function.Supplier;

/**
 * Renders tiles in the background. Every tile is requested at most once at a time, and tiles that are no longer
 * visible when their turn comes are skipped. Requests are made and results delivered in the event dispatch thread.
 */
final class TileScheduler {

    private static final Logger LOGGER = LogManager.getLogger();

    private final Executor executor;
    private final Set<TileCache.Key> pending = new HashSet<>();

    TileScheduler(Executor executor) {
        this.executor = executor;
    }

    /**
     * @param key the tile
     * @param renderer renders the tile in a background thread, may return null
     * @param stillWanted tested in the background thread before rendering, must be thread-safe
     * @param done receives the rendered tile in the event dispatch thread
     */
    void request(TileCache.Key key, Supplier<TileCache.Tile> renderer, Predicate<TileCache.Key> stillWanted,
                 Consumer<TileCache.Tile> done) {
        if (!pending.add(key)) {
            return;
        }
        executor.execute(() -> {
            TileCache.Tile tile = null;
            try {
                if (stillWanted.test(key)) {
                    tile = renderer.get();
                }
            } catch (ByteSourceClosedException e) {
                // the file was closed because another one was loaded
            } catch (Throwable t) {
                LOGGER.error("Could not render a tile of the visualization", t);
            }
            TileCache.Tile result = tile;
            Runnable finish = () -> {
                pending.remove(key);
                if (result != null) {
                    done.accept(result);
                }
            };
            if (SwingUtilities.isEventDispatchThread()) {
                finish.run();
            } else {
                SwingUtilities.invokeLater(finish);
            }
        });
    }

    /**
     * Forgets the pending requests, e.g., because another file was loaded. Their results are still delivered.
     */
    void clear() {
        pending.clear();
    }

    int pendingCount() {
        return pending.size();
    }
}
