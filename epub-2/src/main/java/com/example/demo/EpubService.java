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
import java.util.Map;
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
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

@Service
public class EpubService{
	private static final Logger logger = LoggerFactory.getLogger(EpubService.class);

	// Base directory for temporary EPUB creation
	private String epubBaseDir;
	private String oebpsDir;
	private String metaInfDir;
	private String imagesDir;
	private String stylesDir;
	private String xhtmlDir;
	private int imageCounter = 1; // Global image counter
	private int chapterCounter = 1; // Chapter numbering

	public String createEpubFromPdf(MultipartFile file) throws Exception {
		try {
			setupDirectories();
			PDDocument document = PDDocument.load(file.getInputStream());
			List<String> xhtmlFiles = extractContent(document);
			createIndex();
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
		StringBuilder combinedXhtmlContent = new StringBuilder();
		combinedXhtmlContent.append("<?xml version=\"1.0\" encoding=\"UTF-8\"?>\n").append("<!DOCTYPE html>\n")
				.append("<html xml:lang=\"en\" lang=\"en\" ").append("xmlns=\"http://www.w3.org/1999/xhtml\" ")
				.append("xmlns:epub=\"http://www.idpf.org/2007/ops\">\n").append("<head>\n")
				.append("<title>Combined EPUB Content</title>\n")
				.append("<link rel=\"stylesheet\" type=\"text/css\" href=\"../styles/styles.css\"/>\n")
				.append("</head>\n").append("<body epub:type=\"bodymatter\">\n");

		PDFTextStripper stripper = new PDFTextStripper();
		PDPageTree pages = document.getDocumentCatalog().getPages();
		int totalPages = pages.getCount();

		int chapterCount = 0;
		int paraCount = 0;
		StringBuilder paragraph = new StringBuilder();

		for (int pageIndex = 0; pageIndex < totalPages; pageIndex++) {
			stripper.setStartPage(pageIndex + 1);
			stripper.setEndPage(pageIndex + 1);
			String pageText = stripper.getText(document);

			combinedXhtmlContent.append("<page xml:id=\"page-").append(pageIndex + 1).append("\">\n");

			// Extract images from the current page
			List<ImageInfo> images = extractImages(document, pageIndex + 1, pageIndex + 1);

			// Split the page text into lines
			String[] lines = pageText.split("\\r?\\n");

			for (String line : lines) {
				String trimmedLine = line.trim();

				if (isChapterTitle(trimmedLine)) {
					chapterCount++;
					paraCount = flushParagraph(combinedXhtmlContent, paragraph, paraCount);

					String chapterTitle = trimmedLine;
					String xhtmlFileName = "chapter-" + chapterCount + ".xhtml";
					chapterSectionMap.put(chapterTitle, xhtmlFileName);

					createXhtmlForChapter(xhtmlFileName, pageIndex + 1, lines);

					combinedXhtmlContent.append("<h1><a href=\"xhtml/").append(xhtmlFileName).append("\">")
							.append(escapeXml(chapterTitle)).append("</a></h1>\n");
				} else {
					if (!trimmedLine.isEmpty()) {
						if (paragraph.length() > 0) {
							paragraph.append(" ");
						}
						paragraph.append(escapeXml(trimmedLine));

						// Check if the line ends with punctuation to flush the paragraph
						if (endsWithPunctuation(trimmedLine)) {
							paraCount = flushParagraph(combinedXhtmlContent, paragraph, paraCount);
						}
					}
				}
			}

			// Insert images at their corresponding position
			for (ImageInfo image : images) {
				combinedXhtmlContent.append("<figure>\n").append("<img class=\"image\" src=\"../images/")
						.append(image.getImageName()).append("\" alt=\"Image from page ").append(image.getPageNumber())
						.append("\"/>\n").append("<figcaption>Image from page ").append(image.getPageNumber())
						.append("</figcaption>\n").append("</figure>\n");
			}

			combinedXhtmlContent.append("</page>\n");
		}

		paraCount = flushParagraph(combinedXhtmlContent, paragraph, paraCount);

		combinedXhtmlContent.append("</body>\n</html>\n");

		String combinedXhtmlFileName = "combined.xhtml";
		Path combinedXhtmlFilePath = Paths.get(xhtmlDir, combinedXhtmlFileName);
		Files.write(combinedXhtmlFilePath, combinedXhtmlContent.toString().getBytes());

		createIndex();

		document.close();

		return List.of("xhtml/" + combinedXhtmlFileName);
	}

    private List<ImageInfo> extractImages(PDDocument document, int pageNumber, int chapterNumber) throws IOException {
        List<ImageInfo> images = new ArrayList<>();
        PDPage page = document.getPage(pageNumber - 1);
        PDResources resources = page.getResources();
        int imageIndex = 1;

        for (COSName xobjectName : resources.getXObjectNames()) {
            if (resources.isImageXObject(xobjectName)) {
                PDImageXObject image = (PDImageXObject) resources.getXObject(xobjectName);
                String imageName = generateImageName(chapterNumber, pageNumber, imageIndex);
                saveImage(image, imageName);
                images.add(new ImageInfo(imageName, pageNumber));
                imageIndex++;
            }
        }
        return images;
    }
    
    private String generateImageName(int chapterNumber, int pageNumber, int imageIndex) {
        return String.format("chapter%d_pg%d_%d.jpg", chapterNumber, pageNumber, imageIndex);
    }

    /**
     * Save image to the images directory.
     */
    private void saveImage(PDImageXObject image, String imageName) throws IOException {
        String imagePath = Paths.get(imagesDir, imageName).toString();
        BufferedImage bufferedImage = image.getImage();
        String formatName = image.getSuffix();
        if (formatName == null) {
            formatName = "png"; // Default to PNG if suffix is null
        }
        ImageIO.write(bufferedImage, formatName, new File(imagePath));
    }


	/**
	 * Creates a separate XHTML file for a given chapter.
	 *
	 * @param xhtmlFileName The name of the XHTML file to create.
	 * @param pageIndex     The page number where the chapter starts.
	 * @param lines         The lines of text in the chapter.
	 * @throws IOException If an I/O error occurs.
	 */
	private void createXhtmlForChapter(String xhtmlFileName, int pageIndex, String[] lines) throws IOException {
		StringBuilder chapterContent = new StringBuilder();
		chapterContent.append("<?xml version=\"1.0\" encoding=\"UTF-8\"?>\n").append("<!DOCTYPE html>\n")
				.append("<html xml:lang=\"en\" lang=\"en\" ").append("xmlns=\"http://www.w3.org/1999/xhtml\" ")
				.append("xmlns:epub=\"http://www.idpf.org/2007/ops\">\n").append("<head>\n").append("<title>")
				.append(escapeXml(xhtmlFileName)).append("</title>\n")
				.append("<link rel=\"stylesheet\" type=\"text/css\" href=\"../styles/styles.css\"/>\n")
				.append("</head>\n").append("<body epub:type=\"bodymatter\">\n");

		// Append chapter heading
		chapterContent.append("<section epub:type=\"chapter\" role=\"doc-chapter\">\n")
				.append("<h1 class=\"chapno\">Chapter ").append(chapterCounter).append(": ")
				.append(escapeXml(xhtmlFileName)).append("</h1>\n").append("</section>\n");

		// Initialize flags for lists and code blocks
		boolean insideList = false;
		boolean insideCodeBlock = false;

		for (String line : lines) {
			String trimmedLine = line.trim();
			if (isChapterTitle(trimmedLine)) {
				// Skip if it's a chapter title, already handled
				continue;
			}

			if (isBulletPoint(trimmedLine)) {
				// Handle list items
				if (!insideList) {
					chapterContent.append("<ul class=\"ulist\">\n");
					insideList = true;
				}
				String bulletContent = escapeXml(trimmedLine.substring(1).trim());
				chapterContent.append("<li class=\"bull\">").append(bulletContent).append("</li>\n");
			} else {
				// Close any open list
				if (insideList) {
					chapterContent.append("</ul>\n");
					insideList = false;
				}

				if (isCodeLine(trimmedLine)) {
					if (!insideCodeBlock) {
						chapterContent.append("<computerCode>\n")
								.append("<lineatedText numberLines=\"no\" xml:space=\"preserve\">\n");
						insideCodeBlock = true;
					}
					String codeContent = escapeXml(trimmedLine);
					chapterContent.append("<line><![CDATA[").append(codeContent).append("]]></line>\n");
				} else {
					// Close code block if open
					if (insideCodeBlock) {
						chapterContent.append("</lineatedText>\n</computerCode>\n");
						insideCodeBlock = false;
					}
					// Handle regular paragraphs
					if (!trimmedLine.isEmpty()) {
						chapterContent.append("<p>").append(escapeXml(trimmedLine)).append("</p>\n");
					}

				}
			}
		}

		// Close any open structures at the end of the chapter
		if (insideList) {
			chapterContent.append("</ul>\n");
			insideList = false;
		}
		if (insideCodeBlock) {
			chapterContent.append("</lineatedText>\n</computerCode>\n");
			insideCodeBlock = false;
		}

		chapterContent.append("</body>\n</html>\n");

		// Write the chapter content to a separate XHTML file
		Path chapterXhtmlPath = Paths.get(xhtmlDir, xhtmlFileName);
		Files.write(chapterXhtmlPath, chapterContent.toString().getBytes());
	}

	/**
	 * Extracts the chapter title from an array of paragraphs.
	 *
	 * @param paragraphs The array of paragraphs.
	 * @return The chapter title if found, otherwise null.
	 */
	private String extractSectionTitle(String[] paragraphs) {
		// This method is now redundant as we handle chapter detection directly in
		// extractContent
		return null;
	}

	/**
	 * Creates an index (table of contents) XHTML file based on the detected
	 * chapters.
	 *
	 * @throws IOException If an I/O error occurs.
	 */
	private void createIndex() throws IOException {
		StringBuilder indexContent = new StringBuilder();
		indexContent.append("<?xml version=\"1.0\" encoding=\"UTF-8\"?>\n").append("<!DOCTYPE html>\n").append(
				"<html xmlns=\"http://www.w3.org/1999/xhtml\" xmlns:epub=\"http://www.idpf.org/2007/ops\" lang=\"en\" xml:lang=\"en\">\n")
				.append("<head><title>Index</title></head>\n").append("<body>\n").append("<h1>Index</h1>\n")
				.append("<strong>Contents</strong>\n").append("<ul>\n");

		for (Map.Entry<String, String> entry : chapterSectionMap.entrySet()) {
			String title = escapeXml(entry.getKey());
			String xhtmlFileName = entry.getValue();
			indexContent.append("<li><a href=\"xhtml/").append(xhtmlFileName).append("\">").append(title)
					.append("</a></li>\n");
		}

		indexContent.append("</ul>\n").append("</body>\n").append("</html>\n");

		// Write the index to a separate XHTML file
		Path indexXhtmlPath = Paths.get(xhtmlDir, "index.xhtml");
		Files.write(indexXhtmlPath, indexContent.toString().getBytes());
	}

	/**
	 * Generates XHTML content for a specific page, including paragraphs, lists,
	 * code blocks, and images.
	 *
	 * @param pageNumber     The current page number.
	 * @param contentText    The concatenated content text of the page.
	 * @param images         The list of images on the page.
	 * @param paragraphCount The number of paragraphs on the page.
	 * @return The generated XHTML content as a string.
	 */
	private String generateXhtmlContent(int pageNumber, String contentText, List<ImageInfo> images,
			int paragraphCount) {
		StringBuilder content = new StringBuilder();
		boolean insideList = false;
		boolean insideCodeBlock = false;

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
					content.append("<ul class=\"ulist\">\n"); // Start a list if not already inside one
					insideList = true;
				}
				content.append(para).append("\n"); // Add the list item
			} else if (para.startsWith("<computerCode>")) {
				// Handle code blocks if needed
				// This section can be expanded based on your specific requirements
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

		for (ImageInfo image : images) {
			content.append("<figure>\n").append("<img class=\"image\" src=\"../images/").append(image.getImageName())
					.append("\" alt=\"Image from page ").append(image.getPageNumber()).append("\"/>\n")
					.append("<figcaption>Image from page ").append(image.getPageNumber()).append("</figcaption>\n")
					.append("</figure>\n");
		}

		// Insert images if any
		/*
		 * for (ImageInfo image : images) {
		 * content.append("<figure id=\"fig").append(image.getId()).append("\">\n")
		 * .append("<img class=\"image\" src=\"../images/").append(image.getImageName())
		 * .append("\" alt=\"")
		 * .append(escapeXml(image.getAltText())).append("\"/>\n").append(
		 * "<figcaption>\n")
		 * .append("<p class=\"figcaption\"><span epub:type=\"label\">Fig.</span> <span epub:type=\"ordinal\">"
		 * )
		 * .append(image.getId()).append("</span> ").append(escapeXml(image.getCaption()
		 * )).append("</p>\n") .append("</figcaption>\n").append("</figure>\n"); }
		 */

		return content.toString();
	}

