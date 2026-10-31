package com.astrocam;

import android.annotation.SuppressLint;
import android.content.Context;
import android.graphics.ImageFormat;
import android.graphics.SurfaceTexture;
import android.hardware.camera2.CameraAccessException;
import android.hardware.camera2.CameraCaptureSession;
import android.hardware.camera2.CameraCharacteristics;
import android.hardware.camera2.CameraDevice;
import android.hardware.camera2.CameraManager;
import android.hardware.camera2.CameraMetadata;
import android.hardware.camera2.CaptureFailure;
import android.hardware.camera2.CaptureRequest;
import android.hardware.camera2.CaptureResult;
import android.hardware.camera2.TotalCaptureResult;
import android.hardware.camera2.params.RggbChannelVector;
import android.media.Image;
import android.media.ImageReader;
import android.net.Uri;
import android.os.Handler;
import android.os.HandlerThread;
import android.os.Looper;
import android.util.Log;
import android.util.Range;
import android.util.Size;
import android.util.SparseIntArray;
import android.view.Surface;
import android.view.WindowManager;

import androidx.annotation.NonNull;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.Semaphore;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

public class CameraController {

    private static final String TAG = "CameraController";

    public enum CaptureFormat {
        JPEG,
        RAW,
        RAW_AND_JPEG
    }

    public interface CameraEventListener {
        void onCameraReady(CameraCapabilities capabilities);
        void onPreviewFrameResult(int iso, long exposureNs, float focusDistance);
        void onCaptureStarted(long estimatedDurationMs);
        void onCaptureSaved(Uri uri, String filename, boolean isRaw);
        void onCaptureFinished();
        void onError(String message);
    }

    private static final SparseIntArray ORIENTATIONS = new SparseIntArray();
    static {
        ORIENTATIONS.append(Surface.ROTATION_0, 90);
        ORIENTATIONS.append(Surface.ROTATION_90, 0);
        ORIENTATIONS.append(Surface.ROTATION_180, 270);
        ORIENTATIONS.append(Surface.ROTATION_270, 180);
    }

    private final Context context;
    private final CameraManager cameraManager;
    private final PhotoSaver photoSaver;
    private final Handler mainHandler;

    private CameraEventListener eventListener;
    private AutoFitTextureView textureView;

    private HandlerThread backgroundThread;
    private Handler backgroundHandler;

    private CameraDevice cameraDevice;
    private CameraCaptureSession captureSession;
    private CaptureRequest.Builder previewRequestBuilder;
    private CaptureRequest previewRequest;

    private ImageReader jpegImageReader;
    private ImageReader rawImageReader;

    private CameraCapabilities currentCapabilities;
    private List<CameraCapabilities> availableCameras = new ArrayList<>();
    private String currentCameraId;

    // Manual Settings
    private CaptureFormat captureFormat = CaptureFormat.RAW_AND_JPEG;
    private int currentIso = -1; // -1 = Auto
    private long currentExposureNs = -1L; // -1 = Auto
    private float currentFocusDistance = -1f; // -1 = Auto focus
    private int currentAwbMode = CameraMetadata.CONTROL_AWB_MODE_AUTO;
    private int currentKelvin = -1; // -1 = Auto AWB
    private int lastAutoIso = 400;
    private long lastAutoExposureNs = 33_333_333L; // ~1/30s fallback

    private final Semaphore cameraOpenCloseLock = new Semaphore(1);
    private boolean isCapturing = false;

    // Temporary storage during capture to match Image and TotalCaptureResult
    private TotalCaptureResult lastStillCaptureResult;
    private Image pendingRawImage;
    private Image pendingJpegImage;
    private final Object captureSyncObject = new Object();

    public CameraController(Context context, PhotoSaver photoSaver) {
        this.context = context.getApplicationContext();
        this.cameraManager = (CameraManager) context.getSystemService(Context.CAMERA_SERVICE);
        this.photoSaver = photoSaver;
        this.mainHandler = new Handler(Looper.getMainLooper());
        discoverCameras();
    }

    public void setEventListener(CameraEventListener listener) {
        this.eventListener = listener;
    }

    public void setTextureView(AutoFitTextureView textureView) {
        this.textureView = textureView;
    }

