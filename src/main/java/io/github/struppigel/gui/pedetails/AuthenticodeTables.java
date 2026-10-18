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
package io.github.struppigel.gui.pedetails;

import io.github.struppigel.gui.utils.InfoCard;
import io.github.struppigel.parser.PhysicalLocation;
import io.github.struppigel.parser.sections.cert.AuthenticodeCertificate;
import io.github.struppigel.parser.sections.cert.AuthenticodeSignature;
import io.github.struppigel.parser.sections.cert.AuthenticodeVerification;
import io.github.struppigel.parser.sections.cert.CertificateChain;
import io.github.struppigel.parser.sections.cert.CertificateIssue;
import io.github.struppigel.parser.sections.cert.CertificateTable;
import io.github.struppigel.parser.sections.cert.CertificateVerification;
import io.github.struppigel.parser.sections.cert.PageHashes;
import io.github.struppigel.parser.sections.cert.SignerInfo;
import io.github.struppigel.parser.sections.cert.Timestamp;
import io.github.struppigel.parser.sections.cert.TimestampVerification;
import io.github.struppigel.parser.sections.cert.VerificationResult;
import io.github.struppigel.parser.sections.cert.WinCertificate;

import java.time.Instant;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;

/**
 * The texts and table rows of the Authenticode view, built from the parsed certificate table and the result of its
 * verification. Offsets are Long, so that the tables show them as hex and navigate to them; rows without an offset
 * have an empty string there.
 */
final class AuthenticodeTables {

    static final String[] SIGNATURE_HEADER = {"Description", "Value", "File offset"};
    static final int SIGNATURE_OFFSET_COLUMN = 2;
    static final String[] CERTIFICATE_HEADER = {"Subject", "Issuer", "Valid from", "Valid to", "Key", "Status",
            "Source", "Serial", "SHA-1", "SHA-256", "File offset"};
    static final int CERTIFICATE_OFFSET_COLUMN = 10;
    static final String[] ENTRY_HEADER = {"Entry", "Offset", "Length", "Revision", "Certificate type", "Content"};
    static final int ENTRY_OFFSET_COLUMN = 1;
    static final String[] ISSUE_HEADER = {"Description", "Type", "File offset"};
    static final int ISSUE_OFFSET_COLUMN = 2;

    private static final String NL = "\n";
    private static final String NO_OFFSET = "";
    private static final String UNPARSABLE = "INVALID (the signature can not be parsed)";
    private static final DateTimeFormatter TIME_FORMAT =
            DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss 'UTC'").withZone(ZoneOffset.UTC);

    private AuthenticodeTables() {
    }

    /**
     * @return the overview above the tabs: integrity, table, signatures and the primary signer
     */
    static String summary(CertificateTable table, Optional<AuthenticodeVerification> verification,
                          boolean hexEnabled) {
        StringBuilder text = new StringBuilder();
        text.append("Integrity: ").append(integrity(table, verification));
        if (table.getPrimarySignature().isPresent() && verification.isPresent()) {
            text.append(" (integrity only, no trust decision)");
        }
        text.append(NL);
        int entries = table.getEntries().size();
        text.append("Certificate table: offset ").append(number(table.getOffset(), hexEnabled))
                .append(", size ").append(number(table.getDeclaredSize(), hexEnabled))
                .append(", ").append(entries).append(entries == 1 ? " entry" : " entries");
        if (table.isTruncated()) {
            text.append(", truncated to ").append(number(table.getReadSize(), hexEnabled));
        }
        text.append(NL);
        List<AuthenticodeSignature> signatures = table.getAllSignatures();
        long nested = signatures.stream().filter(s -> s.getNestingLevel() > 0).count();
        text.append("Signatures: ").append(signatures.size());
        if (nested > 0) {
            text.append(" (").append(nested).append(" nested)");
        }
        text.append(NL);
        Optional<SignerInfo> signer = primarySigner(table);
        if (signer.isPresent()) {
            text.append("Signer: ").append(signerName(signer.get())).append(NL);
            Optional<Instant> time = signer.get().getEffectiveSigningTime();
            if (time.isPresent()) {
                text.append("Signing time: ").append(time(time)).append(NL);
            }
        }
        text.append("Issues: ").append(issueRows(table, verification).size()).append(NL);
        return text.toString();
    }

