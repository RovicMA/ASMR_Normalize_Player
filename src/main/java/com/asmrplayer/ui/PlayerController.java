package com.asmrplayer.ui;

import com.asmrplayer.audio.AudioNormalizer;
import com.asmrplayer.model.SubtitleEntry;
import com.asmrplayer.subtitle.SubtitleParser;

import javafx.animation.AnimationTimer;
import javafx.application.Platform;
import javafx.fxml.FXML;
import javafx.fxml.Initializable;
import javafx.scene.control.Button;
import javafx.scene.control.CheckBox;
import javafx.scene.control.Label;
import javafx.scene.control.Slider;
import javafx.scene.layout.StackPane;
import javafx.stage.FileChooser;

import uk.co.caprica.vlcj.factory.discovery.NativeDiscovery;
import uk.co.caprica.vlcj.player.base.MediaPlayer;
import uk.co.caprica.vlcj.player.base.MediaPlayerEventAdapter;
import uk.co.caprica.vlcj.player.embedded.EmbeddedMediaPlayer;
import uk.co.caprica.vlcj.factory.MediaPlayerFactory;

import java.io.File;
import java.net.URL;
import java.nio.file.Path;
import java.util.Collections;
import java.util.List;
import java.util.ResourceBundle;

public class PlayerController implements Initializable {

    // ── FXML nodes ───────────────────────────────────────────────────────────
    @FXML private StackPane videoPane;
    @FXML private Label     subtitleLabel;
    @FXML private Label     timeLabel;
    @FXML private Label     fileLabel;
    @FXML private Label     subtitleFileLabel;
    @FXML private Slider    seekSlider;
    @FXML private Slider    volumeSlider;
    @FXML private Slider    gainSlider;
    @FXML private Button    playPauseBtn;
    @FXML private Button    stopBtn;
    @FXML private Button    openBtn;
    @FXML private CheckBox  normalizeCheck;

    // ── Internal state ───────────────────────────────────────────────────────
    private MediaPlayerFactory  factory;
    private EmbeddedMediaPlayer mediaPlayer;
    private List<SubtitleEntry> subtitles  = Collections.emptyList();
    private AudioNormalizer     normalizer = new AudioNormalizer();
    private boolean             seeking    = false;
    private boolean             playing    = false;
    private String              currentMrl = null;

    // Polls VLCJ ~60fps to keep seek bar and subtitles in sync
    private final AnimationTimer uiTimer = new AnimationTimer() {
        @Override
        public void handle(long now) {
            updateUi();
        }
    };

    // ── Initializable ────────────────────────────────────────────────────────
    @Override
    public void initialize(URL url, ResourceBundle rb) {
        new NativeDiscovery().discover();

        factory     = new MediaPlayerFactory();
        mediaPlayer = factory.mediaPlayers().newEmbeddedMediaPlayer();

        // Listen for player state changes
        mediaPlayer.events().addMediaPlayerEventListener(new MediaPlayerEventAdapter() {
            @Override
            public void playing(MediaPlayer mp) {
                Platform.runLater(() -> {
                    playing = true;
                    playPauseBtn.setText("⏸");
                    uiTimer.start();
                });
            }

            @Override
            public void paused(MediaPlayer mp) {
                Platform.runLater(() -> {
                    playing = false;
                    playPauseBtn.setText("▶");
                });
            }

            @Override
            public void stopped(MediaPlayer mp) {
                Platform.runLater(() -> {
                    playing = false;
                    playPauseBtn.setText("▶");
                    seekSlider.setValue(0);
                    timeLabel.setText("00:00 / 00:00");
                    subtitleLabel.setText("");
                    uiTimer.stop();
                });
            }

            @Override
            public void finished(MediaPlayer mp) {
                Platform.runLater(() -> {
                    playing = false;
                    playPauseBtn.setText("▶");
                    uiTimer.stop();
                });
            }

            @Override
            public void error(MediaPlayer mp) {
                Platform.runLater(() ->
                    fileLabel.setText("Error loading file — check format or VLC installation"));
            }
        });

        // Volume slider: 0–200 maps to VLC's 0–200 volume scale
        volumeSlider.setValue(100);
        volumeSlider.valueProperty().addListener((obs, old, val) ->
                mediaPlayer.audio().setVolume(val.intValue()));

        // Gain slider: 0.0 – 2.0
        gainSlider.setMin(0.0);
        gainSlider.setMax(AudioNormalizer.MAX_GAIN);
        gainSlider.setValue(AudioNormalizer.DEFAULT_GAIN);
        gainSlider.valueProperty().addListener((obs, old, val) ->
                normalizer.setManualGain(val.floatValue()));

        // Seek slider — mouse press/release to avoid feedback loop
        seekSlider.setOnMousePressed(e  -> seeking = true);
        seekSlider.setOnMouseReleased(e -> {
            mediaPlayer.controls().setPosition((float)(seekSlider.getValue() / 100.0));
            seeking = false;
        });
    }

