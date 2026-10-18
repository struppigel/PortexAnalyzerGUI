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

import java.io.File;
import java.io.IOException;
import java.net.URISyntaxException;
import java.nio.file.Files;

/**
 * Chooses the folder of the log file. Must run before the first logger is created and therefore must not use log4j.
 */
public final class LogSetup {

    /** the system property that log4j2.xml reads the folder of the log file from */
    static final String DIRECTORY_PROPERTY = "portex.log.dir";
    static final String FILE_NAME = "portexanalyzer.log";

    private LogSetup() {
    }

    /**
     * Sets the folder of the log file to the folder of the application, or to the temp folder if the application
     * folder is not writable. A folder that is set already, e.g. with -Dportex.log.dir, stays.
     */
    public static void init() {
        if (System.getProperty(DIRECTORY_PROPERTY) == null) {
            System.setProperty(DIRECTORY_PROPERTY, chooseDirectory().getAbsolutePath());
        }
    }

    /**
     * @return the log file, which log4j overwrites at every start
     */
    public static File logFile() {
        String directory = System.getProperty(DIRECTORY_PROPERTY);
        return new File(directory != null ? directory : System.getProperty("java.io.tmpdir"), FILE_NAME);
    }

    static File chooseDirectory() {
        File application = applicationFolder();
        if (application != null && isWritable(application)) {
            return application;
        }
        return new File(System.getProperty("java.io.tmpdir"));
    }

    private static File applicationFolder() {
        try {
            File location = new File(LogSetup.class.getProtectionDomain().getCodeSource().getLocation().toURI())
                    .getAbsoluteFile();
            return location.isDirectory() ? location : location.getParentFile();
        } catch (URISyntaxException | RuntimeException e) {
            return null;
        }
    }

    /** Tries to create a file, because the permission check of the file system is unreliable, e.g. in Program Files. */
    private static boolean isWritable(File directory) {
        try {
            File probe = Files.createTempFile(directory.toPath(), "portex", ".tmp").toFile();
            return probe.delete() || !probe.exists();
        } catch (IOException | RuntimeException e) {
            return false;
        }
    }
}
