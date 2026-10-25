package com.astrocam;

import android.os.Handler;
import android.os.Looper;

import java.util.Locale;

public class IntervalometerController {

    public enum State {
        IDLE,
        COUNTDOWN,
        EXPOSING,
        SAVING,
        INTERVAL_WAIT,
        STOPPING
    }

    public interface IntervalometerListener {
        void onStateChanged(State state);
        void onProgressUpdate(int currentFrame, int totalFrames, long elapsedMs, long estimatedRemainingMs, String statusMessage);
        void onSequenceFinished(int completedFrames, boolean cancelled);
        void onWarning(String warning);
    }

    private final CameraController cameraController;
    private final Handler handler = new Handler(Looper.getMainLooper());

    private IntervalometerListener listener;

    private State currentState = State.IDLE;
    private int totalFrames = 10; // -1 for unlimited
    private int intervalSeconds = 2; // delay between shots
    private int countdownSeconds = 3; // initial start countdown

    private int framesCaptured = 0;
    private long sequenceStartTimeMs = 0;
    private long lastFrameStartMs = 0;
    private long averageFrameDurationMs = 0;

    private boolean isRunning = false;
    private boolean isStopping = false;

    private Runnable countdownRunnable;
    private Runnable intervalWaitRunnable;
    private int remainingIntervalWaitSec = 0;

    public IntervalometerController(CameraController cameraController) {
        this.cameraController = cameraController;
    }

    public void setListener(IntervalometerListener listener) {
        this.listener = listener;
    }

    public State getState() {
        return currentState;
    }

    public boolean isRunning() {
        return isRunning;
    }

    public void setTotalFrames(int frames) {
        this.totalFrames = frames;
    }

    public int getTotalFrames() {
        return totalFrames;
    }

    public void setIntervalSeconds(int seconds) {
        this.intervalSeconds = Math.max(1, seconds);
    }

    public int getIntervalSeconds() {
        return intervalSeconds;
    }

    public void setCountdownSeconds(int seconds) {
        this.countdownSeconds = Math.max(0, seconds);
    }

    public int getCountdownSeconds() {
        return countdownSeconds;
    }

    public String validateSettings() {
        long exposureNs = cameraController.getCurrentExposureNs();
        long exposureMs = (exposureNs > 0) ? (exposureNs / 1_000_000L) : 250L;

        if (intervalSeconds < 1) {
            return "Interval must be at least 1 second.";
        }
        if (intervalSeconds * 1000L < 1500L && cameraController.getCaptureFormat() != CameraController.CaptureFormat.JPEG) {
            return "Notice: RAW capture requires ~1.5-2s between exposures for DNG creation.";
        }
        return null;
    }

    public void startSequence() {
        if (isRunning) return;

        isRunning = true;
        isStopping = false;
        framesCaptured = 0;
        sequenceStartTimeMs = System.currentTimeMillis();

        if (countdownSeconds > 0) {
            startCountdown(countdownSeconds);
        } else {
            executeNextFrame();
        }
    }

    private void startCountdown(final int secondsLeft) {
        setState(State.COUNTDOWN);
        if (listener != null) {
            listener.onProgressUpdate(0, totalFrames, 0, 0,
                    "Starting in " + secondsLeft + "s… (Keep phone still)");
        }

        countdownRunnable = new Runnable() {
            @Override
            public void run() {
                if (!isRunning || isStopping) {
                    finishSequence(true);
                    return;
                }
                if (secondsLeft > 1) {
                    startCountdown(secondsLeft - 1);
                } else {
                    executeNextFrame();
                }
            }
        };
        handler.postDelayed(countdownRunnable, 1000);
    }

    private void executeNextFrame() {
        if (!isRunning || isStopping) {
            finishSequence(true);
            return;
        }

        if (totalFrames > 0 && framesCaptured >= totalFrames) {
            finishSequence(false);
            return;
        }

        setState(State.EXPOSING);
        lastFrameStartMs = System.currentTimeMillis();

        long elapsedMs = System.currentTimeMillis() - sequenceStartTimeMs;
        long estimatedRemainingMs = calculateEstimatedRemainingTime(elapsedMs);

        int currentTargetFrame = framesCaptured + 1;
        String frameStr = (totalFrames > 0) ? (currentTargetFrame + " / " + totalFrames) : (currentTargetFrame + " / ∞");

        long exposureNs = cameraController.getCurrentExposureNs();
        String expStr = (exposureNs > 0) ? String.format(Locale.US, " (%.1fs)", exposureNs / 1_000_000_000.0) : "";

        if (listener != null) {
            listener.onProgressUpdate(currentTargetFrame, totalFrames, elapsedMs, estimatedRemainingMs,
                    "Frame " + frameStr + expStr);
        }

        cameraController.takePicture();
    }