    // ── UI update loop ───────────────────────────────────────────────────────
    private void updateUi() {
        long posMs = mediaPlayer.status().time();
        long durMs = mediaPlayer.media().info() != null
                ? mediaPlayer.media().info().duration()
                : 0;

        if (durMs <= 0) return;

        if (!seeking) {
            seekSlider.setValue((posMs / (double) durMs) * 100.0);
        }

        timeLabel.setText(formatTime(posMs) + " / " + formatTime(durMs));
        subtitleLabel.setText(SubtitleParser.getActiveText(subtitles, posMs));
    }

    // ── FXML handlers ────────────────────────────────────────────────────────
    @FXML
    private void onOpen() {
        FileChooser fc = new FileChooser();
        fc.setTitle("Open audio / video file");
        fc.getExtensionFilters().add(new FileChooser.ExtensionFilter(
                "Media files", "*.m4a", "*.mp4", "*.aac", "*.flac", "*.mp3"));

        File chosen = fc.showOpenDialog(openBtn.getScene().getWindow());
        if (chosen == null) return;

        mediaPlayer.controls().stop();
        subtitles  = Collections.emptyList();
        currentMrl = chosen.getAbsolutePath();

        String[] options = normalizeCheck.isSelected()
                ? AudioNormalizer.buildVlcNormOptions(normalizer.getTargetRms())
                : new String[0];

        mediaPlayer.media().play(currentMrl, options);
        fileLabel.setText(chosen.getName());

        // Look for matching .lrc or .srt sidecar
        Path sidecar = SubtitleParser.findSidecar(chosen.toPath());
        if (sidecar != null) {
            try {
                subtitles = SubtitleParser.parse(sidecar);
                subtitleFileLabel.setText("Subtitles: " + sidecar.getFileName());
            } catch (Exception ex) {
                subtitleFileLabel.setText("Subtitle error: " + ex.getMessage());
            }
        } else {
            subtitleFileLabel.setText("No subtitle file found");
        }
    }

    @FXML
    private void onPlayPause() {
        if (playing) {
            mediaPlayer.controls().pause();
        } else {
            mediaPlayer.controls().play();
        }
    }

    @FXML
    private void onStop() {
        mediaPlayer.controls().stop();
    }

    @FXML
    private void onSkipBack() {
        mediaPlayer.controls().skipTime(-10_000);
    }

    @FXML
    private void onSkipForward() {
        mediaPlayer.controls().skipTime(10_000);
    }

    @FXML
    private void onNormalizeToggle() {
        if (currentMrl == null) return;
        long position = mediaPlayer.status().time();
        String[] options = normalizeCheck.isSelected()
                ? AudioNormalizer.buildVlcNormOptions(normalizer.getTargetRms())
                : new String[0];
        mediaPlayer.media().play(currentMrl, options);
        mediaPlayer.controls().setTime(position);
    }

    // ── Cleanup ───────────────────────────────────────────────────────────────
    public void shutdown() {
        uiTimer.stop();
        mediaPlayer.controls().stop();
        mediaPlayer.release();
        factory.release();
    }

    // ── Helpers ───────────────────────────────────────────────────────────────
    private static String formatTime(long ms) {
        long secs    = ms / 1000;
        long minutes = secs / 60;
        long seconds = secs % 60;
        return String.format("%02d:%02d", minutes, seconds);
    }
}