	/**
	 * Extracts paragraphs from the content text, handling inline elements and list
	 * items.
	 *
	 * @param contentText The raw content text.
	 * @return An array of XHTML-formatted paragraphs.
	 */
	private String[] extractParagraphs(String contentText) {
		// Split content based on newlines as paragraphs.
		String[] paragraphs = contentText.split("\\r?\\n+");

		List<String> cleanedParagraphs = new ArrayList<>();
		for (String paragraph : paragraphs) {
			paragraph = paragraph.trim(); // Trim each paragraph

			if (!paragraph.isEmpty()) {
				// Detect and wrap list items (e.g., bullets or dashes)
				if (isBulletPoint(paragraph)) {
					cleanedParagraphs.add("<li class=\"bull\">" + escapeXml(paragraph.substring(1).trim()) + "</li>");
				} else {
					// Handle inline formulas or other inline elements
					String processedParagraph = processInlineElements(paragraph);
					cleanedParagraphs.add("<p>" + processedParagraph + "</p>");
				}
			}
		}

		return cleanedParagraphs.toArray(new String[0]);
	}

	/**
	 * Processes inline elements such as math formulas within a paragraph.
	 *
	 * @param text The paragraph text.
	 * @return The processed paragraph with inline elements.
	 */
	private String processInlineElements(String text) {
		// Example handling for inline math formulas
		if (text.contains("Te=")) {
			text = text.replace("Te=", "<math display=\"inline\" xmlns=\"http://www.w3.org/1998/Math/MathML\">"
					+ "<mtext>Thus</mtext><mo>:</mo><msub><mi>T</mi><mi>e</mi></msub>"
					+ "<mo>=</mo><msqrt><mrow><mrow><mo>(</mo><mrow>"
					+ "<msubsup><mi>T</mi><mi>i</mi><mn>2</mn></msubsup><mo>+</mo>"
					+ "<msubsup><mi>T</mi><mi>u</mi><mn>2</mn></msubsup><mo>+</mo>"
					+ "<msubsup><mi>T</mi><mi>s</mi><mn>2</mn></msubsup></mrow>" + "<mo>)</mo></mrow></msqrt></math>");
		}
		// Add more inline processing as needed
		return text;
	}

