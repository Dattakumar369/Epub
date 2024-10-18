package com.example.demo;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.List;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;

import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.text.PDFTextStripper;
import org.apache.pdfbox.text.TextPosition;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;
@Service
public class EpubService3 {
    private static final Logger logger = LoggerFactory.getLogger(EpubService.class);

    // Base directory for temporary EPUB creation
    private String epubBaseDir;
    private String oebpsDir;
    private String metaInfDir;
    private String imagesDir;
    private String stylesDir;
    private String xhtmlDir;
    private int imageCounter = 1; // Global image counter

    private void setupDirectories() throws IOException {
        epubBaseDir = Files.createTempDirectory("epub").toString();
        oebpsDir = Paths.get(epubBaseDir, "OEBPS").toString();
        metaInfDir = Paths.get(epubBaseDir, "META-INF").toString();
        imagesDir = Paths.get(oebpsDir, "images").toString();
        stylesDir = Paths.get(oebpsDir, "styles").toString();
        xhtmlDir = Paths.get(oebpsDir, "xhtml").toString();

        Files.createDirectories(Paths.get(metaInfDir));
        Files.createDirectories(Paths.get(oebpsDir));
        Files.createDirectories(Paths.get(imagesDir));
        Files.createDirectories(Paths.get(stylesDir));
        Files.createDirectories(Paths.get(xhtmlDir));
    }

    public String createEpubFromPdf(MultipartFile file) throws Exception {
        try {
            setupDirectories();
            PDDocument document = PDDocument.load(file.getInputStream());
            List<String> xhtmlFiles = extractContent(document);
            createStylesheet();
            createContentOpf(xhtmlFiles);
            createNavXhtml(xhtmlFiles);
            createContainerXml();
            createMimetypeFile();
            return packageEpub();
        } catch (IOException e) {
            logger.error("Error creating EPUB from PDF: {}", e.getMessage());
            throw new Exception("Failed to create EPUB", e);
        } finally {
            deleteTempDirectories();
        }
    }

    private void createStylesheet() throws IOException {
        String cssContent = "body { font-family: Arial, sans-serif; }\n" +
                "h1, h2, h3 { color: #333; }\n" +
                "p { margin: 0.5em 0; }\n" +
                "table { width: 100%; border-collapse: collapse; }\n" +
                "table, th, td { border: 1px solid black; }\n" +
                "th, td { padding: 0.5em; text-align: left; }\n";

        Path cssPath = Paths.get(stylesDir, "styles.css");
        Files.write(cssPath, cssContent.getBytes());
    }

    private String packageEpub() throws IOException {
        String epubFilePath = epubBaseDir + ".epub";
        try (ZipOutputStream zipOut = new ZipOutputStream(Files.newOutputStream(Paths.get(epubFilePath)))) {
            // Add the mimetype file first (required by EPUB specification)
            ZipEntry mimetypeEntry = new ZipEntry("mimetype");
            zipOut.putNextEntry(mimetypeEntry);
            zipOut.write(Files.readAllBytes(Paths.get(epubBaseDir, "mimetype")));
            zipOut.closeEntry();

            // Add META-INF directory
            ZipEntry metaInfEntry = new ZipEntry("META-INF/");
            zipOut.putNextEntry(metaInfEntry);
            zipOut.closeEntry();

            // Add META-INF/container.xml
            ZipEntry containerEntry = new ZipEntry("META-INF/container.xml");
            zipOut.putNextEntry(containerEntry);
            zipOut.write(Files.readAllBytes(Paths.get(metaInfDir, "container.xml")));
            zipOut.closeEntry();

            // Add OEBPS directory
            ZipEntry oebpsEntry = new ZipEntry("OEBPS/");
            zipOut.putNextEntry(oebpsEntry);
            zipOut.closeEntry();

            // Add OEBPS content.opf
            ZipEntry contentOpfEntry = new ZipEntry("OEBPS/content.opf");
            zipOut.putNextEntry(contentOpfEntry);
            zipOut.write(Files.readAllBytes(Paths.get(oebpsDir, "content.opf")));
            zipOut.closeEntry();

            // Add OEBPS nav.xhtml
            ZipEntry navEntry = new ZipEntry("OEBPS/nav.xhtml");
            zipOut.putNextEntry(navEntry);
            zipOut.write(Files.readAllBytes(Paths.get(xhtmlDir, "nav.xhtml")));
            zipOut.closeEntry();

            // Add OEBPS xhtml files
            for (String xhtmlFile : Files.list(Paths.get(xhtmlDir)).map(Path::getFileName).map(Path::toString).toList()) {
                ZipEntry xhtmlEntry = new ZipEntry("OEBPS/xhtml/" + xhtmlFile);
                zipOut.putNextEntry(xhtmlEntry);
                zipOut.write(Files.readAllBytes(Paths.get(xhtmlDir, xhtmlFile)));
                zipOut.closeEntry();
            }

            // Add OEBPS styles directory and CSS file
            ZipEntry stylesEntry = new ZipEntry("OEBPS/styles/");
            zipOut.putNextEntry(stylesEntry);
            zipOut.closeEntry();

            ZipEntry cssEntry = new ZipEntry("OEBPS/styles/styles.css");
            zipOut.putNextEntry(cssEntry);
            zipOut.write(Files.readAllBytes(Paths.get(stylesDir, "styles.css")));
            zipOut.closeEntry();

            // Add images directory
            ZipEntry imagesEntry = new ZipEntry("OEBPS/images/");
            zipOut.putNextEntry(imagesEntry);
            zipOut.closeEntry();

            // Add images
            for (String imageFile : Files.list(Paths.get(imagesDir)).map(Path::getFileName).map(Path::toString).toList()) {
                ZipEntry imageEntry = new ZipEntry("OEBPS/images/" + imageFile);
                zipOut.putNextEntry(imageEntry);
                zipOut.write(Files.readAllBytes(Paths.get(imagesDir, imageFile)));
                zipOut.closeEntry();
            }
        }
        return epubFilePath;
    }

