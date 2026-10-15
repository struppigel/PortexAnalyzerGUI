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
package io.github.struppigel.gui.utils;

import io.github.struppigel.parser.*;
import io.github.struppigel.parser.sections.SectionHeader;
import io.github.struppigel.parser.sections.SectionLoader;
import io.github.struppigel.parser.sections.SectionTable;
import io.github.struppigel.parser.sections.cert.AuthenticodeVerification;
import io.github.struppigel.parser.sections.cert.CertificateTable;
import io.github.struppigel.parser.sections.clr.*;
import io.github.struppigel.parser.sections.debug.*;
import io.github.struppigel.parser.sections.edata.ExportEntry;
import io.github.struppigel.parser.sections.edata.ExportNameEntry;
import io.github.struppigel.parser.sections.idata.*;
import io.github.struppigel.parser.sections.rsrc.IDOrName;
import io.github.struppigel.parser.sections.rsrc.Level;
import io.github.struppigel.parser.sections.rsrc.Resource;
import io.github.struppigel.parser.sections.rsrc.version.VersionInfo;
import io.github.struppigel.tools.*;
import io.github.struppigel.tools.anomalies.Anomaly;
import io.github.struppigel.tools.anomalies.PEAnomalyScanner;
import io.github.struppigel.tools.layout.Detail;
import io.github.struppigel.tools.layout.SignatureHit;
import io.github.struppigel.tools.layout.StructureMap;
import io.github.struppigel.tools.layout.StructureMapBuilder;
import io.github.struppigel.tools.sigscanner.LocationKind;
import io.github.struppigel.tools.sigscanner.SignatureMatch;
import io.github.struppigel.gui.FullPEData;
import io.github.struppigel.gui.MainFrame;
import java.util.Optional;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import javax.swing.*;
import java.io.File;
import java.io.IOException;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ExecutionException;
import java.util.stream.Collectors;

/**
 * Loads and computes all the data related to PE files, so that this code does not run in the event dispatch thread.
 * Everything that is too complicated here must be improved in the library PortEx.
 */
public class PELoadWorker extends SwingWorker<FullPEData, String> {
    private static final Logger LOGGER = LogManager.getLogger();
    private final String NL = System.getProperty("line.separator");
    private final File file;
    private final MainFrame frame;
    private final JLabel progressText;

    public PELoadWorker(File file, MainFrame frame, JLabel progressText) {
        this.file = file;
        this.frame = frame;
        this.progressText = progressText;
    }

    @Override
    protected FullPEData doInBackground() throws Exception {
        setProgress(0);
        publish("Loading Headers...");
        PEData data = PELoader.loadPE(file);
        java.util.Optional<CLRSection> maybeCLR = loadCLRSection(data);
        List<StandardField> dotnetMetaDataRootEntries = createDotNetMetadataRootEntries(maybeCLR);
        List<Object[]> dotNetStreamHeaders = createDotNetStreamHeaders(maybeCLR);
        List<StandardField> optimizedStreamEntries = createOptimizedStreamEntries(maybeCLR);
        Map<String, List<List<Object>>> clrTables = data.loadCLRTables();
        Map<String, List<String>> clrTableHeaders = data.loadCLRTableHeaders();
        setProgress(10);

        publish("Calculating Hashes...");
        ReportCreator r = new ReportCreator(data);
        String hashesReport = createHashesReport(data);
        List<Object[]> hashesForSections = createHashTableEntries(data);
        setProgress(20);

        publish("Calculating Entropies...");
        double[] sectionEntropies = calculateSectionEntropies(data);
        setProgress(30);

        publish("Extracting Imports...");
        List<ImportDLL> importDLLs = data.loadImports();
        List<ImportDLL> delayDLLs = data.loadDelayLoadImports();
        List<Object[]> impEntries = createImportTableEntries(importDLLs);
        List<Object[]> delayLoadEntries = createImportTableEntries(delayDLLs);
        List<Object[]> boundImportEntries = createBoundImportEntries(data);
        setProgress(40);

        publish("Scanning for signatures...");
        // scans all default locations once, later calls of getSignatureMatches are cache hits
        data.getSignatureMatches();
        String rehintsReport = r.reversingHintsReport();
        setProgress(50);

        publish("Loading Resources...");
        List<Object[]> resourceTableEntries = createResourceTableEntries(data);
        List<String> manifests = data.loadManifests();
        List<Object[]> vsInfoTable = createVersionInfoEntries(data);
        List<Object[]> stringTableEntries = createStringTableEntries(data);
        setProgress(60);

        publish("Loading Exports...");
        List<ExportEntry> exports = data.loadExports();
        List<Object[]> exportEntries = createExportTableEntries(data);
        setProgress(70);

        publish("Loading Debug Info...");
        List<TableContent> debugTableEntries = getDebugTableEntries(data);
        data.loadExtendedDllCharacteristics(); // preload it so it can be accessed next time
        setProgress(80);

        publish("Verifying Authenticode signatures...");
        java.util.Optional<CertificateTable> certificateTable = loadCertificateTable(data);
        java.util.Optional<AuthenticodeVerification> verification = certificateTable.isPresent()
                ? loadAuthenticodeVerification(data) : java.util.Optional.empty();
        hashesReport += createAuthentihashReport(data, verification);
        setProgress(83);

        publish("Scanning for Anomalies...");
        List<Anomaly> anomalies = PEAnomalyScanner.newInstance(data).getAnomalies();
        List<Object[]> anomaliesTable = createAnomalyTableEntries(anomalies);
        setProgress(85);

        publish("Scanning Overlay...");
        Overlay overlay = new Overlay(data);
        double overlayEntropy = ShannonEntropy.entropy(data.getFile(), overlay.getOffset(), overlay.getSize());
        List<SignatureMatch> overlaySignatures = data.getSignatureMatches(LocationKind.OVERLAY);
        setProgress(95);

        publish("Mapping structures...");
        StructureMap structureMap = createStructureMap(data, anomalies);
        setProgress(100);

        publish("Done!");
        return new FullPEData(data, overlay, overlayEntropy, overlaySignatures, sectionEntropies, importDLLs,
                impEntries, delayLoadEntries, boundImportEntries, resourceTableEntries, data.loadResources(), manifests, exportEntries, exports,
                hashesReport, hashesForSections, anomaliesTable, debugTableEntries, vsInfoTable,
                rehintsReport, stringTableEntries, dotnetMetaDataRootEntries, maybeCLR, dotNetStreamHeaders,
                optimizedStreamEntries, clrTables, clrTableHeaders, anomalies, structureMap,
                certificateTable, verification);
    }

