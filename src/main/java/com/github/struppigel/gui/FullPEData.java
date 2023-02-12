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
package com.github.struppigel.gui;

import com.github.katjahahn.parser.PEData;
import com.github.katjahahn.parser.sections.edata.ExportEntry;
import com.github.katjahahn.parser.sections.idata.ImportDLL;
import com.github.katjahahn.parser.sections.rsrc.Resource;
import com.github.katjahahn.parser.sections.rsrc.icon.IconParser;
import com.github.katjahahn.tools.Overlay;
import com.github.struppigel.gui.utils.TableContent;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import java.io.File;
import java.io.IOException;
import java.util.List;

/**
 * Wrapper for the PEData.
 * This class makes sure to save things that we do not want to compute several times and
 * definitely do not want to compute in the event dispatch threat.
 */
public class FullPEData {
    private static final Logger LOGGER = LogManager.getLogger();
    private final PEData pedata;
    private final Overlay overlay;
    private final double overlayEntropy;
    private final List<String> overlaySignatures;
    private final double[] sectionEntropies;
    private final List<ImportDLL> imports;
    private final List<Object[]> importTableEntries;
    private final List<Object[]> resourceTableEntries;
    private final List<Resource> resources;
    private final List<ExportEntry> exports;
    private final String hashes;
    private final List<Object[]> anomaliesTable;
    private final List<TableContent> debugTableEntries;
    private final List<Object[]> vsInfoTable;
    private final List<String> manifests;
    private List<Object[]> exportTableEntries;
    private List<Object[]> sectionHashTableEntries;
    private String signatureReport;
    private List<Object[]> stringTableEntries;

    public FullPEData(PEData data, Overlay overlay, double overlayEntropy, List<String> overlaySignatures,
                      double[] sectionEntropies, List<ImportDLL> imports, List<Object[]> importTableEntries,
                      List<Object[]> resourceTableEntries, List<Resource> resources, List<String> manifests,
                      List<Object[]> exportTableEntries, List<ExportEntry> exports,
                      String hashes, List<Object[]> sectionHashTableEntries,
                      List<Object[]> anomaliesTable, List<TableContent> debugTableEntries, List<Object[]> vsInfoTable,
                      String signatureReport, List<Object[]> stringTableEntries) {
        this.pedata = data;
        this.overlay = overlay;
        this.overlayEntropy = overlayEntropy;
        this.overlaySignatures = overlaySignatures;
        this.sectionEntropies = sectionEntropies;
        this.imports = imports;
        this.importTableEntries = importTableEntries;
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
        this.signatureReport = signatureReport;
        this.stringTableEntries = stringTableEntries;
    }

    public PEData getPeData() {
        return pedata;
    }

    public Overlay getOverlay() {
        return overlay;
    }

    public double getOverlayEntropy() {
        return overlayEntropy;
    }

    public List<String> getOverlaySignatures() {
        return overlaySignatures;
    }

    public File getFile() {
        return pedata.getFile();
    }

    public List<Object[]> getImportTableEntries() {
        return importTableEntries;
    }


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
        return resources.stream().anyMatch(r -> IconParser.isGroupIcon(r));
    }

    public String getSignatureReport() {
        return this.signatureReport;
    }

    public boolean hasRTStrings() {
        return !stringTableEntries.isEmpty();
    }

    public List<Object[]> getRTStringTableEntries() {
        return this.stringTableEntries;
    }
}
