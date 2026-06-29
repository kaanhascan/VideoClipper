package com.videoTrimmer.service;

import java.io.BufferedReader;
import java.io.File;
import java.io.InputStreamReader;

public class VideoProcessingService {

    private final String ffmpegPath;
    private final String ffprobePath;

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
                ffprobePath,
                "-v", "error",
                "-show_entries", "format=duration",
                "-of", "default=noprint_wrappers=1:nokey=1",
                videoFile.getAbsolutePath()
        );


        Process process = pb.start();


        BufferedReader reader = new BufferedReader(new InputStreamReader(process.getInputStream()));
        String line = reader.readLine();


        process.waitFor();

        if (line != null && !line.trim().isEmpty()) {
            return Double.parseDouble(line.trim()); // String olarak gelen süreyi ondalıklı sayıya çevir
        } else {
            throw new Exception("Süre okunamadı, dosya bozuk veya desteklenmiyor olabilir.");
        }
    }


    public String getFfmpegPath() {
        return ffmpegPath;
    }

    public void trimVideo(File inputFile, File outputFolder, int secondsToKeep) throws Exception {

        double totalDuration = getVideoDuration(inputFile);
        double startTime = totalDuration - secondsToKeep;


        if (startTime < 0) {
            startTime = 0;
        }


        String originalName = inputFile.getName();
        String nameWithoutExt = originalName.substring(0, originalName.lastIndexOf('.'));
        String ext = originalName.substring(originalName.lastIndexOf('.'));
        File outputFile = new File(outputFolder, nameWithoutExt + "_kesilmiş" + ext);


        ProcessBuilder pb = new ProcessBuilder(
                ffmpegPath,
                "-y",
                "-ss", String.format(java.util.Locale.US, "%.2f", startTime),
                "-i", inputFile.getAbsolutePath(),
                "-c", "copy",
                outputFile.getAbsolutePath()
        );

        Process process = pb.start();
        process.waitFor();

        if (process.exitValue() != 0) {
            throw new Exception("FFmpeg kesme işlemi başarısız oldu. Hata Kodu: " + process.exitValue());
        }
    }

    public String getFfprobePath() {
        return ffprobePath;
    }
}