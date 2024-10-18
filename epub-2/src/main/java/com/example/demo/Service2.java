// src/main/java/com/example/demo/EpubService.java
package com.example.demo;

import java.awt.image.BufferedImage;
import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;

import javax.imageio.ImageIO;

import org.apache.pdfbox.cos.COSName;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.pdmodel.PDPage;
import org.apache.pdfbox.pdmodel.PDPageTree;
import org.apache.pdfbox.pdmodel.PDResources;
import org.apache.pdfbox.pdmodel.graphics.image.PDImageXObject;
import org.apache.pdfbox.text.PDFTextStripper;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

@Service
public class Service2 {

	// Base directory for temporary EPUB creation
	private String epubBaseDir;
	private String oebpsDir;
	private String metaInfDir;
	private String imagesDir;
	private String stylesDir;
	private String xhtmlDir;
	private int imageCounter = 1; // Global image counter
	private int chapterCounter = 1; // Chapter numbering

	public String createEpubFromPdf(MultipartFile file) throws IOException {
		// Step 1: Create temporary directories
		setupDirectories();

		// Step 2: Load PDF document
		PDDocument document = PDDocument.load(file.getInputStream());

		// Step 3: Extract content and generate XHTML files
		List<String> xhtmlFiles = extractContent(document);
 	        createIndex();


		// Step 4: Create stylesheet
		createStylesheet();

		// Step 5: Create content.opf
		createContentOpf(xhtmlFiles);

		// Step 6: Create nav.xhtml
		createNavXhtml(xhtmlFiles);

		// Step 7: Create container.xml
		createContainerXml();

		// Step 8: Create mimetype file
		createMimetypeFile();

		// Step 9: Package everything into EPUB (ZIP)
		String epubFilePath = packageEpub();
		
       

		// Clean up temporary directories
		document.close();
		deleteTempDirectories();

		return epubFilePath;
	}

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

	
	 private HashMap<String, String> chapterSectionMap = new HashMap<>(); // Field Declaration
  

public List<String> extractContent(PDDocument document) throws IOException {
    StringBuilder xhtmlContent = new StringBuilder();
    xhtmlContent.append("<?xml version=\"1.0\" encoding=\"UTF-8\"?>\n")
            .append("<!DOCTYPE html PUBLIC \"-//W3C//DTD XHTML 1.1//EN\" ")
            .append("\"http://www.w3.org/TR/xhtml11/DTD/xhtml11.dtd\">\n")
            .append("<html xml:lang=\"en\" lang=\"en\" ")
            .append("xmlns=\"http://www.w3.org/1999/xhtml\" ")
            .append("xmlns:epub=\"http://www.idpf.org/2007/ops\">\n")
            .append("<head>\n")
            .append("<title>Combined EPUB Content</title>\n")
            .append("<link rel=\"stylesheet\" type=\"text/css\" href=\"../styles/styles.css\"/>\n")
            .append("</head>\n")
            .append("<body epub:type=\"bodymatter\">\n");

    PDFTextStripper stripper = new PDFTextStripper();
    PDPageTree pages = document.getDocumentCatalog().getPages();
    int totalPages = pages.getCount();

    for (int pageIndex = 1; pageIndex <= totalPages; pageIndex++) {
        // Set the page to extract
        stripper.setStartPage(pageIndex);
        stripper.setEndPage(pageIndex);

        String extractedText = stripper.getText(document);
        String[] paragraphs = extractParagraphs(extractedText);

        // Extract images for this page
        List<ImageInfo> images = extractImages(document, pageIndex, pageIndex);

        // Extract chapter/section titles and create links
        String sectionTitle = extractSectionTitle(paragraphs);
        if (sectionTitle != null) {
            String xhtmlFileName = "chapter-" + chapterCounter++ + ".xhtml";
            chapterSectionMap.put(sectionTitle, xhtmlFileName);
            // Create a new XHTML file for this section
            createXhtmlForSection(xhtmlFileName, pageIndex, paragraphs);
        }

        // Generate content for this specific page, including images
        xhtmlContent.append(generateXhtmlContent(pageIndex, String.join("\n", paragraphs), images, paragraphs.length));
    }

    xhtmlContent.append("</body>\n</html>\n");

    // Write combined content to a single XHTML file
    String xhtmlFileName = "combined.xhtml";
    Path xhtmlFilePath = Paths.get(xhtmlDir, xhtmlFileName);
    Files.write(xhtmlFilePath, xhtmlContent.toString().getBytes());

    return List.of("xhtml/" + xhtmlFileName);
}

