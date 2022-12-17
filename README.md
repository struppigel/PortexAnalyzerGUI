# PortexAnalyzerGUI

Graphical interface for PortEx, a Portable Executable and Malware Analysis Library

![visualizer example](https://github.com/struppigel/PortexAnalyzerGUI/raw/main/resources/screenshot.png)

### Features

* Header information from: MSDOS Header, Rich Header, COFF File Header, Optional Header, Section Table
* PE Structures: Import Section, Resource Section, Export Section, Debug Section
* Scanning for file format anomalies, including structural anomalies, deprecated, reserved, wrong or non-default values.
* Visualize file structure, local entropies and byteplot
* Calculate Shannon Entropy, imphash, MD5, SHA256, Rich and RichPV hash
* Overlay and overlay signature scanning
* Version information and manifest

### Future

I will be including more and more features that PortEx already provides.
If you need them right now, check the PortexAnalyzer CLI version, which you can find here: [PortexAnalyzer CLI](https://github.com/struppigel/PortEx/tree/master/progs)

These features include among others:

* customized visualization
* extraction and conversion of icons to .ICO files
* dumping of sections, overlay, resources
* customized signature scanning
