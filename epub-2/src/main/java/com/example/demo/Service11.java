package com.example.demo;

import java.awt.image.BufferedImage;
import java.io.File;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.List;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;

import javax.imageio.ImageIO;

import org.apache.pdfbox.cos.COSName;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.pdmodel.PDPage;
import org.apache.pdfbox.pdmodel.PDResources;
import org.apache.pdfbox.pdmodel.graphics.PDXObject;
import org.apache.pdfbox.pdmodel.graphics.image.PDImageXObject;
import org.apache.pdfbox.text.PDFTextStripper;
import org.apache.pdfbox.text.TextPosition;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;
@Service             /////tables and paragraphs /images /math/href links
public class Service11 {
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
        String cssContent = "/* Body */\r\n"
        		+ "body{margin-left: 1.5em; margin-right: 2.25em;}\r\n"
        		+ "h1,h2,h3,h4,h5,h6{font-weight:bold;}\r\n"
        		+ "figure{margin:0em;}\r\n"
        		+ "img\r\n"
        		+ "{\r\n"
        		+ "vertical-align:middle;\r\n"
        		+ "}\r\n"
        		+ "a\r\n"
        		+ "{\r\n"
        		+ "color:#0000FF;\r\n"
        		+ "text-decoration: underline;\r\n"
        		+ "}\r\n"
        		+ ".underline\r\n"
        		+ "{\r\n"
        		+ "text-decoration: underline;\r\n"
        		+ "}\r\n"
        		+ "sup\r\n"
        		+ "{\r\n"
        		+ "vertical-align:0.55em;\r\n"
        		+ "font-size:0.6em;\r\n"
        		+ "line-height:0em;\r\n"
        		+ "}\r\n"
        		+ "sub\r\n"
        		+ "{\r\n"
        		+ "vertical-align:-0.4em;\r\n"
        		+ "font-size:0.6em;\r\n"
        		+ "line-height:0em;\r\n"
        		+ "}\r\n"
        		+ "/* Frontmatter */\r\n"
        		+ ".halftitle\r\n"
        		+ "{\r\n"
        		+ "font-size:150%;\r\n"
        		+ "margin-top:0.5em;\r\n"
        		+ "margin-bottom:0em;\r\n"
        		+ "text-indent:0em;\r\n"
        		+ "font-weight:normal;\r\n"
        		+ "text-align:center;\r\n"
        		+ "}\r\n"
        		+ ".halftitle1a\r\n"
        		+ "{\r\n"
        		+ "font-size:110%;\r\n"
        		+ "margin-top:0.5em;\r\n"
        		+ "margin-bottom:0em;\r\n"
        		+ "text-indent:0em;\r\n"
        		+ "font-weight:normal;\r\n"
        		+ "text-align:center;\r\n"
        		+ "}\r\n"
        		+ ".halftitle1b\r\n"
        		+ "{\r\n"
        		+ "font-size:110%;\r\n"
        		+ "margin-top:5em;\r\n"
        		+ "margin-bottom:0em;\r\n"
        		+ "text-indent:0em;\r\n"
        		+ "font-weight:normal;\r\n"
        		+ "text-align:center;\r\n"
        		+ "}\r\n"
        		+ ".booktitle\r\n"
        		+ "{\r\n"
        		+ "font-size:180%;\r\n"
        		+ "margin-top:2em;\r\n"
        		+ "margin-bottom:0.5em;\r\n"
        		+ "text-indent:0em;\r\n"
        		+ "font-weight:normal;\r\n"
        		+ "text-align:left;\r\n"
        		+ "}\r\n"
        		+ ".booksubtitle\r\n"
        		+ "{\r\n"
        		+ "font-size:120%;\r\n"
        		+ "margin-top:0em;\r\n"
        		+ "margin-bottom:0em;\r\n"
        		+ "text-indent:0em;\r\n"
        		+ "font-weight:normal;\r\n"
        		+ "text-align:left;\r\n"
        		+ "}\r\n"
        		+ ".booksubtitle1\r\n"
        		+ "{\r\n"
        		+ "font-size:100%;\r\n"
        		+ "margin-top:5em;\r\n"
        		+ "margin-bottom:2em;\r\n"
        		+ "text-indent:0em;\r\n"
        		+ "font-weight:normal;\r\n"
        		+ "text-align:left;\r\n"
        		+ "}\r\n"
        		+ ".box\r\n"
        		+ "{\r\n"
        		+ "border:solid 1px;\r\n"
        		+ "margin-top:1em;\r\n"
        		+ "margin-bottom:0em;\r\n"
        		+ "padding:0.5em;\r\n"
        		+ "text-indent:0em;\r\n"
        		+ "}\r\n"
        		+ ".numlistx\r\n"
        		+ "{\r\n"
        		+ "font-size:90%;\r\n"
        		+ "margin-top:0em;\r\n"
        		+ "margin-bottom:0em;\r\n"
        		+ "text-indent:0em;\r\n"
        		+ "margin-left:0em;\r\n"
        		+ "text-align:left;\r\n"
        		+ "}\r\n"
        		+ ".bnumlist1b\r\n"
        		+ "{\r\n"
        		+ "font-size:100%;\r\n"
        		+ "margin-top:0em;\r\n"
        		+ "margin-bottom:0em;\r\n"
        		+ "text-indent:0em;\r\n"
        		+ "margin-left:0em;\r\n"
        		+ "text-align:left;\r\n"
        		+ "}\r\n"
        		+ ".bnumlist1\r\n"
        		+ "{\r\n"
        		+ "font-size:100%;\r\n"
        		+ "margin-top:0em;\r\n"
        		+ "margin-bottom:0em;\r\n"
        		+ "text-indent:0em;\r\n"
        		+ "margin-left:3em;\r\n"
        		+ "text-align:left;\r\n"
        		+ "}\r\n"
        		+ ".bnumlist1a\r\n"
        		+ "{\r\n"
        		+ "font-size:100%;\r\n"
        		+ "margin-top:0em;\r\n"
        		+ "margin-bottom:0em;\r\n"
        		+ "text-indent:0em;\r\n"
        		+ "margin-left:1.5em;\r\n"
        		+ "text-align:left;\r\n"
        		+ "}\r\n"
        		+ ".bnumlist2\r\n"
        		+ "{\r\n"
        		+ "font-size:100%;\r\n"
        		+ "margin-top:0em;\r\n"
        		+ "margin-bottom:0em;\r\n"
        		+ "text-indent:0em;\r\n"
        		+ "margin-left:5.5em;\r\n"
        		+ "text-align:left;\r\n"
        		+ "}\r\n"
        		+ ".bft\r\n"
        		+ "{\r\n"
        		+ "float:left;\r\n"
        		+ "text-indent:-2em;\r\n"
        		+ "}\r\n"
        		+ ".bft1\r\n"
        		+ "{\r\n"
        		+ "float:left;\r\n"
        		+ "text-indent:-2.5em;\r\n"
        		+ "}\r\n"
        		+ ".battrib\r\n"
        		+ "{\r\n"
        		+ "font-size:100%;\r\n"
        		+ "margin-top:0.5em;\r\n"
        		+ "margin-bottom:0.5em;\r\n"
        		+ "font-weight:bold;\r\n"
        		+ "text-indent:0em;\r\n"
        		+ "text-align:left;\r\n"
        		+ "margin-left:1em;\r\n"
        		+ "}\r\n"
        		+ ".halftitle1\r\n"
        		+ "{\r\n"
        		+ "font-size:100%;\r\n"
        		+ "margin-top:0em;\r\n"
        		+ "margin-bottom:0em;\r\n"
        		+ "font-weight:bold;\r\n"
        		+ "text-indent:0em;\r\n"
        		+ "text-align:center;\r\n"
        		+ "}\r\n"
        		+ ".edition\r\n"
        		+ "{\r\n"
        		+ "font-size:90%;\r\n"
        		+ "margin-top:0em;\r\n"
        		+ "font-weight:bold;\r\n"
        		+ "margin-bottom:1em;\r\n"
        		+ "text-indent:0em;\r\n"
        		+ "text-align:center;\r\n"
        		+ "}\r\n"
        		+ ".subtitle\r\n"
        		+ "{\r\n"
        		+ "font-size:150%;\r\n"
        		+ "margin-top:.1em;\r\n"
        		+ "margin-bottom:0em;\r\n"
        		+ "text-indent:0em;\r\n"
        		+ "text-align:left;\r\n"
        		+ "}\r\n"
        		+ ".subtitleb\r\n"
        		+ "{\r\n"
        		+ "font-size:150%;\r\n"
        		+ "margin-top:.1em;\r\n"
        		+ "margin-bottom:4em;\r\n"
        		+ "text-indent:0em;\r\n"
        		+ "text-align:left;\r\n"
        		+ "}\r\n"
        		+ ".fmtitle\r\n"
        		+ "{\r\n"
        		+ "font-variant: small-caps;  \r\n"
        		+ "margin-top:2em;\r\n"
        		+ "margin-bottom:3em;\r\n"
        		+ "text-indent:0em;\r\n"
        		+ "text-align:center;\r\n"
        		+ "font-weight:bold;\r\n"
        		+ "font-size:140%;\r\n"
        		+ "}\r\n"
        		+ ".bmtitle1\r\n"
        		+ "{\r\n"
        		+ "margin-top:3em;\r\n"
        		+ "margin-bottom:0em;\r\n"
        		+ "text-indent:0em;\r\n"
        		+ "text-align:center;\r\n"
        		+ "font-size:130%;\r\n"
        		+ "}\r\n"
        		+ ".bmtitle1a\r\n"
        		+ "{\r\n"
        		+ "margin-top:1em;\r\n"
        		+ "margin-bottom:0em;\r\n"
        		+ "text-indent:0em;\r\n"
        		+ "text-align:center;\r\n"
        		+ "font-size:130%;\r\n"
        		+ "}\r\n"
        		+ ".bmtitle\r\n"
        		+ "{\r\n"
        		+ "margin-top:2em;\r\n"
        		+ "margin-bottom:3em;\r\n"
        		+ "text-indent:0em;\r\n"
        		+ "text-align:center;\r\n"
        		+ "font-weight:bold;\r\n"
        		+ "font-size:140%;\r\n"
        		+ "font-variant: small-caps;  \r\n"
        		+ "}\r\n"
        		+ ".bkauthor\r\n"
        		+ "{\r\n"
        		+ "font-size:90%;\r\n"
        		+ "margin-top:2em;\r\n"
        		+ "margin-bottom:0em;\r\n"
        		+ "text-indent:0em;\r\n"
        		+ "font-weight:bold;\r\n"
        		+ "text-align:center;\r\n"
        		+ "}\r\n"
        		+ ".bkauthor1\r\n"
        		+ "{\r\n"
        		+ "font-size:80%;\r\n"
        		+ "margin-top:0em;\r\n"
        		+ "margin-bottom:0em;\r\n"
        		+ "text-indent:0em;\r\n"
        		+ "font-weight:bold;\r\n"
        		+ "text-align:center;\r\n"
        		+ "}\r\n"
        		+ ".tcap\r\n"
        		+ "{\r\n"
        		+ "float:left;\r\n"
        		+ "text-indent:-4.5em;\r\n"
        		+ "}\r\n"
        		+ ".tcaption\r\n"
        		+ "{\r\n"
        		+ "font-size:80%;\r\n"
        		+ "margin-top:1em;\r\n"
        		+ "margin-left:4.5em;\r\n"
        		+ "margin-bottom:.3em;\r\n"
        		+ "text-indent:0em;\r\n"
        		+ "text-align:left;\r\n"
        		+ "}\r\n"
        		+ ".copyt\r\n"
        		+ "{\r\n"
        		+ "margin-top:1em;\r\n"
        		+ "margin-bottom:0em;\r\n"
        		+ "text-align:left;\r\n"
        		+ "font-size:70%;\r\n"
        		+ "text-indent:0em;\r\n"
        		+ "}\r\n"
        		+ ".copy1\r\n"
        		+ "{\r\n"
        		+ "margin-top:0em;\r\n"
        		+ "margin-bottom:0em;\r\n"
        		+ "margin-left:0em;\r\n"
        		+ "padding-left:1em;\r\n"
        		+ "text-align:left;\r\n"
        		+ "font-size:70%;\r\n"
        		+ "text-indent:0em;\r\n"
        		+ "}\r\n"
        		+ ".copyh\r\n"
        		+ "{\r\n"
        		+ "margin-top:0em;\r\n"
        		+ "margin-bottom:0em;\r\n"
        		+ "margin-left:1em;\r\n"
        		+ "text-align:left;\r\n"
        		+ "font-size:70%;\r\n"
        		+ "text-indent:-1em;\r\n"
        		+ "}\r\n"
        		+ ".copyc\r\n"
        		+ "{\r\n"
        		+ "margin-top:1em;\r\n"
        		+ "margin-bottom:1em;\r\n"
        		+ "text-align:center;\r\n"
        		+ "font-size:70%;\r\n"
        		+ "text-indent:0em;\r\n"
        		+ "}\r\n"
        		+ ".copy\r\n"
        		+ "{\r\n"
        		+ "margin-top:0em;\r\n"
        		+ "margin-bottom:0em;\r\n"
        		+ "text-align:left;\r\n"
        		+ "font-size:70%;\r\n"
        		+ "text-indent:0em;\r\n"
        		+ "margin-left:0em;\r\n"
        		+ "padding-left:0em;\r\n"
        		+ "}\r\n"
        		+ ".right\r\n"
        		+ "{\r\n"
        		+ "margin-top:0em;\r\n"
        		+ "margin-bottom:0em;\r\n"
        		+ "margin-right:0em;\r\n"
        		+ "text-align:right;\r\n"
        		+ "font-size:80%;\r\n"
        		+ "text-indent:0em;\r\n"
        		+ "}\r\n"
        		+ ".rightb\r\n"
        		+ "{\r\n"
        		+ "margin-top:0em;\r\n"
        		+ "margin-bottom:4em;\r\n"
        		+ "margin-right:0em;\r\n"
        		+ "text-align:right;\r\n"
        		+ "font-size:80%;\r\n"
        		+ "text-indent:0em;\r\n"
        		+ "}\r\n"
        		+ ".rightt\r\n"
        		+ "{\r\n"
        		+ "margin-top:1em;\r\n"
        		+ "margin-bottom:0em;\r\n"
        		+ "margin-right:0em;\r\n"
        		+ "text-align:right;\r\n"
        		+ "font-size:80%;\r\n"
        		+ "text-indent:0em;\r\n"
        		+ "}\r\n"
        		+ ".tocch\r\n"
        		+ "{\r\n"
        		+ "margin-top:.1em;\r\n"
        		+ "margin-bottom:0em;\r\n"
        		+ "text-align:left;\r\n"
        		+ "font-size:80%;\r\n"
        		+ "margin-left:6em;\r\n"
        		+ "text-indent:0em;\r\n"
        		+ "}\r\n"
        		+ ".toccht\r\n"
        		+ "{\r\n"
        		+ "margin-top:1em;\r\n"
        		+ "margin-bottom:0em;\r\n"
        		+ "text-align:left;\r\n"
        		+ "font-size:80%;\r\n"
        		+ "margin-left:6em;\r\n"
        		+ "text-indent:0em;\r\n"
        		+ "}\r\n"
        		+ ".tocpt\r\n"
        		+ "{\r\n"
        		+ "color:#808285;\r\n"
        		+ "margin-top:1em;\r\n"
        		+ "margin-bottom:.5em;\r\n"
        		+ "text-align:left;\r\n"
        		+ "font-weight:bold;\r\n"
        		+ "font-size:90%;\r\n"
        		+ "margin-left:6em;\r\n"
        		+ "text-indent:0em;\r\n"
        		+ "}\r\n"
        		+ ".flt\r\n"
        		+ "{\r\n"
        		+ "float:left;\r\n"
        		+ "margin-left:-6em;\r\n"
        		+ "}\r\n"
        		+ ".fltr\r\n"
        		+ "{\r\n"
        		+ "float:left;\r\n"
        		+ "margin-left:-1.5em;\r\n"
        		+ "}\r\n"
        		+ ".fltr1\r\n"
        		+ "{\r\n"
        		+ "float:left;\r\n"
        		+ "margin-left:-2em;\r\n"
        		+ "}\r\n"
        		+ ".flta\r\n"
        		+ "{\r\n"
        		+ "float:left;\r\n"
        		+ "margin-left:-6em;\r\n"
        		+ "}\r\n"
        		+ ".ff1\r\n"
        		+ "{\r\n"
        		+ "float:left;\r\n"
        		+ "margin-left:-2.5em;\r\n"
        		+ "}\r\n"
        		+ ".ff1a\r\n"
        		+ "{\r\n"
        		+ "float:left;\r\n"
        		+ "margin-left:-3.5em;\r\n"
        		+ "}\r\n"
        		+ ".chtoc\r\n"
        		+ "{\r\n"
        		+ "margin-top:1em;\r\n"
        		+ "margin-bottom:0em;\r\n"
        		+ "margin-left:2.5em;\r\n"
        		+ "text-align:left;\r\n"
        		+ "font-size:80%;\r\n"
        		+ "text-indent:0em;\r\n"
        		+ "font-variant: small-caps;\r\n"
        		+ "}\r\n"
        		+ ".ff2\r\n"
        		+ "{\r\n"
        		+ "float:left;\r\n"
        		+ "margin-left:-4.5em;\r\n"
        		+ "}\r\n"
        		+ ".chtoc1\r\n"
        		+ "{\r\n"
        		+ "margin-top:0em;\r\n"
        		+ "margin-bottom:0em;\r\n"
        		+ "margin-left:6em;\r\n"
        		+ "text-align:left;\r\n"
        		+ "font-size:80%;\r\n"
        		+ "text-indent:0em;\r\n"
        		+ "}\r\n"
        		+ ".chtoct\r\n"
        		+ "{\r\n"
        		+ "margin-top:1em;\r\n"
        		+ "margin-bottom:0em;\r\n"
        		+ "margin-left:0em;\r\n"
        		+ "text-align:left;\r\n"
        		+ "font-size:80%;\r\n"
        		+ "text-indent:0em;\r\n"
        		+ "font-variant: small-caps;\r\n"
        		+ "}\r\n"
        		+ ".tocentry\r\n"
        		+ "{\r\n"
        		+ "margin-top:1em;\r\n"
        		+ "margin-bottom:0em;\r\n"
        		+ "margin-left:6.5em;\r\n"
        		+ "text-align:left;\r\n"
        		+ "font-size:80%;\r\n"
        		+ "text-indent:0em;\r\n"
        		+ "}\r\n"
        		+ ".tocentry1\r\n"
        		+ "{\r\n"
        		+ "margin-top:1em;\r\n"
        		+ "margin-bottom:0em;\r\n"
        		+ "margin-left:6.5em;\r\n"
        		+ "text-align:left;\r\n"
        		+ "font-size:80%;\r\n"
        		+ "text-indent:0em;\r\n"
        		+ "}\r\n"
        		+ ".tocentry1\r\n"
        		+ "{\r\n"
        		+ "margin-top:1em;\r\n"
        		+ "margin-bottom:0em;\r\n"
        		+ "margin-left:6.5em;\r\n"
        		+ "text-align:left;\r\n"
        		+ "font-size:80%;\r\n"
        		+ "text-indent:0em;\r\n"
        		+ "}\r\n"
        		+ ".tocentry1a\r\n"
        		+ "{\r\n"
        		+ "margin-top:1em;\r\n"
        		+ "margin-bottom:0em;\r\n"
        		+ "margin-left:6.5em;\r\n"
        		+ "text-align:left;\r\n"
        		+ "font-size:80%;\r\n"
        		+ "text-indent:0em;\r\n"
        		+ "}\r\n"
        		+ ".tocentry2\r\n"
        		+ "{\r\n"
        		+ "margin-top:.2em;\r\n"
        		+ "margin-bottom:0em;\r\n"
        		+ "margin-left:6.5em;\r\n"
        		+ "text-align:left;\r\n"
        		+ "font-size:80%;\r\n"
        		+ "text-indent:0em;\r\n"
        		+ "}\r\n"
        		+ ".f1\r\n"
        		+ "{\r\n"
        		+ "float:left;\r\n"
        		+ "margin-left:-6.5em;\r\n"
        		+ "}\r\n"
        		+ ".f1a\r\n"
        		+ "{\r\n"
        		+ "float:left;\r\n"
        		+ "margin-left:-6.4em;\r\n"
        		+ "}\r\n"
        		+ ".f2\r\n"
        		+ "{\r\n"
        		+ "float:left;\r\n"
        		+ "margin-left:-3em;\r\n"
        		+ "}\r\n"
        		+ ".f3\r\n"
        		+ "{\r\n"
        		+ "float:left;\r\n"
        		+ "margin-left:-3em;\r\n"
        		+ "}\r\n"
        		+ ".f4\r\n"
        		+ "{\r\n"
        		+ "float:left;\r\n"
        		+ "margin-left:-3.1em;\r\n"
        		+ "}\r\n"
        		+ ".toc\r\n"
        		+ "{\r\n"
        		+ "margin-top:.1em;\r\n"
        		+ "margin-bottom:0em;\r\n"
        		+ "margin-left:0em;\r\n"
        		+ "text-align:left;\r\n"
        		+ "font-size:80%;\r\n"
        		+ "text-indent:0em;\r\n"
        		+ "}\r\n"
        		+ ".toct\r\n"
        		+ "{\r\n"
        		+ "margin-top:1em;\r\n"
        		+ "margin-bottom:0em;\r\n"
        		+ "margin-left:0em;\r\n"
        		+ "text-align:left;\r\n"
        		+ "font-size:80%;\r\n"
        		+ "text-indent:0em;\r\n"
        		+ "}\r\n"
        		+ ".poemt\r\n"
        		+ "{\r\n"
        		+ "margin-top:1em;\r\n"
        		+ "margin-bottom:0em;\r\n"
        		+ "margin-left:1em;\r\n"
        		+ "text-align:left;\r\n"
        		+ "font-size:80%;\r\n"
        		+ "text-indent:0em;\r\n"
        		+ "}\r\n"
        		+ ".poem\r\n"
        		+ "{\r\n"
        		+ "margin-top:0em;\r\n"
        		+ "margin-bottom:0em;\r\n"
        		+ "margin-left:1em;\r\n"
        		+ "text-align:left;\r\n"
        		+ "font-size:80%;\r\n"
        		+ "text-indent:0em;\r\n"
        		+ "}\r\n"
        		+ ".center\r\n"
        		+ "{\r\n"
        		+ "margin-top:1em;\r\n"
        		+ "margin-bottom:1em;\r\n"
        		+ "text-align:center;\r\n"
        		+ "font-weight:normal;\r\n"
        		+ "font-size:80%;\r\n"
        		+ "text-indent:0em;\r\n"
        		+ "}\r\n"
        		+ ".centera\r\n"
        		+ "{\r\n"
        		+ "margin-top:0em;\r\n"
        		+ "margin-bottom:0em;\r\n"
        		+ "text-align:center;\r\n"
        		+ "font-weight:normal;\r\n"
        		+ "font-size:80%;\r\n"
        		+ "text-indent:0em;\r\n"
        		+ "}\r\n"
        		+ ".text\r\n"
        		+ "{\r\n"
        		+ "margin-top:1em;\r\n"
        		+ "background-color:#CCCCCC;\r\n"
        		+ "color:#000000;\r\n"
        		+ "margin-bottom:1em;\r\n"
        		+ "text-align:justify;\r\n"
        		+ "font-weight:normal;\r\n"
        		+ "font-size:80%;\r\n"
        		+ "text-indent:0em;\r\n"
        		+ "padding:.5em;\r\n"
        		+ "}\r\n"
        		+ ".eqn\r\n"
        		+ "{\r\n"
        		+ "font-size: 80%;\r\n"
        		+ "margin-top:1em;\r\n"
        		+ "margin-bottom:1em;\r\n"
        		+ "text-align:center;\r\n"
        		+ "text-indent:0em;\r\n"
        		+ "}\r\n"
        		+ ".eqnr\r\n"
        		+ "{\r\n"
        		+ "float:right;\r\n"
        		+ "font-weight:normal;\r\n"
        		+ "text-indent:0em;\r\n"
        		+ "}\r\n"
        		+ ".fl1\r\n"
        		+ "{\r\n"
        		+ "float:right;\r\n"
        		+ "}\r\n"
        		+ "/* Bodymatter */\r\n"
        		+ ".chapno\r\n"
        		+ "{\r\n"
        		+ "font-size:130%;\r\n"
        		+ "margin-top:3em;\r\n"
        		+ "margin-bottom:1em;\r\n"
        		+ "text-align:center;\r\n"
        		+ "text-indent:0em;\r\n"
        		+ "font-variant: small-caps;\r\n"
        		+ "}\r\n"
        		+ ".ch\r\n"
        		+ "{\r\n"
        		+ "display:block;\r\n"
        		+ "margin-top:1em;\r\n"
        		+ "}\r\n"
        		+ ".chaptitle\r\n"
        		+ "{\r\n"
        		+ "font-size:140%;\r\n"
        		+ "margin-top:0em;\r\n"
        		+ "margin-bottom:1em;\r\n"
        		+ "text-align:center;\r\n"
        		+ "text-indent:0em;\r\n"
        		+ "}\r\n"
        		+ ".center1\r\n"
        		+ "{\r\n"
        		+ "font-size:130%;\r\n"
        		+ "margin-top:1em;\r\n"
        		+ "margin-bottom:1em;\r\n"
        		+ "text-align:center;\r\n"
        		+ "text-indent:0em;\r\n"
        		+ "}\r\n"
        		+ ".chapter\r\n"
        		+ "{\r\n"
        		+ "font-size:150%;\r\n"
        		+ "margin-top:1em;\r\n"
        		+ "margin-bottom:3em;\r\n"
        		+ "text-align:left;\r\n"
        		+ "text-indent:0em;\r\n"
        		+ "}\r\n"
        		+ ".chaptera\r\n"
        		+ "{\r\n"
        		+ "font-size:150%;\r\n"
        		+ "margin-top:1em;\r\n"
        		+ "margin-bottom:.5em;\r\n"
        		+ "text-align:left;\r\n"
        		+ "text-indent:0em;\r\n"
        		+ "}\r\n"
        		+ ".partno\r\n"
        		+ "{\r\n"
        		+ "font-size:160%;\r\n"
        		+ "margin-top:1em;\r\n"
        		+ "margin-bottom:2em;\r\n"
        		+ "text-align:left;\r\n"
        		+ "text-indent:0em;\r\n"
        		+ "}\r\n"
        		+ ".parttitle\r\n"
        		+ "{\r\n"
        		+ "display:block;\r\n"
        		+ "margin-top:1em;\r\n"
        		+ "margin-bottom:0em;\r\n"
        		+ "text-indent:0em;\r\n"
        		+ "}\r\n"
        		+ ".chtitle\r\n"
        		+ "{\r\n"
        		+ "display:block;\r\n"
        		+ "margin-top:2em;\r\n"
        		+ "margin-bottom:0em;\r\n"
        		+ "text-indent:0em;\r\n"
        		+ "}\r\n"
        		+ ".chauthor\r\n"
        		+ "{\r\n"
        		+ "font-size:95%;\r\n"
        		+ "font-style:italic;\r\n"
        		+ "margin-top:1em;\r\n"
        		+ "margin-bottom:5em;\r\n"
        		+ "font-weight:normal;\r\n"
        		+ "text-indent:0em;\r\n"
        		+ "text-align:right;\r\n"
        		+ "}\r\n"
        		+ ".s1\r\n"
        		+ "{\r\n"
        		+ "float:left;\r\n"
        		+ "text-indent:-2.5em;\r\n"
        		+ "}\r\n"
        		+ ".subhead1\r\n"
        		+ "{\r\n"
        		+ "margin-top:1.5em;\r\n"
        		+ "margin-bottom:.5em;\r\n"
        		+ "font-size:105%;\r\n"
        		+ "text-align:left;\r\n"
        		+ "margin-left:2.5em;\r\n"
        		+ "font-weight:bold;\r\n"
        		+ "text-indent:0em;\r\n"
        		+ "}\r\n"
        		+ ".subhead1a\r\n"
        		+ "{\r\n"
        		+ "margin-top:1.5em;\r\n"
        		+ "margin-bottom:1em;\r\n"
        		+ "font-size:105%;\r\n"
        		+ "text-align:center;\r\n"
        		+ "margin-left:0em;\r\n"
        		+ "font-weight:bold;\r\n"
        		+ "text-indent:0em;\r\n"
        		+ "font-variant: small-caps;\r\n"
        		+ "}\r\n"
        		+ ".subhead1b\r\n"
        		+ "{\r\n"
        		+ "margin-top:1em;\r\n"
        		+ "margin-bottom:1em;\r\n"
        		+ "font-size:100%;\r\n"
        		+ "text-align:left;\r\n"
        		+ "margin-left:0em;\r\n"
        		+ "font-weight:bold;\r\n"
        		+ "text-indent:0em;\r\n"
        		+ "font-variant: small-caps;\r\n"
        		+ "}\r\n"
        		+ ".subhead1bi\r\n"
        		+ "{\r\n"
        		+ "margin-top:1em;\r\n"
        		+ "margin-bottom:0em;\r\n"
        		+ "font-size:100%;\r\n"
        		+ "text-align:left;\r\n"
        		+ "margin-left:0em;\r\n"
        		+ "font-weight:bold;\r\n"
        		+ "text-indent:0em;\r\n"
        		+ "font-variant: small-caps;\r\n"
        		+ "}\r\n"
        		+ ".s2\r\n"
        		+ "{\r\n"
        		+ "float:left;\r\n"
        		+ "text-indent:-3.2em;\r\n"
        		+ "}\r\n"
        		+ ".subhead2\r\n"
        		+ "{\r\n"
        		+ "margin-top:1em;\r\n"
        		+ "margin-bottom:.6em;\r\n"
        		+ "font-size:95%;\r\n"
        		+ "text-align:left;\r\n"
        		+ "margin-left:3.2em;\r\n"
        		+ "font-weight:bold;\r\n"
        		+ "font-style:italic;\r\n"
        		+ "text-indent:0em;\r\n"
        		+ "}\r\n"
        		+ ".subhead2a\r\n"
        		+ "{\r\n"
        		+ "margin-top:1em;\r\n"
        		+ "margin-bottom:0em;\r\n"
        		+ "font-size:95%;\r\n"
        		+ "text-align:left;\r\n"
        		+ "margin-left:3em;\r\n"
        		+ "font-weight:bold;\r\n"
        		+ "font-style:italic;\r\n"
        		+ "text-indent:0em;\r\n"
        		+ "}\r\n"
        		+ ".subhead2b\r\n"
        		+ "{\r\n"
        		+ "margin-top:1em;\r\n"
        		+ "margin-bottom:0em;\r\n"
        		+ "font-size:95%;\r\n"
        		+ "text-align:left;\r\n"
        		+ "margin-left:3em;\r\n"
        		+ "font-weight:bold;\r\n"
        		+ "text-indent:0em;\r\n"
        		+ "}\r\n"
        		+ ".s3\r\n"
        		+ "{\r\n"
        		+ "float:left;\r\n"
        		+ "text-indent:-3.5em;\r\n"
        		+ "}\r\n"
        		+ ".subhead3x\r\n"
        		+ "{\r\n"
        		+ "margin-top:1.2em;\r\n"
        		+ "margin-bottom:.5em;\r\n"
        		+ "font-size:90%;\r\n"
        		+ "font-weight:bold;\r\n"
        		+ "text-align:left;\r\n"
        		+ "margin-left:3.5em;\r\n"
        		+ "text-indent:0em;\r\n"
        		+ "}\r\n"
        		+ ".subhead3\r\n"
        		+ "{\r\n"
        		+ "margin-top:1.2em;\r\n"
        		+ "margin-bottom:.5em;\r\n"
        		+ "font-size:90%;\r\n"
        		+ "font-weight:bold;\r\n"
        		+ "text-align:left;\r\n"
        		+ "margin-left:0em;\r\n"
        		+ "text-indent:0em;\r\n"
        		+ "}\r\n"
        		+ ".subhead3a\r\n"
        		+ "{\r\n"
        		+ "margin-top:1.2em;\r\n"
        		+ "margin-bottom:0em;\r\n"
        		+ "font-size:90%;\r\n"
        		+ "text-align:left;\r\n"
        		+ "margin-left:0em;\r\n"
        		+ "text-indent:0em;\r\n"
        		+ "}\r\n"
        		+ ".subhead4\r\n"
        		+ "{\r\n"
        		+ "margin-top:1.5em;\r\n"
        		+ "margin-bottom:.5em;\r\n"
        		+ "font-size:85%;\r\n"
        		+ "text-align:left;\r\n"
        		+ "margin-left:0em;\r\n"
        		+ "font-weight:bold;\r\n"
        		+ "text-indent:0em;\r\n"
        		+ "}\r\n"
        		+ ".subhead4a\r\n"
        		+ "{\r\n"
        		+ "margin-top:0.5em;\r\n"
        		+ "margin-bottom:0em;\r\n"
        		+ "font-size:85%;\r\n"
        		+ "text-align:left;\r\n"
        		+ "margin-left:0em;\r\n"
        		+ "font-weight:bold;\r\n"
        		+ "text-indent:0em;\r\n"
        		+ "}\r\n"
        		+ ".subhead4b\r\n"
        		+ "{\r\n"
        		+ "margin-top:0em;\r\n"
        		+ "margin-bottom:0em;\r\n"
        		+ "font-size:85%;\r\n"
        		+ "text-align:left;\r\n"
        		+ "margin-left:0em;\r\n"
        		+ "font-weight:bold;\r\n"
        		+ "text-indent:0em;\r\n"
        		+ "}\r\n"
        		+ ".subhead4c\r\n"
        		+ "{\r\n"
        		+ "margin-top:0em;\r\n"
        		+ "margin-bottom:0em;\r\n"
        		+ "font-size:85%;\r\n"
        		+ "text-align:left;\r\n"
        		+ "margin-left:0em;\r\n"
        		+ "font-weight:normal;\r\n"
        		+ "text-indent:0em;\r\n"
        		+ "}\r\n"
        		+ ".subhead2i\r\n"
        		+ "{\r\n"
        		+ "margin-top:1em;\r\n"
        		+ "margin-bottom:0em;\r\n"
        		+ "font-size:95%;\r\n"
        		+ "text-align:left;\r\n"
        		+ "margin-left:0em;\r\n"
        		+ "font-weight:bold;\r\n"
        		+ "font-style:italic;\r\n"
        		+ "text-indent:0em;\r\n"
        		+ "}\r\n"
        		+ ".noindent\r\n"
        		+ "{\r\n"
        		+ "font-size:80%;\r\n"
        		+ "margin-top:0em;\r\n"
        		+ "margin-bottom:0em;\r\n"
        		+ "text-indent:0em;\r\n"
        		+ "text-align:justify;\r\n"
        		+ "}\r\n"
        		+ ".noindentt\r\n"
        		+ "{\r\n"
        		+ "font-size:80%;\r\n"
        		+ "margin-top:1em;\r\n"
        		+ "margin-bottom:0em;\r\n"
        		+ "text-indent:0em;\r\n"
        		+ "text-align:justify;\r\n"
        		+ "}\r\n"
        		+ ".indent\r\n"
        		+ "{\r\n"
        		+ "font-size:80%;\r\n"
        		+ "margin-top:0em;\r\n"
        		+ "margin-bottom:0em;\r\n"
        		+ "text-indent:1em;\r\n"
        		+ "text-align:justify;\r\n"
        		+ "}\r\n"
        		+ ".indentt\r\n"
        		+ "{\r\n"
        		+ "font-size:80%;\r\n"
        		+ "margin-top:1em;\r\n"
        		+ "margin-bottom:0em;\r\n"
        		+ "text-indent:1em;\r\n"
        		+ "text-align:justify;\r\n"
        		+ "}\r\n"
        		+ ".image\r\n"
        		+ "{\r\n"
        		+ "text-align:center;\r\n"
        		+ "margin-top:1em;\r\n"
        		+ "margin-right:auto;\r\n"
        		+ "margin-left:auto;\r\n"
        		+ "margin-bottom:1em;\r\n"
        		+ "text-indent:0em;\r\n"
        		+ "display:block;\r\n"
        		+ "}\r\n"
        		+ ".logo\r\n"
        		+ "{\r\n"
        		+ "text-align:left;\r\n"
        		+ "margin-top:1em;\r\n"
        		+ "margin-right:auto;\r\n"
        		+ "margin-left:auto;\r\n"
        		+ "margin-bottom:0.5em;\r\n"
        		+ "text-indent:0em;\r\n"
        		+ "}\r\n"
        		+ ".pub\r\n"
        		+ "{\r\n"
        		+ "text-align:center;\r\n"
        		+ "margin-top:5em;\r\n"
        		+ "margin-right:auto;\r\n"
        		+ "margin-left:auto;\r\n"
        		+ "margin-bottom:1em;\r\n"
        		+ "text-indent:0em;\r\n"
        		+ "display:block;\r\n"
        		+ "}\r\n"
        		+ ".pub1\r\n"
        		+ "{\r\n"
        		+ "text-align:left;\r\n"
        		+ "margin-top:1em;\r\n"
        		+ "margin-right:auto;\r\n"
        		+ "margin-left:auto;\r\n"
        		+ "margin-bottom:1em;\r\n"
        		+ "text-indent:0em;\r\n"
        		+ "display:block;\r\n"
        		+ "}\r\n"
        		+ ".fg\r\n"
        		+ "{\r\n"
        		+ "float:left;\r\n"
        		+ "text-indent:-4.5em;\r\n"
        		+ "}\r\n"
        		+ ".figcaption\r\n"
        		+ "{\r\n"
        		+ "margin-top:1em;\r\n"
        		+ "margin-bottom:0em;\r\n"
        		+ "font-weight:normal;\r\n"
        		+ "text-align:justify;\r\n"
        		+ "margin-left:0em;\r\n"
        		+ "font-size:80%;\r\n"
        		+ "text-indent:0em;\r\n"
        		+ "}\r\n"
        		+ ".figcaption1\r\n"
        		+ "{\r\n"
        		+ "margin-top:0em;\r\n"
        		+ "margin-bottom:0em;\r\n"
        		+ "font-weight:normal;\r\n"
        		+ "text-align:justify;\r\n"
        		+ "margin-left:4.5em;\r\n"
        		+ "font-size:80%;\r\n"
        		+ "text-indent:0em;\r\n"
        		+ "}\r\n"
        		+ ".figcaptiona\r\n"
        		+ "{\r\n"
        		+ "margin-top:1em;\r\n"
        		+ "margin-bottom:0em;\r\n"
        		+ "font-weight:normal;\r\n"
        		+ "text-align:justify;\r\n"
        		+ "margin-left:0em;\r\n"
        		+ "font-size:80%;\r\n"
        		+ "text-indent:0em;\r\n"
        		+ "}\r\n"
        		+ ".table1\r\n"
        		+ "{\r\n"
        		+ "margin-left:0em;\r\n"
        		+ "margin-top:1em;\r\n"
        		+ "margin-bottom:1em;\r\n"
        		+ "width:100%;\r\n"
        		+ "border-spacing:0em;\r\n"
        		+ "padding:0em;\r\n"
        		+ "border:solid 1px;\r\n"
        		+ "border-collapse:collapse;\r\n"
        		+ "}\r\n"
        		+ ".table1a\r\n"
        		+ "{\r\n"
        		+ "margin-left:0em;\r\n"
        		+ "margin-top:1em;\r\n"
        		+ "margin-bottom:1em;\r\n"
        		+ "width:100%;\r\n"
        		+ "border-spacing:0em;\r\n"
        		+ "padding:0em;\r\n"
        		+ "border-collapse:collapse;\r\n"
        		+ "}\r\n"
        		+ "td\r\n"
        		+ "{\r\n"
        		+ "vertical-align:top;\r\n"
        		+ "}\r\n"
        		+ ".table\r\n"
        		+ "{\r\n"
        		+ "margin-left:0em;\r\n"
        		+ "margin-top:0.5em;\r\n"
        		+ "margin-bottom:0.5em;\r\n"
        		+ "border-spacing:0em;\r\n"
        		+ "padding:0em;\r\n"
        		+ "}\r\n"
        		+ ".table1a\r\n"
        		+ "{\r\n"
        		+ "margin-left:0em;\r\n"
        		+ "padding:0em;\r\n"
        		+ "margin-top:0.5em;\r\n"
        		+ "margin-bottom:0.5em;\r\n"
        		+ "}\r\n"
        		+ ".table1b\r\n"
        		+ "{\r\n"
        		+ "border-top:1px solid;\r\n"
        		+ "margin-left:0em;\r\n"
        		+ "margin-top:1em;\r\n"
        		+ "margin-bottom:1em;\r\n"
        		+ "width:100%;\r\n"
        		+ "border-spacing:0em;\r\n"
        		+ "padding:0em;\r\n"
        		+ "}\r\n"
        		+ ".tbr\r\n"
        		+ "{\r\n"
        		+ "border-top:1px solid;\r\n"
        		+ "border-right:1px solid;\r\n"
        		+ "border-bottom:1px solid;\r\n"
        		+ "}\r\n"
        		+ ".br\r\n"
        		+ "{\r\n"
        		+ "border-right:1px solid;\r\n"
        		+ "border-bottom:1px solid;\r\n"
        		+ "}\r\n"
        		+ ".rr\r\n"
        		+ "{\r\n"
        		+ "border-right:1px solid;\r\n"
        		+ "}\r\n"
        		+ ".bb\r\n"
        		+ "{\r\n"
        		+ "border-bottom:1px solid;\r\n"
        		+ "}\r\n"
        		+ ".ll\r\n"
        		+ "{\r\n"
        		+ "border-left:1px solid;\r\n"
        		+ "}\r\n"
        		+ ".lb\r\n"
        		+ "{\r\n"
        		+ "border-left:1px solid;\r\n"
        		+ "border-bottom:1px solid;\r\n"
        		+ "}\r\n"
        		+ ".tab1\r\n"
        		+ "{\r\n"
        		+ "font-size:80%;\r\n"
        		+ "padding:0em;\r\n"
        		+ "margin-top:0em;\r\n"
        		+ "margin-bottom:0em;\r\n"
        		+ "margin-left:.3em;\r\n"
        		+ "text-indent:0em;\r\n"
        		+ "text-align:left;\r\n"
        		+ "}\r\n"
        		+ ".tab1r\r\n"
        		+ "{\r\n"
        		+ "font-size:80%;\r\n"
        		+ "padding:0em;\r\n"
        		+ "margin-top:0em;\r\n"
        		+ "margin-bottom:0em;\r\n"
        		+ "margin-left:0.8em;\r\n"
        		+ "text-indent:0em;\r\n"
        		+ "text-align:left;\r\n"
        		+ "}\r\n"
        		+ ".tab1aa\r\n"
        		+ "{\r\n"
        		+ "font-size:80%;\r\n"
        		+ "padding:0em;\r\n"
        		+ "margin-top:0em;\r\n"
        		+ "margin-bottom:0em;\r\n"
        		+ "margin-left:.3em;\r\n"
        		+ "margin-right:.2em;\r\n"
        		+ "text-indent:0em;\r\n"
        		+ "text-align:justify;\r\n"
        		+ "}\r\n"
        		+ ".tab1a\r\n"
        		+ "{\r\n"
        		+ "font-size:80%;\r\n"
        		+ "padding:0em;\r\n"
        		+ "margin-top:0em;\r\n"
        		+ "margin-bottom:0em;\r\n"
        		+ "margin-left:1.7em;\r\n"
        		+ "margin-right:.2em;\r\n"
        		+ "text-indent:0em;\r\n"
        		+ "text-align:justify;\r\n"
        		+ "}\r\n"
        		+ ".tab1b\r\n"
        		+ "{\r\n"
        		+ "font-size:80%;\r\n"
        		+ "padding:0em;\r\n"
        		+ "margin-top:0em;\r\n"
        		+ "margin-bottom:0em;\r\n"
        		+ "margin-left:.3em;\r\n"
        		+ "font-weight:bold;\r\n"
        		+ "text-indent:0em;\r\n"
        		+ "text-align:justify;\r\n"
        		+ "}\r\n"
        		+ ".table11a\r\n"
        		+ "{\r\n"
        		+ "margin-left:0em;\r\n"
        		+ "margin-top:1em;\r\n"
        		+ "margin-bottom:1em;\r\n"
        		+ "width:100%;\r\n"
        		+ "border-spacing:0em;\r\n"
        		+ "padding:0em;\r\n"
        		+ "border-collapse:collapse;\r\n"
        		+ "}\r\n"
        		+ ".thead\r\n"
        		+ "{\r\n"
        		+ "font-size:80%;\r\n"
        		+ "margin-top:0em;\r\n"
        		+ "margin-bottom:0em;\r\n"
        		+ "margin-left:.3em;\r\n"
        		+ "text-indent:0em;\r\n"
        		+ "text-align:justify;\r\n"
        		+ "}\r\n"
        		+ ".theadc\r\n"
        		+ "{\r\n"
        		+ "font-size:80%;\r\n"
        		+ "margin-top:0em;\r\n"
        		+ "margin-bottom:0em;\r\n"
        		+ "margin-left:.3em;\r\n"
        		+ "text-indent:0em;\r\n"
        		+ "text-align:center;\r\n"
        		+ "}\r\n"
        		+ ".clr\r\n"
        		+ "{\r\n"
        		+ "background-color:#CCCCCC;\r\n"
        		+ "color:#000000;\r\n"
        		+ "}\r\n"
        		+ ".tab1c\r\n"
        		+ "{\r\n"
        		+ "font-size:80%;\r\n"
        		+ "margin-top:0em;\r\n"
        		+ "margin-bottom:0em;\r\n"
        		+ "text-indent:0em;\r\n"
        		+ "text-align:center;\r\n"
        		+ "}\r\n"
        		+ ".attrib\r\n"
        		+ "{\r\n"
        		+ "font-size:80%;\r\n"
        		+ "margin-top:.3em;\r\n"
        		+ "margin-bottom:0em;\r\n"
        		+ "text-indent:0em;\r\n"
        		+ "text-align:left;\r\n"
        		+ "}\r\n"
        		+ ".quote\r\n"
        		+ "{\r\n"
        		+ "margin-top:0em;\r\n"
        		+ "margin-left:0em;\r\n"
        		+ "margin-right:0em;\r\n"
        		+ "margin-bottom:0em;\r\n"
        		+ "text-align:justify;\r\n"
        		+ "font-size:80%;\r\n"
        		+ "text-indent:0em;\r\n"
        		+ "}\r\n"
        		+ ".quotet\r\n"
        		+ "{\r\n"
        		+ "margin-top:1em;\r\n"
        		+ "margin-left:0em;\r\n"
        		+ "margin-right:0em;\r\n"
        		+ "margin-bottom:0em;\r\n"
        		+ "text-align:justify;\r\n"
        		+ "font-size:80%;\r\n"
        		+ "text-indent:0em;\r\n"
        		+ "}\r\n"
        		+ ".quotei\r\n"
        		+ "{\r\n"
        		+ "margin-top:0em;\r\n"
        		+ "margin-left:0em;\r\n"
        		+ "margin-right:0em;\r\n"
        		+ "margin-bottom:0em;\r\n"
        		+ "text-align:justify;\r\n"
        		+ "font-size:80%;\r\n"
        		+ "text-indent:1em;\r\n"
        		+ "}\r\n"
        		+ ".pgnote\r\n"
        		+ "{\r\n"
        		+ "margin-top:0em;\r\n"
        		+ "margin-left:0em;\r\n"
        		+ "margin-right:0em;\r\n"
        		+ "margin-bottom:0em;\r\n"
        		+ "text-align:justify;\r\n"
        		+ "font-size:75%;\r\n"
        		+ "text-indent:0em;\r\n"
        		+ "}\r\n"
        		+ ".fltn\r\n"
        		+ "{\r\n"
        		+ "float:left;\r\n"
        		+ "text-indent:-1.5em;\r\n"
        		+ "}\r\n"
        		+ ".btnumlist\r\n"
        		+ "{\r\n"
        		+ "margin-top:0em;\r\n"
        		+ "margin-bottom:0em;\r\n"
        		+ "text-align:justify;\r\n"
        		+ "font-style:normal;\r\n"
        		+ "font-size:.90rem;\r\n"
        		+ "margin-left:2em;\r\n"
        		+ "text-indent:0em;\r\n"
        		+ "}\r\n"
        		+ ".numlist\r\n"
        		+ "{\r\n"
        		+ "margin-top:0em;\r\n"
        		+ "margin-bottom:0em;\r\n"
        		+ "text-align:justify;\r\n"
        		+ "font-style:normal;\r\n"
        		+ "font-size:.80rem;\r\n"
        		+ "margin-left:1.5em;\r\n"
        		+ "text-indent:0em;\r\n"
        		+ "}\r\n"
        		+ ".numlist1\r\n"
        		+ "{\r\n"
        		+ "margin-top:0em;\r\n"
        		+ "margin-bottom:0em;\r\n"
        		+ "text-align:justify;\r\n"
        		+ "font-style:normal;\r\n"
        		+ "font-size:.80rem;\r\n"
        		+ "margin-left:1em;\r\n"
        		+ "text-indent:0em;\r\n"
        		+ "}\r\n"
        		+ ".nn\r\n"
        		+ "{\r\n"
        		+ "float:left;\r\n"
        		+ "margin-left:-2em;\r\n"
        		+ "}\r\n"
        		+ ".numlistn\r\n"
        		+ "{\r\n"
        		+ "margin-top:0em;\r\n"
        		+ "margin-bottom:0em;\r\n"
        		+ "text-align:justify;\r\n"
        		+ "font-style:normal;\r\n"
        		+ "font-size:80%;\r\n"
        		+ "margin-left:2em;\r\n"
        		+ "text-indent:0em;\r\n"
        		+ "}\r\n"
        		+ ".simplelist1\r\n"
        		+ "{\r\n"
        		+ "margin-top:0em;\r\n"
        		+ "margin-bottom:0em;\r\n"
        		+ "text-align:justify;\r\n"
        		+ "font-style:normal;\r\n"
        		+ "font-size:80%;\r\n"
        		+ "margin-left:2em;\r\n"
        		+ "text-indent:0em;\r\n"
        		+ "}\r\n"
        		+ ".sim\r\n"
        		+ "{\r\n"
        		+ "float:left;\r\n"
        		+ "margin-left:-5em;\r\n"
        		+ "}\r\n"
        		+ ".sim1\r\n"
        		+ "{\r\n"
        		+ "float:left;\r\n"
        		+ "margin-left:-2em;\r\n"
        		+ "}\r\n"
        		+ ".laphalist\r\n"
        		+ "{\r\n"
        		+ "margin-top:.2em;\r\n"
        		+ "margin-bottom:0em;\r\n"
        		+ "text-align:justify;\r\n"
        		+ "font-style:normal;\r\n"
        		+ "font-size:.80rem;\r\n"
        		+ "margin-left:2em;\r\n"
        		+ "text-indent:0em;\r\n"
        		+ "}\r\n"
        		+ ".ualphalist\r\n"
        		+ "{\r\n"
        		+ "margin-top:.2em;\r\n"
        		+ "margin-bottom:0em;\r\n"
        		+ "text-align:justify;\r\n"
        		+ "font-style:normal;\r\n"
        		+ "font-size:.80rem;\r\n"
        		+ "margin-left:0em;\r\n"
        		+ "text-indent:0em;\r\n"
        		+ "}\r\n"
        		+ ".bulldash\r\n"
        		+ "{\r\n"
        		+ "margin-top:0em;\r\n"
        		+ "margin-bottom:0em;\r\n"
        		+ "text-align:justify;\r\n"
        		+ "font-style:normal;\r\n"
        		+ "font-size:80%;\r\n"
        		+ "margin-left:2em;\r\n"
        		+ "text-indent:0em;\r\n"
        		+ "padding-left:0.5em;\r\n"
        		+ "}\r\n"
        		+ ".bull\r\n"
        		+ "{\r\n"
        		+ "margin-top:0em;\r\n"
        		+ "margin-bottom:0em;\r\n"
        		+ "text-align:justify;\r\n"
        		+ "font-style:normal;\r\n"
        		+ "font-size:80%;\r\n"
        		+ "margin-left:0em;\r\n"
        		+ "text-indent:0em;\r\n"
        		+ "padding-left:0.5em;\r\n"
        		+ "}\r\n"
        		+ ".bull1\r\n"
        		+ "{\r\n"
        		+ "margin-top:0em;\r\n"
        		+ "margin-bottom:0em;\r\n"
        		+ "text-align:justify;\r\n"
        		+ "font-style:normal;\r\n"
        		+ "font-size:.80rem;\r\n"
        		+ "margin-left:0em;\r\n"
        		+ "text-indent:0em;\r\n"
        		+ "}\r\n"
        		+ ".bulli\r\n"
        		+ "{\r\n"
        		+ "margin-top:0em;\r\n"
        		+ "margin-bottom:0em;\r\n"
        		+ "text-align:justify;\r\n"
        		+ "font-style:normal;\r\n"
        		+ "font-size:.80rem;\r\n"
        		+ "margin-left:0em;\r\n"
        		+ "text-indent:1.5em;\r\n"
        		+ "}\r\n"
        		+ ".footnote\r\n"
        		+ "{\r\n"
        		+ "border-top:1px solid;\r\n"
        		+ "padding-top:.2em;\r\n"
        		+ "margin-top:2em;\r\n"
        		+ "margin-bottom:0em;\r\n"
        		+ "margin-left:0em;\r\n"
        		+ "text-indent:0em;\r\n"
        		+ "}\r\n"
        		+ "/* Backmatter */\r\n"
        		+ ".hh\r\n"
        		+ "{\r\n"
        		+ "float:left;\r\n"
        		+ "text-indent:-5em;\r\n"
        		+ "}\r\n"
        		+ ".hangt\r\n"
        		+ "{\r\n"
        		+ "margin-top:1em;\r\n"
        		+ "margin-bottom:0em;\r\n"
        		+ "text-align:justify;\r\n"
        		+ "font-size:80%;\r\n"
        		+ "margin-right:0em;\r\n"
        		+ "margin-left:5em;\r\n"
        		+ "padding-left:0em;\r\n"
        		+ "text-indent:0em;\r\n"
        		+ "}\r\n"
        		+ ".hangt1\r\n"
        		+ "{\r\n"
        		+ "margin-top:1em;\r\n"
        		+ "margin-bottom:0em;\r\n"
        		+ "text-align:justify;\r\n"
        		+ "font-size:80%;\r\n"
        		+ "margin-right:0em;\r\n"
        		+ "margin-left:0em;\r\n"
        		+ "padding-left:0em;\r\n"
        		+ "text-indent:0em;\r\n"
        		+ "}\r\n"
        		+ ".hang\r\n"
        		+ "{\r\n"
        		+ "margin-top:0em;\r\n"
        		+ "margin-bottom:0em;\r\n"
        		+ "text-align:justify;\r\n"
        		+ "margin-right:1em;\r\n"
        		+ "font-size:80%;\r\n"
        		+ "margin-left:1em;\r\n"
        		+ "text-indent:-1em;\r\n"
        		+ "}\r\n"
        		+ ".hang1\r\n"
        		+ "{\r\n"
        		+ "margin-top:0em;\r\n"
        		+ "margin-bottom:0em;\r\n"
        		+ "text-align:justify;\r\n"
        		+ "margin-right:1em;\r\n"
        		+ "font-size:80%;\r\n"
        		+ "margin-left:1em;\r\n"
        		+ "text-indent:-1em;\r\n"
        		+ "}\r\n"
        		+ ".hang2\r\n"
        		+ "{\r\n"
        		+ "margin-top:0em;\r\n"
        		+ "margin-bottom:0em;\r\n"
        		+ "text-align:justify;\r\n"
        		+ "margin-right:1em;\r\n"
        		+ "font-size:80%;\r\n"
        		+ "margin-left:5em;\r\n"
        		+ "text-indent:-1em;\r\n"
        		+ "}\r\n"
        		+ ".top\r\n"
        		+ "{\r\n"
        		+ "margin-top:1em;\r\n"
        		+ "}\r\n"
        		+ ".index\r\n"
        		+ "{\r\n"
        		+ "margin-top:0em;\r\n"
        		+ "margin-bottom:0em;\r\n"
        		+ "text-align:left;\r\n"
        		+ "margin-left:1em;\r\n"
        		+ "text-indent:-1em;\r\n"
        		+ "font-size:.70rem;\r\n"
        		+ "}\r\n"
        		+ ".index2\r\n"
        		+ "{\r\n"
        		+ "margin-top:0em;\r\n"
        		+ "margin-bottom:0em;\r\n"
        		+ "text-align:left;\r\n"
        		+ "margin-left:2em;\r\n"
        		+ "text-indent:-1em;\r\n"
        		+ "font-size:.70rem;\r\n"
        		+ "}\r\n"
        		+ ".index3\r\n"
        		+ "{\r\n"
        		+ "margin-top:0em;\r\n"
        		+ "margin-bottom:0em;\r\n"
        		+ "text-align:left;\r\n"
        		+ "margin-left:3em;\r\n"
        		+ "text-indent:-1em;\r\n"
        		+ "font-size:.70rem;\r\n"
        		+ "}\r\n"
        		+ ".r1\r\n"
        		+ "{\r\n"
        		+ "float:left;\r\n"
        		+ "text-indent:-2.5em;\r\n"
        		+ "}\r\n"
        		+ ".ref\r\n"
        		+ "{\r\n"
        		+ "font-size:75%;\r\n"
        		+ "margin-top:0em;\r\n"
        		+ "text-align:justify;\r\n"
        		+ "margin-bottom:0em;\r\n"
        		+ "margin-left:2.5em;\r\n"
        		+ "text-indent:0em;\r\n"
        		+ "}\r\n"
        		+ ".reft\r\n"
        		+ "{\r\n"
        		+ "font-size:75%;\r\n"
        		+ "margin-top:1em;\r\n"
        		+ "text-align:justify;\r\n"
        		+ "margin-bottom:0em;\r\n"
        		+ "margin-left:2.5em;\r\n"
        		+ "text-indent:0em;\r\n"
        		+ "}\r\n"
        		+ ".refp\r\n"
        		+ "{\r\n"
        		+ "font-size:75%;\r\n"
        		+ "margin-top:0em;\r\n"
        		+ "text-align:justify;\r\n"
        		+ "margin-bottom:0em;\r\n"
        		+ "margin-left:1em;\r\n"
        		+ "text-indent:-1em;\r\n"
        		+ "}\r\n"
        		+ ".top\r\n"
        		+ "{\r\n"
        		+ "margin-top:1em;\r\n"
        		+ "}\r\n"
        		+ ".ref1\r\n"
        		+ "{\r\n"
        		+ "font-size:75%;\r\n"
        		+ "margin-top:0em;\r\n"
        		+ "text-align:justify;\r\n"
        		+ "margin-bottom:0em;\r\n"
        		+ "margin-left:2.5em;\r\n"
        		+ "text-indent:0em;\r\n"
        		+ "}\r\n"
        		+ "blockquote\r\n"
        		+ "{\r\n"
        		+ "padding:0em;\r\n"
        		+ "margin:1em 1.5em 1em 1.5em;\r\n"
        		+ "}\r\n"
        		+ ".olist\r\n"
        		+ "{\r\n"
        		+ "padding:0em;\r\n"
        		+ "margin:.5em 0em .5em 0em;\r\n"
        		+ "}\r\n"
        		+ ".olist1\r\n"
        		+ "{\r\n"
        		+ "padding:0em;\r\n"
        		+ "margin:1em 0em 0em 0em;\r\n"
        		+ "list-style:none;\r\n"
        		+ "}\r\n"
        		+ ".olist2\r\n"
        		+ "{\r\n"
        		+ "padding:0em;\r\n"
        		+ "margin:1em 0em 0em 0em;\r\n"
        		+ "list-style:upper-alpha;\r\n"
        		+ "}\r\n"
        		+ ".nlist\r\n"
        		+ "{\r\n"
        		+ "padding:0em;\r\n"
        		+ "margin:0em 0em .5em 0em;\r\n"
        		+ "list-style:none;\r\n"
        		+ "}\r\n"
        		+ ".lalpha\r\n"
        		+ "{\r\n"
        		+ "padding:0em;\r\n"
        		+ "margin:.5em 0em .5em 0em;\r\n"
        		+ "list-style:lower-alpha;\r\n"
        		+ "}\r\n"
        		+ ".ualpha\r\n"
        		+ "{\r\n"
        		+ "padding:0em;\r\n"
        		+ "margin:0.5em 0em 0em 0em;\r\n"
        		+ "list-style:upper-alpha;\r\n"
        		+ "}\r\n"
        		+ ".ulist1\r\n"
        		+ "{\r\n"
        		+ "padding:0em;\r\n"
        		+ "margin:0.5em 0em 0em 0em;\r\n"
        		+ "list-style:disc;\r\n"
        		+ "}\r\n"
        		+ ".ulist_p\r\n"
        		+ "{\r\n"
        		+ "padding:0em;\r\n"
        		+ "margin:0em 0em 0em 0em;\r\n"
        		+ "list-style:disc;\r\n"
        		+ "}\r\n"
        		+ ".tlist\r\n"
        		+ "{\r\n"
        		+ "padding:0em;\r\n"
        		+ "margin:0em 0em 0em 0em;\r\n"
        		+ "list-style:disc;\r\n"
        		+ "}\r\n"
        		+ ".f11\r\n"
        		+ "{\r\n"
        		+ "float:left;\r\n"
        		+ "text-indent:-1.5em;\r\n"
        		+ "}\r\n"
        		+ ".ulist\r\n"
        		+ "{\r\n"
        		+ "padding:0em;\r\n"
        		+ "margin:.5em 0em .5em .8em;\r\n"
        		+ "list-style:disc\r\n"
        		+ "}\r\n"
        		+ ".ulist2\r\n"
        		+ "{\r\n"
        		+ "padding:0em;\r\n"
        		+ "margin:.0em 0em 0em .8em;\r\n"
        		+ "list-style:disc\r\n"
        		+ "}\r\n"
        		+ ".bolist\r\n"
        		+ "{\r\n"
        		+ "padding:0em;\r\n"
        		+ "margin:0em 0em 0em 0em;\r\n"
        		+ "list-style:none;\r\n"
        		+ "}\r\n"
        		+ ".simple\r\n"
        		+ "{\r\n"
        		+ "list-style:none;\r\n"
        		+ "padding:0em;\r\n"
        		+ "margin:.5em 0em .5em 1em;\r\n"
        		+ "}\r\n"
        		+ "th\r\n"
        		+ "{\r\n"
        		+ "font-weight:normal;\r\n"
        		+ "}\r\n"
        		+ ".ulindex\r\n"
        		+ "{\r\n"
        		+ "padding:0em;\r\n"
        		+ "margin:0em;\r\n"
        		+ "list-style:none;\r\n"
        		+ "}\r\n"
        		+ ".none\r\n"
        		+ "{\r\n"
        		+ "padding:0em;\r\n"
        		+ "margin:1em 0em 0em 0em;\r\n"
        		+ "list-style:none;\r\n"
        		+ "}\r\n"
        		+ "small\r\n"
        		+ "{\r\n"
        		+ "text-transform:uppercase;\r\n"
        		+ "}\r\n"
        		+ ".rlist\r\n"
        		+ "{\r\n"
        		+ "list-style-type: none;\r\n"
        		+ "margin-top:0em;\r\n"
        		+ "margin-bottom:0em;\r\n"
        		+ "margin-left:0em;\r\n"
        		+ "padding-left:0em;\r\n"
        		+ "text-indent:0em;\r\n"
        		+ "}\r\n"
        		+ ".blk\r\n"
        		+ "{\r\n"
        		+ "display:block;\r\n"
        		+ "}\r\n"
        		+ ".hide\r\n"
        		+ "{\r\n"
        		+ "display:none;\r\n"
        		+ "visibility: hidden;\r\n"
        		+ "}\r\n"
        		+ ".space\r\n"
        		+ "{\r\n"
        		+ "padding:1em;\r\n"
        		+ "}\r\n"
        		+ ".tochead\r\n"
        		+ "{\r\n"
        		+ "margin-top:1em;\r\n"
        		+ "margin-bottom:2em;\r\n"
        		+ "text-align:center;\r\n"
        		+ "font-weight:bold;\r\n"
        		+ "font-size:90%;\r\n"
        		+ "text-indent:0em;\r\n"
        		+ "font-variant: small-caps;\r\n"
        		+ "}\r\n"
        		+ ".tab\r\n"
        		+ "{\r\n"
        		+ "font-size:80%;\r\n"
        		+ "padding:0em;\r\n"
        		+ "margin-top:0em;\r\n"
        		+ "margin-bottom:0em;\r\n"
        		+ "margin-left:0em;\r\n"
        		+ "text-indent:0em;\r\n"
        		+ "text-align:justify;\r\n"
        		+ "}\r\n"
        		+ ".tf\r\n"
        		+ "{\r\n"
        		+ "float:left;\r\n"
        		+ "text-indent:-1.5em;\r\n"
        		+ "}\r\n"
        		+ ".tabfn\r\n"
        		+ "{\r\n"
        		+ "font-size:80%;\r\n"
        		+ "margin-top:0em;\r\n"
        		+ "margin-bottom:0em;\r\n"
        		+ "margin-left:1.5em;\r\n"
        		+ "text-indent:0em;\r\n"
        		+ "text-align:justify;\r\n"
        		+ "}\r\n"
        		+ ".tabfn1\r\n"
        		+ "{\r\n"
        		+ "font-size:80%;\r\n"
        		+ "padding:0em;\r\n"
        		+ "margin-top:0.5em;\r\n"
        		+ "margin-bottom:0em;\r\n"
        		+ "margin-left:0em;\r\n"
        		+ "text-indent:0em;\r\n"
        		+ "text-align:justify;\r\n"
        		+ "}\r\n"
        		+ ".tbull\r\n"
        		+ "{\r\n"
        		+ "margin-top:0em;\r\n"
        		+ "margin-bottom:0em;\r\n"
        		+ "text-align:justify;\r\n"
        		+ "font-style:normal;\r\n"
        		+ "font-size:80%;\r\n"
        		+ "margin-left:1.5em;\r\n"
        		+ "text-indent:0em;\r\n"
        		+ "padding-left:0.5em;\r\n"
        		+ "}\r\n"
        		+ ".tbull_p\r\n"
        		+ "{\r\n"
        		+ "margin-top:0em;\r\n"
        		+ "margin-bottom:0em;\r\n"
        		+ "text-align:justify;\r\n"
        		+ "font-style:normal;\r\n"
        		+ "font-size:80%;\r\n"
        		+ "margin-left:1.5em;\r\n"
        		+ "text-indent:0em;\r\n"
        		+ "padding-left:0em;\r\n"
        		+ "}\r\n"
        		+ ".tfa\r\n"
        		+ "{\r\n"
        		+ "float:left;\r\n"
        		+ "text-indent:-3.5em;\r\n"
        		+ "}\r\n"
        		+ ".tabfna\r\n"
        		+ "{\r\n"
        		+ "font-size:80%;\r\n"
        		+ "margin-top:0em;\r\n"
        		+ "margin-bottom:0em;\r\n"
        		+ "margin-left:3.5em;\r\n"
        		+ "text-indent:0em;\r\n"
        		+ "text-align:justify;\r\n"
        		+ "}\r\n"
        		+ ".bs\r\n"
        		+ "{\r\n"
        		+ "float:left;\r\n"
        		+ "text-indent:-1.5em;\r\n"
        		+ "}\r\n"
        		+ ".bulls\r\n"
        		+ "{\r\n"
        		+ "margin-top:0em;\r\n"
        		+ "margin-bottom:0em;\r\n"
        		+ "text-align:justify;\r\n"
        		+ "font-style:normal;\r\n"
        		+ "font-size:80%;\r\n"
        		+ "margin-left:0.5em;\r\n"
        		+ "text-indent:0em;\r\n"
        		+ "padding-left:0em;\r\n"
        		+ "}\r\n"
        		+ ".bx\r\n"
        		+ "{\r\n"
        		+ "float:left;\r\n"
        		+ "text-indent:-1.5em;\r\n"
        		+ "}\r\n"
        		+ ".bullx\r\n"
        		+ "{\r\n"
        		+ "margin-top:0em;\r\n"
        		+ "margin-bottom:0em;\r\n"
        		+ "text-align:justify;\r\n"
        		+ "font-style:normal;\r\n"
        		+ "font-size:80%;\r\n"
        		+ "margin-left:1.5em;\r\n"
        		+ "text-indent:0em;\r\n"
        		+ "padding-left:0em;\r\n"
        		+ "}\r\n"
        		+ ".ball\r\n"
        		+ "{\r\n"
        		+ "border:1px solid;\r\n"
        		+ "}\r\n"
        		+ ".ss\r\n"
        		+ "{\r\n"
        		+ "float:left;\r\n"
        		+ "text-indent:-3.5em;\r\n"
        		+ "}\r\n"
        		+ ".ns\r\n"
        		+ "{\r\n"
        		+ "margin-top:0em;\r\n"
        		+ "margin-bottom:0em;\r\n"
        		+ "text-align:justify;\r\n"
        		+ "font-style:normal;\r\n"
        		+ "font-size:80%;\r\n"
        		+ "margin-left:3.5em;\r\n"
        		+ "text-indent:0em;\r\n"
        		+ "padding-left:0em;\r\n"
        		+ "}\r\n"
        		+ ".nst\r\n"
        		+ "{\r\n"
        		+ "margin-top:1em;\r\n"
        		+ "margin-bottom:0em;\r\n"
        		+ "text-align:justify;\r\n"
        		+ "font-style:normal;\r\n"
        		+ "font-size:80%;\r\n"
        		+ "margin-left:3.5em;\r\n"
        		+ "text-indent:0em;\r\n"
        		+ "padding-left:0em;\r\n"
        		+ "}\r\n"
        		+ ".cc\r\n"
        		+ "{\r\n"
        		+ "background-color:#dcdcdc;\r\n"
        		+ "border-bottom:1px solid;\r\n"
        		+ "border-right:1px solid;\r\n"
        		+ "}\r\n"
        		+ ".tabx\r\n"
        		+ "{\r\n"
        		+ "font-size:80%;\r\n"
        		+ "padding:0em;\r\n"
        		+ "margin-top:0em;\r\n"
        		+ "margin-bottom:0em;\r\n"
        		+ "margin-left:1em;\r\n"
        		+ "text-indent:-1em;\r\n"
        		+ "text-align:justify;\r\n"
        		+ "}\r\n"
        		+ ".tab1x\r\n"
        		+ "{\r\n"
        		+ "font-size:80%;\r\n"
        		+ "padding:0em;\r\n"
        		+ "margin-top:0em;\r\n"
        		+ "margin-bottom:0em;\r\n"
        		+ "margin-left:1em;\r\n"
        		+ "text-indent:0em;\r\n"
        		+ "text-align:left;\r\n"
        		+ "}\r\n"
        		+ ".scroll\r\n"
        		+ "{\r\n"
        		+ "width: auto;\r\n"
        		+ "height: auto;\r\n"
        		+ "overflow-x: auto;\r\n"
        		+ "display: block;\r\n"
        		+ "-webkit-overflow-scrolling: touch;\r\n"
        		+ "margin-top:1em;\r\n"
        		+ "margin-bottom:1em;\r\n"
        		+ "}\r\n"
        		+ "\r\n"
        		+ "/*Extra classes*/\r\n"
        		+ "";

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

private void createNavXhtml(List<String> links) throws IOException {
    String xhtmlHeader = "<?xml version=\"1.0\" encoding=\"UTF-8\"?>\n" +
                         "<!DOCTYPE html PUBLIC \"-//W3C//DTD XHTML 1.1//EN\" " +
                         "\"http://www.w3.org/TR/xhtml11/DTD/xhtml11.dtd\">\n" +
                         "<html xmlns=\"http://www.w3.org/1999/xhtml\">\n" +
                         "<head>\n" +
                         "    <title>Table of Contents</title>\n" +
                         "    <style type=\"text/css\">\n" +
                         "        nav#toc { background: #f9f9f9; padding: 20px; margin-bottom: 20px; }\n" +
                         "        nav#toc ul { list-style-type: none; padding-left: 0; }\n" +
                         "        nav#toc li { margin-bottom: 5px; }\n" +
                         "        nav#toc a { text-decoration: none; color: #000; }\n" +
                         "        nav#toc a:hover { text-decoration: underline; }\n" +
                         "    </style>\n" +
                         "</head>\n" +
                         "<body>\n" +
                         "<nav id=\"toc\">\n" +
                         "<h2>Table of Contents</h2>\n" +
                         "<ul>\n";

    // Append links from the list
    StringBuilder linksBuilder = new StringBuilder();
    for (String link : links) {
        linksBuilder.append("<li>").append(link).append("</li>\n");
    }

    String xhtmlFooter = "</ul>\n</nav>\n</body>\n</html>";

    String xhtmlContent = xhtmlHeader + linksBuilder.toString() + xhtmlFooter;

    // Specify the path to the file
    Path filePath = Paths.get("path/to/nav.xhtml");

    // Ensure the parent directory exists
    Files.createDirectories(filePath.getParent());

    // Write to the file
    Files.write(filePath, xhtmlContent.getBytes());
}

private String extractTitleFromFileName(String fileName) {
    if (fileName == null || fileName.isEmpty()) {
        return "Untitled";
    }
    int dotIndex = fileName.lastIndexOf('.');
    String nameWithoutExtension = (dotIndex == -1) ? fileName : fileName.substring(0, dotIndex);
    // Optionally, split by underscores or hyphens and capitalize words
    String[] words = nameWithoutExtension.split("_|-");
    StringBuilder titleBuilder = new StringBuilder();
    for (String word : words) {
        if (word.length() > 0) {
            titleBuilder.append(Character.toUpperCase(word.charAt(0)))
                        .append(word.substring(1)).append(" ");
        }
    }
    return titleBuilder.toString().trim();
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
    int formulaCount = 0;
    int listItemCount = 0;
    boolean insideList = false;
    boolean insideMathBlock = false; // Reserved for future MathML block handling
    boolean insideTable = false;

    // Initialize paragraph outside the loop
    StringBuilder paragraph = new StringBuilder();

    // Initialize TOC builders
    StringBuilder tocSections = new StringBuilder();
    StringBuilder tocTables = new StringBuilder();
    StringBuilder tocPages = new StringBuilder();

    // Stack to keep track of current section levels
    Deque<Integer> sectionStack = new ArrayDeque<>();

    for (int pageIndex = 0; pageIndex < numberOfPages; pageIndex++) {
        textStripper.setStartPage(pageIndex + 1);
        textStripper.setEndPage(pageIndex + 1);
        String pageText = textStripper.getText(document);

        xmlContent.append("<page id=\"page-").append(pageIndex + 1).append("\">\n");

        // Add TOC entry for the page
        tocPages.append("<li><a href=\"#page-")
                .append(pageIndex + 1)
                .append("\">Page ")
                .append(pageIndex + 1)
                .append("</a></li>\n");

        // Split the page text into lines
        String[] lines = pageText.split("\\r?\\n");

        for (String line : lines) {
            lineCount++;
            String trimmedLine = line.trim();

            // Handle non-breaking characters and avoid invalid symbols
            trimmedLine = trimmedLine.replace("\uFFFD", ""); // Remove replacement character
            trimmedLine = trimmedLine.replaceAll("[^\\x20-\\x7E]+", ""); // Remove non-ASCII characters

            // Detect heading levels
            HeadingInfo headingInfo = detectHeadingLevel(trimmedLine);
            if (headingInfo.isHeading) {
                // Flush any paragraph before starting a new section
                paraCount = flushParagraph(xmlContent, paragraph, paraCount);
                // Close any open table or list
                if (insideTable) {
                    xmlContent.append("</table>\n");
                    insideTable = false;
                }
                if (insideList) {
                    xmlContent.append("</ul>\n");
                    insideList = false;
                }

                // Manage section hierarchy based on heading level
                manageSectionHierarchy(xmlContent, sectionStack, headingInfo.level, sectionCount);

                // Increment section count if it's a top-level section
                if (headingInfo.level == 1) {
                    sectionCount++;
                }

                // Append the heading with appropriate tag and attributes
                xmlContent.append("<").append("h").append(headingInfo.level).append(" ")
                          .append(getHeadingAttributes(headingInfo)).append(">") 
                          .append(headingInfo.content)
                          .append("</").append("h").append(headingInfo.level).append(">\n");

                // Add TOC entry for the section
                tocSections.append("<li class=\"level").append(headingInfo.level).append("\">")
                           .append("<a href=\"#").append(headingInfo.id).append("\">")
                           .append(headingInfo.content)
                           .append("</a></li>\n");
            } 
            // Detect if the line starts a table
            else if (trimmedLine.startsWith("TABLE")) {
                // Start a new table with borders
                if (!insideTable) {
                    xmlContent.append("<table id=\"table-")
                              .append(sectionCount)
                              .append("\" style=\"border-collapse: collapse; width: 100%; border: 1px solid black;\">\n");
                    insideTable = true;

                    // Add TOC entry for the table
                    tocTables.append("<li><a href=\"#table-")
                             .append(sectionCount)
                             .append("\">Table in Section ")
                             .append(sectionCount)
                             .append("</a></li>\n");
                }
                // Here you can add your logic to parse table rows and columns
            } else if (insideTable) {
                // Handle table rows with borders
                xmlContent.append("<tr style=\"border: 1px solid black;\">\n");
                String[] columns = trimmedLine.split("\\s+"); // Split columns based on whitespace
                for (String column : columns) {
                    xmlContent.append("<td style=\"border: 1px solid black; padding: 8px;\">")
                              .append(column)
                              .append("</td>\n"); // Add each column in a table cell with borders and padding
                }
                xmlContent.append("</tr>\n");
            }
            // Detect if the line is a bulleted list item
            else {
            	// Extend bullet symbols to include numeric and alphabetic patterns, both with period and comma
            	String[] bulletSymbols = {"•", "○", "▪", "❑", "✓", "–", "-", "*",
            	                          "1.", "2.", "3.", "4.", "5.", "6.", "7.", "8.", "9.", "10.",
            	                          "a.", "b.", "c.", "d.", "e.", "f.", "g.", "h.", "i.", "j.", "k.", "l.", "m.", "n.", "o.", "p.", 
            	                          "A.", "B.", "C.", "D.", "E.", "F.", "G.", "H.", "I.", "J.", "K.", "L.", "M.", "N.", "O.", "P.",
            	                          "1,", "2,", "3,", "4,", "5,", "6,", "7,", "8,", "9,", "10,",  // Numeric list with commas
            	                          "a,", "b,", "c,", "d,", "e,", "f,", "g,", "h,", "i,", "j,", "k,", "l.", "m,", "n,", "o,", "p,",  // Alphabetic list with commas
            	                          "A,", "B,", "C,", "D,", "E,", "F,", "G,", "H,", "I,", "J,", "K,", "L,", "M,", "N.", "O.", "P."  // Uppercase alphabetic with commas
            	                         };

                boolean isBullet = false;
                String bulletSymbol = "";

                for (String symbol : bulletSymbols) {
                    if (trimmedLine.startsWith(symbol + " ")) { // Ensuring a space after the bullet
                        isBullet = true;
                        bulletSymbol = symbol;
                        trimmedLine = trimmedLine.substring(symbol.length()).trim(); // Remove the bullet symbol
                        break;
                    }
                }

                if (isBullet) {
                    listItemCount++;
                    if (!insideList) {
                        paraCount = flushParagraph(xmlContent, paragraph, paraCount);
                        // Close any open table
                        if (insideTable) {
                            xmlContent.append("</table>\n");
                            insideTable = false;
                        }
                        xmlContent.append("<ul id=\"list-")
                                  .append(listItemCount)
                                  .append("\" style=\"list-style-type: disc; margin-left: 20px;\">\n");
                        insideList = true;
                    }
                    // Add the list item with count and style
                    xmlContent.append("<li id=\"li-").append(String.format("%04d", listItemCount)).append("\">")
                    .append(trimmedLine).append("</li>\n");
                 } else {
                    // If we were inside a bulleted list, close it
                    if (insideList) {
                        xmlContent.append("</ul>\n");
                        insideList = false;
                    }

                    // Detect if the line is a block display formula
                    if (isBlockFormula(trimmedLine)) {
                        paraCount = flushParagraph(xmlContent, paragraph, paraCount);
                        // Close any open table
                        if (insideTable) {
                            xmlContent.append("</table>\n");
                            insideTable = false;
                        }
                        formulaCount++;
                        xmlContent.append("<p class=\"noindentt\" id=\"formula-")
                                  .append(String.format("%04d", formulaCount))
                                  .append("\">")
                                  .append(getMathMLBlockFormula(trimmedLine))
                                  .append("</p>\n");
                     } else {
                        // Handle regular paragraph text (concatenating lines)
                        if (!trimmedLine.isEmpty()) {
                            // Detect formatting (bold and italic) if applicable
                            String formattedLine = formatText(trimmedLine);

                            // Handle inline formulas within the paragraph
                            String processedLine = insertInlineFormula(formattedLine);

                            if (paragraph.length() > 0) {
                                paragraph.append(" "); // Add space between concatenated lines
                            }
                            paragraph.append(processedLine);

                            // If the line ends with punctuation, it's the end of a paragraph
                            if (trimmedLine.endsWith(".") || trimmedLine.endsWith("!") || trimmedLine.endsWith("?")) {
                                paraCount = flushParagraph(xmlContent, paragraph, paraCount);
                             }
                        }
                    }
                }
            }
        }

        // Extract and insert images after processing the text of the page
        List<String> imageFileNames = extractImages(document, pageIndex);
        for (String imageFileName : imageFileNames) {
            // Assuming images are placed after the text content of the page
            int figNumber = imageFileNames.indexOf(imageFileName) + 1;
            String figureId = "fig" + (pageIndex + 1) + "_" + figNumber;

            xmlContent.append("<figure id=\"").append(figureId).append("\">\n");
            xmlContent.append("    <img class=\"image\" src=\"../images/").append(imageFileName).append("\" alt=\"\"/>\n");
            xmlContent.append("    <figcaption>\n");
            xmlContent.append("        <p class=\"figcaption\"><span epub:type=\"label\">Fig.</span> ")
                      .append("<span epub:type=\"ordinal\">").append(pageIndex + 1).append("_").append(figNumber).append("</span> ")
                      .append("Description of the image.</p>\n"); // You can customize the caption as needed
            xmlContent.append("    </figcaption>\n");
            xmlContent.append("</figure>\n");
         }

        // Close any open structures at the end of the page
        if (insideList) {
            xmlContent.append("</ul>\n");
            insideList = false;
         }
        if (insideTable) {
            xmlContent.append("</table>\n");
            insideTable = false;
         }

        xmlContent.append("</page>\n");
    }

    // Finalize any remaining paragraph
    paraCount = flushParagraph(xmlContent, paragraph, paraCount);

    StringBuilder tocContent = new StringBuilder();
    tocContent.append("<nav id=\"toc\">\n");
    tocContent.append("<h2>Table of Contents</h2>\n");
    tocContent.append("<ul>\n");
    tocContent.append("<li>Sections\n<ul>\n").append(tocSections).append("</ul></li>\n");
    tocContent.append("<li>Tables\n<ul>\n").append(tocTables).append("</ul></li>\n");
    tocContent.append("<li>Pages\n<ul>\n").append(tocPages).append("</ul></li>\n");
    tocContent.append("</ul>\n");
    tocContent.append("</nav>\n");

    createXhtmlFile("nav.xhtml", tocContent.toString());

    // Create the full content with TOC
    StringBuilder fullContent = new StringBuilder();
     fullContent.append("<title>Your Document Title</title>\n");
     fullContent.append("<p><a href=\"nav.xhtml\">Go to Table of Contents</a></p>\n");
    fullContent.append(xmlContent.toString());
 
    // Create the XHTML file with TOC
    String xhtmlFile = "output.xhtml";
    createXhtmlFile(xhtmlFile, fullContent.toString());
    xhtmlFiles.add(xhtmlFile);

    document.close();
    return xhtmlFiles;
}

/**
 * Detects the heading level of a line based on its format.
 *
 * @param line The line of text to evaluate.
 * @return A HeadingInfo object containing heading details.
 */
private HeadingInfo detectHeadingLevel(String line) {
    HeadingInfo info = new HeadingInfo();
    info.isHeading = false;

    // Example criteria for heading levels
    // Adjust these based on your PDF's formatting

    // H1: All uppercase, longer length, no punctuation
    if (line.matches("^[A-Z ]{5,}$") && !line.matches(".*[.!?].*")) {
        info.isHeading = true;
        info.level = 1;
        info.content = line;
        int sectionCount = 0;
		info.id = "chapter-" + (sectionCount + 1); // Ensure sectionCount is accessible
        return info;
    }

    // H2: Numbered sections like "1.1 Title"
    if (line.matches("^\\d+\\.\\d+\\s+.*")) {
        info.isHeading = true;
        info.level = 2;
        info.content = line.replaceFirst("^\\d+\\.\\d+\\s+", "");
        info.id = "sec-" + line.split("\\s+")[0].replace(".", "-");
        return info;
    }

    // H3: Numbered subsections like "1.1.1 Title"
    if (line.matches("^\\d+\\.\\d+\\.\\d+\\s+.*")) {
        info.isHeading = true;
        info.level = 3;
        info.content = line.replaceFirst("^\\d+\\.\\d+\\.\\d+\\s+", "");
        info.id = "sec-" + line.split("\\s+")[0].replace(".", "-");
        return info;
    }

    // H4: Numbered sub-subsections like "1.1.1.1 Title"
    if (line.matches("^\\d+\\.\\d+\\.\\d+\\.\\d+\\s+.*")) {
        info.isHeading = true;
        info.level = 4;
        info.content = line.replaceFirst("^\\d+\\.\\d+\\.\\d+\\.\\d+\\s+", "");
        info.id = "sec-" + line.split("\\s+")[0].replace(".", "-");
        return info;
    }

    return info;
}

/**
 * Manages the section hierarchy based on the current heading level.
 *
 * @param xmlContent   The StringBuilder accumulating the XML content.
 * @param sectionStack The stack tracking current section levels.
 * @param level        The heading level of the current section.
 * @param sectionCount The current section count.
 */
private void manageSectionHierarchy(StringBuilder xmlContent, Deque<Integer> sectionStack, int level, int sectionCount) {
    while (!sectionStack.isEmpty() && sectionStack.peek() >= level) {
        xmlContent.append("</section>\n");
        sectionStack.pop();
    }
    xmlContent.append("<section aria-labelledby=\"").append("sec-").append(sectionCount).append("\">\n");
    sectionStack.push(level);
}

/**
 * Retrieves the necessary attributes for a heading tag based on its level.
 *
 * @param headingInfo The HeadingInfo object containing heading details.
 * @return A string of attributes for the heading tag.
 */
private String getHeadingAttributes(HeadingInfo headingInfo) {
    StringBuilder attributes = new StringBuilder();
    switch (headingInfo.level) {
        case 1:
            attributes.append("epub:type=\"chapter\" role=\"doc-chapter\"");
            break;
        case 2:
            attributes.append("aria-labelledby=\"").append(headingInfo.id).append("\"");
            break;
        case 3:
        case 4:
            attributes.append("aria-labelledby=\"").append(headingInfo.id).append("\"");
            break;
        default:
            break;
    }
    return attributes.toString();
}

/**
 * Helper class to store heading information.
 */
private class HeadingInfo {
    boolean isHeading;
    int level;
    String content;
    String id;
}



      
private List<String> extractImages(PDDocument document, int pageIndex) throws IOException {
    PDPage page = document.getPage(pageIndex);
    PDResources pdResources = page.getResources();
    Iterable<COSName> xobjectNames = pdResources.getXObjectNames();
    List<String> imageFileNames = new ArrayList<>();
    int imageCounter = 0;

    for (COSName xobjectName : xobjectNames) {
        PDXObject xobject = pdResources.getXObject(xobjectName);
        if (xobject instanceof PDImageXObject) {
            PDImageXObject image = (PDImageXObject) xobject;
            BufferedImage bImage = image.getImage();
            if (bImage != null) {
                imageCounter++;
                // Naming convention: pg4.jpg, pg4_1.jpg, pg4_2.jpg, etc.
                String imageFileName = "pg" + (pageIndex + 1) + (imageCounter > 1 ? "_" + (imageCounter - 1) : "") + ".jpg";
                File imageFile = new File(imagesDir, imageFileName); // Save to OEBPS/images
                // Save the image as JPEG
                ImageIO.write(bImage, "jpg", imageFile);
                imageFileNames.add(imageFileName);
            }
        }
    }

    return imageFileNames;
}

/**
         * Determines if a line represents a block display formula.
         * Modify this method based on how formulas are marked in your source text.
         *
         * @param line The line of text to check.
         * @return True if the line is a block formula, false otherwise.
         */
        private boolean isBlockFormula(String line) {
            // Example: Lines enclosed within [formula]...[/formula] tags
            // Modify the markers based on your actual formula delimiters
            return line.startsWith("[formula]") && line.endsWith("[/formula]");
        }

