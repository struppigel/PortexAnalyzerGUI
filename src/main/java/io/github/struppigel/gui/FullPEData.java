/**
 * *****************************************************************************
 * Copyright 2022 Karsten Hahn
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

import io.github.struppigel.parser.PEData;
import io.github.struppigel.parser.StandardField;
import io.github.struppigel.parser.sections.cert.AuthenticodeVerification;
import io.github.struppigel.parser.sections.cert.CertificateTable;
import io.github.struppigel.parser.sections.clr.CLRSection;
import io.github.struppigel.parser.sections.clr.CLRTable;
import io.github.struppigel.parser.sections.clr.OptimizedStream;
import io.github.struppigel.parser.sections.edata.ExportEntry;
import io.github.struppigel.parser.sections.idata.ImportDLL;
import io.github.struppigel.parser.sections.rsrc.Resource;
import io.github.struppigel.parser.sections.rsrc.icon.IconParser;
import io.github.struppigel.tools.Overlay;
import io.github.struppigel.tools.anomalies.Anomaly;
import io.github.struppigel.tools.layout.StructureMap;
import io.github.struppigel.tools.sigscanner.SignatureMatch;
import io.github.struppigel.gui.utils.TableContent;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import java.io.File;
import java.io.IOException;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * Wrapper for the PE data. Contains all data that must be parsed from file and converted by a worker.
 * This class makes sure to save all the data that we do not want to compute several times and
 * definitely do not want to compute in the event dispatch thread.
 * It stores that data in a form that is easily digestible by the GUI and does not need intensive transformations.
 */
public class FullPEData {
    private static final Logger LOGGER = LogManager.getLogger();
    private final PEData pedata;
    private final Overlay overlay;
    private final double overlayEntropy;
    private final List<SignatureMatch> overlaySignatures;
    private final double[] sectionEntropies;
    private final List<ImportDLL> imports;
    private final List<Object[]> importTableEntries;
    private final List<Object[]> delayLoadEntries;

    private final List<Object[]> boundImportEntries;
    private final List<Object[]> resourceTableEntries;
    private final List<Resource> resources;
    private final List<ExportEntry> exports;
    private final String hashes;
    private final List<Object[]> anomaliesTable;
    private final List<TableContent> debugTableEntries;
    private final List<Object[]> vsInfoTable;
    private final List<String> manifests;

    private final List<Object[]> exportTableEntries;
    private final List<Object[]> sectionHashTableEntries;
    private final String rehintsReport;
    private final List<Object[]> stringTableEntries;

    private final List<StandardField> dotNetMetadataRootTableEntries;
    private final java.util.Optional<CLRSection> maybeCLR;
    private final List<Object[]> dotNetStreamHeaders;

    private final List<StandardField> optimizedStreamEntries;
    private final Map<String, List<List<Object>>> clrTables;
    private final Map<String, List<String>> clrTableHeaders;
    private final List<Anomaly> anomalies;
    private final StructureMap structureMap;
    private final Optional<CertificateTable> certificateTable;
    private final Optional<AuthenticodeVerification> authenticodeVerification;

