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
package io.github.struppigel.settings;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import javax.swing.*;
import java.io.*;
import java.net.URISyntaxException;
import java.net.URL;
import java.nio.file.Paths;
import java.util.HashMap;
import java.util.Map;

import static io.github.struppigel.settings.PortexSettingsKey.*;

public class PortexSettings extends HashMap<PortexSettingsKey, String> {

    private static final Logger LOGGER = LogManager.getLogger();
    private static final String SETTINGS_FILE_NAME = "settings.ini";
    private static final String USER_SIGNATURES_FOLDER_NAME = "signatures";
    private static final String SETTINGS_DELIMITER = ":::";
    private final File settingsFile;

    public PortexSettings() {
        this(defaultSettingsFile());
    }

    /**
     * Uses the given settings file instead of the one next to the application.
     *
     * @param settingsFile the file to read the settings from and write them to
     */
    public PortexSettings(File settingsFile) {
        this.settingsFile = settingsFile;
        loadSettings();
    }

    private static File defaultSettingsFile() {
        URL appPath = PortexSettings.class.getProtectionDomain().getCodeSource().getLocation();
        try {
            File app = new File(appPath.toURI());
            File folder = app.getAbsoluteFile();
            if(!folder.isDirectory()) { // different behaviour Win vs Linux here
                folder = folder.getParentFile();
            }
            return Paths.get(folder.getAbsolutePath(), SETTINGS_FILE_NAME).toFile();
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
                                // e.g., the keys of features that were removed
                                LOGGER.info("Ignoring unknown settings key " + pair[0].trim());
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
        applySettingIfNotSet(DISABLE_UPDATE, "0");
        applySettingIfNotSet(LOOK_AND_FEEL, LookAndFeelSetting.PORTEX.toString());
        applySettingIfNotSet(VALUES_AS_HEX, "1");
        applySettingIfNotSet(CONTENT_PREVIEW, "1");
        applySettingIfNotSet(HEX_BYTES_PER_LINE, "0");
        // empty, so that the folder moves with the application
        applySettingIfNotSet(USER_SIGNATURES_FOLDER, "");
    }

    private void applySettingIfNotSet(PortexSettingsKey key, String value) {
        if(!this.containsKey(key)) {
            this.put(key, value);
        }
    }

    public boolean valueEquals(PortexSettingsKey key, String value) {
        return this.containsKey(key) && this.get(key).equals(value);
    }

    /**
     * @return the folder "signatures" next to the settings file, which holds the user's signatures unless another
     *         folder is set
     */
    public File defaultUserSignaturesFolder() {
        return new File(settingsFile.getAbsoluteFile().getParentFile(), USER_SIGNATURES_FOLDER_NAME);
    }

    /**
     * @return the folder of the user's signature databases: the folder that is set, relative to the folder of the
     *         settings file, or the default folder if none is set
     */
    public File userSignaturesFolder() {
        String value = getOrDefault(USER_SIGNATURES_FOLDER, "").trim();
        if (value.isEmpty()) {
            return defaultUserSignaturesFolder();
        }
        File folder = new File(value);
        return folder.isAbsolute() ? folder : new File(settingsFile.getAbsoluteFile().getParentFile(), value);
    }

    public void writeSettings() throws IOException {
        HashMap<PortexSettingsKey, String> map = new HashMap<>(this); // shallow copy
        new SettingsWriter(settingsFile, map).execute();
    }

    /**
     * Writes the settings synchronously, unlike {@link #writeSettings()}.
     */
    void writeSettingsNow() throws IOException {
        write(settingsFile, new HashMap<>(this));
    }

    private static void write(File settingsFile, Map<PortexSettingsKey, String> map) throws IOException {
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
            write(settingsFile, map);
            return null;
        }
    }
}
