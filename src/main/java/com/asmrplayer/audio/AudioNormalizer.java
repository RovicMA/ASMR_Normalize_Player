package com.asmrplayer.audio;

import uk.co.caprica.vlcj.factory.MediaPlayerFactory;
import uk.co.caprica.vlcj.player.base.MediaPlayer;

import java.nio.file.Path;

/**
 * Computes a normalisation gain factor for an audio/video file.
 *
 * Strategy: VLCJ exposes libVLC's audio filters. We use the built-in
 * "normvol" audio filter which adjusts gain in real time so the output
 * stays near a target RMS level. We also expose a manual gain slider
 * (0.0 – 2.0×) so the user can fine-tune the ASMR volume themselves.
 *
 * For a proper offline EBU R128 scan you would decode all PCM frames and
 * compute integrated loudness — that's shown as a commented stub below,
 * ready to implement if you later add a javax.sound.sampled decode pass.
 */
public class AudioNormalizer {

    /** Target RMS level for normalisation (0.0 – 1.0). 0.25 is comfortable for ASMR. */
    public static final float DEFAULT_TARGET_RMS = 0.25f;

    /** Default manual gain multiplier (1.0 = no change). */
    public static final float DEFAULT_GAIN = 1.0f;

    /** Maximum gain the slider may apply (prevents clipping on very quiet files). */
    public static final float MAX_GAIN = 2.0f;

    private float targetRms;
    private float manualGain;

    public AudioNormalizer() {
        this.targetRms  = DEFAULT_TARGET_RMS;
        this.manualGain = DEFAULT_GAIN;
    }

    // -------------------------------------------------------------------------
    // VLCJ real-time normalisation
    // -------------------------------------------------------------------------

    /**
     * Builds the libVLC option string that enables real-time volume normalisation.
     * Pass this into MediaPlayerFactory or addMediaOptions() when opening a file.
     *
     * Usage:
     *   String[] options = AudioNormalizer.buildVlcNormOptions(targetRms);
     *   mediaPlayer.media().play(filePath, options);
     */
    public static String[] buildVlcNormOptions(float targetRms) {
        // libVLC's normvol filter accepts a "level" from 0.0 to 2.0
        // We map our 0–1 target onto that range directly.
        return new String[]{
            ":audio-filter=normvol",
            ":norm-buff-size=20",          // smoothing window in seconds
            String.format(":norm-max-level=%.2f", targetRms * 2.0f)
        };
    }

    /**
     * Returns the audio gain option string for a manual gain multiplier.
     * Can be set dynamically via MediaPlayer.audio().setVolume() instead,
     * but this approach lets you bake it into the media open call.
     */
    public static String buildGainOption(float gain) {
        // Clamp to safe range
        float clamped = Math.max(0.0f, Math.min(gain, MAX_GAIN));
        int vlcVolume = Math.round(clamped * 100); // VLC uses 0–200 integer scale
        return ":audio-gain=" + vlcVolume;
    }

    // -------------------------------------------------------------------------
    // Offline RMS scan stub
    // (Use this if you want a pre-play loudness analysis pass)
    // -------------------------------------------------------------------------

    /**
     * STUB: Scans a PCM byte buffer and returns its RMS level (0.0 – 1.0).
     *
     * To use: decode the audio file to raw PCM first using javax.sound.sampled
     * or a JNA call into libavcodec, feed the PCM bytes here, then compute
     *   gainFactor = targetRms / rms
     * and apply it via mediaPlayer.audio().setVolume().
     *
     * @param pcmSamples 16-bit little-endian PCM samples
     * @return RMS level in [0.0, 1.0]
     */
    public static float computeRms(byte[] pcmSamples) {
        if (pcmSamples == null || pcmSamples.length < 2) return 0f;

        double sumSquares = 0.0;
        int sampleCount = pcmSamples.length / 2;

        for (int i = 0; i < pcmSamples.length - 1; i += 2) {
            // Combine two bytes into a signed 16-bit sample
            short sample = (short) ((pcmSamples[i + 1] << 8) | (pcmSamples[i] & 0xFF));
            double normalised = sample / 32768.0; // normalise to -1.0 .. 1.0
            sumSquares += normalised * normalised;
        }

        return (float) Math.sqrt(sumSquares / sampleCount);
    }

    /**
     * Computes the gain factor needed to bring an RMS level up to targetRms.
     * Example: if measured RMS is 0.1 and target is 0.25, returns 2.5.
     *
     * @param measuredRms the RMS returned by computeRms()
     * @param targetRms   desired output RMS
     * @return gain multiplier (clamped to MAX_GAIN)
     */
    public static float computeGainFactor(float measuredRms, float targetRms) {
        if (measuredRms <= 0.001f) return 1.0f; // avoid divide-by-zero on silence
        float factor = targetRms / measuredRms;
        return Math.min(factor, MAX_GAIN);
    }

    // -------------------------------------------------------------------------
    // Getters / setters for UI binding
    // -------------------------------------------------------------------------

    public float getTargetRms()          { return targetRms; }
    public void  setTargetRms(float v)   { this.targetRms = v; }
    public float getManualGain()         { return manualGain; }
    public void  setManualGain(float v)  { this.manualGain = Math.max(0f, Math.min(v, MAX_GAIN)); }
}
