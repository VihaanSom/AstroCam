# AstroCam — Manual Astrophotography Camera for Android

A lightweight native Android camera application designed specifically for Milky Way photography, star-trail capture, and astrophotography sessions. AstroCam provides full manual sensor controls and a dedicated intervalometer, capturing clean uncompressed RAW (DNG) and JPEG frames for stacking and processing in PC software (such as Siril, DeepSkyStacker, Sequator, RawTherapee, or Adobe Lightroom).

---

## Key Features

### 1. Manual Camera Controls (Camera2 API)
- **Manual ISO Sensitivity (`SENSOR_SENSITIVITY`)**:
  - Dynamically queries supported ISO range from `CameraCharacteristics.SENSOR_INFO_SENSITIVITY_RANGE`.
  - Presets (Auto, 100, 200, 400, 800, 1600, 3200, 6400, Sensor Max) filtered to supported hardware limits.
- **Manual Shutter Speed / Exposure Time (`SENSOR_EXPOSURE_TIME`)**:
  - Reads exposure time range from `CameraCharacteristics.SENSOR_INFO_EXPOSURE_TIME_RANGE`.
  - Filtered presets from 1/1000s up to maximum sensor exposure time (e.g. 10s, 30s, 32s).
  - Manual sensor mode enabled via `CONTROL_AE_MODE_OFF`.
- **Manual Lens Focus (`LENS_FOCUS_DISTANCE`)**:
  - Manual focus slider mapped from 0.0 diopters (infinity focus) to `LENS_INFO_MINIMUM_FOCUS_DISTANCE`.
  - Dedicated **∞ Infinity Focus** shortcut button for instant star focusing.
  - One-tap **Auto Focus (AF)** toggle.
- **White Balance Control (`CONTROL_AWB_MODE`)**:
  - Presets: Auto (AWB), Daylight (5500K), Cloudy (6500K), Shade (7500K), Tungsten (3000K), Fluorescent (4000K), and Twilight.
- **Camera Lens Switching**:
  - Automatically identifies all physical and logical cameras with facing direction and RAW capabilities.
  - Quick lens switcher dialog for switching between Wide, Ultra-wide, and Telephoto sensors.
- **Format Selection**:
  - `RAW + JPG`: Simultaneously captures both high-resolution JPEG and uncompressed DNG RAW files.
  - `RAW (DNG)`: Saves 16-bit sensor DNG RAW files containing full Bayer sensor data.
  - `JPEG`: Fast compressed preview captures.

### 2. High-Precision Astrophotography Intervalometer
- **Configurable Sequence**:
  - Exposure duration derived from manual shutter speed.
  - Configurable interval between shots (1s, 2s, 3s, 5s, 10s, 15s, 30s, 60s).
  - Configurable frame count (10, 20, 50, 100, 200, 500, or ∞ Continuous).
  - 3-second vibration-reduction lead-in countdown so touching the screen does not shake the tripod.
- **Sequential Pipeline**:
  - Sequential capture queue ensures zero overlapping requests or pipeline stalls.
  - Automatically verifies file I/O completion before scheduling the next frame.
  - Screen wake-lock (`FLAG_KEEP_SCREEN_ON`) prevents phone from sleeping during active star-trail sessions.
  - Safe stop control allows sequences to terminate immediately without corrupting in-flight files.

### 3. File Storage & Verification
- Saved in dedicated `DCIM/AstroCam` directory.
- Timestamp-based filenames: `AstroCam_yyyyMMdd_HHmmss_SSS.(jpg|dng)`.
- Uses Android `MediaStore` with scoped storage compatibility (API 26 through API 35+).
- DNG files written via Android's `DngCreator` with full camera characteristics, color calibration matrices, and EXIF metadata.

---

## Technical Architecture

```
com.astrocam/
├── MainActivity.java             # UI lifecycle, tactile controls, drawer panels & HUD
├── CameraController.java         # Camera2 session, preview requests, manual sensor controls & capture
├── CameraCapabilities.java       # Runtime hardware query (ISO, exposure, focus, RAW support)
├── PhotoSaver.java               # Dedicated background I/O executor for JPEG and DNG file writes
├── IntervalometerController.java # Sequence state machine, cadence timing, and progress calculation
└── AutoFitTextureView.java       # Aspect-ratio-preserving preview view
```

---

## Build & Run Instructions

### Prerequisites
- JDK 17 or JDK 21
- Android SDK Platform 35 or 36
- Android SDK Build-Tools 34.0.0+

### Building via Gradle
```bash
# Build Debug APK
./gradlew assembleDebug

# Install to connected device
adb install -r app/build/outputs/apk/debug/app-debug.apk

# Launch AstroCam
adb shell am start -n com.astrocam/.MainActivity
```
