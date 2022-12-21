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
package com.github.struppigel.gui.utils;

import com.github.struppigel.settings.PortexSettings;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import javax.swing.*;
import java.io.IOException;

public class WriteSettingsWorker extends SwingWorker<Boolean, Void> {
    private static final Logger LOGGER = LogManager.getLogger();
    private final PortexSettings settings;

    public WriteSettingsWorker(PortexSettings settings) {
        this.settings = settings;
    }
    @Override
    protected Boolean doInBackground() {
        try {
            settings.writeSettings();
            return true;
        } catch (IOException e) {
        e.printStackTrace();
        LOGGER.error(e);
        }
        return false;
    }
}