    /**
     * Called by MainActivity when CameraController finishes saving all images for the current frame.
     */
    public void onCameraCaptureFinished() {
        if (!isRunning) return;

        framesCaptured++;
        long frameDuration = System.currentTimeMillis() - lastFrameStartMs;
        if (averageFrameDurationMs == 0) {
            averageFrameDurationMs = frameDuration + (intervalSeconds * 1000L);
        } else {
            averageFrameDurationMs = (averageFrameDurationMs + frameDuration + (intervalSeconds * 1000L)) / 2;
        }

        if (isStopping) {
            finishSequence(true);
            return;
        }

        if (totalFrames > 0 && framesCaptured >= totalFrames) {
            finishSequence(false);
            return;
        }

        // Wait interval before next shot
        startIntervalWait(intervalSeconds);
    }

    private void startIntervalWait(final int secondsLeft) {
        setState(State.INTERVAL_WAIT);
        remainingIntervalWaitSec = secondsLeft;

        updateIntervalWaitProgress();

        intervalWaitRunnable = new Runnable() {
            @Override
            public void run() {
                if (!isRunning || isStopping) {
                    finishSequence(true);
                    return;
                }

                remainingIntervalWaitSec--;
                if (remainingIntervalWaitSec > 0) {
                    updateIntervalWaitProgress();
                    handler.postDelayed(this, 1000);
                } else {
                    executeNextFrame();
                }
            }
        };
        handler.postDelayed(intervalWaitRunnable, 1000);
    }

    private void updateIntervalWaitProgress() {
        long elapsedMs = System.currentTimeMillis() - sequenceStartTimeMs;
        long estimatedRemainingMs = calculateEstimatedRemainingTime(elapsedMs);

        String frameStr = (totalFrames > 0) ? (framesCaptured + " / " + totalFrames) : (framesCaptured + " / ∞");
        if (listener != null) {
            listener.onProgressUpdate(framesCaptured, totalFrames, elapsedMs, estimatedRemainingMs,
                    "Captured " + frameStr + " • Next in " + remainingIntervalWaitSec + "s");
        }
    }

    private long calculateEstimatedRemainingTime(long elapsedMs) {
        if (totalFrames <= 0) return 0;
        int remainingFrames = totalFrames - framesCaptured;
        if (remainingFrames <= 0) return 0;

        if (averageFrameDurationMs > 0) {
            return remainingFrames * averageFrameDurationMs;
        } else {
            long expMs = (cameraController.getCurrentExposureNs() > 0) ? (cameraController.getCurrentExposureNs() / 1_000_000L) : 250L;
            return remainingFrames * (expMs + (intervalSeconds * 1000L) + 1000L);
        }
    }

    public void stopSequence() {
        if (!isRunning) return;

        isStopping = true;
        setState(State.STOPPING);

        // Cancel countdown or interval wait immediately
        if (countdownRunnable != null) {
            handler.removeCallbacks(countdownRunnable);
            countdownRunnable = null;
        }
        if (intervalWaitRunnable != null) {
            handler.removeCallbacks(intervalWaitRunnable);
            intervalWaitRunnable = null;
        }

        // If camera is not currently exposing, finish right away
        if (!cameraController.isCapturing()) {
            finishSequence(true);
        } else {
            if (listener != null) {
                listener.onProgressUpdate(framesCaptured, totalFrames,
                        System.currentTimeMillis() - sequenceStartTimeMs, 0,
                        "Stopping safely… finishing current frame");
            }
        }
    }

    private void finishSequence(boolean cancelled) {
        isRunning = false;
        isStopping = false;
        setState(State.IDLE);

        if (countdownRunnable != null) {
            handler.removeCallbacks(countdownRunnable);
            countdownRunnable = null;
        }
        if (intervalWaitRunnable != null) {
            handler.removeCallbacks(intervalWaitRunnable);
            intervalWaitRunnable = null;
        }

        if (listener != null) {
            listener.onSequenceFinished(framesCaptured, cancelled);
        }
    }

    private void setState(State state) {
        this.currentState = state;
        if (listener != null) {
            listener.onStateChanged(state);
        }
    }

    public int getFramesCaptured() {
        return framesCaptured;
    }
}
