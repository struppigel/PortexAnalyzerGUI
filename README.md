# PortexAnalyzerGUI

Graphical interface for PortEx, a Portable Executable and Malware Analysis Library

![visualizer example](https://github.com/struppigel/PortexAnalyzerGUI/raw/main/resources/screenshot.png)

![visualizer example](https://github.com/struppigel/PortexAnalyzerGUI/raw/main/resources/screenshot2.png)

## Download

[Releases page](https://github.com/struppigel/PortexAnalyzerGUI/releases)

## Features

* Header information from: MSDOS Header, Rich Header, COFF File Header, Optional Header, Section Table, .NET Metadata
* PE Structures: Import Section, Resource Section, Export Section, Debug Section
* Scanning for file format anomalies and reversing hints for certain combinations
* Visualize file structure, local entropies and byteplot, and save it as PNG
* Calculate Shannon Entropy, Imphash, MD5, SHA256, Rich and RichPV hash
* Overlay and overlay signature scanning, dump or remove overlay
* Version information and manifest
* Icon extraction and saving as PNG
* Customized signature scanning via Yara. Internal signature scans using PEiD signatures and an internal filetype scanner.

## Supported OS and JRE

I test this program on Linux and Windows. But it should work on any OS with JRE version 9 or higher.

## Future

I will be including more and more features that PortEx already provides.

These features include among others:

* customized visualization
* extraction and conversion of icons to .ICO files
* dumping of sections, resources
* export reports to txt, json, csv

Some of these features are already provided by PortexAnalyzer CLI version, which you can find here: [PortexAnalyzer CLI](https://github.com/struppigel/PortEx/tree/master/progs)

## Donations

I develop PortEx and PortexAnalyzer as a hobby in my free time. If you like it, please consider buying me a coffee: https://ko-fi.com/struppigel

## Author

Karsten Hahn 

Twitter: [@Struppigel](https://twitter.com/struppigel)

Mastodon: [struppigel@infosec.exchange](https://infosec.exchange/@struppigel)

Youtube: [MalwareAnalysisForHedgehogs](https://www.youtube.com/c/MalwareAnalysisForHedgehogs)

## License

[License](https://github.com/struppigel/PortexAnalyzerGUI/blob/main/LICENSE)