	/**
	 * Flushes the current paragraph to the XML content and resets the paragraph
	 * builder.
	 *
	 * @param xmlContent The StringBuilder containing the XML content.
	 * @param paragraph  The StringBuilder containing the current paragraph.
	 * @param paraCount  The current paragraph count.
	 * @return The updated paragraph count.
	 */
	private int flushParagraph(StringBuilder xmlContent, StringBuilder paragraph, int paraCount) {
		if (paragraph.length() > 0) {
			paraCount++;
			xmlContent.append("<p xml:id=\"para-").append(paraCount).append("\">").append(paragraph.toString().trim())
					.append("</p>\n");
			paragraph.setLength(0); // Reset the paragraph
		}
		return paraCount;
	}

	/**
	 * Checks if a line is a chapter title based on the criteria: - Starts with
	 * "Chapter" followed by a number - Optionally followed by a colon and title
	 * text
	 *
	 * @param line The line to check.
	 * @return True if the line is a chapter title, false otherwise.
	 */
	private boolean isChapterTitle(String line) {
		return line.matches("^Chapter\\s+\\d+(\\.\\d+)*\\s*[:\\-]?\\s*.*$");
	}

	/**
	 * Checks if a line is a bullet point based on common bullet symbols.
	 *
	 * @param line The line to check.
	 * @return True if the line is a bullet point, false otherwise.
	 */
	private boolean isBulletPoint(String line) {
		String[] bulletSymbols = { "•", "○", "▪", "❑", "✓", "–", "-", "*" };

		for (String symbol : bulletSymbols) {
			if (line.startsWith(symbol)) {
				return true;
			}
		}
		return false;
	}

