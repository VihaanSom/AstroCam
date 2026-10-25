package com.astrocam;

import android.graphics.ImageFormat;
import android.graphics.SurfaceTexture;
import android.hardware.camera2.CameraCharacteristics;
import android.hardware.camera2.CameraMetadata;
import android.hardware.camera2.params.StreamConfigurationMap;
import android.util.Range;
import android.util.Size;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;

public class CameraCapabilities {

    private final String cameraId;
    private final CameraCharacteristics characteristics;
    private final int lensFacing;
    private final int hardwareLevel;
    private final boolean manualSensorSupported;
    private final boolean manualPostProcessingSupported;
    private final boolean rawSupported;
    private final Range<Integer> isoRange;
    private final Range<Long> exposureTimeRangeNs;
    private final Float minFocusDistance;
    private final boolean controllableFocusSupported;
    private final int[] supportedAwbModes;

    private Size previewSize;
    private Size jpegSize;
    private Size rawSize;

    public CameraCapabilities(String cameraId, CameraCharacteristics characteristics) {
        this.cameraId = cameraId;
        this.characteristics = characteristics;

        Integer facing = characteristics.get(CameraCharacteristics.LENS_FACING);
        this.lensFacing = (facing != null) ? facing : CameraCharacteristics.LENS_FACING_BACK;

        Integer hwLevel = characteristics.get(CameraCharacteristics.INFO_SUPPORTED_HARDWARE_LEVEL);
        this.hardwareLevel = (hwLevel != null) ? hwLevel : CameraCharacteristics.INFO_SUPPORTED_HARDWARE_LEVEL_LEGACY;

        // Check capabilities
        int[] caps = characteristics.get(CameraCharacteristics.REQUEST_AVAILABLE_CAPABILITIES);
        boolean manualSensor = false;
        boolean manualPost = false;
        boolean raw = false;
        if (caps != null) {
            for (int cap : caps) {
                if (cap == CameraCharacteristics.REQUEST_AVAILABLE_CAPABILITIES_MANUAL_SENSOR) {
                    manualSensor = true;
                } else if (cap == CameraCharacteristics.REQUEST_AVAILABLE_CAPABILITIES_MANUAL_POST_PROCESSING) {
                    manualPost = true;
                } else if (cap == CameraCharacteristics.REQUEST_AVAILABLE_CAPABILITIES_RAW) {
                    raw = true;
                }
            }
        }
        this.manualSensorSupported = manualSensor;
        this.manualPostProcessingSupported = manualPost;
        this.rawSupported = raw;

        // ISO & Exposure ranges
        this.isoRange = characteristics.get(CameraCharacteristics.SENSOR_INFO_SENSITIVITY_RANGE);
        this.exposureTimeRangeNs = characteristics.get(CameraCharacteristics.SENSOR_INFO_EXPOSURE_TIME_RANGE);

        // Focus
        this.minFocusDistance = characteristics.get(CameraCharacteristics.LENS_INFO_MINIMUM_FOCUS_DISTANCE);
        this.controllableFocusSupported = (minFocusDistance != null && minFocusDistance > 0.0f);

        // AWB
        int[] awb = characteristics.get(CameraCharacteristics.CONTROL_AWB_AVAILABLE_MODES);
        this.supportedAwbModes = (awb != null) ? awb : new int[]{CameraMetadata.CONTROL_AWB_MODE_AUTO};

        // Determine optimal resolutions
        initSizes();
    }

    private void initSizes() {
        StreamConfigurationMap map = characteristics.get(CameraCharacteristics.SCALER_STREAM_CONFIGURATION_MAP);
        if (map == null) return;

        // Largest JPEG
        Size[] jpegSizes = map.getOutputSizes(ImageFormat.JPEG);
        if (jpegSizes != null && jpegSizes.length > 0) {
            jpegSize = Collections.max(Arrays.asList(jpegSizes), new CompareSizesByArea());
        }

        // Largest RAW
        if (rawSupported) {
            Size[] rawSizes = map.getOutputSizes(ImageFormat.RAW_SENSOR);
            if (rawSizes != null && rawSizes.length > 0) {
                rawSize = Collections.max(Arrays.asList(rawSizes), new CompareSizesByArea());
            }
        }

        // Optimal Preview Size (prefer matching aspect ratio of JPEG/RAW, capped at 1920x1080)
        Size[] previewSizes = map.getOutputSizes(SurfaceTexture.class);
        if (previewSizes != null && previewSizes.length > 0) {
            double targetAspect = (jpegSize != null) ? (double) jpegSize.getWidth() / jpegSize.getHeight() : 4.0 / 3.0;
            Size bestSize = null;
            double minAspectDiff = Double.MAX_VALUE;

            for (Size sz : previewSizes) {
                if (sz.getWidth() <= 1920 && sz.getHeight() <= 1080) {
                    double aspect = (double) sz.getWidth() / sz.getHeight();
                    double diff = Math.abs(aspect - targetAspect);
                    if (diff < minAspectDiff || (Math.abs(diff - minAspectDiff) < 0.01 && (bestSize == null || sz.getWidth() > bestSize.getWidth()))) {
                        minAspectDiff = diff;
                        bestSize = sz;
                    }
                }
            }
            if (bestSize == null) {
                bestSize = previewSizes[0];
            }
            previewSize = bestSize;
        }
    }

