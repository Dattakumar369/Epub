package com.example.demo;
// TOCEntry.java
public class TOCEntry {
    private String title;
    private String href;
    private String type; // "section", "table", "page"

    public TOCEntry(String title, String href, String type) {
        this.title = title;
        this.href = href;
        this.type = type;
    }

    // Getters
    public String getTitle() { return title; }
    public String getHref() { return href; }
    public String getType() { return type; }
}