    public void discoverCameras() {
        availableCameras.clear();
        try {
            String[] ids = cameraManager.getCameraIdList();
            Log.i(TAG, "CameraManager found camera IDs: " + Arrays.toString(ids));
            for (String id : ids) {
                CameraCharacteristics chars = cameraManager.getCameraCharacteristics(id);
                CameraCapabilities caps = new CameraCapabilities(id, chars);
                availableCameras.add(caps);
                Log.i(TAG, "Found camera: " + caps.getDisplayName() +
                        ", RAW: " + caps.isRawSupported() +
                        ", ManualSensor: " + caps.isManualSensorSupported() +
                        ", ISO: " + caps.getIsoRange() +
                        ", ExpNs: " + caps.getExposureTimeRangeNs());
            }
        } catch (CameraAccessException e) {
            Log.e(TAG, "Error discovering cameras", e);
        }

        // Prefer back camera with RAW support if available
        if (currentCameraId == null && !availableCameras.isEmpty()) {
            CameraCapabilities selected = availableCameras.get(0);
            for (CameraCapabilities caps : availableCameras) {
                if (caps.isBackFacing()) {
                    selected = caps;
                    if (caps.isRawSupported()) {
                        break;
                    }
                }
            }
            currentCameraId = selected.getCameraId();
            currentCapabilities = selected;
            Log.i(TAG, "Default camera selected: " + currentCameraId + " (" + selected.getDisplayName() + ")");
        }
    }

    public List<CameraCapabilities> getAvailableCameras() {
        return availableCameras;
    }

    public CameraCapabilities getCurrentCapabilities() {
        return currentCapabilities;
    }

    public void switchCamera(String newCameraId) {
        if (newCameraId.equals(currentCameraId) && cameraDevice != null) return;
        closeCamera();
        currentCameraId = newCameraId;
        for (CameraCapabilities caps : availableCameras) {
            if (caps.getCameraId().equals(newCameraId)) {
                currentCapabilities = caps;
                break;
            }
        }
        // Adjust capture format if RAW is not supported on this camera
        if (currentCapabilities != null && !currentCapabilities.isRawSupported() && captureFormat != CaptureFormat.JPEG) {
            captureFormat = CaptureFormat.JPEG;
        }
        openCamera();
    }

    public void startBackgroundThread() {
        backgroundThread = new HandlerThread("AstroCameraBackground");
        backgroundThread.start();
        backgroundHandler = new Handler(backgroundThread.getLooper());
    }

    public void stopBackgroundThread() {
        if (backgroundThread != null) {
            backgroundThread.quitSafely();
            try {
                backgroundThread.join();
                backgroundThread = null;
                backgroundHandler = null;
            } catch (InterruptedException e) {
                Log.e(TAG, "Error stopping background thread", e);
            }
        }
    }

    @SuppressLint("MissingPermission")
    public void openCamera() {
        Log.i(TAG, "openCamera() invoked. currentCameraId=" + currentCameraId +
                ", textureViewAvailable=" + (textureView != null && textureView.isAvailable()));

        if (textureView == null || !textureView.isAvailable()) {
            return;
        }

        if (currentCameraId == null) {
            postError("No suitable camera found on device.");
            return;
        }

        try {
            if (!cameraOpenCloseLock.tryAcquire(2500, TimeUnit.MILLISECONDS)) {
                throw new RuntimeException("Time out waiting to lock camera opening.");
            }

            CameraCharacteristics characteristics = cameraManager.getCameraCharacteristics(currentCameraId);
            currentCapabilities = new CameraCapabilities(currentCameraId, characteristics);

            Size previewSize = currentCapabilities.getPreviewSize();
            Log.i(TAG, "Configuring previewSize: " + previewSize.getWidth() + "x" + previewSize.getHeight());
            textureView.setAspectRatio(previewSize.getHeight(), previewSize.getWidth());

            setUpImageReaders(currentCapabilities);

            Log.i(TAG, "Calling cameraManager.openCamera for ID: " + currentCameraId);
            cameraManager.openCamera(currentCameraId, stateCallback, backgroundHandler);

        } catch (CameraAccessException e) {
            Log.e(TAG, "Cannot access camera", e);
            postError("Cannot access camera: " + e.getMessage());
            cameraOpenCloseLock.release();
        } catch (InterruptedException e) {
            postError("Interrupted while opening camera.");
            cameraOpenCloseLock.release();
        }
    }

