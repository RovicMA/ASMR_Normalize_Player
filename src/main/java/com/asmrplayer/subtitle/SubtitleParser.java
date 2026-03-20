package com.asmrplayer.subtitle;

import com.asmrplayer.model.SubtitleEntry;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Detects subtitle format by file extension and parses into SubtitleEntry list.
 *
 * Supported formats:
 *   .lrc  — LRC lyric format:  [mm:ss.xx] text
 *   .srt  — SubRip format:     index \n HH:MM:SS,mmm --> HH:MM:SS,mmm \n text
 */
public class SubtitleParser {

    // LRC:  [02:34.50] Some text here
    private static final Pattern LRC_PATTERN =
            Pattern.compile("\\[(\\d{2}):(\\d{2})\\.(\\d{2,3})](.*)");

    // SRT timestamp:  00:02:34,500 --> 00:02:37,000
    private static final Pattern SRT_TIMESTAMP =
            Pattern.compile("(\\d{2}):(\\d{2}):(\\d{2}),(\\d{3})\\s*-->\\s*(\\d{2}):(\\d{2}):(\\d{2}),(\\d{3})");

    /**
     * Finds the sidecar subtitle file for a given media file.
     * E.g. given /music/track.m4a, looks for /music/track.lrc then /music/track.srt.
     *
     * @return Path to the subtitle file, or null if none found.
     */
    public static Path findSidecar(Path mediaFile) {
        String base = stripExtension(mediaFile.toString());
        Path lrc = Path.of(base + ".lrc");
        Path srt = Path.of(base + ".srt");
        if (Files.exists(lrc)) return lrc;
        if (Files.exists(srt)) return srt;
        return null;
    }

    /**
     * Parses the subtitle file at the given path.
     * Dispatches to the correct parser based on file extension.
     */
    public static List<SubtitleEntry> parse(Path subtitleFile) throws IOException {
        String name = subtitleFile.getFileName().toString().toLowerCase();
        if (name.endsWith(".lrc")) {
            return parseLrc(subtitleFile);
        } else if (name.endsWith(".srt")) {
            return parseSrt(subtitleFile);
        } else {
            throw new IllegalArgumentException("Unsupported subtitle format: " + name);
        }
    }

    // -------------------------------------------------------------------------
    // LRC parser
    // -------------------------------------------------------------------------

    private static List<SubtitleEntry> parseLrc(Path file) throws IOException {
        List<String> lines = Files.readAllLines(file);
        List<SubtitleEntry> raw = new ArrayList<>();

        for (String line : lines) {
            Matcher m = LRC_PATTERN.matcher(line.trim());
            if (!m.matches()) continue;

            int minutes = Integer.parseInt(m.group(1));
            int seconds = Integer.parseInt(m.group(2));
            String centStr = m.group(3);
            // Normalise centiseconds to milliseconds (2-digit → ×10, 3-digit as-is)
            long millis = centStr.length() == 2
                    ? Long.parseLong(centStr) * 10
                    : Long.parseLong(centStr);

            long startMs = (minutes * 60L + seconds) * 1000L + millis;
            String text = m.group(4).trim();
            if (!text.isEmpty()) {
                // End time is set later once we know the next line's start
                raw.add(new SubtitleEntry(startMs, startMs, text));
            }
        }

        Collections.sort(raw);

        // Set each entry's end time to the next entry's start (or start+5s for the last)
        List<SubtitleEntry> result = new ArrayList<>();
        for (int i = 0; i < raw.size(); i++) {
            SubtitleEntry curr = raw.get(i);
            long endMs = (i + 1 < raw.size())
                    ? raw.get(i + 1).getStartMs()
                    : curr.getStartMs() + 5000L;
            result.add(new SubtitleEntry(curr.getStartMs(), endMs, curr.getText()));
        }
        return result;
    }

    // -------------------------------------------------------------------------
    // SRT parser
    // -------------------------------------------------------------------------

    private static List<SubtitleEntry> parseSrt(Path file) throws IOException {
        List<String> lines = Files.readAllLines(file);
        List<SubtitleEntry> entries = new ArrayList<>();

        int i = 0;
        while (i < lines.size()) {
            // Skip blank lines and index numbers
            String line = lines.get(i).trim();
            if (line.isEmpty() || line.matches("\\d+")) {
                i++;
                continue;
            }

            // Try to parse a timestamp line
            Matcher m = SRT_TIMESTAMP.matcher(line);
            if (m.find()) {
                long startMs = toMs(m.group(1), m.group(2), m.group(3), m.group(4));
                long endMs   = toMs(m.group(5), m.group(6), m.group(7), m.group(8));

                // Collect all text lines until blank or end
                StringBuilder text = new StringBuilder();
                i++;
                while (i < lines.size() && !lines.get(i).trim().isEmpty()) {
                    if (text.length() > 0) text.append(" ");
                    text.append(lines.get(i).trim());
                    i++;
                }

                if (text.length() > 0) {
                    entries.add(new SubtitleEntry(startMs, endMs, text.toString()));
                }
            } else {
                i++;
            }
        }

        Collections.sort(entries);
        return entries;
    }

    /** Returns the active subtitle for the given playback position, or empty string. */
    public static String getActiveText(List<SubtitleEntry> entries, long positionMs) {
        // Simple linear scan — fine for typical subtitle counts (<1000 lines)
        for (SubtitleEntry e : entries) {
            if (e.isActiveAt(positionMs)) return e.getText();
            if (e.getStartMs() > positionMs) break; // list is sorted, no need to continue
        }
        return "";
    }

    // -------------------------------------------------------------------------
    // Helpers
    // -------------------------------------------------------------------------

    private static long toMs(String h, String m, String s, String ms) {
        return Long.parseLong(h) * 3_600_000L
             + Long.parseLong(m) * 60_000L
             + Long.parseLong(s) * 1_000L
             + Long.parseLong(ms);
    }

    private static String stripExtension(String path) {
        int dot = path.lastIndexOf('.');
        return dot >= 0 ? path.substring(0, dot) : path;
    }
}