    private void createXhtmlForSection(String xhtmlFileName, int pageIndex, String[] paragraphs) throws IOException {
        StringBuilder sectionContent = new StringBuilder();
        sectionContent.append("<?xml version=\"1.0\" encoding=\"UTF-8\"?>\n")
                .append("<!DOCTYPE html PUBLIC \"-//W3C//DTD XHTML 1.1//EN\" ")
                .append("\"http://www.w3.org/TR/xhtml11/DTD/xhtml11.dtd\">\n")
                .append("<html xml:lang=\"en\" lang=\"en\" ").append("xmlns=\"http://www.w3.org/1999/xhtml\" ")
                .append("xmlns:epub=\"http://www.idpf.org/2007/ops\">\n").append("<head>\n")
                .append("<title>").append(xhtmlFileName).append("</title>\n")
                .append("<link rel=\"stylesheet\" type=\"text/css\" href=\"../styles/styles.css\"/>\n")
                .append("</head>\n").append("<body epub:type=\"bodymatter\">\n");

        sectionContent.append("<h1>").append(xhtmlFileName).append("</h1>\n");
        for (String paragraph : paragraphs) {
            sectionContent.append("<p>").append(paragraph).append("</p>\n");
        }

        sectionContent.append("</body>\n</html>\n");

        // Write the section content to a separate XHTML file
        Path sectionXhtmlPath = Paths.get(xhtmlDir, xhtmlFileName);
        Files.write(sectionXhtmlPath, sectionContent.toString().getBytes());
    }

    private String extractSectionTitle(String[] paragraphs) {
        // Example: Check for bold text or a specific pattern to determine section titles
        for (String paragraph : paragraphs) {
            if (paragraph.startsWith("Chapter") || paragraph.startsWith("Section")) {
                return paragraph.trim(); // Return the title as it can be used as a key
            }
        }
        return null; // No title found
    }

    private void createIndex() throws IOException {
        StringBuilder indexContent = new StringBuilder();
        indexContent.append("<?xml version=\"1.0\" encoding=\"UTF-8\"?>\n")
                .append("<!DOCTYPE html>\n")
                .append("<html xmlns=\"http://www.w3.org/1999/xhtml\" xmlns:epub=\"http://www.idpf.org/2007/ops\" lang=\"en\" xml:lang=\"en\">\n")
                .append("<head><title>Index</title></head>\n")
                .append("<body>\n").append("<h1>Index</h1>\n").append("<strong>Contents</strong>\n").append("<ul>\n");

        for (String title : chapterSectionMap.keySet()) {
            String xhtmlFileName = chapterSectionMap.get(title);
            indexContent.append("<li><a href=\"xhtml/").append(xhtmlFileName).append("\">").append(title).append("</a></li>\n");
        }

        indexContent.append("</ul>\n").append("</body>\n").append("</html>\n");

        // Write the index to a separate XHTML file
        Path indexXhtmlPath = Paths.get(xhtmlDir, "index.xhtml");
        Files.write(indexXhtmlPath, indexContent.toString().getBytes());
    }
	private String generateXhtmlContent(int pageNumber, String contentText, List<ImageInfo> images,
			int paragraphCount) {
		StringBuilder content = new StringBuilder();
		boolean insideList = false;

		// Page Break
		content.append("<span id=\"page-").append(pageNumber).append("\" aria-label=\"").append(pageNumber)
				.append("\" epub:type=\"pagebreak\" role=\"doc-pagebreak\">Page ").append(pageNumber)
				.append("</span>\n");

		content.append("<p>Paragraph count on this page: ").append(paragraphCount).append("</p>\n");

		// Split the contentText into paragraphs
		String[] paragraphs = extractParagraphs(contentText);

		// Body Content - process paragraphs and images
		for (String para : paragraphs) {
			para = para.trim();

			// Detect list items (<li>)
			if (para.startsWith("<li>")) {
				if (!insideList) {
					content.append("<ul>\n"); // Start a list if not already inside one
					insideList = true;
				}
				content.append(para).append("\n"); // Add the list item
			} else {
				if (insideList) {
					content.append("</ul>\n"); // Close the list if we finished
					insideList = false;
				}
				content.append(para).append("\n"); // Add the paragraph
			}
		}

		if (insideList) {
			content.append("</ul>\n"); // Close any remaining open list
		}

		// Insert images if any
		for (ImageInfo image : images) {
			content.append("<figure>\n").append("<img class=\"image\" src=\"../images/").append(image.getImageName())
					.append("\" alt=\"Image from page ").append(image.getPageNumber()).append("\"/>\n")
					.append("<figcaption>Image from page ").append(image.getPageNumber()).append("</figcaption>\n")
					.append("</figure>\n");
		}

		return content.toString();
	}

