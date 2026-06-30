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
import javafx.scene.layout.HBox;
import javafx.scene.layout.VBox;
import javafx.scene.media.Media;
import javafx.scene.media.MediaPlayer;
import javafx.scene.media.MediaView;
import javafx.stage.DirectoryChooser;
import javafx.stage.FileChooser;
import javafx.stage.Stage;
import javafx.util.Duration;

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
    @FXML private Button mergeButton;


    @FXML private MediaView mediaView;
    @FXML private Slider timelineSlider;
    @FXML private HBox thumbnailContainer;
    @FXML private Label currentTimeLabel;
    @FXML private Label totalTimeLabel;
    @FXML private Button playPauseButton;
    @FXML private ScrollPane timelineScrollPane;
    @FXML private VBox previewPlaceholder;
    @FXML private VBox timelinePlaceholder;
    @FXML private VBox timelineContent;
    @FXML private Button stopButton;
    @FXML private CheckBox selectAllCheckBox;


    private MediaPlayer mediaPlayer;
    private boolean isSliderDragging = false;

    private final ObservableList<Video> videoList = FXCollections.observableArrayList();
    private final VideoProcessingService videoService = new VideoProcessingService();

    @FXML
    public void initialize() {
        videoListView.setItems(videoList);
        videoListView.setCellFactory(param -> new ListCell<>() {
            private final CheckBox checkBox = new CheckBox();

            @Override
            protected void updateItem(Video video, boolean empty) {
                super.updateItem(video, empty);
                if (empty || video == null) {
                    setGraphic(null);
                } else {
                    checkBox.setText(video.getFile().getName() + " - " + video.getStatus());
                    checkBox.setStyle("-fx-text-fill: #dbdee1; -fx-font-size: 13px;");

                    checkBox.setOnAction(e -> video.setSelected(checkBox.isSelected()));
                    checkBox.setSelected(video.isSelected());

                    setGraphic(checkBox);
                }
            }
        });
        setupDragAndDrop();

        progressBar.setProgress(0.0);
        progressLabel.setText("0/0 (0%)");


        videoListView.getSelectionModel().selectedItemProperty().addListener((observable, oldValue, newValue) -> {
            if (newValue != null) {
                loadVideoPreview(newValue.getFile());
            }
        });


        timelineSlider.setOnMousePressed(event -> isSliderDragging = true);
        timelineSlider.setOnMouseReleased(event -> {
            if (mediaPlayer != null) {
                mediaPlayer.seek(Duration.seconds(timelineSlider.getValue()));
            }
            isSliderDragging = false;
        });

        if (!videoService.checkDependencies()) {
            System.err.println("❌ DİKKAT: ffmpeg.exe veya ffprobe.exe bulunamadı!");
        }

        setEmptyState(true);
    }


    private void loadVideoPreview(File file) {
        setEmptyState(false);
        if (mediaPlayer != null) {
            mediaPlayer.stop();
            mediaPlayer.dispose();
        }

        try {

            Media media = new Media(file.toURI().toString());
            mediaPlayer = new MediaPlayer(media);
            mediaView.setMediaPlayer(mediaPlayer);


            mediaPlayer.setOnReady(() -> {
                double totalSecs = media.getDuration().toSeconds();
                timelineSlider.setMax(totalSecs);
                timelineSlider.setValue(0.0);
                totalTimeLabel.setText(formatTime(totalSecs));
                currentTimeLabel.setText("00:00");
                playPauseButton.setText("▶ Oynat");

                generateTimelineThumbnails(file, totalSecs);
            });


            mediaPlayer.currentTimeProperty().addListener((observable, oldValue, newValue) -> {
                if (!isSliderDragging) {
                    timelineSlider.setValue(newValue.toSeconds());
                    currentTimeLabel.setText(formatTime(newValue.toSeconds()));
                }
            });


            mediaPlayer.setOnEndOfMedia(() -> {
                mediaPlayer.seek(Duration.ZERO);
                mediaPlayer.pause();
                playPauseButton.setText("▶ Oynat");
            });

        } catch (Exception e) {
            System.err.println("Video önizlemesi yüklenemedi: " + e.getMessage());
        }
    }

    @FXML
    private void handlePlayPause() {
        if (mediaPlayer == null) return;

        if (mediaPlayer.getStatus() == MediaPlayer.Status.PLAYING) {
            mediaPlayer.pause();
            playPauseButton.setText("▶ Oynat");
        } else {
            mediaPlayer.play();
            playPauseButton.setText("⏸ Durdur");
        }
    }

    @FXML
    private void handleStopVideo() {
        if (mediaPlayer != null) {
            mediaPlayer.stop();
            timelineSlider.setValue(0.0);
            currentTimeLabel.setText("00:00");
            playPauseButton.setText("▶ Oynat");
        }
    }


    private String formatTime(double seconds) {
        int mins = (int) seconds / 60;
        int secs = (int) seconds % 60;
        return String.format("%02d:%02d", mins, secs);
    }

    @FXML
    private void handleDefaultNameToggle() {
        customNameField.setDisable(defaultNameCheckBox.isSelected());
        if (defaultNameCheckBox.isSelected()) {
            customNameField.clear();
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
        fileChooser.getExtensionFilters().add(new FileChooser.ExtensionFilter("Video Dosyaları", "*.mp4", "*.mkv", "*.avi", "*.mov"));
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
        List<Video> selectedVideos = videoList.stream().filter(Video::isSelected).toList();

        if(selectedVideos.isEmpty()) {
            return;
        }

        handleStopVideo();

        videoList.removeAll(selectedVideos);

        if(videoList.isEmpty()) {
            if(mediaPlayer != null) {
                mediaPlayer.dispose();
                mediaPlayer = null;
            }
            setEmptyState(true);
        }

        if(selectAllCheckBox != null){
            selectAllCheckBox.setSelected(false);
        }

        progressBar.setProgress(0.0);
        progressLabel.setText("0/0 (0%)");
        videoListView.refresh();


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
        List<Video> selectedVideos = videoList.stream().filter(Video::isSelected).toList();
        if (selectedVideos.isEmpty()) return;

        handleStopVideo();
        File outputDir = new File(outputFolderField.getText());
        if (!outputDir.exists()) outputDir.mkdirs();

        int secondsToKeep = Integer.parseInt(durationField.getText());

        startButton.setDisable(true);
        if(mergeButton != null) mergeButton.setDisable(true);

        Task<Void> processTask = new Task<>() {
            @Override
            protected Void call() throws Exception {
                int totalVideos = selectedVideos.size();
                List<Video> successfullyProcessed = new java.util.ArrayList<>();

                for (int i = 0; i < totalVideos; i++) {
                    Video video = selectedVideos.get(i);
                    String finalFileName = video.getFile().getName().replace(".mp4", "_kesilmiş"); // Örnek

                    Platform.runLater(() -> {
                        video.setStatus("İşleniyor...");
                        videoListView.refresh();
                    });

                    try {
                        videoService.trimVideo(video.getFile(), outputDir, finalFileName, secondsToKeep, pct -> {
                            Platform.runLater(() -> {
                                video.setStatus(String.format("İşleniyor (%%%d)", (int) pct));
                                videoListView.refresh();
                            });
                        });

                        // İŞLEM BAŞARILIYSA SİLİNECEKLER LİSTESİNE EKLE
                        successfullyProcessed.add(video);

                    } catch (Exception e) {
                        Platform.runLater(() -> {
                            video.setStatus("Hata");
                            videoListView.refresh();
                        });
                    }
                }

                // DÖNGÜ BİTİNCE BAŞARILI OLANLARI LİSTEDEN SİL
                Platform.runLater(() -> {
                    videoList.removeAll(successfullyProcessed);
                    videoListView.refresh();
                });

                return null;
            }
        };

        processTask.setOnSucceeded(e -> { startButton.setDisable(false); if(mergeButton != null) mergeButton.setDisable(false); });
        processTask.setOnFailed(e -> { startButton.setDisable(false); if(mergeButton != null) mergeButton.setDisable(false); });
        new Thread(processTask).start();
    }

    private void generateTimelineThumbnails(File videoFile, double totalSecs) {
        thumbnailContainer.getChildren().clear();


        int frameCount = (int) Math.getExponent(totalSecs) > 0 ? (int) Math.ceil(totalSecs) : 1;
        if (frameCount == 0) frameCount = 1;

        double frameWidth = 80.0;
        double totalTimelineWidth = frameCount * frameWidth;


        Platform.runLater(() -> {
            timelineSlider.setMinWidth(totalTimelineWidth);
            timelineSlider.setMaxWidth(totalTimelineWidth);
            timelineSlider.setPrefWidth(totalTimelineWidth);
            thumbnailContainer.setMinWidth(totalTimelineWidth);
        });

        final int finalFrameCount = frameCount;

        Task<Void> thumbTask = new Task<>() {
            @Override
            protected Void call() throws Exception {
                File tempDir = new File(System.getProperty("java.io.tmpdir"), "mmatrimmer_thumbs");
                if (!tempDir.exists()) tempDir.mkdirs();

                for (int i = 0; i < finalFrameCount; i++) {
                    double time = i; // 0. saniye, 1. saniye, 2. saniye...
                    File tempThumb = new File(tempDir, "thumb_" + i + ".jpg");

                    videoService.extractFrame(videoFile, time, tempThumb);

                    if (tempThumb.exists()) {
                        String imgUrl = tempThumb.toURI().toString();
                        Platform.runLater(() -> {
                            javafx.scene.image.Image img = new javafx.scene.image.Image(imgUrl, frameWidth, 50, false, true);
                            javafx.scene.image.ImageView imgView = new javafx.scene.image.ImageView(img);
                            imgView.setFitHeight(50);
                            imgView.setFitWidth(frameWidth);
                            imgView.setPreserveRatio(false);

                            thumbnailContainer.getChildren().add(imgView);
                        });
                    }
                }
                return null;
            }
        };

        Thread t = new Thread(thumbTask);
        t.setDaemon(true);
        t.start();
    }

    private void setEmptyState(boolean isEmpty) {
        previewPlaceholder.setVisible(isEmpty);
        timelinePlaceholder.setVisible(isEmpty);


        mediaView.setVisible(!isEmpty);
        timelineContent.setVisible(!isEmpty);


        playPauseButton.setDisable(isEmpty);
        stopButton.setDisable(isEmpty);
    }

    @FXML
    private void handleSelectAll() {
        boolean isSelected = selectAllCheckBox.isSelected();
        for (Video v : videoList) {
            v.setSelected(isSelected);
        }
        videoListView.refresh();
    }

    @FXML
    private void handleMergeVideos() {
        List<Video> selectedVideos = videoList.stream().filter(Video::isSelected).toList();


        if (selectedVideos.size() < 2) {
            System.out.println("Lütfen birleştirmek için en az 2 video seçin.");
            return;
        }

        handleStopVideo();

        File outputDir = new File(outputFolderField.getText());
        if (!outputDir.exists()) outputDir.mkdirs();

        startButton.setDisable(true);
        mergeButton.setDisable(true);
        progressBar.setProgress(0.0);
        progressLabel.setText("Birleştiriliyor... (0%)");

        Task<Void> mergeTask = new Task<>() {
            @Override
            protected Void call() throws Exception {

                List<File> filesToMerge = selectedVideos.stream()
                        .map(Video::getFile)
                        .toList();


                String finalFileName;
                if (defaultNameCheckBox.isSelected() || customNameField.getText().trim().isEmpty()) {
                    finalFileName = "Birleştirilmiş_Video";
                } else {
                    finalFileName = customNameField.getText().trim();
                }


                Platform.runLater(() -> {
                    for (Video v : selectedVideos) v.setStatus("Birleştiriliyor...");
                    videoListView.refresh();
                });

                try {
                    videoService.mergeVideos(filesToMerge, outputDir, finalFileName, pct -> {
                        Platform.runLater(() -> {
                            progressBar.setProgress(pct / 100.0);
                            progressLabel.setText(String.format("Birleştiriliyor... (%%%d)", (int) pct));
                        });
                    });

                    Platform.runLater(() -> {
                        videoList.removeAll(selectedVideos);
                        progressBar.setProgress(1.0);
                        progressLabel.setText("Birleştirme Başarılı! (100%)");
                        videoListView.refresh();
                    });

                } catch (Exception e) {

                    Platform.runLater(() -> {
                        for (Video v : selectedVideos) v.setStatus("Hata");
                        progressLabel.setText("Birleştirme Başarısız!");
                        videoListView.refresh();
                    });
                }
                return null;
            }
        };


        mergeTask.setOnSucceeded(e -> { startButton.setDisable(false); mergeButton.setDisable(false); });
        mergeTask.setOnFailed(e -> { startButton.setDisable(false); mergeButton.setDisable(false); });

        Thread thread = new Thread(mergeTask);
        thread.setDaemon(true);
        thread.start();
    }
}