    /**
     * @return one line for the summary of the file, e.g., "Authenticode: VALID, signed by Microsoft Corporation"
     */
    static String statusLine(Optional<CertificateTable> table, Optional<AuthenticodeVerification> verification) {
        if (!table.isPresent()) {
            return "Authenticode: Not signed";
        }
        String line = "Authenticode: " + integrity(table.get(), verification);
        Optional<SignerInfo> signer = primarySigner(table.get());
        if (signer.isPresent()) {
            line += ", signed by " + signerName(signer.get());
        }
        return line;
    }

    /**
     * @param status the integrity as shown in the summary, e.g., "VALID, signed by Microsoft" or "Not signed"
     * @return how to color a badge with the status
     */
    static InfoCard.Kind kindOf(String status) {
        if (status.startsWith("VALID")) {
            return InfoCard.Kind.GOOD;
        } else if (status.startsWith("INVALID")) {
            return InfoCard.Kind.BAD;
        } else if (status.startsWith("UNVERIFIABLE")) {
            return InfoCard.Kind.WARN;
        }
        return InfoCard.Kind.NEUTRAL;
    }

    /**
     * Windows verifies only the primary signature, so that one decides the integrity of the file.
     */
    private static String integrity(CertificateTable table, Optional<AuthenticodeVerification> verification) {
        if (!table.getPrimarySignature().isPresent()) {
            return UNPARSABLE;
        }
        return verification.isPresent() ? verification.get().getStatus().toString() : "not verified";
    }

    private static Optional<SignerInfo> primarySigner(CertificateTable table) {
        Optional<AuthenticodeSignature> primary = table.getPrimarySignature();
        return primary.isPresent() ? primary.get().getSigner() : Optional.empty();
    }

    private static String signerName(SignerInfo signer) {
        Optional<AuthenticodeCertificate> certificate = signer.getSignerCertificate();
        return certificate.isPresent() ? subject(certificate.get()) : "not embedded";
    }

    /**
     * @return the title of the tab of the signature, e.g., "Signature 2 (nested in 1)"
     */
    static String title(AuthenticodeSignature signature) {
        String label = signature.getLabel();
        return label.isEmpty() ? label : Character.toUpperCase(label.charAt(0)) + label.substring(1);
    }

    /**
     * @return the verification result of the signature, empty if it was not verified
     */
    static Optional<VerificationResult> resultFor(AuthenticodeSignature signature,
                                                  Optional<AuthenticodeVerification> verification) {
        if (!verification.isPresent()) {
            return Optional.empty();
        }
        for (VerificationResult result : verification.get().getResults()) {
            if (result.getSignature() == signature) {
                return Optional.of(result);
            }
        }
        return Optional.empty();
    }

    /**
     * @return the innermost signature that contains the offset, e.g., a nested one inside the primary signature
     */
    static Optional<AuthenticodeSignature> innermostSignatureAt(CertificateTable table, long offset) {
        AuthenticodeSignature innermost = null;
        for (AuthenticodeSignature signature : table.getAllSignatures()) {
            PhysicalLocation loc = signature.getLocation();
            if (offset >= loc.from() && offset < loc.from() + loc.size()
                    && (innermost == null || signature.getNestingLevel() > innermost.getNestingLevel())) {
                innermost = signature;
            }
        }
        return Optional.ofNullable(innermost);
    }

