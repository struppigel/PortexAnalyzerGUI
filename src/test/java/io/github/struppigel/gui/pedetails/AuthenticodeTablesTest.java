package io.github.struppigel.gui.pedetails;

import io.github.struppigel.gui.FullPEData;
import io.github.struppigel.gui.utils.TestPEData;
import io.github.struppigel.parser.sections.cert.AuthenticodeCertificate;
import io.github.struppigel.parser.sections.cert.AuthenticodeSignature;
import io.github.struppigel.parser.sections.cert.AuthenticodeVerification;
import io.github.struppigel.parser.sections.cert.CertificateTable;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.File;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Optional;
import java.util.stream.Collectors;

import static io.github.struppigel.testsupport.TestFiles.*;
import static org.junit.jupiter.api.Assertions.*;

class AuthenticodeTablesTest {

    @TempDir
    Path tempDir;

    @Test
    void validSignature() throws Exception {
        FullPEData data = TestPEData.load(SIGNED);
        CertificateTable table = data.getCertificateTable().get();
        Optional<AuthenticodeVerification> verification = data.getAuthenticodeVerification();
        AuthenticodeSignature signature = table.getAllSignatures().get(0);

        String summary = AuthenticodeTables.summary(table, verification, true);
        assertTrue(summary.contains("Integrity: VALID (integrity only, no trust decision)"), summary);
        assertTrue(summary.contains("Certificate table: offset 0x52800, size 0x2388, 1 entry"), summary);
        assertTrue(summary.contains("Signing time: 2020-04-17 18:37:46 UTC"), summary);
        assertTrue(summary.contains("Issues: 0"), summary);

        List<Object[]> rows = AuthenticodeTables.signatureRows(signature,
                AuthenticodeTables.resultFor(signature, verification));
        assertEquals("VALID", value(rows, "Status"));
        assertTrue(value(rows, "Signer").contains("Microsoft"), value(rows, "Signer"));
        assertTrue(value(rows, "Signer issuer").contains("Microsoft Code Signing PCA"), value(rows, "Signer issuer"));
        assertEquals("Strings", value(rows, "Program name"));
        assertEquals("https://www.sysinternals.com", value(rows, "More info URL"));
        assertTrue(value(rows, "Computed digest").endsWith("(VALID)"), value(rows, "Computed digest"));
        assertEquals("2020-04-17 18:37:46 UTC (VALID)", value(rows, "Timestamp (RFC3161)"));
        assertInstanceOf(Long.class, row(rows, "Status")[AuthenticodeTables.SIGNATURE_OFFSET_COLUMN]);
        assertInstanceOf(Long.class, row(rows, "Signer")[AuthenticodeTables.SIGNATURE_OFFSET_COLUMN]);

        List<Object[]> certificates = AuthenticodeTables.certificateRows(signature,
                AuthenticodeTables.resultFor(signature, verification));
        assertEquals(AuthenticodeTables.certificates(signature).size(), certificates.size());
        assertTrue(certificates.size() >= 2, "signer and issuer certificates");
        for (Object[] certificate : certificates) {
            assertEquals(AuthenticodeTables.CERTIFICATE_HEADER.length, certificate.length);
            assertInstanceOf(Long.class, certificate[AuthenticodeTables.CERTIFICATE_OFFSET_COLUMN]);
        }
        assertTrue(certificates.stream().anyMatch(c -> c[4].toString().endsWith(" 2048 bits")),
                "the RSA key of the signer certificate");

        List<Object[]> entries = AuthenticodeTables.entryRows(table);
        assertEquals(1, entries.size());
        assertEquals(0x52800L, entries.get(0)[AuthenticodeTables.ENTRY_OFFSET_COLUMN]);
        assertEquals("0x200 REVISION_2_0", entries.get(0)[3]);
        assertEquals("0x2 PKCS_SIGNED_DATA", entries.get(0)[4]);
        assertEquals("Signature 1", entries.get(0)[5]);

        assertTrue(AuthenticodeTables.issueRows(table, verification).isEmpty());
        assertTrue(AuthenticodeTables.statusLine(data.getCertificateTable(), verification)
                .startsWith("Authenticode: VALID, signed by Microsoft"));
    }

    @Test
    void decimalOffsetsInTheSummary() throws Exception {
        FullPEData data = TestPEData.load(SIGNED);
        String summary = AuthenticodeTables.summary(data.getCertificateTable().get(),
                data.getAuthenticodeVerification(), false);
        assertTrue(summary.contains("offset " + 0x52800 + ", size " + 0x2388), summary);
    }

