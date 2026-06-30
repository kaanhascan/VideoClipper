package com.videoTrimmer.model;

import java.io.File;

public class Video {
    private final File file;
    private String status;
    private boolean selected;

    public Video(File file) {
        this.file = file;
        this.status = "Bekliyor";
        this.selected = false;
    }

    public File getFile() {
        return file;
    }

    public String getStatus() {
        return status;
    }

    public void setStatus(String status) {
        this.status = status;
    }

    public boolean isSelected() {
        return selected;
    }


    public void setSelected(boolean selected) {
        this.selected = selected;
    }


    @Override
    public String toString() {
        return "🎥 " + file.getName() + " \t\t\t [" + status + "]";
    }
}