    private void setUpImageReaders(CameraCapabilities caps) {
        if (jpegImageReader != null) {
            jpegImageReader.close();
            jpegImageReader = null;
        }
        if (rawImageReader != null) {
            rawImageReader.close();
            rawImageReader = null;
        }

        Size jpegSize = caps.getJpegSize();
        jpegImageReader = ImageReader.newInstance(jpegSize.getWidth(), jpegSize.getHeight(), ImageFormat.JPEG, 2);
        jpegImageReader.setOnImageAvailableListener(jpegImageListener, backgroundHandler);

        if (caps.isRawSupported() && caps.getRawSize() != null) {
            Size rawSize = caps.getRawSize();
            rawImageReader = ImageReader.newInstance(rawSize.getWidth(), rawSize.getHeight(), ImageFormat.RAW_SENSOR, 2);
            rawImageReader.setOnImageAvailableListener(rawImageListener, backgroundHandler);
        }
    }

    private final CameraDevice.StateCallback stateCallback = new CameraDevice.StateCallback() {
        @Override
        public void onOpened(@NonNull CameraDevice camera) {
            cameraOpenCloseLock.release();
            cameraDevice = camera;
            createCameraPreviewSession();
            mainHandler.post(new Runnable() {
                @Override
                public void run() {
                    if (eventListener != null) {
                        eventListener.onCameraReady(currentCapabilities);
                    }
                }
            });
        }

        @Override
        public void onDisconnected(@NonNull CameraDevice camera) {
            cameraOpenCloseLock.release();
            camera.close();
            cameraDevice = null;
        }

        @Override
        public void onError(@NonNull CameraDevice camera, int error) {
            cameraOpenCloseLock.release();
            camera.close();
            cameraDevice = null;
            postError("Camera device error: " + error);
        }
    };

    private void createCameraPreviewSession() {
        try {
            SurfaceTexture texture = textureView.getSurfaceTexture();
            if (texture == null) return;

            Size previewSize = currentCapabilities.getPreviewSize();
            texture.setDefaultBufferSize(previewSize.getWidth(), previewSize.getHeight());
            Surface previewSurface = new Surface(texture);

            previewRequestBuilder = cameraDevice.createCaptureRequest(CameraDevice.TEMPLATE_PREVIEW);
            previewRequestBuilder.addTarget(previewSurface);

            List<Surface> surfaces = new ArrayList<>();
            surfaces.add(previewSurface);
            if (jpegImageReader != null) {
                surfaces.add(jpegImageReader.getSurface());
            }
            if (rawImageReader != null) {
                surfaces.add(rawImageReader.getSurface());
            }

            cameraDevice.createCaptureSession(surfaces, new CameraCaptureSession.StateCallback() {
                @Override
                public void onConfigured(@NonNull CameraCaptureSession session) {
                    if (cameraDevice == null) return;
                    captureSession = session;
                    applySettingsToPreview();
                }

                @Override
                public void onConfigureFailed(@NonNull CameraCaptureSession session) {
                    postError("Failed to configure camera preview session.");
                }
            }, backgroundHandler);

        } catch (CameraAccessException e) {
            postError("Failed to start camera preview: " + e.getMessage());
        }
    }