    private final long OFFSET_DEFAULT = 0L;
    public FullPEData(PEData data, Overlay overlay, double overlayEntropy, List<SignatureMatch> overlaySignatures,
                      double[] sectionEntropies, List<ImportDLL> imports, List<Object[]> importTableEntries, List<Object[]> delayLoadEntries,
                      List<Object[]> boundImportEntries,
                      List<Object[]> resourceTableEntries, List<Resource> resources, List<String> manifests,
                      List<Object[]> exportTableEntries, List<ExportEntry> exports,
                      String hashes, List<Object[]> sectionHashTableEntries,
                      List<Object[]> anomaliesTable, List<TableContent> debugTableEntries, List<Object[]> vsInfoTable,
                      String rehintsReport, List<Object[]> stringTableEntries, List<StandardField> dotNetMetadataRootTableEntries,
                      java.util.Optional<CLRSection> maybeCLR, List<Object[]> dotNetStreamHeaders, List<StandardField> optimizedStreamEntries,
                      Map<String, List<List<Object>>> clrTables, Map<String, List<String>> clrTableHeaders,
                      List<Anomaly> anomalies, StructureMap structureMap,
                      Optional<CertificateTable> certificateTable,
                      Optional<AuthenticodeVerification> authenticodeVerification) {
        this.pedata = data;
        this.overlay = overlay;
        this.overlayEntropy = overlayEntropy;
        this.overlaySignatures = overlaySignatures;
        this.sectionEntropies = sectionEntropies;
        this.imports = imports;
        this.importTableEntries = importTableEntries;
        this.delayLoadEntries = delayLoadEntries;
        this.boundImportEntries = boundImportEntries;
        this.resourceTableEntries = resourceTableEntries;
        this.resources = resources;
        this.manifests = manifests;
        this.exportTableEntries = exportTableEntries;
        this.exports = exports;
        this.hashes = hashes;
        this.sectionHashTableEntries = sectionHashTableEntries;
        this.anomaliesTable = anomaliesTable;
        this.debugTableEntries = debugTableEntries;
        this.vsInfoTable = vsInfoTable;
        this.rehintsReport = rehintsReport;
        this.stringTableEntries = stringTableEntries;
        this.dotNetMetadataRootTableEntries = dotNetMetadataRootTableEntries;
        this.maybeCLR = maybeCLR;
        this.dotNetStreamHeaders = dotNetStreamHeaders;
        this.optimizedStreamEntries = optimizedStreamEntries;
        this.clrTables = clrTables;
        this.clrTableHeaders = clrTableHeaders;
        this.anomalies = anomalies;
        this.structureMap = structureMap;
        this.certificateTable = certificateTable;
        this.authenticodeVerification = authenticodeVerification;
    }

    public Map<String, List<String>> getClrTableHeaders() { return clrTableHeaders; }

    /**
     * @return the parsed certificate table, also if it is broken or empty; absent if the file declares none
     */
    public Optional<CertificateTable> getCertificateTable() { return certificateTable; }

    public boolean hasCertificateTable() { return certificateTable.isPresent(); }

    /**
     * @return the result of the integrity check of the signatures, absent if there is no certificate table
     */
    public Optional<AuthenticodeVerification> getAuthenticodeVerification() { return authenticodeVerification; }

    public List<Anomaly> getAnomalies() { return anomalies; }

    /**
     * @return the structures of the file by offset; built while loading, immutable and safe to use in any thread
     */
    public StructureMap getStructureMap() { return structureMap; }

    public Map<String, List<List<Object>>> getClrTables() { return clrTables; }

    public PEData getPeData() {
        return pedata;
    }

    public Overlay getOverlay() {
        return overlay;
    }

    public double getOverlayEntropy() {
        return overlayEntropy;
    }

    public List<SignatureMatch> getOverlaySignatures() {
        return overlaySignatures;
    }

    public File getFile() {
        return pedata.getFile();
    }

    public List<Object[]> getImportTableEntries() {
        return importTableEntries;
    }

    public List<Object[]> getDelayLoadEntries() {return delayLoadEntries;}

    public List<Object[]> getBoundImportEntries() {return boundImportEntries;}

    public boolean hasBoundImportEntries() {return !boundImportEntries.isEmpty();}

    public boolean hasManifest() {
        return getManifests().size() > 0;
    }

    /**
     * Obtain manifest as string. Returns empty string if no manifest exists, could not be read or is too large.
     * @return UTF-8 string of manifest, or empty string if not exists
     */
    public List<String> getManifests() {
        return manifests;
    }

    /**
     * Check for presence of resources,
     * @return true of at least one resource exists, false if not there or if exceptions occur
     */
    public boolean hasResources() {
        return resources.size() > 0;
    }

