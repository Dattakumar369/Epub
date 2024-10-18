// src/main/java/com/example/demo/CustomPDFTextStripper.java
package com.example.demo;

import java.io.IOException;

import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.text.PDFTextStripper;
import org.apache.pdfbox.text.TextPosition;

public class CustomPDFTextStripper extends PDFTextStripper {

    private StringBuilder formattedText = new StringBuilder();
    private boolean isBold = false;
    private boolean isItalic = false;

    public CustomPDFTextStripper() throws IOException {
        super();
    }

    @Override
    protected void processTextPosition(TextPosition text) {
        // Detect bold and italic by checking the font's name or attributes
        String fontName = text.getFont().getName().toLowerCase();
        boolean currentBold = fontName.contains("bold");
        boolean currentItalic = fontName.contains("italic") || fontName.contains("oblique");

        // Open or close <b> tags
        if (currentBold && !isBold) {
            formattedText.append("<b>");
            isBold = true;
        } else if (!currentBold && isBold) {
            formattedText.append("</b>");
            isBold = false;
        }

        // Open or close <i> tags
        if (currentItalic && !isItalic) {
            formattedText.append("<i>");
            isItalic = true;
        } else if (!currentItalic && isItalic) {
            formattedText.append("</i>");
            isItalic = false;
        }

        // Append the actual text
        formattedText.append(text.getUnicode());
    }

    @Override
    public void endDocument(PDDocument document) throws IOException {
        // Close any remaining open tags
        if (isBold) {
            formattedText.append("</b>");
        }
        if (isItalic) {
            formattedText.append("</i>");
        }
        super.endDocument(document);
    }

    public String getFormattedText() {
        return formattedText.toString();
    }
}
