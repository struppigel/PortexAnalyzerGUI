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
import io.github.struppigel.tools.sigscanner.LocationKind;
import io.github.struppigel.tools.sigscanner.SignatureMatch;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import java.io.File;
import java.nio.channels.ClosedByInterruptException;
import java.util.Collections;
import java.util.List;
import java.util.Optional;
import java.util.function.Supplier;
import java.util.stream.Collectors;

/**
 * The matches of the user's signatures in one file.
 */
public final class UserSignatureMatches {
    private static final Logger LOGGER = LogManager.getLogger();

    /**
     * No signatures and no matches.
     */
    public static final UserSignatureMatches NONE = new UserSignatureMatches(UserSignatureSet.NONE,
            Collections.emptyList(), null);

    private final UserSignatureSet signatures;
    private final List<SignatureMatch> matches;
    private final String error;

    private UserSignatureMatches(UserSignatureSet signatures, List<SignatureMatch> matches, String error) {
        this.signatures = signatures;
        this.matches = Collections.unmodifiableList(matches);
        this.error = error;
    }

    /**
     * Scans all locations of a PE file. Must run in the thread that loads the file, because the caches of PEData are
     * not thread-safe.
     */
    public static UserSignatureMatches scan(UserSignatureSet signatures, PEData data) {
        return scan(signatures, () -> signatures.scan(data));
    }

    /**
     * Scans a file that is no PE file or cannot be parsed as one, only the locations file and eof.
     */
    public static UserSignatureMatches scanFile(UserSignatureSet signatures, File file) {
        return scan(signatures, () -> signatures.scanFile(file));
    }

    /**
     * A scan that fails is reported as error of the matches, so that the file is still shown.
     */
    private static UserSignatureMatches scan(UserSignatureSet signatures, Supplier<List<SignatureMatch>> scan) {
        if (signatures.isEmpty()) {
            return new UserSignatureMatches(signatures, Collections.emptyList(), null);
        }
        try {
            return new UserSignatureMatches(signatures, scan.get(), null);
        } catch (AnalysisInterruptedException e) {
            throw e;
        } catch (Exception e) {
            if (e instanceof ClosedByInterruptException || Thread.currentThread().isInterrupted()) {
                throw new AnalysisInterruptedException();
            }
            LOGGER.error("Could not scan with the signatures of " + signatures.getFolder(), e);
            return new UserSignatureMatches(signatures, Collections.emptyList(),
                    "Could not scan with your signatures: " + e);
        }
    }

    /**
     * @return the signatures that were scanned with
     */
    public UserSignatureSet getSignatures() {
        return signatures;
    }

    /**
     * @return the matches, ordered by location kind, region, offset and database order
     */
    public List<SignatureMatch> getMatches() {
        return matches;
    }

    public List<SignatureMatch> getMatches(LocationKind kind) {
        return matches.stream().filter(m -> m.getLocationKind() == kind).collect(Collectors.toList());
    }

    /**
     * @return why the scan failed, empty if it succeeded
     */
    public Optional<String> getError() {
        return Optional.ofNullable(error);
    }
}