        /**
         * Generates the MathML representation for a block display formula.
         * Modify this method to parse and convert the formula content as needed.
         *
         * @param line The line containing the formula.
         * @return A string containing the MathML for the block formula.
         */
        private String getMathMLBlockFormula(String line) {
            // Extract the formula content between [formula] and [/formula]
            String formulaContent = line.substring("[formula]".length(), line.length() - "[/formula]".length()).trim();

            // For demonstration, returning a static MathML. Replace this with actual parsing if needed.
            // Ideally, you would convert from LaTeX or another format to MathML here.
            return "<math display='block' xmlns='http://www.w3.org/1998/Math/MathML'>"
                   + "<mtext>Thus</mtext><mo>:</mo><msub><mi>T</mi><mi>e</mi></msub><mo>=</mo>"
                   + "<msqrt><mrow><mrow><mo>(</mo><mrow><msubsup><mi>T</mi><mi>i</mi><mn>2</mn></msubsup>"
                   + "<mo>+</mo><msubsup><mi>T</mi><mi>u</mi><mn>2</mn></msubsup><mo>+</mo>"
                   + "<msubsup><mi>T</mi><mi>s</mi><mn>2</mn></msubsup></mrow><mo>)</mo></mrow></mrow></msqrt>"
                   + "</math>";
        }