	// Method to extract paragraphs from the content text
	private String[] extractParagraphs(String contentText) {
		// Split content based on newlines as paragraphs.
		String[] paragraphs = contentText.split("\\r?\\n+");

		List<String> cleanedParagraphs = new ArrayList<>();
		for (String paragraph : paragraphs) {
			paragraph = paragraph.trim(); // Trim each paragraph

			if (!paragraph.isEmpty()) {
				// Detect and wrap list items (e.g., bullets or dashes)
				if (paragraph.matches("^[\\-\\*•].+")) {
					cleanedParagraphs.add("<li>" + paragraph.substring(1).trim() + "</li>");
				} else {
					// Handle inline formulas in paragraphs
					String processedParagraph = processInlineElements(paragraph);
					cleanedParagraphs.add(
							"<p id=\"para-" + (cleanedParagraphs.size() + 1) + "\">" + processedParagraph + "</p>");
				}
			}
		}

		return cleanedParagraphs.toArray(new String[0]);
	}

	// Process inline elements (handle inline formulas)
	private String processInlineElements(String text) {
		// Example handling for inline math formulas
		if (text.contains("Te=")) {
			text = text.replace("Te=",
					"<math display=\"inline\" xmlns=\"http://www.w3.org/1998/Math/MathML\"><mtext>Thus</mtext><mo>:</mo><msub><mi>T</mi><mi>e</mi></msub><mo>=</mo><msqrt><mrow><mrow><mo>(</mo><mrow><msubsup><mi>T</mi><mi>i</mi><mn>2</mn></msubsup><mo>+</mo><msubsup><mi>T</mi><mi>u</mi><mn>2</mn></msubsup><mo>+</mo><msubsup><mi>T</mi><mi>s</mi><mn>2</mn></msubsup></mrow><mo>)</mo></mrow></msqrt></math>");
		}
		return text;
	}

	private List<ImageInfo> extractImages(PDDocument document, int startPage, int endPage) throws IOException {
		List<ImageInfo> images = new ArrayList<>();
		for (int pageNumber = startPage; pageNumber <= endPage; pageNumber++) {
			PDPage page = document.getPage(pageNumber - 1); // 0-based index
			PDResources pdResources = page.getResources();

			for (COSName name : pdResources.getXObjectNames()) {
				if (pdResources.isImageXObject(name)) {
					PDImageXObject imageObject = (PDImageXObject) pdResources.getXObject(name);
					BufferedImage bufferedImage = imageObject.getImage();

					String imageName = "image" + (imageCounter++) + ".png";
					File imageFile = new File(imagesDir, imageName);
					ImageIO.write(bufferedImage, "png", imageFile);

					ImageInfo imageInfo = new ImageInfo(pageNumber, imageName);
					images.add(imageInfo);
				}
			}
		}
		return images;
	}

	private void createStylesheet() throws IOException {
		String cssContent = "body { font-family: Arial, sans-serif; margin: 1em; }\n"
				+ ".image { display: block; margin: 1em auto; max-width: 100%; height: auto; }\n";
		Path cssFilePath = Paths.get(stylesDir, "styles.css");
		Files.write(cssFilePath, cssContent.getBytes());
	}