    public void applySettingsToPreview() {
        if (captureSession == null || previewRequestBuilder == null) return;

        try {
            // Apply Manual vs Auto Exposure
            if (currentIso > 0 || currentExposureNs > 0) {
                if (currentCapabilities.isManualSensorSupported()) {
                    previewRequestBuilder.set(CaptureRequest.CONTROL_AE_MODE, CaptureRequest.CONTROL_AE_MODE_OFF);
                    int isoToSet = (currentIso > 0) ? currentIso : lastAutoIso;
                    previewRequestBuilder.set(CaptureRequest.SENSOR_SENSITIVITY, isoToSet);

                    long expToSet = (currentExposureNs > 0) ? currentExposureNs : lastAutoExposureNs;
                    // Clamp preview exposure to 1/10s (100ms) so preview stays fluid for framing stars
                    long previewExposure = Math.min(expToSet, 100_000_000L);
                    previewRequestBuilder.set(CaptureRequest.SENSOR_EXPOSURE_TIME, previewExposure);
                } else {
                    previewRequestBuilder.set(CaptureRequest.CONTROL_AE_MODE, CaptureRequest.CONTROL_AE_MODE_ON);
                }
            } else {
                previewRequestBuilder.set(CaptureRequest.CONTROL_AE_MODE, CaptureRequest.CONTROL_AE_MODE_ON);
            }

            // Apply Manual vs Auto Focus
            if (currentFocusDistance >= 0f && currentCapabilities.isControllableFocusSupported()) {
                previewRequestBuilder.set(CaptureRequest.CONTROL_AF_MODE, CaptureRequest.CONTROL_AF_MODE_OFF);
                previewRequestBuilder.set(CaptureRequest.LENS_FOCUS_DISTANCE, currentFocusDistance);
            } else {
                previewRequestBuilder.set(CaptureRequest.CONTROL_AF_MODE, CaptureRequest.CONTROL_AF_MODE_CONTINUOUS_PICTURE);
            }

            // Apply White Balance
            applyWhiteBalance(previewRequestBuilder);

            previewRequest = previewRequestBuilder.build();
            captureSession.setRepeatingRequest(previewRequest, previewCaptureCallback, backgroundHandler);

        } catch (CameraAccessException e) {
            Log.e(TAG, "Error applying settings to preview", e);
        }
    }

    private final CameraCaptureSession.CaptureCallback previewCaptureCallback = new CameraCaptureSession.CaptureCallback() {
        @Override
        public void onCaptureCompleted(@NonNull CameraCaptureSession session,
                                       @NonNull CaptureRequest request,
                                       @NonNull TotalCaptureResult result) {
            super.onCaptureCompleted(session, request, result);
            Integer iso = result.get(CaptureResult.SENSOR_SENSITIVITY);
            Long exp = result.get(CaptureResult.SENSOR_EXPOSURE_TIME);
            Float focus = result.get(CaptureResult.LENS_FOCUS_DISTANCE);

            if (iso != null && iso > 0) {
                lastAutoIso = iso;
            }
            if (exp != null && exp > 0) {
                lastAutoExposureNs = exp;
            }

            final int finalIso = (iso != null) ? iso : -1;
            final long finalExp = (exp != null) ? exp : -1L;
            final float finalFocus = (focus != null) ? focus : -1f;

            mainHandler.post(new Runnable() {
                @Override
                public void run() {
                    if (eventListener != null) {
                        eventListener.onPreviewFrameResult(finalIso, finalExp, finalFocus);
                    }
                }
            });
        }
    };

    private int pendingSavesCount = 0;
    private int currentCaptureOrientation = 0;

