package com.example.demo;

import java.util.ArrayList;
import java.util.List;

public class Chapter {
    private String title;
    private List<String> content;

    public Chapter(String title) {
        this.title = title;
        this.content = new ArrayList<>();
    }

    public String getTitle() {
        return title;
    }

    public List<String> getContent() {
        return content;
    }

    public void addContent(String text) {
        this.content.add(text);
    }
}
