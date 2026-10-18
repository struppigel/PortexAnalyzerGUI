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
package io.github.struppigel.gui.utils;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import javax.swing.*;
import java.awt.*;

/**
 * Logs exceptions that nothing else handles and shows the log, so that the user can report them.
 */
public final class UnhandledErrors {

    private static final Logger LOGGER = LogManager.getLogger();
    private static JDialog dialog;
    private static LogViewerPanel panel;

    private UnhandledErrors() {
    }

    /**
     * Makes exceptions of all threads, including the Swing event thread, that nothing else handles open the log.
     */
    public static void install() {
        Thread.setDefaultUncaughtExceptionHandler((thread, error) -> {
            try {
                LOGGER.fatal("Unhandled exception in thread " + thread.getName(), error);
                if (!GraphicsEnvironment.isHeadless()) {
                    SwingUtilities.invokeLater(() -> showLog("An unexpected error occurred: " + error));
                }
            } catch (Throwable ignored) {
                // an error while reporting an error must not end in a loop
            }
        });
    }

    /**
     * Shows the log in a window. An open window is reused. Call on the Swing event thread.
     */
    static void showLog(String summary) {
        if (dialog != null && dialog.isDisplayable()) {
            panel.setSummary(summary);
            panel.refresh();
            dialog.toFront();
            return;
        }
        panel = new LogViewerPanel(LogSetup.logFile(), summary);
        dialog = new JDialog((Frame) null, "Unexpected error", false);
        dialog.setContentPane(panel);
        dialog.setSize(900, 600);
        dialog.setLocationRelativeTo(null);
        dialog.setDefaultCloseOperation(WindowConstants.DISPOSE_ON_CLOSE);
        dialog.setVisible(true);
    }
}
