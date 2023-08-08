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
package com.github.struppigel.gui.utils;

import com.github.katjahahn.parser.*;
import com.github.katjahahn.parser.sections.SectionHeader;
import com.github.katjahahn.parser.sections.SectionLoader;
import com.github.katjahahn.parser.sections.SectionTable;
import com.github.katjahahn.parser.sections.debug.*;
import com.github.katjahahn.parser.sections.edata.ExportEntry;
import com.github.katjahahn.parser.sections.edata.ExportNameEntry;
import com.github.katjahahn.parser.sections.idata.ImportDLL;
import com.github.katjahahn.parser.sections.idata.NameImport;
import com.github.katjahahn.parser.sections.idata.OrdinalImport;
import com.github.katjahahn.parser.sections.idata.SymbolDescription;
import com.github.katjahahn.parser.sections.rsrc.IDOrName;
import com.github.katjahahn.parser.sections.rsrc.Level;
import com.github.katjahahn.parser.sections.rsrc.Resource;
import com.github.katjahahn.parser.sections.rsrc.version.VersionInfo;
import com.github.katjahahn.tools.*;
import com.github.katjahahn.tools.anomalies.Anomaly;
import com.github.katjahahn.tools.anomalies.PEAnomalyScanner;
import com.github.katjahahn.tools.sigscanner.FileTypeScanner;
import com.github.katjahahn.tools.sigscanner.SignatureScanner;
import com.github.struppigel.gui.FullPEData;
import com.github.struppigel.gui.MainFrame;
import com.google.common.base.Optional;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import javax.swing.*;
import java.io.File;
import java.io.IOException;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ExecutionException;
import java.util.stream.Collectors;
import java.util.stream.Stream;

/**
 * Loads and computes all the data related to PE files, so that this code does not run in the event dispatch thread.
 * Everything that is too complicated here must be improved in the library PortEx.
 */
public class PELoadWorker extends SwingWorker<FullPEData, String> {
    private static final Logger LOGGER = LogManager.getLogger();
    private final String NL = System.getProperty("line.separator");
    private final File file;
    private final MainFrame frame;

    public static final int MAX_MANIFEST_SIZE = 0x2000;
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
        setProgress(10);

        publish("Calculating Hashes...");
        ReportCreator r = ReportCreator.newInstance(data.getFile());
        String hashesReport = createHashesReport(data);
        List<Object[]> hashesForSections = createHashTableEntries(data);
        setProgress(20);

        publish("Calculating Entropies...");
        double[] sectionEntropies = calculateSectionEntropies(data);
        setProgress(30);

        publish("Extracting Imports...");
        List<ImportDLL> importDLLs = data.loadImports();
        List<Object[]> impEntries = createImportTableEntries(importDLLs);
        setProgress(40);

        publish("Scanning for signatures...");
        String signatureReport = r.peidReport();
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
        setProgress(80);

        publish("Scanning for Anomalies...");
        List<Object[]> anomaliesTable = createAnomalyTableEntries(data);
        setProgress(90);

        publish("Scanning Overlay...");
        Overlay overlay = new Overlay(data);
        double overlayEntropy = ShannonEntropy.entropy(data.getFile(), overlay.getOffset(), overlay.getSize());
        List<String> overlaySignatures = new SignatureScanner(SignatureScanner.loadOverlaySigs()).scanAtToString(data.getFile(), overlay.getOffset());
        setProgress(100);

        publish("Done!");
        return new FullPEData(data, overlay, overlayEntropy, overlaySignatures, sectionEntropies, importDLLs,
                impEntries, resourceTableEntries, data.loadResources(), manifests, exportEntries, exports,
                hashesReport, hashesForSections, anomaliesTable, debugTableEntries, vsInfoTable,
                signatureReport, stringTableEntries);
    }

    private List<Object[]> createStringTableEntries(PEData data) {
        List<Object[]> entries = new ArrayList<>();
        Map<Long, String> strTable = data.loadStringTable();
        for(Map.Entry<Long, String> entry : strTable.entrySet()){
            Object[] row = {entry.getKey(), entry.getValue()};
            entries.add(row);
        }
        return entries;
    }

    private List<Object[]> createVersionInfoEntries(PEData data) {
        List<Resource> res = data.loadResources();
        List<Object[]> entries = new ArrayList<>();
        for (Resource r : res) {
            if (r.getType().equals("RT_VERSION")) {
                VersionInfo versionInfo = VersionInfo.apply(r, data.getFile());
                Map<String, String> map = versionInfo.getVersionStrings();
                for (Map.Entry<String, String> e : map.entrySet()) {
                    Object[] entry = {e.getKey(), e.getValue()};
                    entries.add(entry);
                }
            }
        }
        return entries;
    }

    private List<Object[]> createAnomalyTableEntries(PEData data) {
        List<Object[]> entries = new ArrayList<>();
        List<Anomaly> anomalies = PEAnomalyScanner.newInstance(data).getAnomalies();
        for (Anomaly a : anomalies) {
            Object[] entry = {a.description(), a.getType(), a.subtype(), a.key()};
            entries.add(entry);
        }
        return entries;
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

    private String getDebugInfo(DebugDirectoryEntry d) {
        String report = "Time date stamp: " + d.getTimeDateStamp() + NL;
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
                    String debugInfo = getDebugInfo(d);
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
        List<Object[]> entries = new ArrayList<>();
        for (Resource r : resources) {
            Map<Level, IDOrName> lvlIds = r.getLevelIDs();
            String nameId = lvlIds.get(Level.nameLevel()).toString();
            String langId = lvlIds.get(Level.languageLevel()).toString();
            String signatures;
            long offset = r.rawBytesLocation().from();
            long size = r.rawBytesLocation().size();

            Stream<String> scanresults = FileTypeScanner.apply(pedata.getFile())
                    .scanAtReport(offset).stream()
                    // TODO this is a hack because lack of support from PortEx for scanAt function
                    .map(s -> s.contains("bytes matched:") ? s.split("bytes matched:")[0] : s);
            signatures = scanresults.collect(Collectors.joining(", "));
            Object[] entry = {r.getType(), nameId, langId, offset, size, signatures};
            entries.add(entry);
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
                    description = symbol.get().getDescription().or("");
                    // TODO replacing special chars is a hack for proper symbol descriptions, fix this in PortEx
                    category = symbol.get().getCategory().replace("]", "").replace("[", "");
                    String subcategory = symbol.get().getSubCategory().or("").replace(">", "").replace("<", "");
                    if (!subcategory.equals("")) {
                        category += " -> " + subcategory;
                    }
                }
                Object[] entry = {dll.getName(), category, imp.getName(), description, imp.getRVA(), imp.getHint()};
                entries.add(entry);
            }
            for (OrdinalImport imp : dll.getOrdinalImports()) {
                Object[] entry = {dll.getName(), "", imp.getOrdinal() + "", "", imp.getRVA(), ""};
                entries.add(entry);
            }
        }
        return entries;
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
