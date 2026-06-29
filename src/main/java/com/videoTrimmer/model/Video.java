package com.videoTrimmer.model;

import java.io.File;

public class Video {
    private final File file;
    private String status;

    public Video(File file) {
        this.file = file;
        this.status = "Bekliyor";
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


    @Override
    public String toString() {
        return "🎥 " + file.getName() + " \t\t\t [" + status + "]";
    }
}