    /**
     * Parses the certificate table. A table that cannot be parsed must not prevent showing the file.
     */
    private java.util.Optional<CertificateTable> loadCertificateTable(PEData data) {
        try {
            return data.loadCertificateTable();
        } catch (AnalysisInterruptedException e) {
            throw e;
        } catch (RuntimeException e) {
            LOGGER.error("Could not parse the certificate table", e);
            return java.util.Optional.empty();
        }
    }

    /**
     * Verifies the integrity of the signatures, which hashes the whole file. The anomaly scanner uses the cached
     * result.
     */
    private java.util.Optional<AuthenticodeVerification> loadAuthenticodeVerification(PEData data) {
        try {
            return data.loadAuthenticodeVerification();
        } catch (AnalysisInterruptedException e) {
            throw e;
        } catch (RuntimeException e) {
            LOGGER.error("Could not verify the Authenticode signatures", e);
            return java.util.Optional.empty();
        }
    }

    /**
     * @return the Authenticode image digests, one line per algorithm: SHA-256 for every file, signed or not, like
     *         VirusTotal shows it, and the other algorithms that the signatures use
     */
    private String createAuthentihashReport(PEData data, java.util.Optional<AuthenticodeVerification> verification) {
        // computed to verify the signatures, so these are not hashed again
        Map<String, String> digests = new LinkedHashMap<>();
        if (verification.isPresent()) {
            digests.putAll(verification.get().getComputedImageDigests());
        }
        String sha256 = digests.remove("SHA-256");
        if (sha256 == null) {
            sha256 = computeSha256Authentihash(data);
        }
        String text = "";
        if (sha256 != null) {
            text += "Authentihash (SHA-256): " + sha256.toLowerCase() + NL;
        }
        for (Map.Entry<String, String> digest : digests.entrySet()) {
            text += "Authentihash (" + digest.getKey() + "): " + digest.getValue().toLowerCase() + NL;
        }
        return text;
    }

    /**
     * @return the SHA-256 authentihash, null if the file cannot be read
     */
    private String computeSha256Authentihash(PEData data) {
        try {
            return hash(new Hasher(data).authentihash(MessageDigest.getInstance("SHA-256")));
        } catch (NoSuchAlgorithmException e) {
            throw new RuntimeException(e);
        } catch (AnalysisInterruptedException e) {
            throw e;
        } catch (IOException | RuntimeException e) {
            LOGGER.error("Could not compute the authentihash", e);
            return null;
        }
    }

