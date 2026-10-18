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
package io.github.struppigel.gui;

import io.github.struppigel.gui.usersignatures.UserSignatureMatches;
import io.github.struppigel.tools.layout.StructureMap;
import io.github.struppigel.tools.sigscanner.SignatureMatch;

import java.io.File;
import java.util.List;
import java.util.Optional;

/**
 * The data that every loaded file has, also files that are no PE files or cannot be parsed as one. Computed by a
 * worker, so that the event dispatch thread only displays it. PE files are {@link FullPEData}.
 */
public class FileData {
    private final File file;
    private final String hashes;
    private final List<SignatureMatch> signatureMatches;
    private final StructureMap structureMap;
    private final String peParseError;
    private final UserSignatureMatches userSignatures;

    /**
     * Data without matches of the user's signatures.
     *
     * @param peParseError why the file could not be parsed as PE file although it has a PE signature, null if it
     *                     was parsed or has no PE signature
     */
    public FileData(File file, String hashes, List<SignatureMatch> signatureMatches, StructureMap structureMap,
                    String peParseError) {
        this(file, hashes, signatureMatches, structureMap, peParseError, UserSignatureMatches.NONE);
    }

    /**
     * @param signatureMatches the matches of the bundled signatures
     * @param peParseError why the file could not be parsed as PE file although it has a PE signature, null if it
     *                     was parsed or has no PE signature
     * @param userSignatures the matches of the user's signatures
     */
    public FileData(File file, String hashes, List<SignatureMatch> signatureMatches, StructureMap structureMap,
                    String peParseError, UserSignatureMatches userSignatures) {
        this.file = file;
        this.hashes = hashes;
        this.signatureMatches = signatureMatches;
        this.structureMap = structureMap;
        this.peParseError = peParseError;
        this.userSignatures = userSignatures;
    }

    public File getFile() {
        return file;
    }

    public String getHashesReport() {
        return hashes;
    }

    /**
     * @return the matches of the bundled signatures; for PE files in all default locations, otherwise only at the
     *         start and end of the file
     */
    public List<SignatureMatch> getSignatureMatches() {
        return signatureMatches;
    }

    /**
     * @return the user's signatures that were scanned with, their warnings and matches
     */
    public UserSignatureMatches getUserSignatures() {
        return userSignatures;
    }

    /**
     * @return the matches of the user's signatures; for PE files in all locations, otherwise only at the start and
     *         end of the file
     */
    public List<SignatureMatch> getUserSignatureMatches() {
        return userSignatures.getMatches();
    }

    /**
     * @return the structures of the file by offset; built while loading, immutable and safe to use in any thread
     */
    public StructureMap getStructureMap() {
        return structureMap;
    }

    /**
     * @return why the file with PE signature could not be parsed, so that it is shown without PE structures
     */
    public Optional<String> getPeParseError() {
        return Optional.ofNullable(peParseError);
    }

    /**
     * @return the PE data, empty if the file is no PE file or could not be parsed as one
     */
    public Optional<FullPEData> asPE() {
        return Optional.empty();
    }

    public final boolean isPE() {
        return asPE().isPresent();
    }
}
