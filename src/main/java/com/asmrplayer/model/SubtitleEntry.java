package com.asmrplayer.model;

/**
 * A single timed subtitle line.
 * startMs and endMs are milliseconds from the start of the media.
 * For LRC files, endMs is set to startMs + 5000 (next line acts as the end).
 */
public class SubtitleEntry implements Comparable<SubtitleEntry> {

    private final long startMs;
    private final long endMs;
    private final String text;

    public SubtitleEntry(long startMs, long endMs, String text) {
        this.startMs = startMs;
        this.endMs = endMs;
        this.text = text;
    }

    public long getStartMs() { return startMs; }
    public long getEndMs()   { return endMs; }
    public String getText()  { return text; }

    /** True if the given playback position (ms) falls within this entry's window. */
    public boolean isActiveAt(long positionMs) {
        return positionMs >= startMs && positionMs < endMs;
    }

    @Override
    public int compareTo(SubtitleEntry other) {
        return Long.compare(this.startMs, other.startMs);
    }

    @Override
    public String toString() {
        return String.format("[%d-%d] %s", startMs, endMs, text);
    }
}