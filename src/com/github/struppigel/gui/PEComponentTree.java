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

import com.github.struppigel.gui.pedetails.PEDetailsPanel;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import javax.swing.*;
import javax.swing.tree.DefaultMutableTreeNode;
import javax.swing.tree.DefaultTreeCellRenderer;
import javax.swing.tree.DefaultTreeModel;
import javax.swing.tree.TreePath;
import java.util.Collections;
import java.util.List;

/**
 * The tree on the left side
 */
public class PEComponentTree extends JPanel {

    private static final Logger LOGGER = LogManager.getLogger();
    private static final String DOS_STUB_TEXT = "MS DOS Stub";
    private static final String COFF_FILE_HEADER_TEXT = "COFF File Header";
    private static final String OPTIONAL_HEADER_TEXT = "Optional Header";
    private static final String STANDARD_FIELDS_TEXT = "Standard Fields";
    private static final String WINDOWS_FIELDS_TEXT = "Windows Fields";
    private static final String DATA_DIRECTORY_TEXT = "Data Directory";
    private static final String SECTION_TABLE_TEXT = "Section Table";
    private static final String PE_HEADERS_TEXT = "PE Headers";
    private static final String OVERLAY_TEXT = "Overlay";
    private static final String RICH_TEXT = "Rich Header";
    private static final String MANIFEST_TEXT = "Manifests";
    private static final String RESOURCES_TEXT = "Resources";
    private static final String RT_STRING_TEXT = "String Table";
    private static final String VERSIONINFO_TEXT = "Version Info";
    private static final String IMPORTS_TEXT = "Imports";
    private static final String EXPORTS_TEXT = "Exports";
    private static final String DEBUG_TEXT = "Debug";
    private static final String ANOMALY_TEXT = "Anomalies";
    private static final String HASHES_TEXT = "Hashes";
    private static final String VISUALIZATION_TEXT = "Visualization";
    private static final String PE_FORMAT_TEXT = "PE Format";
    private static final String ICONS_TEXT = "Icons";
    private static final String SIGNATURES_TEXT = "Signatures";
    private final PEDetailsPanel peDetailsPanel;
    private FullPEData peData = null;
    private JTree peTree;

    public PEComponentTree(PEDetailsPanel peDetailsPanel) {
        this.peDetailsPanel = peDetailsPanel;
        initTree();
    }

    public void setPeData(FullPEData peData) {
        this.peData = peData;
        updateTree();
    }

    private void updateTree() {
        LOGGER.debug("Updating tree");
        DefaultMutableTreeNode root = (DefaultMutableTreeNode) peTree.getModel().getRoot();
        root.removeAllChildren();
        //create the child nodes
        DefaultMutableTreeNode pe = new DefaultMutableTreeNode(PE_FORMAT_TEXT);
        DefaultMutableTreeNode dosStub = new DefaultMutableTreeNode(DOS_STUB_TEXT);
        DefaultMutableTreeNode rich = new DefaultMutableTreeNode(RICH_TEXT);
        DefaultMutableTreeNode coff = new DefaultMutableTreeNode(COFF_FILE_HEADER_TEXT);
        DefaultMutableTreeNode optional = new DefaultMutableTreeNode(OPTIONAL_HEADER_TEXT);
        DefaultMutableTreeNode sections = new DefaultMutableTreeNode(SECTION_TABLE_TEXT);
        DefaultMutableTreeNode overlay = new DefaultMutableTreeNode(OVERLAY_TEXT);

        DefaultMutableTreeNode standard = new DefaultMutableTreeNode(STANDARD_FIELDS_TEXT);
        DefaultMutableTreeNode windows = new DefaultMutableTreeNode(WINDOWS_FIELDS_TEXT);
        DefaultMutableTreeNode datadir = new DefaultMutableTreeNode(DATA_DIRECTORY_TEXT);

        DefaultMutableTreeNode resources = new DefaultMutableTreeNode(RESOURCES_TEXT);
        DefaultMutableTreeNode manifest = new DefaultMutableTreeNode(MANIFEST_TEXT);
        DefaultMutableTreeNode version = new DefaultMutableTreeNode(VERSIONINFO_TEXT);
        DefaultMutableTreeNode icons = new DefaultMutableTreeNode(ICONS_TEXT);
        DefaultMutableTreeNode rtstrings = new DefaultMutableTreeNode(RT_STRING_TEXT);

        DefaultMutableTreeNode imports = new DefaultMutableTreeNode(IMPORTS_TEXT);
        DefaultMutableTreeNode exports = new DefaultMutableTreeNode(EXPORTS_TEXT);
        DefaultMutableTreeNode debug = new DefaultMutableTreeNode(DEBUG_TEXT);

        DefaultMutableTreeNode anomaly = new DefaultMutableTreeNode(ANOMALY_TEXT);
        DefaultMutableTreeNode hashes = new DefaultMutableTreeNode(HASHES_TEXT);
        DefaultMutableTreeNode vis = new DefaultMutableTreeNode(VISUALIZATION_TEXT);
        DefaultMutableTreeNode signatures = new DefaultMutableTreeNode(SIGNATURES_TEXT);

        optional.add(standard);
        optional.add(windows);
        optional.add(datadir);

        //add the child nodes to the root node
        root.add(pe);
        pe.add(dosStub);
        if (peData.getPeData().maybeGetRichHeader().isPresent()) {
            pe.add(rich);
        }
        pe.add(coff);
        pe.add(optional);
        pe.add(sections);
        if (peData.hasResources()) {
            pe.add(resources);
            if (peData.hasManifest()) {
                resources.add(manifest);
            }
            if(peData.hasVersionInfo()) {
                resources.add(version);
            }
            if(peData.hasIcons()){
                resources.add(icons);
            }
            if(peData.hasRTStrings()) {
                resources.add(rtstrings);
            }
        }

        if(peData.hasImports()){
            pe.add(imports);
        }

        if(peData.hasExports()){
            pe.add(exports);
        }

        if(peData.hasDebugInfo()) {
            pe.add(debug);
        }

        if (peData.overlayExists()) {
            pe.add(overlay);
            LOGGER.debug("Overlay added to root node of tree");
        }

        root.add(anomaly);
        root.add(hashes);
        root.add(vis);
        root.add(signatures);

        // no root
        peTree.setRootVisible(false);
        // reload the tree model to actually show the update
        DefaultTreeModel model = (DefaultTreeModel) peTree.getModel();
        model.reload();
        // expand the tree per default
        setTreeExpandedState(peTree, true);
    }