    @Test
    void nestedSignature() throws Exception {
        FullPEData data = TestPEData.load(SIGNED_NESTED);
        CertificateTable table = data.getCertificateTable().get();
        List<AuthenticodeSignature> signatures = table.getAllSignatures();

        assertEquals(2, signatures.size());
        assertEquals("Signature 2 (nested in 1)", AuthenticodeTables.title(signatures.get(1)));
        assertTrue(AuthenticodeTables.summary(table, data.getAuthenticodeVerification(), true)
                .contains("Signatures: 2 (1 nested)"));
        List<Object[]> primary = AuthenticodeTables.signatureRows(signatures.get(0),
                AuthenticodeTables.resultFor(signatures.get(0), data.getAuthenticodeVerification()));
        assertEquals("1", value(primary, "Nested signatures"));

        long nestedStart = signatures.get(1).getLocation().from();
        assertSame(signatures.get(1), AuthenticodeTables.innermostSignatureAt(table, nestedStart).get());
        assertSame(signatures.get(0), AuthenticodeTables.innermostSignatureAt(table,
                signatures.get(0).getLocation().from()).get());
        assertFalse(AuthenticodeTables.innermostSignatureAt(table, 0).isPresent());
    }

    @Test
    void modifiedFileIsInvalid() throws Exception {
        FullPEData data = TestPEData.load(SIGNED_MODIFIED);
        CertificateTable table = data.getCertificateTable().get();
        Optional<AuthenticodeVerification> verification = data.getAuthenticodeVerification();

        assertTrue(AuthenticodeTables.summary(table, verification, true).startsWith("Integrity: INVALID"));
        List<String> issueTypes = AuthenticodeTables.issueRows(table, verification).stream()
                .map(row -> row[1].toString()).collect(Collectors.toList());
        assertTrue(issueTypes.contains("IMAGE_DIGEST_MISMATCH"), issueTypes.toString());
    }

    @Test
    void brokenTable() throws Exception {
        FullPEData data = TestPEData.load(CERT_BROKEN);
        CertificateTable table = data.getCertificateTable().get();

        List<Object[]> entries = AuthenticodeTables.entryRows(table);
        assertTrue(entries.stream().anyMatch(row -> row[3].toString().endsWith("invalid")
                || row[4].toString().endsWith("invalid")), "an entry with invalid revision or type");
        List<Object[]> issues = AuthenticodeTables.issueRows(table, data.getAuthenticodeVerification());
        List<String> issueTypes = issues.stream().map(row -> row[1].toString()).collect(Collectors.toList());
        assertTrue(issueTypes.contains("TABLE_IN_SECTION"), issueTypes.toString());
        assertEquals(issues.size(), issues.stream().map(row -> row[0] + "|" + row[1] + "|" + row[2]).distinct().count(),
                "every issue once");
    }

    @Test
    void unsignedFile() throws Exception {
        FullPEData data = TestPEData.load(NATIVE_X86);
        assertFalse(data.hasCertificateTable());
        assertEquals("Authenticode: Not signed",
                AuthenticodeTables.statusLine(data.getCertificateTable(), data.getAuthenticodeVerification()));
    }

    @Test
    void savedCertificateIsItsDerEncoding() throws Exception {
        FullPEData data = TestPEData.load(SIGNED);
        AuthenticodeCertificate certificate = AuthenticodeTables.certificates(
                data.getCertificateTable().get().getAllSignatures().get(0)).get(0);
        File file = tempDir.resolve("signer.cer").toFile();

        AuthenticodePanel.writeCertificate(certificate, file);

        assertArrayEquals(certificate.getEncoded(), Files.readAllBytes(file.toPath()));
        String name = AuthenticodePanel.defaultFileName(data.getFile(), certificate, 0);
        assertTrue(name.startsWith(data.getFile().getAbsolutePath() + ".Microsoft"), name);
        assertTrue(name.endsWith(".cer"), name);
    }

    private static Object[] row(List<Object[]> rows, String description) {
        for (Object[] row : rows) {
            if (row[0].equals(description)) {
                return row;
            }
        }
        throw new AssertionError("no row " + description + " in "
                + rows.stream().map(r -> r[0].toString()).collect(Collectors.toList()));
    }

    private static String value(List<Object[]> rows, String description) {
        return row(rows, description)[1].toString();
    }
}