    public void takePicture() {
        if (cameraDevice == null || captureSession == null || isCapturing) return;

        isCapturing = true;
        synchronized (captureSyncObject) {
            lastStillCaptureResult = null;
            pendingRawImage = null;
            pendingJpegImage = null;
        }

        try {
            final CaptureRequest.Builder captureBuilder = cameraDevice.createCaptureRequest(CameraDevice.TEMPLATE_STILL_CAPTURE);

            // Add targets based on CaptureFormat
            boolean requestJpeg = (captureFormat == CaptureFormat.JPEG || captureFormat == CaptureFormat.RAW_AND_JPEG);
            boolean requestRaw = (currentCapabilities.isRawSupported() &&
                    (captureFormat == CaptureFormat.RAW || captureFormat == CaptureFormat.RAW_AND_JPEG));

            pendingSavesCount = (requestJpeg ? 1 : 0) + (requestRaw ? 1 : 0);

            if (requestJpeg && jpegImageReader != null) {
                captureBuilder.addTarget(jpegImageReader.getSurface());
            }
            if (requestRaw && rawImageReader != null) {
                captureBuilder.addTarget(rawImageReader.getSurface());
            }

            // Manual ISO & Exposure
            if ((currentIso > 0 || currentExposureNs > 0) && currentCapabilities.isManualSensorSupported()) {
                captureBuilder.set(CaptureRequest.CONTROL_AE_MODE, CaptureRequest.CONTROL_AE_MODE_OFF);
                int isoToSet = (currentIso > 0) ? currentIso : lastAutoIso;
                captureBuilder.set(CaptureRequest.SENSOR_SENSITIVITY, isoToSet);

                long expToSet = (currentExposureNs > 0) ? currentExposureNs : lastAutoExposureNs;
                captureBuilder.set(CaptureRequest.SENSOR_EXPOSURE_TIME, expToSet);
                captureBuilder.set(CaptureRequest.SENSOR_FRAME_DURATION, expToSet);
            } else {
                captureBuilder.set(CaptureRequest.CONTROL_AE_MODE, CaptureRequest.CONTROL_AE_MODE_ON);
            }

            // Focus
            if (currentFocusDistance >= 0f && currentCapabilities.isControllableFocusSupported()) {
                captureBuilder.set(CaptureRequest.CONTROL_AF_MODE, CaptureRequest.CONTROL_AF_MODE_OFF);
                captureBuilder.set(CaptureRequest.LENS_FOCUS_DISTANCE, currentFocusDistance);
            } else {
                captureBuilder.set(CaptureRequest.CONTROL_AF_MODE, CaptureRequest.CONTROL_AF_MODE_CONTINUOUS_PICTURE);
            }

            // White balance
            applyWhiteBalance(captureBuilder);

            // Turn off aggressive noise reduction for pristine stars
            captureBuilder.set(CaptureRequest.NOISE_REDUCTION_MODE, CaptureRequest.NOISE_REDUCTION_MODE_OFF);

            // Orientation
            int rotation = getDisplayRotation();
            int sensorOrientation = currentCapabilities.getCharacteristics().get(CameraCharacteristics.SENSOR_ORIENTATION);
            currentCaptureOrientation = getJpegOrientation(sensorOrientation, rotation);
            captureBuilder.set(CaptureRequest.JPEG_ORIENTATION, currentCaptureOrientation);

            // Estimated exposure duration in ms for UI countdown
            final long exposureMs = (currentExposureNs > 0) ? (currentExposureNs / 1_000_000L) : 250L;
            mainHandler.post(new Runnable() {
                @Override
                public void run() {
                    if (eventListener != null) {
                        eventListener.onCaptureStarted(exposureMs);
                    }
                }
            });

            CameraCaptureSession.CaptureCallback captureCallback = new CameraCaptureSession.CaptureCallback() {
                @Override
                public void onCaptureCompleted(@NonNull CameraCaptureSession session,
                                               @NonNull CaptureRequest request,
                                               @NonNull TotalCaptureResult result) {
                    synchronized (captureSyncObject) {
                        lastStillCaptureResult = result;
                        checkAndProcessPendingCaptures();
                    }
                }

                @Override
                public void onCaptureFailed(@NonNull CameraCaptureSession session,
                                            @NonNull CaptureRequest request,
                                            @NonNull CaptureFailure failure) {
                    isCapturing = false;
                    pendingSavesCount = 0;
                    postError("Capture request failed: reason " + failure.getReason());
                    mainHandler.post(new Runnable() {
                        @Override
                        public void run() {
                            if (eventListener != null) {
                                eventListener.onCaptureFinished();
                            }
                        }
                    });
                }
            };

            // Pause preview repeating request during capture to avoid pipeline contention on long exposures
            captureSession.stopRepeating();
            captureSession.capture(captureBuilder.build(), captureCallback, backgroundHandler);

        } catch (CameraAccessException e) {
            isCapturing = false;
            pendingSavesCount = 0;
            postError("Failed to trigger photo capture: " + e.getMessage());
        }
    }

    private final ImageReader.OnImageAvailableListener jpegImageListener = new ImageReader.OnImageAvailableListener() {
        @Override
        public void onImageAvailable(ImageReader reader) {
            Image image = reader.acquireNextImage();
            if (image == null) return;
            synchronized (captureSyncObject) {
                pendingJpegImage = image;
                checkAndProcessPendingCaptures();
            }
        }
    };