        /**
         * Determines if a line represents a table row.
         * Modify this method based on how table rows are formatted in your source text.
         *
         * @param line The line of text to check.
         * @return True if the line is a table row, false otherwise.
         */
        private boolean isTableRow(String line) {
            // Example: Table rows start with '|'. Modify based on actual table row indicators.
            return line.startsWith("|");
        }

        /**
         * Parses a table row line into individual columns.
         * Modify this method based on how table rows are structured in your source text.
         *
         * @param line The table row line.
         * @return An array of column values.
         */
        private String[] parseTableRow(String line) {
            // Remove leading and trailing '|' if present and split by '|'
            String cleanedLine = line;
            if (cleanedLine.startsWith("|")) {
                cleanedLine = cleanedLine.substring(1);
            }
            if (cleanedLine.endsWith("|")) {
                cleanedLine = cleanedLine.substring(0, cleanedLine.length() - 1);
            }
            return cleanedLine.split("\\|");
        }
 
private String insertInlineFormula(String line) {
    // Example: Inline formulas are enclosed within $...$
    StringBuilder result = new StringBuilder();
    int start = 0;
    while (true) {
        int dollarStart = line.indexOf('$', start);
        if (dollarStart == -1) {
            result.append(line.substring(start));
            break;
        }
        int dollarEnd = line.indexOf('$', dollarStart + 1);
        if (dollarEnd == -1) {
            // No closing $, treat the rest as normal text
            result.append(line.substring(start));
            break;
        }
        // Append text before $
        result.append(line, start, dollarStart);
        // Extract the formula content
        String formula = line.substring(dollarStart + 1, dollarEnd);
        // Replace with MathML inline formula
        result.append("<math display='inline' xmlns='http://www.w3.org/1998/Math/MathML'>")
              .append(formula) // Ideally, convert LaTeX or other format to MathML
              .append("</math>");
        start = dollarEnd + 1;
    }
    return result.toString();
}
 
