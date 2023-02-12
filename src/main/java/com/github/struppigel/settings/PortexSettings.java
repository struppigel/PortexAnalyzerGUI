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
package com.github.struppigel.settings;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import javax.swing.*;
import java.io.*;
import java.net.URISyntaxException;
import java.net.URL;
import java.nio.file.Paths;
import java.util.HashMap;
import java.util.Map;

import static com.github.struppigel.settings.PortexSettingsKey.*;

public class PortexSettings extends HashMap<PortexSettingsKey, String> {

    private static final Logger LOGGER = LogManager.getLogger();
    private final URL appPath = this.getClass().getProtectionDomain().getCodeSource().getLocation();
    private static final String SETTINGS_FILE_NAME = "settings.ini";
    private static final String SETTINGS_DELIMITER = ":::";
    private File settingsFile;

    public PortexSettings() {
        try {
            File app = new File(appPath.toURI());
            File folder = app.getAbsoluteFile();
            if(!folder.isDirectory()) { // different behaviour Win vs Linux here
                folder = folder.getParentFile();
            }
            settingsFile = Paths.get(folder.getAbsolutePath(), SETTINGS_FILE_NAME).toFile();
            loadSettings();
        } catch (URISyntaxException e) {
            LOGGER.fatal(e); // must never happen
            throw new RuntimeException(e);
        }
    }

    private void loadSettings() {

        this.clear();
        try {
            if (settingsFile.exists() && settingsFile.isFile()) {
                try (BufferedReader reader = new BufferedReader(new FileReader(settingsFile))) {
                    String line;
                    while ((line = reader.readLine()) != null) {
                        String[] pair = line.split(SETTINGS_DELIMITER);
                        if (pair.length >= 2) {
                            try {
                                PortexSettingsKey key = PortexSettingsKey.valueOf(pair[0].trim());
                                String value = pair[1];
                                this.put(key, value);
                            } catch (IllegalArgumentException e) {
                                LOGGER.warn("Non-existing settings key read! Will be ignored " + e);
                                e.printStackTrace();
                            }
                        }
                    }
                }
                LOGGER.info("Settings read successfully from " + settingsFile.getAbsolutePath());
            }
        } catch (IOException e) {
            e.printStackTrace();
            LOGGER.error(e);
        }

        applyDefaults();
    }

    private void applyDefaults() {
        // apply defaults
        applySettingIfNotSet(DISABLE_YARA_WARNINGS, "0");
        applySettingIfNotSet(DISABLE_UPDATE, "0");
        applySettingIfNotSet(LOOK_AND_FEEL, LookAndFeelSetting.PORTEX.toString());
        applySettingIfNotSet(VALUES_AS_HEX, "1");
        applySettingIfNotSet(CONTENT_PREVIEW, "1");
    }

    private void applySettingIfNotSet(PortexSettingsKey key, String value) {
        if(!this.containsKey(key)) {
            this.put(key, value);
        }
    }

    public boolean valueEquals(PortexSettingsKey key, String value) {
        return this.containsKey(key) && this.get(key).equals(value);
    }

    public void writeSettings() throws IOException {
        HashMap<PortexSettingsKey, String> map = new HashMap<>(this); // shallow copy
        new SettingsWriter(settingsFile, map).execute();
    }

    private static class SettingsWriter extends SwingWorker<Void, Void> {

        private final File settingsFile;
        private final HashMap<PortexSettingsKey, String> map;

        public SettingsWriter(File settingsFile, HashMap<PortexSettingsKey, String> map) {
            this.settingsFile = settingsFile;
            this.map = map;
        }

        @Override
        protected Void doInBackground() throws Exception {
            try {
                settingsFile.delete();
                settingsFile.createNewFile();
                try (PrintStream out = new PrintStream(new FileOutputStream(settingsFile))) {
                    for (Map.Entry<PortexSettingsKey, String> entry : map.entrySet()) {
                        out.println(entry.getKey() + SETTINGS_DELIMITER + entry.getValue().trim());
                    }
                    LOGGER.info("Settings written to " + settingsFile.getAbsolutePath());
                }
            } catch (IOException e) {
                LOGGER.error("problem with writing " + settingsFile);
                throw e;
            }
            return null;
        }
    }
}