	private void createContentOpf(List<String> xhtmlFiles) throws IOException {
		StringBuilder contentOpf = new StringBuilder();
		contentOpf.append("<?xml version=\"1.0\" encoding=\"UTF-8\"?>\n").append(
				"<package xmlns=\"http://www.idpf.org/2007/opf\" unique-identifier=\"bookid\" version=\"3.0\">\n")
				.append("<metadata xmlns:dc=\"http://purl.org/dc/elements/1.1/\">\n")
				.append("<dc:title>Generated EPUB</dc:title>\n").append("<dc:language>en</dc:language>\n")
				.append("</metadata>\n").append("<manifest>\n")
				.append("<item id=\"nav\" href=\"xhtml/nav.xhtml\" media-type=\"application/xhtml+xml\" properties=\"nav\"/>\n");

		for (String xhtmlFile : xhtmlFiles) {
			contentOpf.append("<item id=\"").append(xhtmlFile.replace("xhtml/", "").replace(".xhtml", ""))
					.append("\" href=\"").append(xhtmlFile).append("\" media-type=\"application/xhtml+xml\"/>\n");
		}

		contentOpf.append("<item id=\"css\" href=\"styles/styles.css\" media-type=\"text/css\"/>\n")
				.append("</manifest>\n").append("<spine>\n");

		for (String xhtmlFile : xhtmlFiles) {
			contentOpf.append("<itemref idref=\"").append(xhtmlFile.replace("xhtml/", "").replace(".xhtml", ""))
					.append("\"/>\n");
		}

		contentOpf.append("</spine>\n").append("</package>\n");

		Path contentOpfPath = Paths.get(oebpsDir, "content.opf");
		Files.write(contentOpfPath, contentOpf.toString().getBytes());
	}

	private void createNavXhtml(List<String> xhtmlFiles) throws IOException {
		StringBuilder navXhtml = new StringBuilder();
		navXhtml.append("<?xml version=\"1.0\" encoding=\"UTF-8\"?>\n").append("<!DOCTYPE html>\n").append(
				"<html xmlns=\"http://www.w3.org/1999/xhtml\" xmlns:epub=\"http://www.idpf.org/2007/ops\" lang=\"en\" xml:lang=\"en\">\n")
				.append("<head><title>Navigation</title></head>\n").append("<body>\n")
				.append("<nav epub:type=\"toc\" id=\"toc\">\n").append("<h1>Table of Contents</h1>\n").append("<ol>\n");

		for (String xhtmlFile : xhtmlFiles) {
			navXhtml.append("<li><a href=\"").append(xhtmlFile).append("\">Chapter ").append(chapterCounter++)
					.append("</a></li>\n");
		}

		navXhtml.append("</ol>\n").append("</nav>\n").append("</body>\n").append("</html>\n");

		Path navXhtmlPath = Paths.get(xhtmlDir, "nav.xhtml");
		Files.write(navXhtmlPath, navXhtml.toString().getBytes());
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

            // Add OEBPS index.xhtml
            ZipEntry indexEntry = new ZipEntry("OEBPS/index.xhtml");
            zipOut.putNextEntry(indexEntry);
            zipOut.write(Files.readAllBytes(Paths.get(xhtmlDir, "index.xhtml")));
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
        Files.walk(basePath)
                .sorted((path1, path2) -> path2.compareTo(path1)) // Delete files first, then directories
                .forEach(path -> {
                    try {
                        Files.delete(path);
                    } catch (IOException e) {
                        e.printStackTrace();
                    }
                });
    }


	private void addDirectoryToZip(Path folderPath, ZipOutputStream zipOut, String baseDir) throws IOException {
		Files.walk(folderPath).filter(path -> !Files.isDirectory(path)).forEach(path -> {
			String zipEntryName = folderPath.relativize(path).toString();
			zipEntryName = baseDir + "/" + zipEntryName;
			try {
				zipOut.putNextEntry(new ZipEntry(zipEntryName));
				Files.copy(path, zipOut);
				zipOut.closeEntry();
			} catch (IOException e) {
				e.printStackTrace();
			}
		});
	}

	 

	// Inner class to store image information
	private class ImageInfo {
		private final int pageNumber;
		private final String imageName;

		public ImageInfo(int pageNumber, String imageName) {
			this.pageNumber = pageNumber;
			this.imageName = imageName;
		}

		public int getPageNumber() {
			return pageNumber;
		}

		public String getImageName() {
			return imageName;
		}
	}
}

 