	/**
	 * Checks if a line likely contains code based on the presence of certain
	 * characters.
	 *
	 * @param line The line to check.
	 * @return True if the line is considered code, false otherwise.
	 */
	private boolean isCodeLine(String line) {
		return line.matches(".*\\{.*") || line.matches(".*\\;.*") || line.matches(".*\\(.*\\).*")
				|| line.matches(".*\\).*$");
	}

	/**
	 * Checks if a line ends with typical punctuation marks indicating the end of a
	 * sentence.
	 *
	 * @param line The line to check.
	 * @return True if the line ends with punctuation, false otherwise.
	 */
	private boolean endsWithPunctuation(String line) {
		return line.endsWith(".") || line.endsWith("!") || line.endsWith("?");
	}

	/**
	 * Escapes XML special characters in a string to prevent XML parsing issues.
	 *
	 * @param text The text to escape.
	 * @return The escaped text.
	 */
	private String escapeXml(String text) {
		if (text == null)
			return "";
		return text.replace("&", "&amp;").replace("<", "&lt;").replace(">", "&gt;").replace("\"", "&quot;").replace("'",
				"&apos;");
	}

	/**
	 * Extracts images from the PDF document for the specified page range.
	 * (Implementation depends on your existing method)
	 *
	 * @param document  The PDF document.
	 * @param startPage The start page index (1-based).
	 * @param endPage   The end page index (1-based).
	 * @return A list of ImageInfo objects representing the images.
	 * @throws IOException If an I/O error occurs.
	 */
	/*
	 * private List<ImageInfo> extractImages(PDDocument document, int startPage, int
	 * endPage) throws IOException { // Implement your image extraction logic here
	 * // This could involve using PDFBox's PDFRenderer or other methods to extract
	 * // images // Example placeholder implementation: List<ImageInfo> images = new
	 * ArrayList<>(); // TODO: Extract images and populate the images list return
	 * images; }
	 * 
	 * 
	 * /** Generates individual XHTML files for each detected chapter.
	 *
	 * @param xhtmlFileName The name of the XHTML file.
	 * 
	 * @param pageIndex The current page index.
	 * 
	 * @param lines The lines of text in the chapter.
	 * 
	 * @throws IOException If an I/O error occurs.
	 */
	/*
	 * private void createXhtmlForChapter(String xhtmlFileName, int pageIndex,
	 * String[] lines) throws IOException { StringBuilder chapterContent = new
	 * StringBuilder();
	 * chapterContent.append("<?xml version=\"1.0\" encoding=\"UTF-8\"?>\n")
	 * .append("<!DOCTYPE html>\n") .append("<html xml:lang=\"en\" lang=\"en\" ")
	 * .append("xmlns=\"http://www.w3.org/1999/xhtml\" ")
	 * .append("xmlns:epub=\"http://www.idpf.org/2007/ops\">\n") .append("<head>\n")
	 * .append("<title>").append(escapeXml(xhtmlFileName)).append("</title>\n")
	 * .append("<link rel=\"stylesheet\" type=\"text/css\" href=\"../styles/styles.css\"/>\n"
	 * ) .append("</head>\n") .append("<body epub:type=\"bodymatter\">\n");
	 * 
	 * // Append chapter heading chapterContent.
	 * append("<section epub:type=\"chapter\" role=\"doc-chapter\">\n")
	 * .append("<h1 class=\"chapno\">").append(escapeXml(xhtmlFileName)).append(
	 * "</h1>\n") .append("</section>\n");
	 * 
	 * // Initialize flags for lists and code blocks boolean insideList = false;
	 * boolean insideCodeBlock = false;
	 * 
	 * for (String line : lines) { String trimmedLine = line.trim();
	 * 
	 * // Skip chapter title as it's already handled if
	 * (isChapterTitle(trimmedLine)) { continue; }
	 * 
	 * if (isBulletPoint(trimmedLine)) { // Handle list items if (!insideList) {
	 * chapterContent.append("<ul class=\"ulist\">\n"); insideList = true; } String
	 * bulletContent = escapeXml(trimmedLine.substring(1).trim());
	 * chapterContent.append("<li class=\"bull\">").append(bulletContent).append(
	 * "</li>\n"); } else { // Close any open list if (insideList) {
	 * chapterContent.append("</ul>\n"); insideList = false; }
	 * 
	 * if (isCodeLine(trimmedLine)) { if (!insideCodeBlock) {
	 * chapterContent.append("<computerCode>\n")
	 * .append("<lineatedText numberLines=\"no\" xml:space=\"preserve\">\n");
	 * insideCodeBlock = true; } String codeContent = escapeXml(trimmedLine);
	 * chapterContent.append("<line><![CDATA[").append(codeContent).append(
	 * "]]></line>\n"); } else { // Close code block if open if (insideCodeBlock) {
	 * chapterContent.append("</lineatedText>\n</computerCode>\n"); insideCodeBlock
	 * = false; } // Handle regular paragraphs if (!trimmedLine.isEmpty()) {
	 * chapterContent.append("<p>").append(escapeXml(trimmedLine)).append("</p>\n");
	 * } } } }
	 * 
	 * // Close any open structures at the end of the chapter if (insideList) {
	 * chapterContent.append("</ul>\n"); insideList = false; } if (insideCodeBlock)
	 * { chapterContent.append("</lineatedText>\n</computerCode>\n");
	 * insideCodeBlock = false; }
	 * 
	 * chapterContent.append("</body>\n</html>\n");
	 * 
	 * // Write the chapter content to a separate XHTML file Path chapterXhtmlPath =
	 * Paths.get(xhtmlDir, xhtmlFileName); Files.write(chapterXhtmlPath,
	 * chapterContent.toString().getBytes()); }
	 * 
	 *//**
		 * Creates an index (table of contents) XHTML file based on the detected
		 * chapters.
		 *
		 * @throws IOException If an I/O error occurs.
		 */

