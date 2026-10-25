package com.astrocam;

import android.Manifest;
import android.content.Context;
import android.content.DialogInterface;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.SurfaceTexture;
import android.hardware.camera2.CameraMetadata;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.os.CountDownTimer;
import android.os.VibrationEffect;
import android.os.Vibrator;
import android.view.TextureView;
import android.view.View;
import android.view.WindowManager;
import android.widget.Button;
import android.widget.FrameLayout;
import android.widget.HorizontalScrollView;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.ProgressBar;
import android.widget.RelativeLayout;
import android.widget.SeekBar;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.app.ActivityCompat;
import androidx.core.content.ContextCompat;

import java.io.InputStream;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

public class MainActivity extends AppCompatActivity {

    private static final int REQUEST_PERMISSIONS_CODE = 1001;

    private AutoFitTextureView textureView;
    private TextView btnLensSwitch;
    private TextView btnFormatSwitch;
    private TextView tvHudStatus;
    private TextView tvCountdownOverlay;
    private TextView tvCaptureStatus;

    // Tabs
    private TextView tabSingleMode;
    private TextView tabIntervalometerMode;

    // Quick settings buttons & labels
    private LinearLayout btnSettingIso;
    private TextView tvValIso;
    private LinearLayout btnSettingShutter;
    private TextView tvValShutter;
    private LinearLayout btnSettingFocus;
    private TextView tvValFocus;
    private LinearLayout btnSettingWb;
    private TextView tvValWb;

    // Expandable Drawer
    private FrameLayout panelDrawer;
    private LinearLayout panelFocusControls;
    private Button btnFocusAf;
    private Button btnFocusInfinity;
    private TextView tvFocusSliderVal;
    private SeekBar sbFocus;

    private HorizontalScrollView hsvChips;
    private LinearLayout llChipsContainer;

    // Intervalometer Panel
    private LinearLayout panelIntervalometer;
    private LinearLayout layoutIntervalConfig;
    private TextView tvIntervalSeconds;
    private Button btnIntervalDec;
    private Button btnIntervalInc;
    private TextView tvFramesCount;
    private Button btnFramesDec;
    private Button btnFramesInc;
    private TextView chipFrames10;
    private TextView chipFrames50;
    private TextView chipFrames100;
    private TextView chipFramesInf;
    private TextView tvIntervalWarning;

    // Intervalometer Running Progress
    private LinearLayout layoutIntervalRunning;
    private TextView tvIntervalProgressFrames;
    private TextView tvIntervalProgressTime;
    private ProgressBar pbIntervalProgress;
    private TextView tvIntervalRunningStatus;

    // Shutter & Triggers
    private RelativeLayout layoutSingleShutter;
    private FrameLayout btnShutterTrigger;
    private ImageView ivLastPhotoThumbnail;
    private LinearLayout layoutIntervalTrigger;
    private Button btnIntervalAction;

    // Core Controllers
    private PhotoSaver photoSaver;
    private CameraController cameraController;
    private IntervalometerController intervalometerController;
    private Vibrator vibrator;

    private enum ActiveDrawer {
        NONE, ISO, SHUTTER, FOCUS, WB
    }
    private ActiveDrawer currentDrawer = ActiveDrawer.NONE;
    private boolean isIntervalMode = false;

