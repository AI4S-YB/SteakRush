package com.steakrush;

import android.content.Context;
import android.media.AudioAttributes;
import android.media.AudioFormat;
import android.media.AudioTrack;
import android.os.Build;
import android.speech.tts.TextToSpeech;

import java.util.Locale;
import java.util.concurrent.atomic.AtomicBoolean;

public class AudioManager {
    private static final int SAMPLE_RATE = 22050;
    private static final int BUFFER_SECONDS = 2;
    private static final double TWO_PI = Math.PI * 2.0;

    private final Context appContext;
    private final AtomicBoolean running = new AtomicBoolean(false);
    private final AtomicBoolean paused = new AtomicBoolean(false);

    private TextToSpeech textToSpeech;
    private volatile boolean ttsReady;
    private AudioTrack musicTrack;
    private Thread musicThread;
    private int sampleCursor;
    private float musicVolume = 0.28f;

    public AudioManager(Context context) {
        this.appContext = context.getApplicationContext();
        initTextToSpeech();
    }

    public void startMusic() {
        if (running.getAndSet(true)) {
            paused.set(false);
            return;
        }

        paused.set(false);
        musicThread = new Thread(new Runnable() {
            @Override
            public void run() {
                runMusicLoop();
            }
        }, "SteakRushMusic");
        musicThread.setDaemon(true);
        musicThread.start();
    }

    public void pause() {
        paused.set(true);
        stopSpeaking();
        AudioTrack track = musicTrack;
        if (track != null) {
            try {
                track.pause();
            } catch (IllegalStateException ignored) {
                // Track can already be stopped during Activity teardown.
            }
        }
    }

    public void resume() {
        paused.set(false);
        startMusic();
        AudioTrack track = musicTrack;
        if (track != null) {
            try {
                track.play();
            } catch (IllegalStateException ignored) {
                // A new track will be created by the music loop if needed.
            }
        }
    }

    public void release() {
        running.set(false);
        paused.set(true);

        Thread thread = musicThread;
        musicThread = null;
        if (thread != null) {
            thread.interrupt();
            try {
                thread.join(300L);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            }
        }

        releaseMusicTrack();

        TextToSpeech tts = textToSpeech;
        textToSpeech = null;
        ttsReady = false;
        if (tts != null) {
            tts.stop();
            tts.shutdown();
        }
    }

    public void setMusicVolume(float volume) {
        musicVolume = Math.max(0f, Math.min(1f, volume));
    }

    public void speakCustomerRequest(String doneness) {
        speak("\u5ba2\u4eba\u70b9\u5355\uff1a" + doneness
                + "\u725b\u6392\uff0c\u52a8\u4f5c\u5feb\u4e00\u70b9\u3002");
    }

    public void speakSuccess() {
        speak("\u51fa\u9910\u6210\u529f\uff0c\u5ba2\u4eba\u5f88\u6ee1\u610f\u3002");
    }

    public void speakFailure() {
        speak("\u8fd9\u4efd\u6ca1\u6709\u8fbe\u5230\u5ba2\u4eba\u8981\u6c42\uff0c\u8bf7\u91cd\u505a\u3002");
    }