    // this method is from https://www.logicbig.com/tutorials/java-swing/jtree-expand-collapse-all-nodes.html
    private static void setTreeExpandedState(JTree tree, boolean expanded) {
        DefaultMutableTreeNode node = (DefaultMutableTreeNode) tree.getModel().getRoot();
        setNodeExpandedState(tree, node, expanded);
    }

    // this method is from https://www.logicbig.com/tutorials/java-swing/jtree-expand-collapse-all-nodes.html
    private static void setNodeExpandedState(JTree tree, DefaultMutableTreeNode node, boolean expanded) {
        List<DefaultMutableTreeNode> list = Collections.list(node.children());
        for (DefaultMutableTreeNode treeNode : list) {
            setNodeExpandedState(tree, treeNode, expanded);
        }
        if (!expanded && node.isRoot()) {
            return;
        }
        TreePath path = new TreePath(node.getPath());
        if (expanded) {
            tree.expandPath(path);
        } else {
            tree.collapsePath(path);
        }
    }

    public void setSelectionRow(int i) {
        peTree.setSelectionRow(i);
    }

    private void initTree() {
        //create the root node
        DefaultMutableTreeNode root = new DefaultMutableTreeNode("PE Format");

        //create the tree by passing in the root node
        this.peTree = new JTree(root);

        DefaultTreeCellRenderer renderer = (DefaultTreeCellRenderer) peTree.getCellRenderer();
        Icon leafIcon = new ImageIcon(getClass().getResource("/document-red-icon.png"));
        Icon openIcon = new ImageIcon(getClass().getResource("/Places-folder-red-icon.png"));
        Icon closeIcon = new ImageIcon(getClass().getResource("/folder-red-icon.png"));
        renderer.setLeafIcon(leafIcon);
        renderer.setOpenIcon(openIcon);
        renderer.setOpenIcon(closeIcon);

        add(peTree);
        peTree.setRootVisible(false);
        peTree.addTreeSelectionListener(e -> selectionChanged(e.getNewLeadSelectionPath()));
        this.setVisible(true);
    }

    private void selectionChanged(TreePath path) {
        if (path == null)
            return; // this happens when a selected node was removed, e.g., new file with no overlay loaded
        String node = path.getLastPathComponent().toString();
        LOGGER.debug("Tree selection changed to " + node);
        switch (node) {
            case DOS_STUB_TEXT:
                peDetailsPanel.showDosStub();
                break;
            case RICH_TEXT:
                peDetailsPanel.showRichHeader();
                break;
            case COFF_FILE_HEADER_TEXT:
                peDetailsPanel.showCoffFileHeader();
                break;
            case OPTIONAL_HEADER_TEXT:
                peDetailsPanel.showOptionalHeader();
                break;
            case STANDARD_FIELDS_TEXT:
                peDetailsPanel.showStandardFieldsTable();
                break;
            case WINDOWS_FIELDS_TEXT:
                peDetailsPanel.showWindowsFieldsTable();
                break;
            case DATA_DIRECTORY_TEXT:
                peDetailsPanel.showDataDirectoryTable();
                break;
            case SECTION_TABLE_TEXT:
                peDetailsPanel.showSectionTable();
                break;
            case PE_HEADERS_TEXT:
                peDetailsPanel.showPEHeaders();
                break;
            case OVERLAY_TEXT:
                peDetailsPanel.showOverlay();
                break;
            case MANIFEST_TEXT:
                peDetailsPanel.showManifests();
                break;
            case RESOURCES_TEXT:
                peDetailsPanel.showResources();
                break;
            case VERSIONINFO_TEXT:
                peDetailsPanel.showVersionInfo();
                break;
            case IMPORTS_TEXT:
                peDetailsPanel.showImports();
                break;
            case EXPORTS_TEXT:
                peDetailsPanel.showExports();
                break;
            case DEBUG_TEXT:
                peDetailsPanel.showDebugInfo();
                break;
            case ANOMALY_TEXT:
                peDetailsPanel.showAnomalies();
                break;
            case HASHES_TEXT:
                peDetailsPanel.showHashes();
                break;
            case VISUALIZATION_TEXT:
                peDetailsPanel.showVisualization();
                break;
            case PE_FORMAT_TEXT:
                peDetailsPanel.showPEFormat();
                break;
            case ICONS_TEXT:
                peDetailsPanel.showIcons();
                break;
            case SIGNATURES_TEXT:
                peDetailsPanel.showSignatures();
                break;
            case RT_STRING_TEXT:
                peDetailsPanel.showRTStrings();
                break;
        }
    }

    public void refreshSelection() {
        TreePath[] paths = peTree.getSelectionModel().getSelectionPaths();
        if(paths.length > 0) {
            // trigger selection change on same element to refresh the view
            selectionChanged(paths[0]);
        }
    }
}