    /**
     * @return description, value and offset of the properties of the signature, its verification and its signers
     */
    static List<Object[]> signatureRows(AuthenticodeSignature signature, Optional<VerificationResult> result) {
        List<Object[]> rows = new ArrayList<>();
        rows.add(row("Status", result.isPresent() ? result.get().getStatus().toString() : "not verified",
                signature.getLocation().from()));
        if (signature.getImageDigestAlgorithm().isPresent() && signature.getImageDigest().isPresent()) {
            rows.add(row("Image digest (" + signature.getImageDigestAlgorithm().get() + ")",
                    hex(signature.getImageDigest().get()), NO_OFFSET));
        }
        if (result.isPresent()) {
            VerificationResult r = result.get();
            rows.add(row("Computed digest", r.getComputedImageDigest().orElse("n.a.").toLowerCase()
                    + " (" + r.getImageDigestStatus() + ")", NO_OFFSET));
            rows.add(row("Message digest", r.getMessageDigestStatus().toString(), NO_OFFSET));
            rows.add(row("Signer signature", r.getSignerSignatureStatus().toString(), NO_OFFSET));
            rows.add(row("Certificate chain", chain(r.getSignerChain()), NO_OFFSET));
        }
        if (signature.getPageHashes().isPresent()) {
            PageHashes pageHashes = signature.getPageHashes().get();
            rows.add(row("Page hashes", pageHashes.getDigestAlgorithmName() + ", " + pageHashes.getEntryCount()
                    + " entries" + (pageHashes.isWellFormed() ? "" : ", malformed"), pageHashes.getLocation().from()));
        }
        List<SignerInfo> signers = signature.getSigners();
        for (int i = 0; i < signers.size(); i++) {
            String prefix = signers.size() > 1 ? "Signer " + (i + 1) + " " : "Signer ";
            addSignerRows(rows, signers.get(i), prefix);
        }
        if (!signers.isEmpty()) {
            List<TimestampVerification> verifications = result.isPresent()
                    ? result.get().getTimestampVerifications() : new ArrayList<>();
            for (Timestamp timestamp : signers.get(0).getTimestamps()) {
                String status = "";
                for (TimestampVerification v : verifications) {
                    if (v.getTimestamp() == timestamp) {
                        status = " (" + v.getStatus() + ")";
                    }
                }
                rows.add(row("Timestamp (" + timestamp.getKind() + ")", time(timestamp.getTime()) + status,
                        timestamp.getLocation().from()));
            }
        }
        if (!signature.getNestedSignatures().isEmpty()) {
            rows.add(row("Nested signatures", String.valueOf(signature.getNestedSignatures().size()), NO_OFFSET));
        }
        if (signature.isBerEncoded()) {
            rows.add(row("Encoding", "BER instead of DER", NO_OFFSET));
        }
        return rows;
    }

    private static void addSignerRows(List<Object[]> rows, SignerInfo signer, String prefix) {
        rows.add(row(prefix.trim(), signerName(signer), signer.getLocation().from()));
        rows.add(row(prefix + "issuer", signer.getIssuerName(), NO_OFFSET));
        if (signer.getSerialNumberHex().isPresent()) {
            rows.add(row(prefix + "serial", signer.getSerialNumberHex().get(), NO_OFFSET));
        }
        rows.add(row("Algorithms", signer.getDigestAlgorithm() + ", " + signer.getSignatureAlgorithm(), NO_OFFSET));
        if (signer.getProgramName().isPresent()) {
            rows.add(row("Program name", signer.getProgramName().get(), NO_OFFSET));
        }
        if (signer.getMoreInfoUrl().isPresent()) {
            rows.add(row("More info URL", signer.getMoreInfoUrl().get(), NO_OFFSET));
        }
        if (signer.getEffectiveSigningTime().isPresent()) {
            rows.add(row("Signing time", time(signer.getEffectiveSigningTime()), NO_OFFSET));
        }
    }

    private static String chain(CertificateChain chain) {
        return chain.getStatus() + (chain.endsInSelfSigned() ? ", ends in a self-signed certificate" : "");
    }

    /**
     * @return the certificates of the signature, then those of its timestamps, in the order of
     * {@link #certificateRows}
     */
    static List<AuthenticodeCertificate> certificates(AuthenticodeSignature signature) {
        List<AuthenticodeCertificate> certificates = new ArrayList<>(signature.getCertificates());
        Set<String> thumbprints = new LinkedHashSet<>();
        for (AuthenticodeCertificate certificate : certificates) {
            thumbprints.add(certificate.getSha256Thumbprint());
        }
        for (SignerInfo signer : signature.getSigners()) {
            for (Timestamp timestamp : signer.getTimestamps()) {
                for (AuthenticodeCertificate certificate : timestamp.getCertificates()) {
                    if (thumbprints.add(certificate.getSha256Thumbprint())) {
                        certificates.add(certificate);
                    }
                }
            }
        }
        return certificates;
    }