    private Uri lastSavedUri;
    private CountDownTimer exposureCountdownTimer;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        getWindow().addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O_MR1) {
            setShowWhenLocked(true);
            setTurnScreenOn(true);
        } else {
            getWindow().addFlags(
                    WindowManager.LayoutParams.FLAG_SHOW_WHEN_LOCKED |
                    WindowManager.LayoutParams.FLAG_TURN_SCREEN_ON
            );
        }
        setContentView(R.layout.activity_main);

        android.util.Log.i("AstroCam", "MainActivity onCreate started");

        vibrator = (Vibrator) getSystemService(Context.VIBRATOR_SERVICE);
        photoSaver = new PhotoSaver(this);
        cameraController = new CameraController(this, photoSaver);
        intervalometerController = new IntervalometerController(cameraController);

        initViews();
        setupListeners();
        updateHudDisplay();
    }

    private void initViews() {
        textureView = findViewById(R.id.texture_view);
        cameraController.setTextureView(textureView);
        textureView.setSurfaceTextureListener(surfaceTextureListener);

        btnLensSwitch = findViewById(R.id.btn_lens_switch);
        btnFormatSwitch = findViewById(R.id.btn_format_switch);
        tvHudStatus = findViewById(R.id.tv_hud_status);
        tvCountdownOverlay = findViewById(R.id.tv_countdown_overlay);
        tvCaptureStatus = findViewById(R.id.tv_capture_status);

        tabSingleMode = findViewById(R.id.tab_single_mode);
        tabIntervalometerMode = findViewById(R.id.tab_intervalometer_mode);

        btnSettingIso = findViewById(R.id.btn_setting_iso);
        tvValIso = findViewById(R.id.tv_val_iso);
        btnSettingShutter = findViewById(R.id.btn_setting_shutter);
        tvValShutter = findViewById(R.id.tv_val_shutter);
        btnSettingFocus = findViewById(R.id.btn_setting_focus);
        tvValFocus = findViewById(R.id.tv_val_focus);
        btnSettingWb = findViewById(R.id.btn_setting_wb);
        tvValWb = findViewById(R.id.tv_val_wb);

        panelDrawer = findViewById(R.id.panel_drawer);
        panelFocusControls = findViewById(R.id.panel_focus_controls);
        btnFocusAf = findViewById(R.id.btn_focus_af);
        btnFocusInfinity = findViewById(R.id.btn_focus_infinity);
        tvFocusSliderVal = findViewById(R.id.tv_focus_slider_val);
        sbFocus = findViewById(R.id.sb_focus);

        hsvChips = findViewById(R.id.hsv_chips);
        llChipsContainer = findViewById(R.id.ll_chips_container);

        panelIntervalometer = findViewById(R.id.panel_intervalometer);
        layoutIntervalConfig = findViewById(R.id.layout_interval_config);
        tvIntervalSeconds = findViewById(R.id.tv_interval_seconds);
        btnIntervalDec = findViewById(R.id.btn_interval_dec);
        btnIntervalInc = findViewById(R.id.btn_interval_inc);
        tvFramesCount = findViewById(R.id.tv_frames_count);
        btnFramesDec = findViewById(R.id.btn_frames_dec);
        btnFramesInc = findViewById(R.id.btn_frames_inc);
        chipFrames10 = findViewById(R.id.chip_frames_10);
        chipFrames50 = findViewById(R.id.chip_frames_50);
        chipFrames100 = findViewById(R.id.chip_frames_100);
        chipFramesInf = findViewById(R.id.chip_frames_inf);
        tvIntervalWarning = findViewById(R.id.tv_interval_warning);

        layoutIntervalRunning = findViewById(R.id.layout_interval_running);
        tvIntervalProgressFrames = findViewById(R.id.tv_interval_progress_frames);
        tvIntervalProgressTime = findViewById(R.id.tv_interval_progress_time);
        pbIntervalProgress = findViewById(R.id.pb_interval_progress);
        tvIntervalRunningStatus = findViewById(R.id.tv_interval_running_status);

        layoutSingleShutter = findViewById(R.id.layout_single_shutter);
        btnShutterTrigger = findViewById(R.id.btn_shutter_trigger);
        ivLastPhotoThumbnail = findViewById(R.id.iv_last_photo_thumbnail);
        layoutIntervalTrigger = findViewById(R.id.layout_interval_trigger);
        btnIntervalAction = findViewById(R.id.btn_interval_action);
    }

    private void setupListeners() {
        // Mode switching
        tabSingleMode.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                switchMode(false);
            }
        });

        tabIntervalometerMode.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                switchMode(true);
            }
        });

        // Lens switch dialog
        btnLensSwitch.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                showLensSelectorDialog();
            }
        });

        // Format toggle
        btnFormatSwitch.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                cycleCaptureFormat();
            }
        });

        // Quick settings drawer toggles
        btnSettingIso.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                toggleDrawer(ActiveDrawer.ISO);
            }
        });
        btnSettingShutter.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                toggleDrawer(ActiveDrawer.SHUTTER);
            }
        });
        btnSettingFocus.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                toggleDrawer(ActiveDrawer.FOCUS);
            }
        });
        btnSettingWb.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                toggleDrawer(ActiveDrawer.WB);
            }
        });

        // Focus controls
        btnFocusAf.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                cameraController.setAutoFocus();
                tvValFocus.setText("AF");
                tvFocusSliderVal.setText("Auto Focus (Continuous)");
                updateHudDisplay();
            }
        });

        btnFocusInfinity.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                cameraController.setInfinityFocus();
                sbFocus.setProgress(0);
                tvValFocus.setText("MF ∞");
                tvFocusSliderVal.setText("MF: 0.00 (Infinity)");
                updateHudDisplay();
            }
        });

        sbFocus.setOnSeekBarChangeListener(new SeekBar.OnSeekBarChangeListener() {
            @Override
            public void onProgressChanged(SeekBar seekBar, int progress, boolean fromUser) {
                if (!fromUser) return;
                CameraCapabilities caps = cameraController.getCurrentCapabilities();
                if (caps == null || !caps.isControllableFocusSupported()) return;

                Float minDistance = caps.getMinFocusDistance();
                if (minDistance == null || minDistance <= 0f) return;

                float diopters = (progress / 1000f) * minDistance;
                cameraController.setFocusDistance(diopters);

                if (diopters == 0.0f) {
                    tvValFocus.setText("MF ∞");
                    tvFocusSliderVal.setText("MF: 0.00 (Infinity)");
                } else {
                    tvValFocus.setText(String.format(Locale.US, "%.1fm", (diopters > 0 ? 1.0f / diopters : 99f)));
                    tvFocusSliderVal.setText(String.format(Locale.US, "MF: %.2f dpt (~%.2fm)", diopters, (1.0f / diopters)));
                }
                updateHudDisplay();
            }

            @Override
            public void onStartTrackingTouch(SeekBar seekBar) {}

            @Override
            public void onStopTrackingTouch(SeekBar seekBar) {}
        });

        // Intervalometer Settings Buttons
        btnIntervalDec.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                int cur = intervalometerController.getIntervalSeconds();
                if (cur > 1) {
                    intervalometerController.setIntervalSeconds(cur - 1);
                    tvIntervalSeconds.setText(intervalometerController.getIntervalSeconds() + "s");
                    checkIntervalSettingsWarning();
                }
            }
        });
        btnIntervalInc.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                int cur = intervalometerController.getIntervalSeconds();
                intervalometerController.setIntervalSeconds(cur + 1);
                tvIntervalSeconds.setText(intervalometerController.getIntervalSeconds() + "s");
                checkIntervalSettingsWarning();
            }
        });

        btnFramesDec.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                int cur = intervalometerController.getTotalFrames();
                if (cur > 5) {
                    intervalometerController.setTotalFrames(cur - 5);
                    tvFramesCount.setText(String.valueOf(intervalometerController.getTotalFrames()));
                }
            }
        });
        btnFramesInc.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                int cur = intervalometerController.getTotalFrames();
                if (cur == -1) cur = 0;
                intervalometerController.setTotalFrames(cur + 5);
                tvFramesCount.setText(String.valueOf(intervalometerController.getTotalFrames()));
            }
        });

        chipFrames10.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                intervalometerController.setTotalFrames(10);
                tvFramesCount.setText("10");
            }
        });
        chipFrames50.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                intervalometerController.setTotalFrames(50);
                tvFramesCount.setText("50");
            }
        });
        chipFrames100.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                intervalometerController.setTotalFrames(100);
                tvFramesCount.setText("100");
            }
        });
        chipFramesInf.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                intervalometerController.setTotalFrames(-1);
                tvFramesCount.setText("∞");
            }
        });

        // Single Shutter trigger
        btnShutterTrigger.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                triggerTactileHaptic();
                tvCaptureStatus.setText("Exposing…");
                tvCaptureStatus.setTextColor(ContextCompat.getColor(MainActivity.this, R.color.accent_amber));
                btnShutterTrigger.setEnabled(false);
                btnShutterTrigger.setAlpha(0.5f);
                cameraController.takePicture();
            }
        });

        // Intervalometer Start/Stop trigger
        btnIntervalAction.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                triggerTactileHaptic();
                if (intervalometerController.isRunning()) {
                    intervalometerController.stopSequence();
                } else {
                    intervalometerController.startSequence();
                }
            }
        });

        // Last photo thumbnail viewer
        ivLastPhotoThumbnail.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                if (lastSavedUri != null) {
                    Intent intent = new Intent(Intent.ACTION_VIEW);
                    intent.setDataAndType(lastSavedUri, "image/*");
                    intent.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION);
                    try {
                        startActivity(intent);
                    } catch (Exception e) {
                        Toast.makeText(MainActivity.this, "Cannot open image viewer: " + e.getMessage(), Toast.LENGTH_SHORT).show();
                    }
                }
            }
        });

        // CameraController callbacks
        cameraController.setEventListener(new CameraController.CameraEventListener() {
            @Override
            public void onCameraReady(CameraCapabilities capabilities) {
                btnLensSwitch.setText(capabilities.getDisplayName());
                updateHudDisplay();
                checkIntervalSettingsWarning();
            }

            @Override
            public void onPreviewFrameResult(int iso, long exposureNs, float focusDistance) {
                // Keep HUD synced
            }

            @Override
            public void onCaptureStarted(long estimatedDurationMs) {
                startExposureCountdown(estimatedDurationMs);
            }

            @Override
            public void onCaptureSaved(Uri uri, String filename, boolean isRaw) {
                lastSavedUri = uri;
                updateThumbnail(uri);
                Toast.makeText(MainActivity.this, "Saved: " + filename, Toast.LENGTH_SHORT).show();
            }

            @Override
            public void onCaptureFinished() {
                if (exposureCountdownTimer != null) {
                    exposureCountdownTimer.cancel();
                }
                tvCaptureStatus.setText("READY");
                tvCaptureStatus.setTextColor(ContextCompat.getColor(MainActivity.this, R.color.accent_green));
                btnShutterTrigger.setEnabled(true);
                btnShutterTrigger.setAlpha(1.0f);

                if (intervalometerController.isRunning()) {
                    intervalometerController.onCameraCaptureFinished();
                }
            }

            @Override
            public void onError(String message) {
                tvCaptureStatus.setText("ERROR");
                tvCaptureStatus.setTextColor(ContextCompat.getColor(MainActivity.this, R.color.accent_red));
                Toast.makeText(MainActivity.this, message, Toast.LENGTH_LONG).show();
                btnShutterTrigger.setEnabled(true);
                btnShutterTrigger.setAlpha(1.0f);
            }
        });

        // IntervalometerController callbacks
        intervalometerController.setListener(new IntervalometerController.IntervalometerListener() {
            @Override
            public void onStateChanged(IntervalometerController.State state) {
                switch (state) {
                    case IDLE:
                        layoutIntervalConfig.setVisibility(View.VISIBLE);
                        layoutIntervalRunning.setVisibility(View.GONE);
                        tvCountdownOverlay.setVisibility(View.GONE);
                        btnIntervalAction.setText("START SEQUENCE (3s Delay)");
                        btnIntervalAction.setBackgroundResource(R.drawable.bg_button_action);
                        break;
                    case COUNTDOWN:
                        layoutIntervalConfig.setVisibility(View.GONE);
                        layoutIntervalRunning.setVisibility(View.VISIBLE);
                        tvCountdownOverlay.setVisibility(View.VISIBLE);
                        btnIntervalAction.setText("STOP SEQUENCE");
                        btnIntervalAction.setBackgroundResource(R.drawable.bg_button_stop);
                        break;
                    case EXPOSING:
                    case INTERVAL_WAIT:
                    case SAVING:
                        layoutIntervalConfig.setVisibility(View.GONE);
                        layoutIntervalRunning.setVisibility(View.VISIBLE);
                        tvCountdownOverlay.setVisibility(View.GONE);
                        btnIntervalAction.setText("STOP SEQUENCE");
                        btnIntervalAction.setBackgroundResource(R.drawable.bg_button_stop);
                        break;
                    case STOPPING:
                        btnIntervalAction.setText("STOPPING…");
                        btnIntervalAction.setEnabled(false);
                        break;
                }
            }

            @Override
            public void onProgressUpdate(int currentFrame, int totalFrames, long elapsedMs, long estimatedRemainingMs, String statusMessage) {
                if (intervalometerController.getState() == IntervalometerController.State.COUNTDOWN) {
                    // Update big countdown text
                    tvCountdownOverlay.setText(statusMessage.contains("in ") ? statusMessage.replaceAll("[^0-9]", "") : "");
                }

                String frameStr = (totalFrames > 0) ? (currentFrame + " / " + totalFrames) : (currentFrame + " / ∞");
                tvIntervalProgressFrames.setText(frameStr);

                String elapsedStr = formatTime(elapsedMs);
                String remStr = (totalFrames > 0) ? formatTime(estimatedRemainingMs) : "--:--";
                tvIntervalProgressTime.setText("Elapsed: " + elapsedStr + " • Left: ~" + remStr);

                if (totalFrames > 0) {
                    int percent = (int) ((currentFrame / (float) totalFrames) * 100);
                    pbIntervalProgress.setProgress(percent);
                } else {
                    pbIntervalProgress.setProgress(50);
                }

                tvIntervalRunningStatus.setText(statusMessage);
            }

            @Override
            public void onSequenceFinished(int completedFrames, boolean cancelled) {
                btnIntervalAction.setEnabled(true);
                String msg = cancelled ? "Sequence cancelled after " + completedFrames + " frames." :
                        "Sequence complete! Captured " + completedFrames + " frames.";
                Toast.makeText(MainActivity.this, msg, Toast.LENGTH_LONG).show();
            }

            @Override
            public void onWarning(String warning) {
                tvIntervalWarning.setVisibility(View.VISIBLE);
                tvIntervalWarning.setText(warning);
            }
        });
    }

    private void switchMode(boolean intervalMode) {
        if (intervalometerController.isRunning()) {
            Toast.makeText(this, "Stop intervalometer sequence first.", Toast.LENGTH_SHORT).show();
            return;
        }

        this.isIntervalMode = intervalMode;
        if (intervalMode) {
            tabSingleMode.setBackgroundResource(R.drawable.bg_chip_normal);
            tabSingleMode.setTextColor(ContextCompat.getColor(this, R.color.text_secondary));
            tabIntervalometerMode.setBackgroundResource(R.drawable.bg_chip_selected);
            tabIntervalometerMode.setTextColor(ContextCompat.getColor(this, R.color.text_primary));

            panelIntervalometer.setVisibility(View.VISIBLE);
            layoutSingleShutter.setVisibility(View.GONE);
            layoutIntervalTrigger.setVisibility(View.VISIBLE);
            closeDrawer();
        } else {
            tabSingleMode.setBackgroundResource(R.drawable.bg_chip_selected);
            tabSingleMode.setTextColor(ContextCompat.getColor(this, R.color.text_primary));
            tabIntervalometerMode.setBackgroundResource(R.drawable.bg_chip_normal);
            tabIntervalometerMode.setTextColor(ContextCompat.getColor(this, R.color.text_secondary));

            panelIntervalometer.setVisibility(View.GONE);
            layoutSingleShutter.setVisibility(View.VISIBLE);
            layoutIntervalTrigger.setVisibility(View.GONE);
        }
    }

    private void toggleDrawer(ActiveDrawer drawer) {
        if (currentDrawer == drawer) {
            closeDrawer();
            return;
        }

        currentDrawer = drawer;
        panelDrawer.setVisibility(View.VISIBLE);
        llChipsContainer.removeAllViews();

        CameraCapabilities caps = cameraController.getCurrentCapabilities();
        if (caps == null) return;

        switch (drawer) {
            case ISO:
                panelFocusControls.setVisibility(View.GONE);
                hsvChips.setVisibility(View.VISIBLE);
                populateIsoChips(caps);
                break;
            case SHUTTER:
                panelFocusControls.setVisibility(View.GONE);
                hsvChips.setVisibility(View.VISIBLE);
                populateShutterChips(caps);
                break;
            case FOCUS:
                hsvChips.setVisibility(View.GONE);
                panelFocusControls.setVisibility(View.VISIBLE);
                setupFocusDrawer(caps);
                break;
            case WB:
                panelFocusControls.setVisibility(View.GONE);
                hsvChips.setVisibility(View.VISIBLE);
                populateWbChips(caps);
                break;
            default:
                closeDrawer();
                break;
        }
    }

    private void closeDrawer() {
        currentDrawer = ActiveDrawer.NONE;
        panelDrawer.setVisibility(View.GONE);
        panelFocusControls.setVisibility(View.GONE);
        hsvChips.setVisibility(View.GONE);
    }

    private void populateIsoChips(CameraCapabilities caps) {
        if (!caps.isManualSensorSupported()) {
            addDisabledNotice("Manual ISO not supported on this camera sensor.");
            return;
        }

        List<Integer> isos = caps.getSupportedIsoPresets();
        int activeIso = cameraController.getCurrentIso();

        for (final int iso : isos) {
            TextView chip = createChipView(iso == -1 ? "AUTO" : "ISO " + iso, iso == activeIso);
            chip.setOnClickListener(new View.OnClickListener() {
                @Override
                public void onClick(View v) {
                    cameraController.setIso(iso);
                    tvValIso.setText(iso == -1 ? "AUTO" : String.valueOf(iso));
                    updateHudDisplay();
                    closeDrawer();
                }
            });
            llChipsContainer.addView(chip);
        }
    }

    private void populateShutterChips(CameraCapabilities caps) {
        if (!caps.isManualSensorSupported()) {
            addDisabledNotice("Manual Shutter not supported on this camera sensor.");
            return;
        }

        List<CameraCapabilities.ShutterPreset> presets = caps.getSupportedShutterPresets();
        long activeNs = cameraController.getCurrentExposureNs();

        for (final CameraCapabilities.ShutterPreset preset : presets) {
            TextView chip = createChipView(preset.label, preset.exposureNs == activeNs);
            chip.setOnClickListener(new View.OnClickListener() {
                @Override
                public void onClick(View v) {
                    cameraController.setShutterSpeed(preset.exposureNs);
                    tvValShutter.setText(preset.label);
                    updateHudDisplay();
                    checkIntervalSettingsWarning();
                    closeDrawer();
                }
            });
            llChipsContainer.addView(chip);
        }
    }

    private void setupFocusDrawer(CameraCapabilities caps) {
        if (!caps.isControllableFocusSupported()) {
            btnFocusInfinity.setEnabled(false);
            sbFocus.setEnabled(false);
            tvFocusSliderVal.setText("Fixed focus camera (not controllable).");
            return;
        }

        btnFocusInfinity.setEnabled(true);
        sbFocus.setEnabled(true);
        float curFocus = cameraController.getCurrentFocusDistance();
        Float minDistance = caps.getMinFocusDistance();

        if (curFocus < 0f || minDistance == null || minDistance <= 0f) {
            sbFocus.setProgress(0);
            tvFocusSliderVal.setText("Auto Focus (Continuous)");
        } else {
            int progress = (int) ((curFocus / minDistance) * 1000);
            sbFocus.setProgress(progress);
            if (curFocus == 0.0f) {
                tvFocusSliderVal.setText("MF: 0.00 (Infinity)");
            } else {
                tvFocusSliderVal.setText(String.format(Locale.US, "MF: %.2f dpt (~%.2fm)", curFocus, (1.0f / curFocus)));
            }
        }
    }

    private void populateWbChips(CameraCapabilities caps) {
        int[] awbModes = caps.getSupportedAwbModes();
        int activeMode = cameraController.getCurrentAwbMode();

        for (final int mode : awbModes) {
            String label = getAwbModeName(mode);
            TextView chip = createChipView(label, mode == activeMode);
            chip.setOnClickListener(new View.OnClickListener() {
                @Override
                public void onClick(View v) {
                    cameraController.setAwbMode(mode);
                    tvValWb.setText(getAwbShortName(mode));
                    updateHudDisplay();
                    closeDrawer();
                }
            });
            llChipsContainer.addView(chip);
        }
    }

    private String getAwbModeName(int mode) {
        switch (mode) {
            case CameraMetadata.CONTROL_AWB_MODE_AUTO: return "Auto";
            case CameraMetadata.CONTROL_AWB_MODE_DAYLIGHT: return "Daylight (5500K)";
            case CameraMetadata.CONTROL_AWB_MODE_CLOUDY_DAYLIGHT: return "Cloudy (6500K)";
            case CameraMetadata.CONTROL_AWB_MODE_SHADE: return "Shade (7500K)";
            case CameraMetadata.CONTROL_AWB_MODE_INCANDESCENT: return "Tungsten (3000K)";
            case CameraMetadata.CONTROL_AWB_MODE_FLUORESCENT: return "Fluorescent (4000K)";
            case CameraMetadata.CONTROL_AWB_MODE_TWILIGHT: return "Twilight";
            default: return "AWB " + mode;
        }
    }

    private String getAwbShortName(int mode) {
        switch (mode) {
            case CameraMetadata.CONTROL_AWB_MODE_AUTO: return "AUTO";
            case CameraMetadata.CONTROL_AWB_MODE_DAYLIGHT: return "Daylight";
            case CameraMetadata.CONTROL_AWB_MODE_CLOUDY_DAYLIGHT: return "Cloudy";
            case CameraMetadata.CONTROL_AWB_MODE_SHADE: return "Shade";
            case CameraMetadata.CONTROL_AWB_MODE_INCANDESCENT: return "Tungsten";
            case CameraMetadata.CONTROL_AWB_MODE_FLUORESCENT: return "Fluores.";
            case CameraMetadata.CONTROL_AWB_MODE_TWILIGHT: return "Twilight";
            default: return "WB";
        }
    }

    private TextView createChipView(String text, boolean isSelected) {
        TextView tv = new TextView(this);
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.WRAP_CONTENT,
                (int) (36 * getResources().getDisplayMetrics().density)
        );
        lp.setMargins(0, 0, (int) (8 * getResources().getDisplayMetrics().density), 0);
        tv.setLayoutParams(lp);
        tv.setPadding((int) (14 * getResources().getDisplayMetrics().density), 0, (int) (14 * getResources().getDisplayMetrics().density), 0);
        tv.setGravity(android.view.Gravity.CENTER);
        tv.setText(text);
        tv.setTextSize(13);
        tv.setTextColor(ContextCompat.getColor(this, isSelected ? R.color.text_primary : R.color.text_secondary));
        tv.setBackgroundResource(isSelected ? R.drawable.bg_chip_selected : R.drawable.bg_chip_normal);
        return tv;
    }

    private void addDisabledNotice(String msg) {
        TextView tv = new TextView(this);
        tv.setText(msg);
        tv.setTextColor(ContextCompat.getColor(this, R.color.accent_amber));
        tv.setTextSize(12);
        tv.setPadding(16, 16, 16, 16);
        llChipsContainer.addView(tv);
    }

    private void cycleCaptureFormat() {
        CameraCapabilities caps = cameraController.getCurrentCapabilities();
        if (caps == null) return;

        if (!caps.isRawSupported()) {
            cameraController.setCaptureFormat(CameraController.CaptureFormat.JPEG);
            btnFormatSwitch.setText("JPEG");
            Toast.makeText(this, "This camera does not support RAW capture.", Toast.LENGTH_SHORT).show();
            updateHudDisplay();
            return;
        }

        CameraController.CaptureFormat current = cameraController.getCaptureFormat();
        switch (current) {
            case RAW_AND_JPEG:
                cameraController.setCaptureFormat(CameraController.CaptureFormat.RAW);
                btnFormatSwitch.setText("RAW (DNG)");
                break;
            case RAW:
                cameraController.setCaptureFormat(CameraController.CaptureFormat.JPEG);
                btnFormatSwitch.setText("JPEG");
                break;
            case JPEG:
            default:
                cameraController.setCaptureFormat(CameraController.CaptureFormat.RAW_AND_JPEG);
                btnFormatSwitch.setText("RAW + JPG");
                break;
        }
        updateHudDisplay();
    }

    private void showLensSelectorDialog() {
        final List<CameraCapabilities> cameras = cameraController.getAvailableCameras();
        if (cameras.isEmpty()) {
            Toast.makeText(this, "No cameras detected.", Toast.LENGTH_SHORT).show();
            return;
        }

        String[] names = new String[cameras.size()];
        int selectedIndex = 0;
        String curId = cameraController.getCurrentCapabilities() != null ?
                cameraController.getCurrentCapabilities().getCameraId() : "";

        for (int i = 0; i < cameras.size(); i++) {
            names[i] = cameras.get(i).getDisplayName();
            if (cameras.get(i).getCameraId().equals(curId)) {
                selectedIndex = i;
            }
        }

        new AlertDialog.Builder(this)
                .setTitle("Select Camera Lens")
                .setSingleChoiceItems(names, selectedIndex, new DialogInterface.OnClickListener() {
                    @Override
                    public void onClick(DialogInterface dialog, int which) {
                        CameraCapabilities chosen = cameras.get(which);
                        cameraController.switchCamera(chosen.getCameraId());
                        btnLensSwitch.setText(chosen.getDisplayName());
                        closeDrawer();
                        dialog.dismiss();
                    }
                })
                .setNegativeButton("Cancel", null)
                .show();
    }

    private void updateHudDisplay() {
        int iso = cameraController.getCurrentIso();
        String isoStr = (iso > 0) ? "ISO " + iso : "ISO AUTO";

        long expNs = cameraController.getCurrentExposureNs();
        String expStr;
        if (expNs > 0) {
            if (expNs >= 1_000_000_000L) {
                expStr = (expNs / 1_000_000_000L) + "s";
            } else {
                long denom = Math.round(1_000_000_000.0 / expNs);
                expStr = "1/" + denom + "s";
            }
        } else {
            expStr = "AUTO";
        }

        float focus = cameraController.getCurrentFocusDistance();
        String focusStr;
        if (focus < 0f) {
            focusStr = "AF";
        } else if (focus == 0.0f) {
            focusStr = "MF ∞";
        } else {
            focusStr = String.format(Locale.US, "MF %.1fm", (1.0f / focus));
        }

        String formatStr;
        switch (cameraController.getCaptureFormat()) {
            case RAW: formatStr = "RAW"; break;
            case JPEG: formatStr = "JPEG"; break;
            case RAW_AND_JPEG: default: formatStr = "RAW+JPG"; break;
        }

        tvHudStatus.setText(isoStr + " • " + expStr + " • " + focusStr + " • " + formatStr);
    }

    private void checkIntervalSettingsWarning() {
        String warning = intervalometerController.validateSettings();
        if (warning != null) {
            tvIntervalWarning.setVisibility(View.VISIBLE);
            tvIntervalWarning.setText(warning);
        } else {
            tvIntervalWarning.setVisibility(View.GONE);
        }
    }

    private void startExposureCountdown(long totalDurationMs) {
        if (exposureCountdownTimer != null) {
            exposureCountdownTimer.cancel();
        }

        if (totalDurationMs >= 1000) {
            exposureCountdownTimer = new CountDownTimer(totalDurationMs, 200) {
                @Override
                public void onTick(long millisUntilFinished) {
                    double secLeft = millisUntilFinished / 1000.0;
                    tvCaptureStatus.setText(String.format(Locale.US, "Exposing… %.1fs", secLeft));
                }

                @Override
                public void onFinish() {
                    tvCaptureStatus.setText("Saving…");
                }
            }.start();
        } else {
            tvCaptureStatus.setText("Processing…");
        }
    }

    private void updateThumbnail(Uri imageUri) {
        try {
            InputStream is = getContentResolver().openInputStream(imageUri);
            if (is != null) {
                BitmapFactory.Options opts = new BitmapFactory.Options();
                opts.inSampleSize = 8; // downsample for memory efficiency
                Bitmap bm = BitmapFactory.decodeStream(is, null, opts);
                is.close();
                if (bm != null) {
                    ivLastPhotoThumbnail.setImageBitmap(bm);
                }
            }
        } catch (Exception ignored) {}
    }

    private String formatTime(long millis) {
        long sec = (millis / 1000) % 60;
        long min = (millis / (1000 * 60)) % 60;
        long hours = (millis / (1000 * 60 * 60));
        if (hours > 0) {
            return String.format(Locale.US, "%02d:%02d:%02d", hours, min, sec);
        } else {
            return String.format(Locale.US, "%02d:%02d", min, sec);
        }
    }

    private void triggerTactileHaptic() {
        if (vibrator != null && vibrator.hasVibrator()) {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                vibrator.vibrate(VibrationEffect.createOneShot(40, VibrationEffect.DEFAULT_AMPLITUDE));
            } else {
                vibrator.vibrate(40);
            }
        }
    }

    @Override
    protected void onResume() {
        super.onResume();
        cameraController.startBackgroundThread();

        boolean perm = hasRequiredPermissions();
        boolean texAvail = textureView != null && textureView.isAvailable();
        android.util.Log.i("AstroCam", "onResume: hasPerm=" + perm + ", texAvail=" + texAvail);

        if (perm) {
            if (texAvail) {
                cameraController.openCamera();
            }
        } else {
            requestRequiredPermissions();
        }
    }

    @Override
    protected void onPause() {
        if (intervalometerController.isRunning()) {
            intervalometerController.stopSequence();
        }
        cameraController.closeCamera();
        cameraController.stopBackgroundThread();
        super.onPause();
    }

    @Override
    protected void onDestroy() {
        photoSaver.release();
        super.onDestroy();
    }

    private final TextureView.SurfaceTextureListener surfaceTextureListener = new TextureView.SurfaceTextureListener() {
        @Override
        public void onSurfaceTextureAvailable(@NonNull SurfaceTexture surface, int width, int height) {
            cameraController.openCamera();
        }

        @Override
        public void onSurfaceTextureSizeChanged(@NonNull SurfaceTexture surface, int width, int height) {}

        @Override
        public boolean onSurfaceTextureDestroyed(@NonNull SurfaceTexture surface) {
            return true;
        }

        @Override
        public void onSurfaceTextureUpdated(@NonNull SurfaceTexture surface) {}
    };

    private boolean hasRequiredPermissions() {
        boolean camera = ContextCompat.checkSelfPermission(this, Manifest.permission.CAMERA) == PackageManager.PERMISSION_GRANTED;
        if (Build.VERSION.SDK_INT <= Build.VERSION_CODES.P) {
            boolean storage = ContextCompat.checkSelfPermission(this, Manifest.permission.WRITE_EXTERNAL_STORAGE) == PackageManager.PERMISSION_GRANTED;
            return camera && storage;
        }
        return camera;
    }

    private void requestRequiredPermissions() {
        List<String> list = new ArrayList<>();
        list.add(Manifest.permission.CAMERA);
        if (Build.VERSION.SDK_INT <= Build.VERSION_CODES.P) {
            list.add(Manifest.permission.WRITE_EXTERNAL_STORAGE);
        }
        ActivityCompat.requestPermissions(this, list.toArray(new String[0]), REQUEST_PERMISSIONS_CODE);
    }

    @Override
    public void onRequestPermissionsResult(int requestCode, @NonNull String[] permissions, @NonNull int[] grantResults) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults);
        if (requestCode == REQUEST_PERMISSIONS_CODE) {
            if (hasRequiredPermissions()) {
                if (textureView.isAvailable()) {
                    cameraController.openCamera();
                } else {
                    textureView.setSurfaceTextureListener(surfaceTextureListener);
                }
            } else {
                Toast.makeText(this, "Camera permission is necessary for AstroCam to operate.", Toast.LENGTH_LONG).show();
            }
        }
    }
}
