// src/main/java/com/example/demo/EpubController.java
package com.example.demo;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.core.io.InputStreamResource;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.io.File;
import java.io.FileInputStream;
import java.nio.file.Files;
import java.nio.file.Path;

@Controller
public class EpubController {

    @Autowired
    private Service11 epubService;

    /**
     * Displays the upload form to the user.
     *
     * @return The name of the Thymeleaf template for upload.
     */
    @GetMapping("/")
    public String showUploadForm() {
        return "upload";
    }

    /**
     * Handles the PDF upload and initiates the EPUB conversion.
     *
     * @param file  The uploaded PDF file.
     * @param model The Spring Model to pass data to the view.
     * @return The name of the Thymeleaf template for the result.
     */
    @PostMapping("/convert")
    public String convertPdfToEpub(@RequestParam("file") MultipartFile file, Model model) {
        if (file.isEmpty()) {
            model.addAttribute("message", "Please select a PDF file to upload.");
            return "upload";
        }

        try {
            // Validate file type
            if (!file.getContentType().equalsIgnoreCase("application/pdf")) {
                model.addAttribute("message", "Invalid file type. Please upload a PDF file.");
                return "upload";
            }

            // Convert PDF to EPUB
            String epubPath = epubService.createEpubFromPdf(file);

            // Pass the EPUB file path to the result view
            // Encode the path or use an identifier if needed
            model.addAttribute("epubPath", epubPath);
            return "result";
        } catch (Exception e) {
            e.printStackTrace();
            model.addAttribute("message", "An error occurred during conversion: " + e.getMessage());
            return "upload";
        }
    }

    /**
     * Handles the EPUB download.
     *
     * @param epubPath The path to the EPUB file.
     * @return ResponseEntity containing the EPUB file as a downloadable resource.
     */
    @GetMapping("/download")
    public ResponseEntity<InputStreamResource> downloadEpub(@RequestParam("path") String epubPath) {
        try {
            File epubFile = new File(epubPath);
            if (!epubFile.exists()) {
                return ResponseEntity.notFound().build();
            }

            InputStreamResource resource = new InputStreamResource(new FileInputStream(epubFile));

            HttpHeaders headers = new HttpHeaders();
            headers.add("Content-Disposition", "attachment; filename=converted.epub");

            return ResponseEntity.ok()
                    .headers(headers)
                    .contentLength(epubFile.length())
                    .contentType(MediaType.parseMediaType("application/epub+zip"))
                    .body(resource);
        } catch (Exception e) {
            e.printStackTrace();
            return ResponseEntity.status(500).build();
        }
    }
}
