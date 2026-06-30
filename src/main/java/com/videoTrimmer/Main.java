package com.videoTrimmer;

import javafx.application.Application;
import javafx.fxml.FXMLLoader;
import javafx.scene.Scene;
import javafx.stage.Stage;

import java.util.Objects;

public class Main extends Application {

    @Override
    public void start(Stage primaryStage) throws Exception {

        FXMLLoader loader = new FXMLLoader(getClass().getResource("/main.fxml"));
        Scene scene = new Scene(loader.load(), 800, 600);


        scene.getStylesheets().add(Objects.requireNonNull(getClass().getResource("/style.css")).toExternalForm());


        primaryStage.setTitle("MMA Special Video Trimmer");
        primaryStage.setScene(scene);
        primaryStage.setMinWidth(1200);
        primaryStage.setMinHeight(650);
        primaryStage.show();
    }

    public static void main(String[] args) {
        launch(args);
    }
}