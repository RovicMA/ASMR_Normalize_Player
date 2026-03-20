package com.asmrplayer.ui;

import javafx.application.Application;
import javafx.fxml.FXMLLoader;
import javafx.scene.Scene;
import javafx.stage.Stage;

import java.io.IOException;

/**
 * Application entry point.
 *
 * Prerequisites before running:
 *   - VLC installed on the host machine (VLCJ wraps the native libVLC)
 *     macOS:   brew install --cask vlc
 *     Ubuntu:  sudo apt install vlc
 *     Windows: download from https://www.videolan.org
 *
 * VM arguments required in Eclipse Run Configuration:
 *   --module-path C:\javafx-sdk-21\lib
 *   --add-modules javafx.controls,javafx.fxml,javafx.media
 *   --add-opens javafx.graphics/com.sun.javafx.application=ALL-UNNAMED
 */
public class App extends Application {

    private PlayerController controller;

    @Override
    public void start(Stage stage) throws IOException {
        FXMLLoader loader = new FXMLLoader(
                getClass().getResource("/fxml/player.fxml"));

        Scene scene = new Scene(loader.load(), 860, 620);
        scene.getStylesheets().add(
                getClass().getResource("/css/dark.css").toExternalForm());

        controller = loader.getController();

        stage.setTitle("ASMR Player");
        stage.setScene(scene);
        stage.setMinWidth(700);
        stage.setMinHeight(500);

        // Release native VLC resources cleanly on window close
        stage.setOnCloseRequest(e -> {
            if (controller != null) {
                controller.shutdown();
            }
        });

        stage.show();
    }

    public static void main(String[] args) {
        launch(args);
    }
}
