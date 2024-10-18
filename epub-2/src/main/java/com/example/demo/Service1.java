package com.example.demo;

import java.awt.image.BufferedImage;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.nio.file.*;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;

import javax.imageio.ImageIO;

import org.apache.pdfbox.cos.COSName;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.pdmodel.PDPage;
import org.apache.pdfbox.pdmodel.PDResources;
import org.apache.pdfbox.pdmodel.graphics.image.PDImageXObject;
import org.apache.pdfbox.text.PDFTextStripper;
import org.apache.pdfbox.text.TextPosition;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

@Service
public class Service1 {
    private static final Logger logger = LoggerFactory.getLogger(Service1.class);

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
            try (PDDocument document = PDDocument.load(file.getInputStream())) {
                // Extract images from the PDF
                List<String> extractedImages = extractImages(document);

                // Extract content and create XHTML files
                List<String> xhtmlFiles = extractContent(document, extractedImages);

                // Create necessary EPUB files
                createStylesheet();
                createContentOpf(xhtmlFiles);
                createNavXhtml(xhtmlFiles);
                createContainerXml();
                createMimetypeFile();

                // Package all components into an EPUB file
                return packageEpub();
            }
        } catch (IOException e) {
            logger.error("Error creating EPUB from PDF: {}", e.getMessage(), e);
            throw new Exception("Failed to create EPUB", e);
        } finally {
            deleteTempDirectories();
        }
    }

    private void createStylesheet() throws IOException {
        String cssContent = "body { font-family: Arial, sans-serif; margin: 1em; }\n" +
                "h1, h2, h3 { color: #333; }\n" +
                "p { margin: 0.5em 0; }\n" +
                "table { width: 100%; border-collapse: collapse; margin: 1em 0; }\n" +
                "table, th, td { border: 1px solid black; }\n" +
                "th, td { padding: 0.5em; text-align: left; }\n" +
                "ul { margin: 0.5em 0; padding-left: 1.5em; }\n" +
                "li { margin: 0.3em 0; }\n" +
                "pre { background-color: #f4f4f4; padding: 1em; overflow-x: auto; }\n" +
                "code { font-family: monospace; }\n";

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
            for (String xhtmlFile : Files.list(Paths.get(xhtmlDir))
                    .map(Path::getFileName)
                    .map(Path::toString)
                    .toList()) {
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
            for (String imageFile : Files.list(Paths.get(imagesDir))
                    .map(Path::getFileName)
                    .map(Path::toString)
                    .toList()) {
                ZipEntry imageEntry = new ZipEntry("OEBPS/images/" + imageFile);
                zipOut.putNextEntry(imageEntry);
                zipOut.write(Files.readAllBytes(Paths.get(imagesDir, imageFile)));
                zipOut.closeEntry();
            }
        }
        return epubFilePath;
    }

    private void deleteTempDirectories() {
        // Recursively delete the temporary EPUB base directory
        Path basePath = Paths.get(epubBaseDir);
        if (Files.exists(basePath)) {
            try {
                Files.walk(basePath)
                        .sorted((path1, path2) -> path2.compareTo(path1)) // Delete files first, then directories
                        .forEach(path -> {
                            try {
                                Files.delete(path);
                            } catch (IOException e) {
                                logger.warn("Failed to delete temporary file: {}", path, e);
                            }
                        });
            } catch (IOException e) {
                logger.warn("Failed to walk through temporary directory: {}", epubBaseDir, e);
            }
        }
    }

    private void createContainerXml() throws IOException {
        String containerXmlContent = "<?xml version=\"1.0\"?>\n"
                + "<container version=\"1.0\" xmlns=\"urn:oasis:names:tc:opendocument:xmlns:container\">\n"
                + "  <rootfiles>\n"
                + "    <rootfile full-path=\"OEBPS/content.opf\" media-type=\"application/oebps-package+xml\"/>\n"
                + "  </rootfiles>\n"
                + "</container>";

        Path containerXmlPath = Paths.get(metaInfDir, "container.xml");
        Files.write(containerXmlPath, containerXmlContent.getBytes());
    }

    private void createMimetypeFile() throws IOException {
        String mimetypeContent = "application/epub+zip";
        Path mimetypeFilePath = Paths.get(epubBaseDir, "mimetype");
        Files.write(mimetypeFilePath, mimetypeContent.getBytes());
    }

    private void createNavXhtml(List<String> xhtmlFiles) throws IOException {
        String navFilePath = Paths.get(xhtmlDir, "nav.xhtml").toString();
        Files.createDirectories(Paths.get(xhtmlDir));

        StringBuilder navContent = new StringBuilder();
        navContent.append("<?xml version=\"1.0\" encoding=\"UTF-8\"?>\n")
                .append("<!DOCTYPE html>\n")
                .append("<html xmlns=\"http://www.w3.org/1999/xhtml\">\n")
                .append("<head>\n")
                .append("    <title>Table of Contents</title>\n")
                .append("</head>\n")
                .append("<body>\n")
                .append("    <nav xmlns:epub=\"http://www.idpf.org/2007/ops\" epub:type=\"toc\">\n")
                .append("        <h1>Table of Contents</h1>\n")
                .append("        <ol>\n");

        // Dynamically add TOC items based on XHTML files
        for (String xhtmlFile : xhtmlFiles) {
            String title = xhtmlFile.replace(".xhtml", "").replace("-", " ").toUpperCase();
            navContent.append("            <li><a href=\"xhtml/")
                      .append(xhtmlFile)
                      .append("\">")
                      .append(title)
                      .append("</a></li>\n");
        }

        navContent.append("        </ol>\n")
                  .append("    </nav>\n")
                  .append("</body>\n")
                  .append("</html>");

        Files.write(Paths.get(navFilePath), navContent.toString().getBytes());
    }

    private void createContentOpf(List<String> xhtmlFiles) throws IOException {
        StringBuilder contentOpf = new StringBuilder();
        contentOpf.append("<?xml version=\"1.0\" encoding=\"UTF-8\"?>\n")
                  .append("<package xmlns=\"http://www.idpf.org/2007/opf\" unique-identifier=\"bookid\" version=\"3.0\">\n")
                  .append("  <metadata xmlns:dc=\"http://purl.org/dc/elements/1.1/\">\n")
                  .append("    <dc:title>Your Book Title</dc:title>\n")
                  .append("    <dc:creator>Your Name</dc:creator>\n")
                  .append("    <dc:identifier id=\"bookid\">urn:uuid:")
                  .append(UUID.randomUUID().toString())
                  .append("</dc:identifier>\n")
                  .append("    <dc:language>en</dc:language>\n")
                  .append("  </metadata>\n")
                  .append("  <manifest>\n");

        // Add XHTML files to manifest
        for (String xhtmlFile : xhtmlFiles) {
            contentOpf.append("    <item id=\"")
                      .append(xhtmlFile.replace(".xhtml", ""))
                      .append("\" href=\"xhtml/")
                      .append(xhtmlFile)
                      .append("\" media-type=\"application/xhtml+xml\"/>\n");
        }

        // Add styles and images to the manifest
        contentOpf.append("    <item id=\"styles\" href=\"styles/styles.css\" media-type=\"text/css\"/>\n");
        for (String imageFile : Files.list(Paths.get(imagesDir))
                .map(Path::getFileName)
                .map(Path::toString)
                .toList()) {
            String mediaType = getImageMediaType(imageFile);
            contentOpf.append("    <item id=\"")
                      .append(imageFile.replace(".", "-"))
                      .append("\" href=\"images/")
                      .append(imageFile)
                      .append("\" media-type=\"")
                      .append(mediaType)
                      .append("\"/>\n");
        }

        contentOpf.append("    <item id=\"nav\" href=\"nav.xhtml\" media-type=\"application/xhtml+xml\" properties=\"nav\"/>\n")
                  .append("  </manifest>\n")
                  .append("  <spine>\n");
        for (String xhtmlFile : xhtmlFiles) {
            contentOpf.append("    <itemref idref=\"")
                      .append(xhtmlFile.replace(".xhtml", ""))
                      .append("\"/>\n");
        }
        contentOpf.append("  </spine>\n")
                  .append("</package>");

        Path opfPath = Paths.get(oebpsDir, "content.opf");
        Files.write(opfPath, contentOpf.toString().getBytes());
    }

    // Helper method to determine media type based on image file extension
    private String getImageMediaType(String imageFile) {
        String lowerCase = imageFile.toLowerCase();
        if (lowerCase.endsWith(".jpg") || lowerCase.endsWith(".jpeg")) {
            return "image/jpeg";
        } else if (lowerCase.endsWith(".png")) {
            return "image/png";
        } else if (lowerCase.endsWith(".gif")) {
            return "image/gif";
        } else if (lowerCase.endsWith(".svg")) {
            return "image/svg+xml";
        } else {
            // Default to binary stream if unknown
            return "application/octet-stream";
        }
    }

    private List<String> extractContent(PDDocument document, List<String> extractedImages) throws IOException {
        List<String> xhtmlFiles = new ArrayList<>();
        PDFTextStripper textStripper = new PDFTextStripper();
        textStripper.setSortByPosition(true);  // Ensure lines are processed in the correct order

        int numberOfPages = document.getNumberOfPages();
        StringBuilder xmlContent = new StringBuilder();
        int sectionCount = 0;
        int paraCount = 0;
        int codeCount = 0;
        int listItemCount = 0;
        boolean insideList = false;
        boolean insideCodeBlock = false;
        boolean insideTable = false;

        // Initialize paragraph outside the loop
        StringBuilder paragraph = new StringBuilder();

        // Map to keep track of images per page
        List<List<String>> imagesPerPage = new ArrayList<>();
        for (int i = 0; i < numberOfPages; i++) {
            imagesPerPage.add(new ArrayList<>());
        }
        // Assign images to pages
        int imgIndex = 0;
        for (String img : extractedImages) {
            // Assuming images are extracted in order and assigned to pages sequentially
            // This may need to be adjusted based on actual extraction
            if (imgIndex < numberOfPages) {
                imagesPerPage.get(imgIndex).add(img);
            }
            imgIndex = (imgIndex + 1) % numberOfPages;
        }

        for (int pageIndex = 0; pageIndex < numberOfPages; pageIndex++) {
            textStripper.setStartPage(pageIndex + 1);
            textStripper.setEndPage(pageIndex + 1);
            String pageText = textStripper.getText(document);

            xmlContent.setLength(0); // Reset content for the new page
            xmlContent.append("<section xmlns=\"http://www.idpf.org/2007/ops\" epub:type=\"bodymatter\">\n");
            xmlContent.append("  <div class=\"page\" id=\"page-").append(pageIndex + 1).append("\">\n");

            // Split the page text into lines
            String[] lines = pageText.split("\\r?\\n");

            for (String line : lines) {
                String trimmedLine = line.trim();

                // Handle non-breaking characters and avoid invalid symbols
                trimmedLine = trimmedLine.replace("\uFFFD", ""); // Remove replacement character
                trimmedLine = trimmedLine.replaceAll("[^\\x20-\\x7E]+", ""); // Remove non-ASCII characters

                // Detect if the line is a title (capital letters and no punctuation)
                if (trimmedLine.matches("^[A-Z ]+$") && !trimmedLine.contains(".") && trimmedLine.length() > 3) {
                    sectionCount++;
                    // Flush any paragraph before starting a new section
                    paraCount = flushParagraph(xmlContent, paragraph, paraCount);
                    xmlContent.append("    <h2>").append(trimmedLine).append("</h2>\n");
                } else if (trimmedLine.startsWith("TABLE")) {
                    // Start a new table
                    if (!insideTable) {
                        xmlContent.append("    <table class=\"epub-table\">\n");
                        insideTable = true;
                    }
                    // Parse table rows and columns
                    String[] columns = trimmedLine.substring(5).trim().split("\\s{2,}"); // Split on two or more spaces
                    xmlContent.append("      <tr>\n");
                    for (String column : columns) {
                        xmlContent.append("        <th>").append(column).append("</th>\n");
                    }
                    xmlContent.append("      </tr>\n");
                } else if (insideTable && trimmedLine.matches(".*\\S.*")) { // Non-empty line within table
                    String[] columns = trimmedLine.split("\\s{2,}"); // Split columns based on two or more whitespace
                    xmlContent.append("      <tr>\n");
                    for (String column : columns) {
                        xmlContent.append("        <td>").append(column).append("</td>\n");
                    }
                    xmlContent.append("      </tr>\n");
                } else {
                    if (insideTable) {
                        xmlContent.append("    </table>\n");
                        insideTable = false;
                    }

                    // Detect if the line is a bulleted list item
                    String[] bulletSymbols = {"•", "○", "▪", "❑", "✓", "–"};
                    boolean isBullet = false;

                    for (String symbol : bulletSymbols) {
                        if (trimmedLine.startsWith(symbol)) {
                            isBullet = true;
                            trimmedLine = trimmedLine.substring(symbol.length()).trim();
                            break;
                        }
                    }

                    if (isBullet) {
                        listItemCount++;
                        if (!insideList) {
                            xmlContent.append("    <ul>\n");
                            insideList = true;
                        }
                        // Add the list item
                        xmlContent.append("      <li>").append(trimmedLine).append("</li>\n");
                    } else {
                        // If we were inside a bulleted list, close it
                        if (insideList) {
                            xmlContent.append("    </ul>\n");
                            insideList = false;
                        }

                        // Detect if the line contains code
                        if (trimmedLine.matches(".*\\{.*|.*\\;.*|.*\\(.*\\).*")) {
                            if (!insideCodeBlock) {
                                // Start a new code block
                                paraCount = flushParagraph(xmlContent, paragraph, paraCount);
                                codeCount++;
                                xmlContent.append("    <pre><code>\n");
                                insideCodeBlock = true;
                            }
                            // Add the code line
                            xmlContent.append(trimmedLine).append("\n");
                        } else {
                            // If we are inside a code block, close it
                            if (insideCodeBlock) {
                                xmlContent.append("    </code></pre>\n");
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
                xmlContent.append("    </ul>\n");
                insideList = false;
            }
            if (insideCodeBlock) {
                xmlContent.append("    </code></pre>\n");
                insideCodeBlock = false;
            }
            if (insideTable) {
                xmlContent.append("    </table>\n");
                insideTable = false; // Close the table after finishing rows
            }

            // Finalize any remaining paragraph
            paraCount = flushParagraph(xmlContent, paragraph, paraCount);

            // Insert images at the end of the page content
            List<String> imagesForPage = imagesPerPage.get(pageIndex);
            for (String imageFileName : imagesForPage) {
                xmlContent.append("    <div class=\"image-container\">\n")
                          .append("      <img src=\"../images/")
                          .append(imageFileName)
                          .append("\" alt=\"Image ")
                          .append(imageCounter)
                          .append("\" />\n")
                          .append("    </div>\n");
                imageCounter++;
            }

            xmlContent.append("  </div>\n")
                      .append("</section>\n");

            // Create the XHTML file for the current page
            String xhtmlFile = "page-" + (pageIndex + 1) + ".xhtml";
            createXhtmlFile(xhtmlFile, xmlContent.toString());
            xhtmlFiles.add(xhtmlFile);
        }

        document.close();
        return xhtmlFiles;
    }

    // Method to extract images from the PDF and save them to imagesDir
    private List<String> extractImages(PDDocument document) {
        List<String> imageFiles = new ArrayList<>();
        try {
            int pageNumber = 0;
            for (PDPage page : document.getPages()) {
                pageNumber++;
                PDResources pdResources = page.getResources();
                Iterable<COSName> xObjectNames = pdResources.getXObjectNames();

                for (COSName xObjectName : xObjectNames) {
                    if (pdResources.isImageXObject(xObjectName)) {
                        PDImageXObject imageObject = (PDImageXObject) pdResources.getXObject(xObjectName);
                        BufferedImage bufferedImage = imageObject.getImage();
                        String imageFormat = imageObject.getSuffix();
                        if (imageFormat == null) {
                            imageFormat = "png"; // Default to PNG if format is unknown
                        }
                        String imageFileName = "image_" + pageNumber + "_" + imageCounter + "." + imageFormat;
                        Path imagePath = Paths.get(imagesDir, imageFileName);

                        // Write the BufferedImage to the file
                        ImageIO.write(bufferedImage, imageFormat, imagePath.toFile());
                        logger.info("Extracted image: {}", imageFileName);
                        imageFiles.add(imageFileName);
                        imageCounter++;
                    }
                }
            }
        } catch (IOException e) {
            logger.error("Error extracting images from PDF: {}", e.getMessage(), e);
        }
        return imageFiles;
    }

    // Method to format text for bold and italic
    private String formatText(String line) {
        String formattedLine = line;
        // Example for bold: *text*
        formattedLine = formattedLine.replaceAll("\\*(.*?)\\*", "<b>$1</b>");
        // Example for italic: _text_
        formattedLine = formattedLine.replaceAll("_(.*?)_", "<i>$1</i>");
        return formattedLine;
    }

    private int flushParagraph(StringBuilder xmlContent, StringBuilder paragraph, int paraCount) {
        if (paragraph.length() > 0) {
            paraCount++;
            xmlContent.append("    <p>").append(paragraph.toString().trim()).append("</p>\n");
            paragraph.setLength(0); // Reset the paragraph
        }
        return paraCount;
    }

    private void createXhtmlFile(String fileName, String content) throws IOException {
        String xhtmlContent = "<?xml version=\"1.0\" encoding=\"UTF-8\"?>\n"
                + "<!DOCTYPE html>\n"
                + "<html xmlns=\"http://www.w3.org/1999/xhtml\">\n"
                + "<head>\n"
                + "    <title>Converted XHTML</title>\n"
                + "    <link rel=\"stylesheet\" type=\"text/css\" href=\"../styles/styles.css\" />\n"
                + "</head>\n"
                + "<body>\n"
                + content
                + "</body>\n"
                + "</html>";
        Files.write(Paths.get(xhtmlDir, fileName), xhtmlContent.getBytes());
    }
}