    private final ImageReader.OnImageAvailableListener rawImageListener = new ImageReader.OnImageAvailableListener() {
        @Override
        public void onImageAvailable(ImageReader reader) {
            Image image = reader.acquireNextImage();
            if (image == null) return;
            synchronized (captureSyncObject) {
                pendingRawImage = image;
                checkAndProcessPendingCaptures();
            }
        }
    };

    private void checkAndProcessPendingCaptures() {
        // Save JPEG
        if (pendingJpegImage != null) {
            Image img = pendingJpegImage;
            pendingJpegImage = null;
            photoSaver.saveJpeg(img, currentCaptureOrientation, new PhotoSaver.OnSaveListener() {
                @Override
                public void onPhotoSaved(Uri uri, String filename, boolean isRaw, long sizeBytes) {
                    handlePhotoSaved(uri, filename, isRaw);
                }

                @Override
                public void onError(String error, Throwable t) {
                    postError(error);
                    handleSaveFailed();
                }
            });
        }

        // Save RAW when both Image and TotalCaptureResult are ready
        if (pendingRawImage != null && lastStillCaptureResult != null) {
            Image rawImg = pendingRawImage;
            pendingRawImage = null;
            TotalCaptureResult res = lastStillCaptureResult;

            photoSaver.saveRawDng(rawImg, currentCapabilities.getCharacteristics(), res, currentCaptureOrientation, new PhotoSaver.OnSaveListener() {
                @Override
                public void onPhotoSaved(Uri uri, String filename, boolean isRaw, long sizeBytes) {
                    handlePhotoSaved(uri, filename, isRaw);
                }

                @Override
                public void onError(String error, Throwable t) {
                    postError(error);
                    handleSaveFailed();
                }
            });
        }
    }

    private void handlePhotoSaved(Uri uri, String filename, boolean isRaw) {
        mainHandler.post(new Runnable() {
            @Override
            public void run() {
                if (eventListener != null) {
                    eventListener.onCaptureSaved(uri, filename, isRaw);
                }
            }
        });
        checkAllSavesCompleted();
    }

    private void handleSaveFailed() {
        checkAllSavesCompleted();
    }

    private synchronized void checkAllSavesCompleted() {
        pendingSavesCount--;
        if (pendingSavesCount <= 0) {
            pendingSavesCount = 0;
            isCapturing = false;
            // Resume preview
            applySettingsToPreview();
            mainHandler.post(new Runnable() {
                @Override
                public void run() {
                    if (eventListener != null) {
                        eventListener.onCaptureFinished();
                    }
                }
            });
        }
    }

    public boolean isCapturing() {
        return isCapturing;
    }

    public void setIso(int iso) {
        this.currentIso = iso;
        applySettingsToPreview();
    }

    public void setShutterSpeed(long exposureNs) {
        this.currentExposureNs = exposureNs;
        applySettingsToPreview();
    }

    public void setFocusDistance(float focusDistance) {
        this.currentFocusDistance = focusDistance;
        applySettingsToPreview();
    }

    public void setInfinityFocus() {
        setFocusDistance(0.0f);
    }

    public void setAutoFocus() {
        setFocusDistance(-1f);
    }

    public void setAwbMode(int awbMode) {
        this.currentKelvin = -1;
        this.currentAwbMode = awbMode;
        applySettingsToPreview();
    }

    public void setKelvin(int kelvin) {
        this.currentKelvin = kelvin;
        applySettingsToPreview();
    }

    public void setAwbAuto() {
        this.currentKelvin = -1;
        this.currentAwbMode = CameraMetadata.CONTROL_AWB_MODE_AUTO;
        applySettingsToPreview();
    }

    public int getCurrentKelvin() {
        return currentKelvin;
    }