    private void deleteTempDirectories() throws IOException {
        // Recursively delete the temporary EPUB base directory
        Path basePath = Paths.get(epubBaseDir);
        Files.walk(basePath).sorted((path1, path2) -> path2.compareTo(path1)) // Delete files first, then directories
                .forEach(path -> {
                    try {
                        Files.delete(path);
                    } catch (IOException e) {
                        e.printStackTrace();
                    }
                });
    }

    private void createContainerXml() throws IOException {
        String containerXmlContent = "<?xml version=\"1.0\"?>\n"
                + "<container version=\"1.0\" xmlns=\"urn:oasis:names:tc:opendocument:xmlns:container\">\n"
                + "<rootfiles>\n"
                + "<rootfile full-path=\"OEBPS/content.opf\" media-type=\"application/oebps-package+xml\"/>\n"
                + "</rootfiles>\n" + "</container>";

        Path containerXmlPath = Paths.get(metaInfDir, "container.xml");
        Files.write(containerXmlPath, containerXmlContent.getBytes());
    }

    private void createMimetypeFile() throws IOException {
        String mimetypeContent = "application/epub+zip";
        Path mimetypeFilePath = Paths.get(epubBaseDir, "mimetype");
        Files.write(mimetypeFilePath, mimetypeContent.getBytes());
    }

    private void createNavXhtml(List<String> xhtmlFiles) throws IOException {
        String navFilePath = xhtmlDir + "/nav.xhtml";
        Files.createDirectories(Paths.get(xhtmlDir));

        String navContent = "<?xml version=\"1.0\" encoding=\"UTF-8\"?>\n" +
                "<!DOCTYPE html>\n" +
                "<html xmlns=\"http://www.w3.org/1999/xhtml\">\n" +
                "<head>\n" +
                "    <title>Table of Contents</title>\n" +
                "</head>\n" +
                "<body>\n" +
                "    <nav xmlns:epub=\"http://www.idpf.org/2007/ops\" epub:type=\"toc\">\n" +
                "        <ol>\n" +
                "            <!-- Add your TOC items here -->\n" +
                "        </ol>\n" +
                "    </nav>\n" +
                "</body>\n" +
                "</html>";

        Files.write(Paths.get(navFilePath), navContent.getBytes());
    }

    private void createContentOpf(List<String> xhtmlFiles) throws IOException {
        StringBuilder contentOpf = new StringBuilder();
        contentOpf.append("<?xml version=\"1.0\" encoding=\"UTF-8\"?>\n").append(
                "<package xmlns=\"http://www.idpf.org/2007/opf\" unique-identifier=\"bookid\" version=\"3.0\">\n")
                .append("<metadata xmlns:dc=\"http://purl.org/dc/elements/1.1/\">\n")
                .append("<dc:title>Your Book Title</dc:title>\n")
                .append("<dc:creator>Your Name</dc:creator>\n")
                .append("<dc:identifier id=\"bookid\">urn:uuid:YOUR-UUID-HERE</dc:identifier>\n")
                .append("<dc:language>en</dc:language>\n")
                .append("</metadata>\n")
                .append("<manifest>\n");

        for (String xhtmlFile : xhtmlFiles) {
            contentOpf.append("<item id=\"").append(xhtmlFile).append("\" href=\"xhtml/")
                    .append(xhtmlFile).append("\" media-type=\"application/xhtml+xml\"/>\n");
        }

        // Add styles and images to the manifest
        contentOpf.append("<item id=\"styles\" href=\"styles/styles.css\" media-type=\"text/css\"/>\n");
        for (String imageFile : Files.list(Paths.get(imagesDir)).map(Path::getFileName).map(Path::toString).toList()) {
            contentOpf.append("<item id=\"").append(imageFile).append("\" href=\"images/")
                    .append(imageFile).append("\" media-type=\"image/jpeg\"/>\n");
        }

        contentOpf.append("</manifest>\n")
                .append("<spine>\n");
        for (String xhtmlFile : xhtmlFiles) {
            contentOpf.append("<itemref idref=\"").append(xhtmlFile).append("\"/>\n");
        }

        contentOpf.append("</spine>\n")
                .append("</package>");

        Path opfPath = Paths.get(oebpsDir, "content.opf");
        Files.write(opfPath, contentOpf.toString().getBytes());
    }


    
    