    public void speak(String text) {
        TextToSpeech tts = textToSpeech;
        if (!ttsReady || tts == null || text == null || text.trim().isEmpty()) {
            return;
        }

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
            tts.speak(text, TextToSpeech.QUEUE_FLUSH, null, "steakrush-voice");
        } else {
            tts.speak(text, TextToSpeech.QUEUE_FLUSH, null);
        }
    }

    public void stopSpeaking() {
        TextToSpeech tts = textToSpeech;
        if (tts != null) {
            tts.stop();
        }
    }

    private void initTextToSpeech() {
        textToSpeech = new TextToSpeech(appContext, new TextToSpeech.OnInitListener() {
            @Override
            public void onInit(int status) {
                if (status != TextToSpeech.SUCCESS || textToSpeech == null) {
                    ttsReady = false;
                    return;
                }

                int languageResult = textToSpeech.setLanguage(Locale.CHINESE);
                if (languageResult == TextToSpeech.LANG_MISSING_DATA
                        || languageResult == TextToSpeech.LANG_NOT_SUPPORTED) {
                    textToSpeech.setLanguage(Locale.getDefault());
                }
                textToSpeech.setPitch(1.08f);
                textToSpeech.setSpeechRate(1.12f);
                ttsReady = true;
            }
        });
    }

    private void runMusicLoop() {
        short[] buffer = new short[SAMPLE_RATE / 8];
        createMusicTrack();

        while (running.get()) {
            if (paused.get()) {
                sleepQuietly(40L);
                continue;
            }

            AudioTrack track = musicTrack;
            if (track == null || track.getState() != AudioTrack.STATE_INITIALIZED) {
                releaseMusicTrack();
                createMusicTrack();
                continue;
            }

            try {
                if (track.getPlayState() != AudioTrack.PLAYSTATE_PLAYING) {
                    track.play();
                }
                fillMusicBuffer(buffer);
                track.write(buffer, 0, buffer.length);
            } catch (IllegalStateException e) {
                releaseMusicTrack();
                sleepQuietly(80L);
            }
        }
    }

    private void createMusicTrack() {
        int minBuffer = AudioTrack.getMinBufferSize(
                SAMPLE_RATE,
                AudioFormat.CHANNEL_OUT_MONO,
                AudioFormat.ENCODING_PCM_16BIT);
        int bufferSize = Math.max(minBuffer, SAMPLE_RATE * BUFFER_SECONDS * 2);

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            musicTrack = new AudioTrack.Builder()
                    .setAudioAttributes(new AudioAttributes.Builder()
                            .setUsage(AudioAttributes.USAGE_GAME)
                            .setContentType(AudioAttributes.CONTENT_TYPE_MUSIC)
                            .build())
                    .setAudioFormat(new AudioFormat.Builder()
                            .setSampleRate(SAMPLE_RATE)
                            .setEncoding(AudioFormat.ENCODING_PCM_16BIT)
                            .setChannelMask(AudioFormat.CHANNEL_OUT_MONO)
                            .build())
                    .setBufferSizeInBytes(bufferSize)
                    .setTransferMode(AudioTrack.MODE_STREAM)
                    .build();
        } else {
            musicTrack = new AudioTrack(
                    android.media.AudioManager.STREAM_MUSIC,
                    SAMPLE_RATE,
                    AudioFormat.CHANNEL_OUT_MONO,
                    AudioFormat.ENCODING_PCM_16BIT,
                    bufferSize,
                    AudioTrack.MODE_STREAM);
        }
    }

    private void releaseMusicTrack() {
        AudioTrack track = musicTrack;
        musicTrack = null;
        if (track == null) {
            return;
        }
        try {
            track.pause();
            track.flush();
        } catch (IllegalStateException ignored) {
            // Release is still valid after a bad play state.
        }
        track.release();
    }

    private void fillMusicBuffer(short[] buffer) {
        for (int i = 0; i < buffer.length; i++) {
            double beat = sampleCursor / (SAMPLE_RATE * 0.36);
            int step = ((int) Math.floor(beat)) % 16;
            double phaseInStep = beat - Math.floor(beat);

            double chordRoot = chordRootForStep(step);
            double bass = softSquare(chordRoot / 2.0, sampleCursor) * envelope(phaseInStep, 0.55) * 0.30;
            double chord = triangle(chordRoot, sampleCursor) * 0.12
                    + triangle(chordRoot * 1.25, sampleCursor) * 0.08
                    + triangle(chordRoot * 1.5, sampleCursor) * 0.07;

            double melodyFreq = melodyForStep(step);
            double melody = triangle(melodyFreq, sampleCursor) * envelope(phaseInStep, 0.70) * 0.28;

            double hat = noise(sampleCursor) * ((step % 2 == 0) ? 0.045 : 0.025) * envelope(phaseInStep, 0.18);
            double kick = Math.sin(TWO_PI * 78.0 * sampleCursor / SAMPLE_RATE)
                    * envelope(phaseInStep, 0.20) * ((step % 4 == 0) ? 0.34 : 0.0);

            double sample = (bass + chord + melody + hat + kick) * musicVolume;
            buffer[i] = (short) Math.max(Short.MIN_VALUE, Math.min(Short.MAX_VALUE, sample * Short.MAX_VALUE));
            sampleCursor++;
        }
    }

    private double chordRootForStep(int step) {
        switch ((step / 4) % 4) {
            case 0:
                return 261.63;
            case 1:
                return 349.23;
            case 2:
                return 392.00;
            default:
                return 329.63;
        }
    }

    private double melodyForStep(int step) {
        double[] melody = {
                523.25, 587.33, 659.25, 587.33,
                698.46, 659.25, 587.33, 523.25,
                783.99, 698.46, 659.25, 587.33,
                659.25, 587.33, 523.25, 392.00
        };
        return melody[step % melody.length];
    }

    private double triangle(double frequency, int sample) {
        double phase = (sample * frequency / SAMPLE_RATE) % 1.0;
        return 4.0 * Math.abs(phase - 0.5) - 1.0;
    }

    private double softSquare(double frequency, int sample) {
        return Math.tanh(Math.sin(TWO_PI * frequency * sample / SAMPLE_RATE) * 2.2);
    }

    private double envelope(double phase, double length) {
        if (phase > length) {
            return 0.0;
        }
        double attack = Math.min(1.0, phase / 0.04);
        double release = Math.max(0.0, 1.0 - (phase / length));
        return attack * release;
    }

    private double noise(int sample) {
        int value = sample * 1103515245 + 12345;
        value ^= value >>> 16;
        return ((value & 0xffff) / 32768.0) - 1.0;
    }

    private void sleepQuietly(long millis) {
        try {
            Thread.sleep(millis);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
    }
}
