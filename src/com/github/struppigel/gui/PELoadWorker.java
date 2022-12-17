/**
 * *****************************************************************************
 * Copyright 2022 Karsten Hahn
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *   http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 * ****************************************************************************
 */
package com.github.struppigel.gui;

import com.github.katjahahn.parser.ByteArrayUtil;
import com.github.katjahahn.parser.IOUtil;
import com.github.katjahahn.parser.PEData;
import com.github.katjahahn.parser.PELoader;
import com.github.katjahahn.parser.sections.SectionHeader;
import com.github.katjahahn.parser.sections.SectionLoader;
import com.github.katjahahn.parser.sections.SectionTable;
import com.github.katjahahn.parser.sections.debug.DebugSection;
import com.github.katjahahn.parser.sections.edata.ExportEntry;
import com.github.katjahahn.parser.sections.edata.ExportNameEntry;
import com.github.katjahahn.parser.sections.edata.ExportSection;
import com.github.katjahahn.parser.sections.idata.*;
import com.github.katjahahn.parser.sections.rsrc.IDOrName;
import com.github.katjahahn.parser.sections.rsrc.Level;
import com.github.katjahahn.parser.sections.rsrc.Resource;
import com.github.katjahahn.parser.sections.rsrc.ResourceSection;
import com.github.katjahahn.parser.sections.rsrc.version.VersionInfo;
import com.github.katjahahn.tools.*;
import com.github.katjahahn.tools.sigscanner.FileTypeScanner;
import com.github.katjahahn.tools.sigscanner.SignatureScanner;
import com.google.common.base.Optional;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import javax.swing.*;
import java.io.File;
import java.io.IOException;
import java.io.RandomAccessFile;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ExecutionException;
import java.util.stream.Collectors;
import java.util.stream.Stream;

public class PELoadWorker extends SwingWorker<FullPEData, Void> {
    private static final Logger LOGGER = LogManager.getLogger();
    private final String NL = System.getProperty("line.separator");
    private final File file;
    private final MainFrame frame;

    public static final int MAX_MANIFEST_SIZE = 0x2000;

    public PELoadWorker(File file, MainFrame frame) {
        this.file = file;
        this.frame = frame;
    }