 // Custom PDFTextStripper to capture word positions
    class WordPDFTextStripper extends PDFTextStripper {
        private StringBuilder contentBuilder;

        public WordPDFTextStripper() throws IOException {
            super();
            contentBuilder = new StringBuilder();
        }

        @Override
        protected void writeString(String string, List<TextPosition> textPositions) throws IOException {
            // Iterate over each text position and capture word and its properties
            for (TextPosition text : textPositions) {
                String word = text.getUnicode().trim();
                if (!word.isEmpty()) {
                    contentBuilder.append("<span style=\"position:absolute; left:")
                            .append(text.getXDirAdj()).append("px; top:")
                            .append(text.getYDirAdj()).append("px; font-size:")
                            .append(text.getFontSizeInPt()).append("pt; white-space: nowrap;\">")
                            .append(word).append("</span>\n");
                }
            }
        }

        public String getExtractedContent() {
            return contentBuilder.toString();
        }
    }

      
        private List<String> extractContent(PDDocument document) throws IOException {
            List<String> xhtmlFiles = new ArrayList<>();
            PDFTextStripper textStripper = new PDFTextStripper();
            textStripper.setSortByPosition(true);  // Ensure lines are processed in the correct order

            int numberOfPages = document.getNumberOfPages();
            StringBuilder xmlContent = new StringBuilder();
            int sectionCount = 0;
            int paraCount = 0;
            int lineCount = 0;
            int codeCount = 0;
            int listItemCount = 0;
            boolean insideList = false;
            boolean insideCodeBlock = false;
            boolean insideTable = false;

            // Initialize paragraph outside the loop
            StringBuilder paragraph = new StringBuilder();

            for (int pageIndex = 0; pageIndex < numberOfPages; pageIndex++) {
                textStripper.setStartPage(pageIndex + 1);
                textStripper.setEndPage(pageIndex + 1);
                String pageText = textStripper.getText(document);

                xmlContent.append("<page id=\"page-").append(pageIndex + 1).append("\">\n");

                // Split the page text into lines
                String[] lines = pageText.split("\\r?\\n");

                for (String line : lines) {
                    lineCount++;
                    String trimmedLine = line.trim();

                    // Handle non-breaking characters and avoid invalid symbols
                    trimmedLine = trimmedLine.replace("\uFFFD", ""); // Remove replacement character
                    trimmedLine = trimmedLine.replaceAll("[^\\x20-\\x7E]+", ""); // Remove non-ASCII characters

                    // Detect if the line is a title (capital letters and no punctuation)
                    if (trimmedLine.matches("^[A-Z ]+$") && !trimmedLine.contains(".") && trimmedLine.length() > 3) {
                        sectionCount++;
                        // Flush any paragraph before starting a new section
                        paraCount = flushParagraph(xmlContent, paragraph, paraCount);
                        xmlContent.append("<section id=\"sec-").append(sectionCount).append("\">\n");
                        xmlContent.append("<title>").append(trimmedLine).append("</title>");
                    } else if (trimmedLine.startsWith("TABLE")) {
                        // Start a new table with borders
                        if (!insideTable) {
                            xmlContent.append("<table id=\"table-")
                                      .append(sectionCount)
                                      .append("\" style=\"border-collapse: collapse; width: 100%; border: 1px solid black;\">");
                            insideTable = true;
                        }
                        // Here you can add your logic to parse table rows and columns
                    } else if (insideTable) {
                        // Handle table rows with borders
                        xmlContent.append("<tr style=\"border: 1px solid black;\">");
                        String[] columns = trimmedLine.split("\\s+"); // Split columns based on whitespace
                        for (String column : columns) {
                            xmlContent.append("<td style=\"border: 1px solid black; padding: 8px;\">")
                                      .append(column)
                                      .append("</td>"); // Add each column in a table cell with borders and padding
                        }
                        xmlContent.append("</tr>");
                    }
 else {
                        // Detect if the line is a bulleted list item
                        String[] bulletSymbols = {"•", "○", "▪", "❑", "✓", "–"};
                        boolean isBullet = false;

                        for (String symbol : bulletSymbols) {
                            if (trimmedLine.startsWith(symbol)) {
                                isBullet = true;
                                break;
                            }
                        }

                        if (isBullet) {
                            listItemCount++;
                            if (!insideList) {
                                xmlContent.append("<list style=\"bulleted\">\n");
                                insideList = true;
                            }
                            // Add the list item with count and style
                            xmlContent.append("<listItem id=\"li-")
                                      .append(String.format("%04d", listItemCount))
                                      .append("\"> ")
                                      .append(trimmedLine.substring(1).trim())
                                      .append("</listItem>\n");
                        } else {
                            // If we were inside a bulleted list, close it
                            if (insideList) {
                                xmlContent.append("</list>\n");
                                insideList = false;
                            }

                            // Detect if the line contains code
                            if (trimmedLine.matches(".*\\{.*|.*\\;.*|.*\\(.*\\).*")) {
                                if (!insideCodeBlock) {
                                    // Start a new code block
                                    paraCount = flushParagraph(xmlContent, paragraph, paraCount);
                                    codeCount++;
                                    xmlContent.append("<computerCode xml:id=\"code-")
                                              .append(String.format("%04d", codeCount)).append("\">\n");
                                    xmlContent.append("<lineatedText numberLines=\"no\" space=\"preserve\" id=\"lntxt-")
                                              .append(String.format("%04d", codeCount)).append("\">\n");
                                    insideCodeBlock = true;
                                }
                                // Add the code line with style
                                xmlContent.append("<line id=\"line-")
                                          .append(String.format("%04d", lineCount))
                                          .append("\"> <![CDATA[")
                                          .append(trimmedLine)
                                          .append("]]></line>\n");
                            } else {
                                // If we are inside a code block, close it
                                if (insideCodeBlock) {
                                    xmlContent.append("</lineatedText>\n</computerCode>\n");
                                    insideCodeBlock = false;
                                }
                                // Handle regular paragraph text (concatenating lines)
                                if (!trimmedLine.isEmpty()) {
                                    // Detect formatting (bold and italic) if applicable
                                    String formattedLine = formatText(trimmedLine);
                                    
                                    if (paragraph.length() > 0) {
                                        paragraph.append(" "); // Add space between concatenated lines
                                    }
                                    paragraph.append(formattedLine);

                                    // If the line ends with punctuation, it's the end of a paragraph
                                    if (trimmedLine.endsWith(".") || trimmedLine.endsWith("!") || trimmedLine.endsWith("?")) {
                                        paraCount = flushParagraph(xmlContent, paragraph, paraCount);
                                    }
                                }
                            }
                        }
                    }
                }

                // Close any open structures at the end of the page
                if (insideList) {
                    xmlContent.append("</list>\n");
                    insideList = false;
                }
                if (insideCodeBlock) {
                    xmlContent.append("</lineatedText>\n</computerCode>\n");
                    insideCodeBlock = false;
                }
                if (insideTable) {
                    xmlContent.append("</table>\n");
                    insideTable = false; // Close the table after finishing rows
                }

                xmlContent.append("</page>\n");
            }

            // Finalize any remaining paragraph
            paraCount = flushParagraph(xmlContent, paragraph, paraCount);

            // Create the XHTML file for the current page
            String xhtmlFile = "output.xhtml";
            createXhtmlFile(xhtmlFile, xmlContent.toString());
            xhtmlFiles.add(xhtmlFile);

            document.close();
            return xhtmlFiles;
        }