    /**
     * @return one row per certificate of the signature and of its timestamps
     */
    static List<Object[]> certificateRows(AuthenticodeSignature signature, Optional<VerificationResult> result) {
        List<Object[]> rows = new ArrayList<>();
        for (AuthenticodeCertificate certificate : certificates(signature)) {
            String status = "";
            if (result.isPresent()) {
                for (CertificateVerification v : result.get().getCertificateVerifications()) {
                    if (v.getCertificate() == certificate) {
                        status = v.getStatus().toString();
                    }
                }
            }
            String source = signature.getCertificates().contains(certificate) ? "signature" : "timestamp";
            rows.add(new Object[]{subject(certificate), issuer(certificate), time(certificate.getNotBefore()),
                    time(certificate.getNotAfter()), key(certificate), status, source,
                    certificate.getSerialNumberHex(), certificate.getSha1Thumbprint(),
                    certificate.getSha256Thumbprint(), certificate.getLocation().from()});
        }
        return rows;
    }

    /**
     * @return the common name of the subject, the whole name if there is none
     */
    static String subject(AuthenticodeCertificate certificate) {
        return certificate.getSubjectCommonName().orElse(certificate.getSubjectName());
    }

    private static String issuer(AuthenticodeCertificate certificate) {
        return certificate.getIssuerCommonName().orElse(certificate.getIssuerName());
    }

    private static String key(AuthenticodeCertificate certificate) {
        String key = certificate.getPublicKeyAlgorithm().toString();
        if (certificate.getPublicKeySize() > 0) {
            key += " " + certificate.getPublicKeySize() + " bits";
        }
        if (certificate.getNamedCurve().isPresent()) {
            key += " " + certificate.getNamedCurve().get();
        }
        return key;
    }

    /**
     * @return one row per WIN_CERTIFICATE structure of the table
     */
    static List<Object[]> entryRows(CertificateTable table) {
        List<Object[]> rows = new ArrayList<>();
        for (WinCertificate entry : table.getEntries()) {
            String revision = "0x" + Long.toHexString(entry.getRevisionValue())
                    + (entry.getRevision().isPresent() ? " " + entry.getRevision().get().name() : " invalid");
            String type = "0x" + Long.toHexString(entry.getCertificateTypeValue())
                    + (entry.getCertificateType().isPresent() ? " " + entry.getCertificateType().get().name()
                    : " invalid");
            String content;
            if (entry.getSignature().isPresent()) {
                content = title(entry.getSignature().get());
            } else if (entry.getX509Certificate().isPresent()) {
                content = "Certificate " + subject(entry.getX509Certificate().get());
            } else {
                content = "could not be parsed";
            }
            if (entry.isTruncated()) {
                content += ", truncated";
            }
            rows.add(new Object[]{"WIN_CERTIFICATE " + entry.getIndex(), entry.getOffset(), entry.getLength(),
                    revision, type, content});
        }
        return rows;
    }

    /**
     * @return the issues of the table, its signatures and their verification, each once
     */
    static List<Object[]> issueRows(CertificateTable table, Optional<AuthenticodeVerification> verification) {
        List<CertificateIssue> issues = new ArrayList<>(table.getIssues());
        for (AuthenticodeSignature signature : table.getAllSignatures()) {
            issues.addAll(signature.getIssues());
        }
        if (verification.isPresent()) {
            issues.addAll(verification.get().getIssues());
        }
        Set<String> seen = new LinkedHashSet<>();
        List<Object[]> rows = new ArrayList<>();
        for (CertificateIssue issue : issues) {
            Object offset = issue.getLocations().isEmpty() ? NO_OFFSET : (Object) issue.getLocations().get(0).from();
            if (seen.add(issue.getType() + "|" + issue.getDescription() + "|" + offset)) {
                rows.add(new Object[]{issue.getDescription(), issue.getType().name(), offset});
            }
        }
        return rows;
    }

    private static Object[] row(String description, String value, Object offset) {
        return new Object[]{description, value, offset};
    }

    private static String time(Optional<Instant> time) {
        return time.isPresent() ? TIME_FORMAT.format(time.get()) : "?";
    }

    private static String number(long value, boolean hexEnabled) {
        return hexEnabled ? "0x" + Long.toHexString(value) : String.valueOf(value);
    }

    private static String hex(byte[] bytes) {
        StringBuilder sb = new StringBuilder();
        for (byte b : bytes) {
            sb.append(String.format("%02x", b));
        }
        return sb.toString();
    }
}
