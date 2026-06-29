package com.videoTrimmer.controller;

import com.videoTrimmer.model.Video;
import com.videoTrimmer.service.VideoProcessingService;
import javafx.application.Platform;
import javafx.collections.FXCollections;
import javafx.collections.ObservableList;
import javafx.concurrent.Task;
import javafx.fxml.FXML;
import javafx.scene.control.*;
import javafx.scene.input.Dragboard;
import javafx.scene.input.TransferMode;
import javafx.stage.DirectoryChooser;
import javafx.stage.FileChooser;
import javafx.stage.Stage;

import java.io.File;
import java.util.List;

public class MainController {

    @FXML private ListView<Video> videoListView;
    @FXML private TextField outputFolderField;
    @FXML private TextField durationField;
    @FXML private ProgressBar progressBar;
    @FXML private Label progressLabel;
    @FXML private Button startButton;
    @FXML private CheckBox defaultNameCheckBox;
    @FXML private TextField customNameField;

    private final ObservableList<Video> videoList = FXCollections.observableArrayList();
    private final VideoProcessingService videoService = new VideoProcessingService();

    @FXML
    public void initialize() {
        videoListView.setItems(videoList);
        setupDragAndDrop();

        // Başlangıç değerleri
        progressBar.setProgress(0.0);
        progressLabel.setText("0/0 (0%)");

        if (videoService.checkDependencies()) {
            System.out.println("✅ FFmpeg ve FFprobe başarıyla entegre edildi!");
        } else {
            System.err.println("❌ DİKKAT: ffmpeg.exe veya ffprobe.exe bulunamadı!");
        }
    }

    private void setupDragAndDrop() {
        videoListView.setOnDragOver(event -> {
            if (event.getGestureSource() != videoListView && event.getDragboard().hasFiles()) {
                event.acceptTransferModes(TransferMode.COPY_OR_MOVE);
            }
            event.consume();
        });

        videoListView.setOnDragDropped(event -> {
            Dragboard db = event.getDragboard();
            boolean success = false;
            if (db.hasFiles()) {
                for (File file : db.getFiles()) {
                    addVideoToList(file);
                }
                success = true;
            }
            event.setDropCompleted(success);
            event.consume();
        });
    }

    @FXML
    private void handleAddFile() {
        FileChooser fileChooser = new FileChooser();
        fileChooser.setTitle("Video Dosyalarını Seç");
        fileChooser.getExtensionFilters().add(
                new FileChooser.ExtensionFilter("Video Dosyaları", "*.mp4", "*.mkv", "*.avi", "*.mov")
        );

        Stage stage = (Stage) videoListView.getScene().getWindow();
        List<File> selectedFiles = fileChooser.showOpenMultipleDialog(stage);

        if (selectedFiles != null) {
            for (File file : selectedFiles) {
                addVideoToList(file);
            }
        }
    }

    @FXML
    private void handleAddFolder() {
        DirectoryChooser directoryChooser = new DirectoryChooser();
        directoryChooser.setTitle("Video Klasörünü Seç");

        Stage stage = (Stage) videoListView.getScene().getWindow();
        File selectedDirectory = directoryChooser.showDialog(stage);

        if (selectedDirectory != null) {
            File[] files = selectedDirectory.listFiles();
            if (files != null) {
                for (File file : files) {
                    addVideoToList(file);
                }
            }
        }
    }

    @FXML
    private void handleClearList() {
        videoList.clear();
        progressBar.setProgress(0.0);
        progressLabel.setText("0/0 (0%)");
    }

    private void addVideoToList(File file) {
        if (file.isFile()) {
            String name = file.getName().toLowerCase();
            if (name.endsWith(".mp4") || name.endsWith(".mkv") || name.endsWith(".avi") || name.endsWith(".mov")) {
                boolean alreadyExists = videoList.stream()
                        .anyMatch(v -> v.getFile().getAbsolutePath().equals(file.getAbsolutePath()));
                if (!alreadyExists) {
                    videoList.add(new Video(file));
                }
            }
        }
    }

    @FXML
    private void handleBrowseOutput() {
        DirectoryChooser directoryChooser = new DirectoryChooser();
        directoryChooser.setTitle("Çıkış Klasörünü Seç");
        Stage stage = (Stage) videoListView.getScene().getWindow();
        File selectedDirectory = directoryChooser.showDialog(stage);
        if (selectedDirectory != null) {
            outputFolderField.setText(selectedDirectory.getAbsolutePath());
        }
    }

    @FXML
    private void handleStartProcessing() {
        if (videoList.isEmpty()) return;

        File outputDir = new File(outputFolderField.getText());
        if (!outputDir.exists()) outputDir.mkdirs();

        int secondsToKeep;
        try {
            secondsToKeep = Integer.parseInt(durationField.getText());
        } catch (NumberFormatException e) {
            secondsToKeep = 30;
            durationField.setText("30");
        }

        final int finalSecondsToKeep = secondsToKeep;


        startButton.setDisable(true);
        progressBar.setProgress(0.0);
        progressLabel.setText("0/" + videoList.size() + " (0%)");


        Task<Void> processTask = new Task<>() {
            @Override
            protected Void call() throws Exception {
                int totalVideos = videoList.size();

                for (int i = 0; i < totalVideos; i++) {
                    Video video = videoList.get(i);

                    String finalFileName;
                    String originalName = video.getFile().getName();
                    String nameWithoutExt = originalName.substring(0, originalName.lastIndexOf('.'));

                    if (defaultNameCheckBox.isSelected() || customNameField.getText().trim().isEmpty()) {
                        finalFileName = nameWithoutExt + "_kesilmiş";
                    } else {
                        String userCustomName = customNameField.getText().trim();
                        if (totalVideos == 1) {
                            finalFileName = userCustomName;
                        } else {

                            finalFileName = userCustomName + "_" + (i + 1);
                        }
                    }


                    Platform.runLater(() -> {
                        video.setStatus("İşleniyor (%0)");
                        videoListView.refresh();
                    });

                    try {

                        videoService.trimVideo(video.getFile(), outputDir, finalFileName, finalSecondsToKeep, pct -> {
                            Platform.runLater(() -> {
                                video.setStatus(String.format("İşleniyor (%%%d)", (int) pct));
                                videoListView.refresh();
                            });
                        });

                        Platform.runLater(() -> {
                            video.setStatus("Tamamlandı");
                            videoListView.refresh();
                        });
                    } catch (Exception e) {
                        Platform.runLater(() -> {
                            video.setStatus("Hata");
                            videoListView.refresh();
                        });
                    }


                    int completedCount = i + 1;
                    double currentProgress = (double) completedCount / totalVideos;
                    int percentage = (int) (currentProgress * 100);

                    Platform.runLater(() -> {
                        progressBar.setProgress(currentProgress);
                        progressLabel.setText(completedCount + "/" + totalVideos + " (" + percentage + "%)");
                    });
                }
                return null;
            }
        };




        processTask.setOnSucceeded(event -> startButton.setDisable(false));
        processTask.setOnFailed(event -> startButton.setDisable(false));


        Thread thread = new Thread(processTask);
        thread.setDaemon(true);
        thread.start();
    }

    @FXML
    private void handleDefaultNameToggle() {
        customNameField.setDisable(defaultNameCheckBox.isSelected());
        if (defaultNameCheckBox.isSelected()) {
            customNameField.clear();
        }
    }
}