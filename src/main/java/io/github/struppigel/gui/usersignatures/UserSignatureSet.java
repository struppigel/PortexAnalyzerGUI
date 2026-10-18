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
package io.github.struppigel.gui.usersignatures;

import io.github.struppigel.parser.AnalysisInterruptedException;
import io.github.struppigel.parser.PEData;
import io.github.struppigel.tools.sigscanner.SignatureDatabase;
import io.github.struppigel.tools.sigscanner.SignatureMatch;
import io.github.struppigel.tools.sigscanner.SignatureScanner;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * The user's signatures of a folder as they were loaded once. Immutable, so that the workers of several files can
 * share it.
 */
public final class UserSignatureSet {
    private static final Logger LOGGER = LogManager.getLogger();

    /**
     * No folder and no signatures.
     */
    public static final UserSignatureSet NONE = new UserSignatureSet(null, false, 0, SignatureDatabase.empty(), null,
            Collections.emptyList());

    private final File folder;
    private final boolean folderExists;
    private final int fileCount;
    private final SignatureDatabase database;
    // null if there are no signatures
    private final SignatureScanner scanner;
    private final List<String> loadWarnings;
    private final List<String> scanWarnings;

    private UserSignatureSet(File folder, boolean folderExists, int fileCount, SignatureDatabase database,
                             SignatureScanner scanner, List<String> loadWarnings) {
        this.folder = folder;
        this.folderExists = folderExists;
        this.fileCount = fileCount;
        this.database = database;
        this.scanner = scanner;
        this.loadWarnings = Collections.unmodifiableList(new ArrayList<>(loadWarnings));
        this.scanWarnings = scanner == null ? Collections.emptyList()
                : Collections.unmodifiableList(new ArrayList<>(scanner.getWarnings()));
    }

    /**
     * Loads the signature databases of the folder. Malformed entries and files that cannot be read are skipped and
     * listed as warnings. Takes a while for big databases, so it must not run in the event dispatch thread.
     *
     * @param folder the folder with the database files, see {@link SignatureDatabase#listFolder(File)}
     * @return the signatures, empty if the folder does not exist
     */
    public static UserSignatureSet load(File folder) {
        if (!folder.isDirectory()) {
            return new UserSignatureSet(folder, false, 0, SignatureDatabase.empty(), null, Collections.emptyList());
        }
        List<String> warnings = new ArrayList<>();
        try {
            int fileCount = SignatureDatabase.listFolder(folder).size();
            SignatureDatabase database = SignatureDatabase.loadFolder(folder, true);
            SignatureScanner scanner = database.size() == 0 ? null : new SignatureScanner(database);
            return new UserSignatureSet(folder, true, fileCount, database, scanner, database.getWarnings());
        } catch (AnalysisInterruptedException e) {
            throw e;
        } catch (IOException e) {
            LOGGER.warn("Could not read the signature folder " + folder.getAbsolutePath(), e);
            warnings.add("Could not read the signature folder: " + e);
        } catch (RuntimeException e) {
            LOGGER.error("Could not load the signatures of " + folder.getAbsolutePath(), e);
            warnings.add("Could not load the signatures: " + e);
        }
        return new UserSignatureSet(folder, true, 0, SignatureDatabase.empty(), null, warnings);
    }

    /**
     * @return the folder of the signatures, null for {@link #NONE}
     */
    public File getFolder() {
        return folder;
    }

    public boolean folderExists() {
        return folderExists;
    }

    /**
     * @return the number of database files in the folder
     */
    public int getFileCount() {
        return fileCount;
    }

    public SignatureDatabase getDatabase() {
        return database;
    }

    public int getSignatureCount() {
        return database.size();
    }

    public boolean isEmpty() {
        return scanner == null;
    }

    /**
     * @return the entries and files that were skipped while loading, with file, line and column
     */
    public List<String> getLoadWarnings() {
        return loadWarnings;
    }

    /**
     * @return the signatures that are slow to scan
     */
    public List<String> getScanWarnings() {
        return scanWarnings;
    }

    public boolean hasSkippedSignatures() {
        return !loadWarnings.isEmpty();
    }

    /**
     * Scans all locations of a PE file, including the location file, which the bundled signatures skip.
     */
    public List<SignatureMatch> scan(PEData data) {
        return isEmpty() ? Collections.emptyList() : scanner.scan(data);
    }

    /**
     * Scans a file that is no PE file or cannot be parsed as one, only the locations file and eof.
     */
    public List<SignatureMatch> scanFile(File file) {
        return isEmpty() ? Collections.emptyList() : scanner.scanFile(file);
    }

    /**
     * @return the database file of the signature of the match, e.g. "mysignatures.sig"
     */
    public static String sourceOf(SignatureMatch match) {
        return match.getSignature().getDatabaseName();
    }

    /**
     * @return the name of the signature and its database file, e.g. "My packer from mysignatures.sig"
     */
    public static String label(SignatureMatch match) {
        return match.getName() + " from " + sourceOf(match);
    }
}