    /**
     * Check overlay presence without dealing  with exceptions from reading. Returns false if exception occurs.
     * @return true if overlay exists, false otherwise or if exception occurs while reading
     */
    public boolean overlayExists() {
        try {
            if (new Overlay(pedata).exists()) {
                return true;
            }
        } catch (IOException e) {
            LOGGER.error(e);
            e.printStackTrace();
        }
        return false;
    }

    public boolean hasVersionInfo() {
        return resources.stream().anyMatch(r -> r.getType().equals("RT_VERSION"));
    }

    public double getEntropyForSection(int secNumber) {
        return sectionEntropies[secNumber-1] * 8;
    }

    public boolean hasImports() {
        return imports.size() > 0;
    }

    public boolean hasDelayLoadImports() {
        return delayLoadEntries.size() > 0;
    }

    public List<Object[]> getResourceTableEntries() {
        return resourceTableEntries;
    }

    public List<Resource> getResources() {
        return resources;
    }

    public boolean hasExports() {
        return exports.size() > 0;
    }

    public List<Object[]> getExportTableEntries() {
        return this.exportTableEntries;
    }

    public boolean hasDebugInfo() {
        return debugTableEntries.size() > 0;
    }

    public String getHashesReport() {
        return this.hashes;
    }

    public List<Object[]> getSectionHashTableEntries() {
        return this.sectionHashTableEntries;
    }

    public List<Object[]> getAnomaliesTable() {
        return anomaliesTable;
    }

    public List<TableContent> getDebugTableEntries() {
        return debugTableEntries;
    }


    public List<Object[]> getVersionInfoTable() {
        return vsInfoTable;
    }

    public boolean hasIcons() {
        return resources.stream().anyMatch(IconParser::isGroupIcon);
    }

    public String getReHintsReport() {
        return this.rehintsReport;
    }

    public boolean hasRTStrings() {
        return !stringTableEntries.isEmpty();
    }

    public List<Object[]> getRTStringTableEntries() {
        return this.stringTableEntries;
    }

    public boolean isDotNet() { return !dotNetMetadataRootTableEntries.isEmpty(); }

    public boolean hasOptimizedStream() { return !optimizedStreamEntries.isEmpty(); }

    public List<StandardField> getDotNetMetadataRootEntries() {
        return dotNetMetadataRootTableEntries;
    }

    public long getDotNetMetadataRootOffset(){
        if(maybeCLR.isPresent() && !maybeCLR.get().isEmpty()) {
            return maybeCLR.get().getMetadataRoot().getOffset();
        } else {
            return OFFSET_DEFAULT;
        }
    }

    public String getDotNetMetadataVersionString(){
        if(maybeCLR.isPresent() && !maybeCLR.get().isEmpty()) {
            return maybeCLR.get().getMetadataRoot().getVersionString();
        } else {
            return "<no metadata root version string>";
        }
    }

    public java.util.Optional<CLRTable> getClrTableForName(String name) {
        Optional<CLRSection> clr = getPeData().loadClrSection();
        if(clr.isPresent()) {
            Optional<OptimizedStream> optStream = clr.get().getMetadataRoot().maybeGetOptimizedStream();
            if (optStream.isPresent()) {
                // compare full names, e.g. "0x23 AssemblyRef", because table names may contain other table names
                return optStream.get().getCLRTables().stream().filter(t -> name.equals(clrTableName(t))).findFirst();
            }
        }
        return Optional.empty();
    }

    // same format as the keys of PEData.loadCLRTables()
    private static String clrTableName(CLRTable table) {
        return String.format("%0#4x %s", table.getIndex(), table.getTableName());
    }

    public long getClrTableOffset(String tableName) {
        java.util.Optional<CLRTable> clrTable = getClrTableForName(tableName);
        return clrTable.map(CLRTable::getRawOffset).orElse(OFFSET_DEFAULT);
    }

    public List<Object[]> getDotNetStreamHeaderEntries() {
        return this.dotNetStreamHeaders;
    }

    public List<StandardField> getOptimizedStreamEntries() {
        return this.optimizedStreamEntries;
    }
}
