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

import io.github.struppigel.tools.layout.FileRegion;
import io.github.struppigel.tools.layout.RegionType;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;
import java.util.List;
import java.util.Optional;

/**
 * Finds the nodes of the {@link PEComponentTree} that show a structure of the file.
 */
final class RegionTreeLabels {

    private RegionTreeLabels() {
    }

    /**
     * @param region the structure
     * @param clrTableNames the labels of the CLR table nodes, e.g., "0x23 AssemblyRef"
     * @return the labels of the tree nodes that show the structure, the best one first; the caller picks the first
     *         one that exists in the tree
     */
    static List<String> labelsFor(FileRegion region, Collection<String> clrTableNames) {
        List<String> labels = new ArrayList<>();
        FileRegion current = region;
        while (current != null) {
            labels.addAll(labelsForType(current, clrTableNames));
            Optional<FileRegion> parent = current.getParent();
            current = parent.isPresent() ? parent.get() : null;
        }
        return labels;
    }

    private static List<String> labelsForType(FileRegion region, Collection<String> clrTableNames) {
        RegionType type = region.getType();
        switch (type) {
            case HEADERS:
            case PE_SIGNATURE:
                return list("PE Format");
            case MSDOS_HEADER:
            case MSDOS_STUB:
                return list("MS DOS Stub");
            case RICH_HEADER:
                return list("Rich Header");
            case COFF_FILE_HEADER:
                return list("COFF File Header");
            case OPTIONAL_HEADER:
                return list("Optional Header");
            case STANDARD_FIELDS:
                return list("Standard Fields");
            case WINDOWS_FIELDS:
                return list("Windows Fields");
            case DATA_DIRECTORY:
            case DATA_DIRECTORY_ENTRY:
            case RELOCATION:
            case RELOCATION_BLOCK:
            case TLS_DIRECTORY:
            case LOAD_CONFIG:
            case EXCEPTION_TABLE:
            case ARCHITECTURE:
            case GLOBAL_PTR:
                return list("Data Directory");
            case CERTIFICATE_TABLE:
            case WIN_CERTIFICATE:
            case PKCS7_SIGNED_DATA:
            case CERTIFICATE:
            case CERTIFICATE_PADDING:
            case SIGNER_INFO:
                return list("Authenticode", "Data Directory");
            case IAT_DIRECTORY:
                return list("Imports", "Data Directory");
            case SECTION_TABLE:
            case SECTION_HEADER:
            case SECTION:
                return list("Section Table");
            case IMPORT_SECTION:
            case IMPORT_DLL:
            case IMPORT_DESCRIPTOR:
            case IMPORT_LOOKUP_TABLE:
            case IMPORT_ADDRESS_TABLE:
            case IMPORT_DLL_NAME:
            case IMPORT_HINT_NAME:
                return list("Imports");
            case DELAY_IMPORT_SECTION:
                return list("Delay Load Imports");
            case BOUND_IMPORT_SECTION:
                return list("Bound Imports");
            case EXPORT_SECTION:
            case EXPORT_DIRECTORY:
            case EXPORT_ADDRESS_TABLE:
            case EXPORT_NAME_POINTER_TABLE:
            case EXPORT_ORDINAL_TABLE:
            case EXPORT_NAME:
                return list("Exports");
            case DEBUG_DIRECTORY:
            case DEBUG_DATA:
            case CODEVIEW:
                return list("Debug");
            case RESOURCE:
                return resourceLabels(region.getAttributes().get(FileRegion.ATTR_RESOURCE_TYPE));
            case RESOURCE_DIRECTORY:
            case RESOURCE_DIRECTORY_TABLE:
            case RESOURCE_DATA_ENTRY:
            case RESOURCE_NAME:
                return list("Resources");
            case CLR_HEADER:
            case CLR_METADATA:
            case METADATA_ROOT:
                return list("Metadata Root");
            case CLR_STREAM:
                return "#~".equals(region.getAttributes().get(FileRegion.ATTR_STREAM_NAME))
                        || "#~".equals(region.getName()) ? list("#~", "Stream Headers") : list("Stream Headers");
            case CLR_TABLE:
                List<String> labels = new ArrayList<>();
                for (String name : clrTableNames) {
                    if (name.endsWith(" " + region.getName())) {
                        labels.add(name);
                    }
                }
                labels.add("#~");
                labels.add("Stream Headers");
                return labels;
            case ENTRY_POINT:
                return list("Standard Fields");
            case OVERLAY:
                return list("Overlay");
            case SIGNATURE_MATCH:
                return list("Signatures");
            case ANOMALY:
                return list("Anomalies");
            default:
                // header fields are found by their parent, unmapped bytes and own regions have no node
                return Collections.emptyList();
        }
    }

    private static List<String> resourceLabels(String resourceType) {
        if (resourceType == null) {
            return list("Resources");
        }
        switch (resourceType) {
            case "RT_MANIFEST":
                return list("Manifests", "Resources");
            case "RT_VERSION":
                return list("Version Info", "Resources");
            case "RT_ICON":
            case "RT_GROUP_ICON":
                return list("Icons", "Resources");
            case "RT_STRING":
                return list("String Table", "Resources");
            default:
                return list("Resources");
        }
    }

    private static List<String> list(String... labels) {
        return Arrays.asList(labels);
    }
}