    /**
     * Builds the structure map for the visualization. Must run in this thread, because the caches of PEData are not
     * thread-safe. A file whose structures cannot be mapped is still shown, only without structures.
     */
    private StructureMap createStructureMap(PEData data, List<Anomaly> anomalies) {
        try {
            return new StructureMapBuilder(data)
                    .withDetail(Detail.FINE)
                    .withAnomalies(anomalies)
                    .withSignatureMatches(SignatureHit.fromMatches(data.getSignatureMatches()))
                    .build();
        } catch (AnalysisInterruptedException e) {
            throw e;
        } catch (RuntimeException e) {
            LOGGER.error("Could not map the structures of the file", e);
            return StructureMap.empty(data.getFile().length());
        }
    }

    private Object[] createBoundImportEntry(BoundImportDescriptor bi) {
        List<Object> line = new ArrayList<>();
        line.add(bi.getName());
        line.add(bi.get(BoundImportDescriptorKey.OFFSET_MODULE_NAME));
        line.add(bi.get(BoundImportDescriptorKey.TIME_DATE_STAMP));
        line.add(bi.get(BoundImportDescriptorKey.NR_OF_MODULE_FORWARDER_REFS));
        line.add(bi.rawOffset());
        return line.toArray();
    }

    private List<Object[]> createBoundImportEntries(PEData data) throws IOException {
        Optional<BoundImportSection> section = new SectionLoader(data).maybeLoadBoundImportSection();
        if(section.isPresent()) {
            BoundImportSection bsec = section.get();
            return bsec.getEntries().stream()
                    .map(this::createBoundImportEntry)
                    .collect(Collectors.toList());
        }
        return new ArrayList<>();
    }

    private Optional<CLRSection> loadCLRSection(PEData data) {
        SectionLoader loader = new SectionLoader(data);
        try {
            Optional<CLRSection> maybeClr = loader.maybeLoadCLRSection();
            return maybeClr;
        } catch(IOException e){
            LOGGER.error(e);
            e.printStackTrace();
        }
        return Optional.empty();
    }

    private List<StandardField> createOptimizedStreamEntries(java.util.Optional<CLRSection> clr) {
        if(clr.isPresent() && !clr.get().isEmpty()) {
            MetadataRoot root = clr.get().metadataRoot();
            if(root.maybeGetOptimizedStream().isPresent()){
               OptimizedStream optStream = root.maybeGetOptimizedStream().get();
               return optStream.getEntriesList();
            }
        }
        return new ArrayList<>();
    }

    private List<Object[]> createDotNetStreamHeaders(java.util.Optional<CLRSection> clr) {
        if(clr.isPresent() && !clr.get().isEmpty()) {
            MetadataRoot root = clr.get().metadataRoot();
            List<StreamHeader> headers = root.getStreamHeaders();
            return headers.stream()
                    .map(h -> new Object[]{ h.name(), h.size(), h.offset(), root.getBSJBOffset() + h.offset() })
                    .collect(Collectors.toList());
        }
        return new ArrayList<>();
    }

    private List<StandardField> createDotNetMetadataRootEntries(java.util.Optional<CLRSection> clr) {
        if(clr.isPresent() && !clr.get().isEmpty()) {
            MetadataRoot root = clr.get().metadataRoot();
            Map<MetadataRootKey, StandardField> entriesMap = root.getMetaDataEntries();
            return entriesMap.values().stream().collect(Collectors.toList());
        }
        return new ArrayList<>();
    }

    private List<Object[]> createStringTableEntries(PEData data) {
        Map<Long, String> strTable = data.loadStringTable();
        return strTable.entrySet().stream()
                .map(e -> new Object[]{e.getKey(), e.getValue()})
                .collect(Collectors.toList());
    }

    private List<Object[]> createVersionInfoEntries(PEData data) {
        return data.loadResources().stream()
                .filter(r -> r.getType().equals("RT_VERSION"))
                .flatMap(
                    r -> VersionInfo.apply(r, data.getFile()).getVersionStrings().entrySet()
                            .stream()
                            .map(e -> new Object[]{e.getKey(), e.getValue()})
                )
                .collect(Collectors.toList());
    }

    private List<Object[]> createAnomalyTableEntries(List<Anomaly> anomalies) {
        return anomalies.stream()
                .map(a -> new Object[]{a.description(), a.getType(), a.subtype(), a.key(), firstOffsetOrEmpty(a.locations())})
                .collect(Collectors.toList());
    }

