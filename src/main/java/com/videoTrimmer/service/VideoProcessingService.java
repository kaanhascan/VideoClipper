package com.videoTrimmer.service;

import java.io.BufferedReader;
import java.io.File;
import java.io.InputStream;
import java.io.InputStreamReader;

public class VideoProcessingService {

    private final String ffmpegPath;
    private final String ffprobePath;


    public interface ProgressListener {
        void onProgress(double percentage);
    }

    public VideoProcessingService() {
        String basePath = System.getProperty("user.dir");
        this.ffmpegPath = basePath + File.separator + "ffmpeg" + File.separator + "ffmpeg.exe";
        this.ffprobePath = basePath + File.separator + "ffmpeg" + File.separator + "ffprobe.exe";
    }

    public boolean checkDependencies() {
        File ffmpeg = new File(ffmpegPath);
        File ffprobe = new File(ffprobePath);
        return ffmpeg.exists() && ffprobe.exists();
    }

    public double getVideoDuration(File videoFile) throws Exception {
        ProcessBuilder pb = new ProcessBuilder(
                ffprobePath, "-v", "error", "-show_entries", "format=duration",
                "-of", "default=noprint_wrappers=1:nokey=1", videoFile.getAbsolutePath()
        );
        Process process = pb.start();
        BufferedReader reader = new BufferedReader(new InputStreamReader(process.getInputStream()));
        String line = reader.readLine();
        process.waitFor();

        if (line != null && !line.trim().isEmpty()) {
            return Double.parseDouble(line.trim());
        } else {
            throw new Exception("Süre okunamadı.");
        }
    }

    public void extractFrame(File inputFile, double timeInSeconds, File outputFile) throws Exception {
        ProcessBuilder pb = new ProcessBuilder(
                ffmpegPath, "-y",
                "-ss", String.format(java.util.Locale.US, "%.2f", timeInSeconds),
                "-i", inputFile.getAbsolutePath(),
                "-vframes", "1",
                "-q:v", "2",
                outputFile.getAbsolutePath()
        );
        Process process = pb.start();
        process.waitFor();
    }


    public void trimVideo(File inputFile, File outputFolder, String outputFileName, int secondsToKeep, ProgressListener listener) throws Exception {
        double totalDuration = getVideoDuration(inputFile);
        double startTime = totalDuration - secondsToKeep;
        if (startTime < 0) startTime = 0;

        double targetDuration = totalDuration - startTime;


        String originalName = inputFile.getName();
        String ext = originalName.substring(originalName.lastIndexOf('.'));


        File outputFile = new File(outputFolder, outputFileName + ext);

        ProcessBuilder pb = new ProcessBuilder(
                ffmpegPath, "-y",
                "-ss", String.format(java.util.Locale.US, "%.2f", startTime),
                "-i", inputFile.getAbsolutePath(),
                "-c", "copy",
                outputFile.getAbsolutePath()
        );

        Process process = pb.start();


        InputStream errorStream = process.getErrorStream();
        StringBuilder lineBuilder = new StringBuilder();
        int ch;


        while ((ch = errorStream.read()) != -1) {
            if (ch == '\n' || ch == '\r') {
                String line = lineBuilder.toString();
                lineBuilder.setLength(0);

                if (line.contains("time=")) {
                    parseAndNotifyProgress(line, targetDuration, listener);
                }
            } else {
                lineBuilder.append((char) ch);
            }
        }

        process.waitFor();

        if (process.exitValue() != 0) {
            throw new Exception("FFmpeg hatası, kod: " + process.exitValue());
        }
    }


    private void parseAndNotifyProgress(String line, double targetDuration, ProgressListener listener) {
        try {
            int timeIndex = line.indexOf("time=");
            if (timeIndex != -1) {

                String timeStr = line.substring(timeIndex + 5).trim().split(" ")[0];


                String[] parts = timeStr.split(":");
                if (parts.length == 3) {
                    double hours = Double.parseDouble(parts[0]);
                    double minutes = Double.parseDouble(parts[1]);
                    double seconds = Double.parseDouble(parts[2]);


                    double currentSeconds = (hours * 3600) + (minutes * 60) + seconds;


                    double percentage = (currentSeconds / targetDuration) * 100;
                    if (percentage > 100) percentage = 100;

                    if (listener != null) {
                        listener.onProgress(percentage);
                    }
                }
            }
        } catch (Exception ignored) {
        }
    }

    public void mergeVideos(java.util.List<File> videoFiles, File outputFolder, String outputFileName, ProgressListener listener) throws Exception {
        if (videoFiles == null || videoFiles.size() < 2) {
            throw new Exception("Birleştirme için en az 2 video gereklidir.");
        }


        double totalDuration = 0;
        for (File file : videoFiles) {
            totalDuration += getVideoDuration(file);
        }


        File listFile = new File(outputFolder, "concat_list.txt");
        try (java.io.PrintWriter writer = new java.io.PrintWriter(listFile)) {
            for (File file : videoFiles) {
                String path = file.getAbsolutePath().replace("\\", "/");
                writer.println("file '" + path + "'");
            }
        }


        String originalName = videoFiles.get(0).getName();
        String ext = originalName.substring(originalName.lastIndexOf('.'));
        File outputFile = new File(outputFolder, outputFileName + ext);


        ProcessBuilder pb = new ProcessBuilder(
                ffmpegPath, "-y",
                "-f", "concat",
                "-safe", "0",
                "-i", listFile.getAbsolutePath(),
                "-c", "copy",
                outputFile.getAbsolutePath()
        );

        Process process = pb.start();


        InputStream errorStream = process.getErrorStream();
        StringBuilder lineBuilder = new StringBuilder();
        int ch;

        while ((ch = errorStream.read()) != -1) {
            if (ch == '\n' || ch == '\r') {
                String line = lineBuilder.toString();
                lineBuilder.setLength(0);
                if (line.contains("time=")) {
                    parseAndNotifyProgress(line, totalDuration, listener);
                }
            } else {
                lineBuilder.append((char) ch);
            }
        }

        process.waitFor();
        listFile.delete();

        if (process.exitValue() != 0) {
            throw new Exception("FFmpeg birleştirme hatası, kod: " + process.exitValue());
        }
    }
}