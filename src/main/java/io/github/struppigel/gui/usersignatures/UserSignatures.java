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

import io.github.struppigel.parser.Interruption;
import io.github.struppigel.tools.sigscanner.SignatureDatabase;

import java.io.File;
import java.io.IOException;
import java.nio.file.NoSuchFileException;
import java.nio.file.NotDirectoryException;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * The user's signatures of a folder. They are loaded on first use and loaded again when a database file of the
 * folder was added, removed or changed. Thread-safe.
 */
public final class UserSignatures {
    private volatile File folder;
    // incremented to load the signatures again, also if they are being loaded right now
    private final AtomicInteger generation = new AtomicInteger();

    // guarded by this
    private UserSignatureSet current;
    private String currentStamp;
    private int currentGeneration;

    /**
     * @param folder the folder of the database files, null for no signatures
     */
    public UserSignatures(File folder) {
        this.folder = folder;
    }

    /**
     * @return user signatures that never load anything
     */
    public static UserSignatures none() {
        return new UserSignatures(null);
    }

    /**
     * @return the folder of the database files, null for no signatures
     */
    public File getFolder() {
        return folder;
    }

    /**
     * Uses another folder from the next call of {@link #current()} on. Fast, so it may be called in the event
     * dispatch thread.
     */
    public void setFolder(File folder) {
        this.folder = folder;
        invalidate();
    }

    /**
     * Loads the signatures again on the next call of {@link #current()}, even if no file changed. Fast, so it may be
     * called in the event dispatch thread.
     */
    public void invalidate() {
        generation.incrementAndGet();
    }

    /**
     * Returns the signatures and loads them first if this was not done yet, if a database file was added, removed or
     * changed, or if they were invalidated. Must not run in the event dispatch thread, because loading takes a while.
     * An interrupted load keeps nothing, so that the next call loads again.
     *
     * @return the current signatures
     */
    public synchronized UserSignatureSet current() {
        File dir = folder;
        if (dir == null) {
            return UserSignatureSet.NONE;
        }
        // computed before loading, so that files that change while loading cause another load next time
        int gen = generation.get();
        String stamp = stampOf(dir);
        if (current == null || gen != currentGeneration || !stamp.equals(currentStamp)) {
            UserSignatureSet loaded = UserSignatureSet.load(dir);
            Interruption.checkInterrupt();
            current = loaded;
            currentStamp = stamp;
            currentGeneration = gen;
        }
        return current;
    }

    /**
     * Loads the signatures again, even if no file changed. Must not run in the event dispatch thread.
     *
     * @return the loaded signatures
     */
    public UserSignatureSet reload() {
        invalidate();
        return current();
    }

    /**
     * @return a text that changes when the folder or one of its database files is added, removed or changed
     */
    static String stampOf(File folder) {
        StringBuilder stamp = new StringBuilder(folder.getAbsolutePath());
        try {
            for (File file : SignatureDatabase.listFolder(folder)) {
                stamp.append('\n').append(file.getName()).append('|').append(file.length())
                        .append('|').append(file.lastModified());
            }
        } catch (NoSuchFileException | NotDirectoryException e) {
            stamp.append("\nmissing");
        } catch (IOException e) {
            stamp.append("\nerror: ").append(e.getMessage());
        }
        return stamp.toString();
    }
}