    private String createHashesReport(PEData data) {
        String text = "Full File Hashes" + NL + NL;
        try {
            MessageDigest sha256 = MessageDigest.getInstance("SHA-256");
            MessageDigest md5 = MessageDigest.getInstance("MD5");
            Hasher hasher = new Hasher(data);
            text += "MD5: " + hash(hasher.fileHash(md5)) + NL;
            text += "SHA256: " + hash(hasher.fileHash(sha256)) + NL;
            text += "ImpHash: " + ImpHash.createString(data.getFile()) + NL;
            java.util.Optional<byte[]> maybeRich = hasher.maybeRichHash();
            if (maybeRich.isPresent()) {
                text += "Rich: " + hash(maybeRich.get()) + NL;
                java.util.Optional<byte[]> maybeRichPV = hasher.maybeRichPVHash();
                if (maybeRichPV.isPresent()) {
                    text += "RichPV: " + hash(maybeRichPV.get()) + NL;
                }
            }

        } catch (NoSuchAlgorithmException | IOException e) {
            throw new RuntimeException(e);
        }
        return text;
    }

    private List<Object[]> createHashTableEntries(PEData data) {
        List<Object[]> entries = new ArrayList<>();
        SectionTable sectionTable = data.getSectionTable();
        try {
            MessageDigest sha256 = MessageDigest.getInstance("SHA-256");
            MessageDigest md5 = MessageDigest.getInstance("MD5");
            Hasher hasher = new Hasher(data);
            for (SectionHeader s : sectionTable.getSectionHeaders()) {
                String secName = s.getNumber() + ". " + s.getName();
                String md5Str = hash(hasher.sectionHash(s.getNumber(), md5));
                String sha256Str = hash(hasher.sectionHash(s.getNumber(), sha256));
                md5Str = md5Str.equals("") ? "empty section" : md5Str;
                sha256Str = sha256Str.equals("") ? "empty section" : sha256Str;
                String[] md5Entry = {secName, "MD5", md5Str};
                String[] sha256Entry = {secName, "SHA256", sha256Str};
                entries.add(md5Entry);
                entries.add(sha256Entry);
            }
        } catch (NoSuchAlgorithmException | IOException e) {
            throw new RuntimeException(e);
        }
        return entries;
    }

    private String hash(byte[] array) {
        return ByteArrayUtil.byteToHex(array, "");
    }

    private String getCodeViewInfo(DebugDirectoryEntry d) {
        String report = "";
        CodeviewInfo c = d.getCodeView();
        report += "Codeview" + NL + NL;
        report += "Path: " + c.filePath() + NL;
        report += "Age: " + c.age() + NL;
        report += "GUID: " + CodeviewInfo.guidToString(c.guid()) + NL;

        return report;
    }

    private String getDebugInfo(DebugDirectoryEntry d, Boolean isRepro) {
        String time = isRepro ? "invalid - reproducibility build" : String.valueOf(d.getTimeDateStamp());
        String report = "Time date stamp: " + time + NL;
        report += "Type: " + d.getTypeDescription() + NL + NL;
        return report;
    }

    private List<TableContent> getDebugTableEntries(PEData pedata) {
        List<TableContent> tables = new ArrayList<TableContent>();
        try {
            Optional<DebugSection> sec = new SectionLoader(pedata).maybeLoadDebugSection();
            if (sec.isPresent()) {
                DebugSection debugSec = sec.get();
                List<DebugDirectoryEntry> debugList = debugSec.getEntries();

                for(DebugDirectoryEntry d : debugList) {
                    Map<DebugDirectoryKey, StandardField> dirTable = d.getDirectoryTable();
                    List<StandardField> vals = dirTable.values().stream().collect(Collectors.toList());
                    String title = d.getDebugType().toString();
                    String debugInfo = getDebugInfo(d, pedata.isReproBuild());
                    if(d.getDebugType() == DebugType.CODEVIEW) {
                        try{
                            debugInfo += getCodeViewInfo(d);
                        } catch (IllegalStateException ise) {
                            debugInfo += "Invalid codeview structure!";
                            LOGGER.warn(ise.getMessage());
                        }
                    }
                    if(d.getDebugType() == DebugType.REPRO) {
                        debugInfo += d.getRepro().getInfo();
                    }
                    if(d.getDebugType() == DebugType.EX_DLLCHARACTERISTICS) {
                        debugInfo += d.getExtendedDLLCharacteristics().getInfo();
                    }
                    tables.add(new TableContent(vals, title, debugInfo));
                }
            }
        } catch (IOException e) {
            LOGGER.error(e);
            e.printStackTrace();
        }
        return tables;
    }