    public String getCameraId() {
        return cameraId;
    }

    public CameraCharacteristics getCharacteristics() {
        return characteristics;
    }

    public int getLensFacing() {
        return lensFacing;
    }

    public boolean isBackFacing() {
        return lensFacing == CameraCharacteristics.LENS_FACING_BACK;
    }

    public boolean isManualSensorSupported() {
        return manualSensorSupported;
    }

    public boolean isRawSupported() {
        return rawSupported && rawSize != null;
    }

    public Range<Integer> getIsoRange() {
        return isoRange;
    }

    public Range<Long> getExposureTimeRangeNs() {
        return exposureTimeRangeNs;
    }

    public Float getMinFocusDistance() {
        return minFocusDistance;
    }

    public boolean isControllableFocusSupported() {
        return controllableFocusSupported;
    }

    public int[] getSupportedAwbModes() {
        return supportedAwbModes;
    }

    public Size getPreviewSize() {
        return previewSize != null ? previewSize : new Size(1920, 1080);
    }

    public Size getJpegSize() {
        return jpegSize != null ? jpegSize : new Size(1920, 1080);
    }

    public Size getRawSize() {
        return rawSize;
    }

    public String getDisplayName() {
        String facingStr = isBackFacing() ? "Rear" : "Front";
        if (rawSupported) {
            return facingStr + " Cam (" + cameraId + ") [RAW]";
        }
        return facingStr + " Cam (" + cameraId + ")";
    }

    public List<ShutterPreset> getSupportedShutterPresets() {
        List<ShutterPreset> presets = new ArrayList<>();
        // Auto
        presets.add(new ShutterPreset("AUTO", -1L));

        // Candidate presets
        long[][] candidates = new long[][]{
                {1000L, 1_000_000L},         // 1/1000s
                {500L, 2_000_000L},          // 1/500s
                {250L, 4_000_000L},          // 1/250s
                {125L, 8_000_000L},          // 1/125s
                {60L, 16_666_666L},          // 1/60s
                {30L, 33_333_333L},          // 1/30s
                {15L, 66_666_666L},          // 1/15s
                {8L, 125_000_000L},          // 1/8s
                {4L, 250_000_000L},          // 1/4s
                {2L, 500_000_000L},          // 1/2s
                {0L, 1_000_000_000L},        // 1s
                {0L, 2_000_000_000L},        // 2s
                {0L, 4_000_000_000L},        // 4s
                {0L, 8_000_000_000L},        // 8s
                {0L, 10_000_000_000L},       // 10s
                {0L, 15_000_000_000L},       // 15s
                {0L, 20_000_000_000L},       // 20s
                {0L, 30_000_000_000L},       // 30s
                {0L, 32_000_000_000L}        // 32s
        };

        if (exposureTimeRangeNs != null) {
            long minNs = exposureTimeRangeNs.getLower();
            long maxNs = exposureTimeRangeNs.getUpper();

            for (long[] c : candidates) {
                long denom = c[0];
                long ns = c[1];
                if (ns >= minNs && ns <= maxNs) {
                    String label;
                    if (denom > 0) {
                        label = "1/" + denom + "s";
                    } else {
                        label = (ns / 1_000_000_000L) + "s";
                    }
                    presets.add(new ShutterPreset(label, ns));
                }
            }

            // Also ensure the exact sensor maximum is available as a preset if not already present
            long maxSec = maxNs / 1_000_000_000L;
            if (maxSec >= 1 && presets.get(presets.size() - 1).exposureNs != maxNs) {
                presets.add(new ShutterPreset(maxSec + "s (Max)", maxNs));
            }
        }

        return presets;
    }

    public List<Integer> getSupportedIsoPresets() {
        List<Integer> list = new ArrayList<>();
        list.add(-1); // Auto ISO

        if (isoRange != null) {
            int min = isoRange.getLower();
            int max = isoRange.getUpper();

            int[] candidates = new int[]{50, 100, 200, 400, 800, 1600, 3200, 6400, 12800};
            for (int iso : candidates) {
                if (iso >= min && iso <= max) {
                    list.add(iso);
                }
            }
            if (!list.contains(max)) {
                list.add(max);
            }
        }
        return list;
    }

    public static class ShutterPreset {
        public final String label;
        public final long exposureNs; // -1 for auto

        public ShutterPreset(String label, long exposureNs) {
            this.label = label;
            this.exposureNs = exposureNs;
        }

        @Override
        public String toString() {
            return label;
        }
    }

    private static class CompareSizesByArea implements Comparator<Size> {
        @Override
        public int compare(Size lhs, Size rhs) {
            return Long.signum((long) lhs.getWidth() * lhs.getHeight() -
                    (long) rhs.getWidth() * rhs.getHeight());
        }
    }
}