    private void applyWhiteBalance(CaptureRequest.Builder builder) {
        if (currentKelvin <= 0) {
            builder.set(CaptureRequest.CONTROL_AWB_MODE, currentAwbMode);
        } else {
            if (currentCapabilities != null && currentCapabilities.isAwbOffSupported()) {
                builder.set(CaptureRequest.CONTROL_AWB_MODE, CameraMetadata.CONTROL_AWB_MODE_OFF);
                builder.set(CaptureRequest.COLOR_CORRECTION_MODE, CameraMetadata.COLOR_CORRECTION_MODE_TRANSFORM_MATRIX);
                builder.set(CaptureRequest.COLOR_CORRECTION_GAINS, kelvinToRggb(currentKelvin));
            } else {
                int mode;
                if (currentKelvin < 3500) {
                    mode = CameraMetadata.CONTROL_AWB_MODE_INCANDESCENT;
                } else if (currentKelvin < 4800) {
                    mode = CameraMetadata.CONTROL_AWB_MODE_FLUORESCENT;
                } else if (currentKelvin < 6000) {
                    mode = CameraMetadata.CONTROL_AWB_MODE_DAYLIGHT;
                } else if (currentKelvin < 7200) {
                    mode = CameraMetadata.CONTROL_AWB_MODE_CLOUDY_DAYLIGHT;
                } else {
                    mode = CameraMetadata.CONTROL_AWB_MODE_SHADE;
                }
                builder.set(CaptureRequest.CONTROL_AWB_MODE, mode);
            }
        }
    }

    public static RggbChannelVector kelvinToRggb(float kelvin) {
        float temp = kelvin / 100.0f;
        float red, green, blue;

        if (temp <= 66f) {
            red = 255f;
        } else {
            float r = temp - 60f;
            red = (float) (329.698727446 * Math.pow(r, -0.1332047592));
            if (red < 0f) red = 0f;
            if (red > 255f) red = 255f;
        }

        if (temp <= 66f) {
            float g = temp;
            green = (float) (99.4708025861 * Math.log(g) - 161.1195681661);
        } else {
            float g = temp - 60f;
            green = (float) (288.1221695283 * Math.pow(g, -0.0755148492));
        }
        if (green < 0f) green = 0f;
        if (green > 255f) green = 255f;

        if (temp >= 66f) {
            blue = 255f;
        } else if (temp <= 19f) {
            blue = 0f;
        } else {
            float b = temp - 10f;
            blue = (float) (138.5177312231 * Math.log(b) - 305.0447927307);
            if (blue < 0f) blue = 0f;
            if (blue > 255f) blue = 255f;
        }

        float factorR = (red > 0f) ? (255f / red) : 1.0f;
        float factorG = 1.0f;
        float factorB = (blue > 0f) ? (255f / blue) : 1.0f;

        return new RggbChannelVector(
                Math.max(1.0f, Math.min(factorR, 4.0f)),
                factorG,
                factorG,
                Math.max(1.0f, Math.min(factorB, 4.0f))
        );
    }

    public void setCaptureFormat(CaptureFormat format) {
        this.captureFormat = format;
    }

    public CaptureFormat getCaptureFormat() {
        return captureFormat;
    }

    public int getCurrentIso() {
        return currentIso;
    }

    public long getCurrentExposureNs() {
        return currentExposureNs;
    }

    public float getCurrentFocusDistance() {
        return currentFocusDistance;
    }

    public int getCurrentAwbMode() {
        return currentAwbMode;
    }

    private int getDisplayRotation() {
        WindowManager wm = (WindowManager) context.getSystemService(Context.WINDOW_SERVICE);
        return (wm != null && wm.getDefaultDisplay() != null)
                ? wm.getDefaultDisplay().getRotation() : Surface.ROTATION_0;
    }

    private int getJpegOrientation(int sensorOrientation, int deviceOrientation) {
        int surfaceRotation = ORIENTATIONS.get(deviceOrientation);
        return (surfaceRotation + sensorOrientation + 270) % 360;
    }

    public void closeCamera() {
        try {
            cameraOpenCloseLock.acquire();
            if (captureSession != null) {
                captureSession.close();
                captureSession = null;
            }
            if (cameraDevice != null) {
                cameraDevice.close();
                cameraDevice = null;
            }
            if (jpegImageReader != null) {
                jpegImageReader.close();
                jpegImageReader = null;
            }
            if (rawImageReader != null) {
                rawImageReader.close();
                rawImageReader = null;
            }
        } catch (InterruptedException e) {
            Log.e(TAG, "Interrupted while closing camera", e);
        } finally {
            cameraOpenCloseLock.release();
        }
    }

    private void postError(final String msg) {
        mainHandler.post(new Runnable() {
            @Override
            public void run() {
                if (eventListener != null) {
                    eventListener.onError(msg);
                }
            }
        });
    }
}
