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

These features include among others:

* customized visualization and save image as PNG
* extraction and conversion of icons to .ICO files
* dumping of sections, overlay, resources
* customized signature scanning
* export reports to txt, json, csv

Many of these features are already provided by PortexAnalyzer CLI version, which you can find here: [PortexAnalyzer CLI](https://github.com/struppigel/PortEx/tree/master/progs)

### Donations

I develop PortEx and PortexAnalyzer as a hobby in my freetime. If you like it, please consider buying me a coffee: https://ko-fi.com/struppigel

### Author

Karsten Hahn 

Twitter: [@Struppigel](https://twitter.com/struppigel)

Mastodon: [struppigel@infosec.exchange](https://infosec.exchange/@struppigel)

Youtube: [MalwareAnalysisForHedgehogs](https://www.youtube.com/c/MalwareAnalysisForHedgehogs)

### License

[License](https://github.com/struppigel/PortexAnalyzerGUI/blob/main/LICENSE)