    private List<Object[]> createExportTableEntries(PEData pedata) {
        List<Object[]> entries = new ArrayList<>();
        List<ExportEntry> exports = pedata.loadExports();
        for (ExportEntry e : exports) {
            // TODO this is a hack because of lacking support to check for export names, *facepalm*
            String name = e instanceof ExportNameEntry ? ((ExportNameEntry) e).name() : "";
            String forwarder = e.maybeGetForwarder().isPresent() ? e.maybeGetForwarder().get() : "";
            Object[] entry = {name, e.ordinal(), e.symbolRVA(), forwarder};
            entries.add(entry);
        }
        return entries;
    }

    private List<Object[]> createResourceTableEntries(PEData pedata) {
        List<Resource> resources = pedata.loadResources();
        Map<Long, List<String>> signaturesByResource = new HashMap<>();
        for (SignatureMatch m : pedata.getSignatureMatches(LocationKind.RESOURCE)) {
            signaturesByResource.computeIfAbsent(m.getRegionStart(), k -> new ArrayList<>()).add(m.getName());
        }
        List<Object[]> entries = new ArrayList<>();
        for (Resource r : resources) {
            Map<Level, IDOrName> lvlIds = r.getLevelIDs();
            if(lvlIds == null) return entries;
            IDOrName nameLvl = lvlIds.get(Level.nameLevel());
            IDOrName langLvl =  lvlIds.get(Level.languageLevel());
            if(nameLvl != null && langLvl != null) {
                String nameId = nameLvl.toString();
                String langId = langLvl.toString();
                long offset = r.rawBytesLocation().from();
                long size = r.rawBytesLocation().size();
                String signatures = String.join(", ",
                        signaturesByResource.getOrDefault(offset, Collections.emptyList()));
                Object[] entry = {r.getType(), nameId, langId, offset, size, signatures};
                entries.add(entry);
            }
        }
        return entries;
    }

    private List<Object[]> createImportTableEntries(List<ImportDLL> imports) {
        List<Object[]> entries = new ArrayList<>();
        for (ImportDLL dll : imports) {
            for (NameImport imp : dll.getNameImports()) {
                Optional<SymbolDescription> symbol = ImportDLL.getSymbolDescriptionForName(imp.getName());
                String description = "";
                String category = "";
                if (symbol.isPresent()) {
                    description = symbol.get().getDescription().orElse("");
                    // TODO replacing special chars is a hack for proper symbol descriptions, fix this in PortEx
                    category = symbol.get().getCategory().replace("]", "").replace("[", "");
                    String subcategory = symbol.get().getSubCategory().orElse("").replace(">", "").replace("<", "");
                    if (!subcategory.equals("")) {
                        category += " -> " + subcategory;
                    }
                }
                Object[] entry = {dll.getName(), category, imp.getName(), description, imp.getRVA(), imp.getHint(),
                        firstOffsetOrEmpty(imp.getLocations())};
                entries.add(entry);
            }
            for (OrdinalImport imp : dll.getOrdinalImports()) {
                Object[] entry = {dll.getName(), "", imp.getOrdinal() + "", "", imp.getRVA(), "",
                        firstOffsetOrEmpty(imp.getLocations())};
                entries.add(entry);
            }
        }
        return entries;
    }

    /**
     * @return the file offset of the first location, "" if there is none, so that the table shows no offset
     */
    private static Object firstOffsetOrEmpty(List<PhysicalLocation> locations) {
        return locations.isEmpty() ? "" : (Object) locations.get(0).from();
    }

    private double[] calculateSectionEntropies(PEData data) {
        int sectionNumber = data.getSectionTable().getNumberOfSections();
        double[] sectionEntropies = new double[sectionNumber];
        ShannonEntropy entropy = new ShannonEntropy(data);
        for (int i = 0; i < sectionNumber; i++) {
            sectionEntropies[i] = entropy.forSection(i + 1);
        }
        return sectionEntropies;
    }

    @Override
    protected void process(List<String> statusText) {
        String lastMsg = statusText.get(statusText.size() - 1);
        progressText.setText(lastMsg);
    }

    @Override
    protected void done() {
        if(isCancelled()) {return;}
        try {
            FullPEData data = get();
            frame.setPeData(data);
        } catch (InterruptedException | ExecutionException e) {
            String message = "Could not load PE file! Reason: " + e.getMessage();
            if (e.getMessage().contains("given file is no PE file")) {
                message = "Could not load PE file! The given file is no PE file";
            }
            LOGGER.warn(message);
            LOGGER.warn(e);
            e.printStackTrace();
            JOptionPane.showMessageDialog(null,
                    message,
                    "Unable to load",
                    JOptionPane.ERROR_MESSAGE);
        }

    }

}