        // Method to format text for bold and italic
        private String formatText(String line) {
            String formattedLine = line;
            if (line.contains("*")) { // Example for bold
              //  formattedLine = formattedLine.replaceAll("\\*(.*?)\\*", "<b>$1</b>");
            }
            if (line.contains("_")) { // Example for italic
               // formattedLine = formattedLine.replaceAll("_(.*?)_", "<i>$1</i>");
            }
            return formattedLine;
        }

        private int flushParagraph(StringBuilder xmlContent, StringBuilder paragraph, int paraCount) {
            if (paragraph.length() > 0) {
                paraCount++;
                xmlContent.append("<p id=\"para-").append(paraCount).append("\">")
                          .append(paragraph.toString().trim()).append("</p>\n");
                paragraph.setLength(0); // Reset the paragraph
            }
            return paraCount;
        }

        private void createXhtmlFile(String fileName, String content) throws IOException {
            String xhtmlContent = "<?xml version=\"1.0\" encoding=\"UTF-8\"?>\n" +
                                  "<!DOCTYPE html>\n<html xmlns=\"http://www.w3.org/1999/xhtml\">\n" +
                                  "<head>\n<title>Converted XHTML</title>\n</head>\n<body>\n" +
                                  content + "</body>\n</html>";
            Files.write(Paths.get(xhtmlDir, fileName), xhtmlContent.getBytes());
        }

        
}