        private int flushParagraph(StringBuilder xmlContent, StringBuilder paragraph, int paraCount) {
            if (paragraph.length() > 0) {
                paraCount++;
                xmlContent.append("<p id=\"para-").append(String.format("%04d", paraCount)).append("\">")
                          .append(paragraph.toString().trim()).append("</p>\n");
                paragraph.setLength(0); // Reset the paragraph
            }
            return paraCount;
        }
 
        private String formatText(String line) {
            String formattedLine = line;
            // Apply bold formatting: text enclosed in *...*
            formattedLine = formattedLine.replaceAll("\\*(.*?)\\*", "<b>$1</b>");
            // Apply italic formatting: text enclosed in _..._
            formattedLine = formattedLine.replaceAll("_(.*?)_", "<i>$1</i>");
            return formattedLine;
        }

        
private void createXhtmlFile(String fileName, String content) throws IOException {
    String xhtmlHeader = "<?xml version=\"1.0\" encoding=\"UTF-8\"?>\r\n"
    		+ "<!DOCTYPE html>\r\n"
    		+ "<html xml:lang=\"en\" lang=\"en\" xmlns=\"http://www.w3.org/1999/xhtml\" xmlns:epub=\"http://www.idpf.org/2007/ops\">\r\n"
    		+ "<head>\r\n"
    		+ "<title>Chapter 1: Introduction</title>\r\n"
    		+ "<link rel=\"stylesheet\" type=\"text/css\" href=\"../styles/stylesheet.css\"/>\r\n"
    		+ "</head>\r\n"
    		+ "<body epub:type=\"bodymatter\">\r\n"
    		;

    String xhtmlFooter = "</body>\n</html>";

    // Write the XHTML content to the specified file
    String xhtmlContent = xhtmlHeader + content + xhtmlFooter;
    Files.write(Paths.get(xhtmlDir, fileName), xhtmlContent.getBytes());
}
        
        
    }