    @Override
    protected FullPEData doInBackground() throws Exception {
        setProgress(0);
        PEData data = PELoader.loadPE(file);
        setProgress(10);
        ReportCreator r = ReportCreator.newInstance(data.getFile());
        String hashesReport = createHashesReport(data);
        List<String[]> hashesForSections = createHashTableEntries(data);
        setProgress(20);
        double[] sectionEntropies = calculateSectionEntropies(data);
        setProgress(30);
        List<ImportDLL> importDLLs = extractImports(data);
        setProgress(40);
        List<String[]> impEntries = createImportTableEntries(importDLLs);
        setProgress(50);
        List<String[]> resourceTableEntries = createResourceTableEntries(data);
        String manifest = readManifest(data);
        String version = readVersionInfo(data);
        setProgress(60);
        List<ExportEntry> exports = getExports(data);
        List<String[]> exportEntries = createExportTableEntries(data);
        setProgress(70);
        String debugInfo = getDebugInfo(data);
        setProgress(80);
        String anomalies = r.anomalyReport();
        setProgress(90);
        Overlay overlay = new Overlay(data);
        double overlayEntropy = ShannonEntropy.entropy(data.getFile(), overlay.getOffset(), overlay.getSize());
        List<String> overlaySignatures = new SignatureScanner(SignatureScanner.loadOverlaySigs()).scanAt(data.getFile(), overlay.getOffset());
        setProgress(100);
        return new FullPEData(data, overlay, overlayEntropy, overlaySignatures, sectionEntropies, importDLLs,
                impEntries, resourceTableEntries, getResources(data), manifest, version, exportEntries, exports,
                debugInfo, anomalies, hashesReport, hashesForSections);
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
            if(maybeRich.isPresent()) {
                text += "Rich: " + hash(maybeRich.get()) + NL;
                java.util.Optional<byte[]> maybeRichPV = hasher.maybeRichPVHash();
                if(maybeRichPV.isPresent()) {
                    text += "RichPV: " + hash(maybeRichPV.get()) + NL;
                }
            }

        } catch (NoSuchAlgorithmException | IOException e) {
            throw new RuntimeException(e);
        }
        return text;
    }

    private List<String[]> createHashTableEntries(PEData data) {
        List<String[]> entries = new ArrayList<>();
        SectionTable sectionTable = data.getSectionTable();
        try {
            MessageDigest sha256 = MessageDigest.getInstance("SHA-256");
            MessageDigest md5 = MessageDigest.getInstance("MD5");
            Hasher hasher = new Hasher(data);
            for (SectionHeader s : sectionTable.getSectionHeaders()) {
                String secName = s.getName();
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

    private String getDebugInfo(PEData pedata) {
        String debugStr = "";
        try {
            Optional<DebugSection> sec = new SectionLoader(pedata).maybeLoadDebugSection();
            if (sec.isPresent()) {
                DebugSection debugSection = sec.get();
                return debugSection.getInfo();
            }
        } catch (IOException e) {
            LOGGER.error(e);
            e.printStackTrace();
        }
        return debugStr;
    }

    private List<ExportEntry> getExports(PEData pedata) {
        try {
            Optional<ExportSection> maybeEdata = new SectionLoader(pedata).maybeLoadExportSection();
            if (maybeEdata.isPresent()) {
                ExportSection edata = maybeEdata.get();
                return edata.getExportEntries();
            }
        } catch (IOException e) {
            LOGGER.error(e);
            e.printStackTrace();
        }
        return new ArrayList<>();
    }

    private List<String[]> createExportTableEntries(PEData pedata) {
        List<String[]> entries = new ArrayList<>();
        List<ExportEntry> exports = getExports(pedata);
        for (ExportEntry e : exports) {
            // TODO this is a hack because of lacking support to check for export names, *facepalm*
            String name = e instanceof ExportNameEntry ? ((ExportNameEntry) e).name() : "";
            String forwarder = e.maybeGetForwarder().isPresent() ? e.maybeGetForwarder().get() : "";
            String[] entry = {name, String.valueOf(e.ordinal()), toHex(e.symbolRVA()), forwarder};
            entries.add(entry);
        }
        return entries;
    }

    private String readVersionInfo(PEData pedata) {
        List<Resource> res = getResources(pedata);
        String versionInfo = "No Version Info";
        for (Resource r : res) {
            if (r.getType().equals("RT_VERSION")) {
                versionInfo = VersionInfo.apply(r, pedata.getFile()).toString(); // TODO maybe consider several RT_VERSION resources?
                break;
            }
        }
        return versionInfo;
    }

    private boolean isLegitManifest(Resource resource) {
        long offset = resource.rawBytesLocation().from();
        long size = resource.rawBytesLocation().size();
        return resource.getType().equals("RT_MANIFEST") && offset > 0 && size > 0 && size < MAX_MANIFEST_SIZE;
    }

    private String bytesToUTF8(byte[] bytes) {
        return new String(bytes, StandardCharsets.UTF_8).trim();
    }

    private byte[] readBytes(Resource resource, PEData pedata) throws IOException {
        return IOUtil.loadBytesSafely(resource.rawBytesLocation().from(), (int) resource.rawBytesLocation().size(),
                new RandomAccessFile(pedata.getFile(), "r"));
    }

    private String toHex(Long num) {
        return "0x" + Long.toHexString(num);
    }

    private String readManifest(PEData pedata) {
        try {
            Optional<ResourceSection> res = new SectionLoader(pedata).maybeLoadResourceSection();
            if (res.isPresent() && !res.get().isEmpty()) {
                List<Resource> resources = res.get().getResources();
                for (Resource r : resources) {
                    if (isLegitManifest(r)) {
                        return bytesToUTF8(readBytes(r, pedata));
                    }
                }
            }
        } catch (IOException e) {
            LOGGER.error(e);
            e.printStackTrace();
        }
        return "";
    }

    private List<String[]> createResourceTableEntries(PEData pedata) {
        List<Resource> resources = getResources(pedata);
        List<String[]> entries = new ArrayList<>();
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
            String[] entry = {r.getType(), nameId, langId, toHex(offset), toHex(size), signatures};
            entries.add(entry);
        }
        return entries;
    }

    /**
     * Obtain a list of resources without having to deal with exceptions.
     *
     * @return List of resources. Empty list if resources do not exist or could not be read
     */
    private List<Resource> getResources(PEData pedata) {
        try {
            Optional<ResourceSection> res = new SectionLoader(pedata).maybeLoadResourceSection();
            if (res.isPresent() && !res.get().isEmpty()) {
                return res.get().getResources();
            }
        } catch (IOException e) {
            LOGGER.error(e);
            e.printStackTrace();
        }
        return new ArrayList<>();
    }

    private List<String[]> createImportTableEntries(List<ImportDLL> imports) {
        List<String[]> entries = new ArrayList<>();
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
                String[] entry = {dll.getName(), category, imp.getName(), description, toHex(imp.getRVA()), imp.getHint() + ""};
                entries.add(entry);
            }
            for (OrdinalImport imp : dll.getOrdinalImports()) {
                String[] entry = {dll.getName(), "", imp.getOrdinal() + "", "", toHex(imp.getRVA()), ""};
                entries.add(entry);
            }
        }
        return entries;
    }

    private List<ImportDLL> extractImports(PEData data) {
        SectionLoader loader = new SectionLoader(data);
        try {
            Optional<ImportSection> maybeImports = loader.maybeLoadImportSection();
            if (maybeImports.isPresent() && !maybeImports.get().isEmpty()) {
                ImportSection importSection = maybeImports.get();
                return importSection.getImports();
            }
        } catch (IOException e) {
            LOGGER.error(e);
            e.printStackTrace();
        }
        return new ArrayList<>();
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
    protected void done() {
        try {
            FullPEData data = get();
            frame.setPeData(data);
        } catch (InterruptedException | ExecutionException e) {
            String message = "Could not load PE file! Reason: " + e.getMessage();
            LOGGER.warn(message);
            LOGGER.warn(e);
            e.printStackTrace();
            JOptionPane.showMessageDialog(null,
                    message,
                    "Unable to load",
                    JOptionPane.ERROR_MESSAGE);
        }

    }

    // TODO build into file loading
    // TODO add progress bar


}