	/*
	 * private void createIndex() throws IOException { StringBuilder indexContent =
	 * new StringBuilder();
	 * indexContent.append("<?xml version=\"1.0\" encoding=\"UTF-8\"?>\n")
	 * .append("<!DOCTYPE html>\n")
	 * .append("<html xmlns=\"http://www.w3.org/1999/xhtml\" xmlns:epub=\"http://www.idpf.org/2007/ops\" lang=\"en\" xml:lang=\"en\">\n"
	 * ) .append("<head><title>Index</title></head>\n") .append("<body>\n")
	 * .append("<h1>Index</h1>\n") .append("<strong>Contents</strong>\n")
	 * .append("<ul>\n");
	 * 
	 * for (Map.Entry<String, String> entry : chapterSectionMap.entrySet()) { String
	 * title = escapeXml(entry.getKey()); String xhtmlFileName = entry.getValue();
	 * indexContent.append("<li><a href=\"xhtml/").append(xhtmlFileName).append(
	 * "\">") .append(title).append("</a></li>\n"); }
	 * 
	 * indexContent.append("</ul>\n") .append("</body>\n") .append("</html>\n");
	 * 
	 * // Write the index to a separate XHTML file Path indexXhtmlPath =
	 * Paths.get(xhtmlDir, "index.xhtml"); Files.write(indexXhtmlPath,
	 * indexContent.toString().getBytes()); }
	 * 
	 *//**
		 * Escapes XML special characters in a string to prevent XML parsing issues.
		 *
		 * @param text The text to escape.
		 * @return The escaped text.
		 *//*
			 * private String escapeXml(String text) { if (text == null) return ""; return
			 * text.replace("&", "&amp;") .replace("<", "&lt;") .replace(">", "&gt;")
			 * .replace("\"", "&quot;") .replace("'", "&apos;"); }
			 * 
			 * private List<ImageInfo> extractImages(PDDocument document, int startPage, int
			 * endPage) throws IOException { List<ImageInfo> images = new ArrayList<>(); for
			 * (int pageNumber = startPage; pageNumber <= endPage; pageNumber++) { PDPage
			 * page = document.getPage(pageNumber - 1); // 0-based index PDResources
			 * pdResources = page.getResources();
			 * 
			 * for (COSName name : pdResources.getXObjectNames()) { if
			 * (pdResources.isImageXObject(name)) { PDImageXObject imageObject =
			 * (PDImageXObject) pdResources.getXObject(name); BufferedImage bufferedImage =
			 * imageObject.getImage();
			 * 
			 * String imageName = "image" + (imageCounter++) + ".png"; File imageFile = new
			 * File(imagesDir, imageName); ImageIO.write(bufferedImage, "png", imageFile);
			 * 
			 * ImageInfo imageInfo = new ImageInfo(pageNumber, imageName);
			 * images.add(imageInfo); } } } return images; }
			 */
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
			for (String xhtmlFile : Files.list(Paths.get(xhtmlDir)).map(Path::getFileName).map(Path::toString)
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
			for (String imageFile : Files.list(Paths.get(imagesDir)).map(Path::getFileName).map(Path::toString)
					.toList()) {
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

	 /**
     * Inner class to hold image information.
     */
    private static class ImageInfo {
        private final String imageName;
        private final int pageNumber;

        public ImageInfo(String imageName, int pageNumber) {
            this.imageName = imageName;
            this.pageNumber = pageNumber;
        }

        public String getImageName() {
            return imageName;
        }

        public int getPageNumber() {
            return pageNumber;
        }
    }
}



 