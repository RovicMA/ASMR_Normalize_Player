# ASMR Player

A full-featured desktop ASMR media player written in **Java 17 + JavaFX + VLCJ**.

Supports `.m4a` (audio only) and `.mp4` (audio + video), with automatic sidecar
subtitle loading (`.lrc` and `.srt`), real-time audio normalisation, and a dark GUI.

---

## Prerequisites

### 1. Java 17+
```bash
java -version   # should show 17 or higher
```
Install via https://adoptium.net if needed.

### 2. Maven
```bash
mvn -version
```

### 3. VLC media player (native library — required by VLCJ)
VLCJ is a Java wrapper around libVLC. VLC must be installed on the host machine.

| OS      | Install command                          |
|---------|------------------------------------------|
| macOS   | `brew install --cask vlc`               |
| Ubuntu  | `sudo apt install vlc`                  |
| Windows | Download from https://www.videolan.org  |

---

## Build and run

```bash
# Clone / enter project directory
cd asmr-player

# Run directly (no fat jar needed)
mvn javafx:run

# Or build a fat jar
mvn package
java --module-path /path/to/javafx-sdk/lib \
     --add-modules javafx.controls,javafx.fxml,javafx.media \
     -jar target/asmr-player-1.0-SNAPSHOT.jar
```

---

## Project structure

```
src/main/java/com/asmrplayer/
├── audio/
│   └── AudioNormalizer.java     ← RMS analysis, VLCJ normvol filter options
├── model/
│   └── SubtitleEntry.java       ← Data class: startMs, endMs, text
├── subtitle/
│   └── SubtitleParser.java      ← .lrc and .srt parsing; sidecar detection
└── ui/
    ├── App.java                 ← JavaFX Application entry point
    └── PlayerController.java   ← FXML controller; wires everything together

src/main/resources/
├── fxml/player.fxml             ← GUI layout
└── css/dark.css                 ← Dark theme
```

---

## How subtitle detection works

When you open `mysound.m4a`, the parser looks for:
1. `mysound.lrc`  (LRC lyric format: `[02:34.50] Text here`)
2. `mysound.srt`  (SubRip format with `HH:MM:SS,mmm --> HH:MM:SS,mmm` timestamps)

The sidecar file must be in the **same directory** and have the **same base name**.

---

## Audio normalisation

Two layers:

| Layer | How |
|-------|-----|
| Real-time (VLCJ normvol filter) | Enabled via the "Normalize" checkbox. Uses libVLC's built-in RMS-tracking volume filter. Applied when a file is opened. |
| Manual gain (slider) | A 0–2× multiplier on top of VLC's volume. Use the "Gain" slider in the mixer row. |

The `AudioNormalizer.computeRms()` and `computeGainFactor()` methods are provided
for an **offline pre-scan** pass if you want to analyse the file before playback begins.

---

## Extending the player

| Feature idea | Where to add it |
|---|---|
| Waveform visualisation | `VideoPane` — sample PCM via VLCJ audio callback |
| Playlist support | New `PlaylistManager` class + ListView in UI |
| EBU R128 loudness | Implement full scan in `AudioNormalizer.computeRms()` using javax.sound.sampled |
| Themes | Add more `.css` files and a theme picker in the menu |
