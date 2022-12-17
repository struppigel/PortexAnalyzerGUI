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

import com.github.katjahahn.parser.PEData;
import com.github.katjahahn.parser.sections.SectionLoader;
import com.github.katjahahn.parser.sections.edata.ExportEntry;
import com.github.katjahahn.parser.sections.idata.ImportDLL;
import com.github.katjahahn.parser.sections.rsrc.Resource;
import com.github.katjahahn.parser.sections.rsrc.ResourceSection;
import com.github.katjahahn.tools.Overlay;
import com.google.common.base.Optional;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import java.io.File;
import java.io.IOException;
import java.util.List;

/**
 * Wrapper for the PEData.
 * This class makes sure to save things that we do not want to compute several times.
 */

// TODO every non-getter method in here probably belongs into PEData! These are API shortcomings
// TODO preload the special sections with SwingWorker
public class FullPEData {
    private static final Logger LOGGER = LogManager.getLogger();
    private final PEData pedata;
    private final Overlay overlay;
    private final double overlayEntropy;
    private final List<String> overlaySignatures;
    private final double[] sectionEntropies;
    private final List<ImportDLL> imports;
    private final List<String[]> importTableEntries;

    private final List<String[]> resourceTableEntries;
    private final List<Resource> resources;
    private final String manifest;
    private final String versionInfo;
    private final List<ExportEntry> exports;
    private final String hashes;
    private List<String[]> exportTableEntries;
    private String debugInfo;
    private String anomalyReport;
    private List<String[]> sectionHashTableEntries;

    public FullPEData(PEData data, Overlay overlay, double overlayEntropy, List<String> overlaySignatures,
                      double[] sectionEntropies, List<ImportDLL> imports, List<String[]> importTableEntries,
                      List<String[]> resourceTableEntries, List<Resource> resources, String manifest,
                      String versionInfo, List<String[]> exportTableEntries, List<ExportEntry> exports, String debugInfo,
                      String anomalyReport, String hashes,List<String[]> sectionHashTableEntries) {
        this.pedata = data;
        this.overlay = overlay;
        this.overlayEntropy = overlayEntropy;
        this.overlaySignatures = overlaySignatures;
        this.sectionEntropies = sectionEntropies;
        this.imports = imports;
        this.importTableEntries = importTableEntries;
        this.resourceTableEntries = resourceTableEntries;
        this.resources = resources;
        this.manifest = manifest;
        this.versionInfo = versionInfo;
        this.exportTableEntries = exportTableEntries;
        this.exports = exports;
        this.debugInfo = debugInfo;
        this.anomalyReport = anomalyReport;
        this.hashes = hashes;
        this.sectionHashTableEntries = sectionHashTableEntries;
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

    public List<String[]> getImportTableEntries() {
        return importTableEntries;
    }


    public boolean hasManifest() {
        return !getManifest().equals("");
    }

    /**
     * Obtain manifest as string. Returns empty string if no manifest exists, could not be read or is too large.
     * @return UTF-8 string of manifest, or empty string if not exists
     */
    public String getManifest() {
        return manifest;
    }



    /**
     * Check for presence of resources,
     * @return true of at least one resource exists, false if not there or if exceptions occur
     */
    public boolean hasResources() {
        try {
            Optional<ResourceSection> res = new SectionLoader(pedata).maybeLoadResourceSection();
            if (res.isPresent() && !res.get().isEmpty()) {
                List<Resource> resources = res.get().getResources();
                return resources.size() > 0;
            }
        } catch (IOException e) {
            LOGGER.error(e);
            e.printStackTrace();
        }
        return false;
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
        List<Resource> res = getResources();
        return res.stream().anyMatch(r -> r.getType().equals("RT_VERSION"));
    }
    public String getVersionInfo() {
        return versionInfo;
    }

    public double getEntropyForSection(int secNumber) {
        return sectionEntropies[secNumber-1] * 8;
    }

    public boolean hasImports() {
        return imports.size() > 0;
    }

    public List<String[]> getResourceTableEntries() {
        return resourceTableEntries;
    }

    public List<Resource> getResources() {
        return resources;
    }

    public boolean hasExports() {
        return exports.size() > 0;
    }

    public List<String[]> getExportTableEntries() {
        return this.exportTableEntries;
    }

    public boolean hasDebugInfo() {
        return !debugInfo.equals("");
    }

    public String getDebugInfo() {
        return this.debugInfo;
    }

    public String getAnomalyReport() {
        return this.anomalyReport;
    }

    public String getHashesReport() {
        return this.hashes;
    }

    public List<String[]> getSectionHashTableEntries() {
        return this.sectionHashTableEntries;
    }
}