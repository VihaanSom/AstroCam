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
import android.view.KeyEvent;
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

    // ISO Controls
    private LinearLayout panelIsoControls;
    private Button btnIsoAuto;
    private TextView tvIsoSliderVal;
    private SeekBar sbIso;

    // Shutter Controls
    private LinearLayout panelShutterControls;
    private Button btnShutterAuto;
    private TextView tvShutterSliderVal;
    private SeekBar sbShutter;

    // Focus Controls
    private LinearLayout panelFocusControls;
    private Button btnFocusAf;
    private Button btnFocusInfinity;
    private TextView tvFocusSliderVal;
    private SeekBar sbFocus;

    // White Balance Controls
    private LinearLayout panelWbControls;
    private Button btnWbAuto;
    private TextView tvWbSliderVal;
    private SeekBar sbWb;

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

        panelIsoControls = findViewById(R.id.panel_iso_controls);
        btnIsoAuto = findViewById(R.id.btn_iso_auto);
        tvIsoSliderVal = findViewById(R.id.tv_iso_slider_val);
        sbIso = findViewById(R.id.sb_iso);

        panelShutterControls = findViewById(R.id.panel_shutter_controls);
        btnShutterAuto = findViewById(R.id.btn_shutter_auto);
        tvShutterSliderVal = findViewById(R.id.tv_shutter_slider_val);
        sbShutter = findViewById(R.id.sb_shutter);

        panelFocusControls = findViewById(R.id.panel_focus_controls);
        btnFocusAf = findViewById(R.id.btn_focus_af);
        btnFocusInfinity = findViewById(R.id.btn_focus_infinity);
        tvFocusSliderVal = findViewById(R.id.tv_focus_slider_val);
        sbFocus = findViewById(R.id.sb_focus);

        panelWbControls = findViewById(R.id.panel_wb_controls);
        btnWbAuto = findViewById(R.id.btn_wb_auto);
        tvWbSliderVal = findViewById(R.id.tv_wb_slider_val);
        sbWb = findViewById(R.id.sb_wb);

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

        // ISO Slider and Auto button
        sbIso.setOnSeekBarChangeListener(new SeekBar.OnSeekBarChangeListener() {
            @Override
            public void onProgressChanged(SeekBar seekBar, int progress, boolean fromUser) {
                if (!fromUser) return;
                CameraCapabilities caps = cameraController.getCurrentCapabilities();
                if (caps == null || !caps.isManualSensorSupported()) return;

                int minIso = caps.getMinIso();
                int maxIso = caps.getMaxIso();
                double logMin = Math.log(minIso);
                double logMax = Math.log(maxIso);
                double rawIso = Math.exp(logMin + (progress / 1000.0) * (logMax - logMin));

                int iso;
                if (rawIso < 500) {
                    iso = (int) (Math.round(rawIso / 10.0) * 10);
                } else if (rawIso < 2000) {
                    iso = (int) (Math.round(rawIso / 50.0) * 50);
                } else {
                    iso = (int) (Math.round(rawIso / 100.0) * 100);
                }
                iso = Math.max(minIso, Math.min(iso, maxIso));

                cameraController.setIso(iso);
                tvValIso.setText(String.valueOf(iso));
                tvIsoSliderVal.setText("ISO " + iso);

                btnIsoAuto.setBackgroundResource(R.drawable.bg_chip_normal);
                btnIsoAuto.setTextColor(ContextCompat.getColor(MainActivity.this, R.color.text_primary));
                updateHudDisplay();
            }

            @Override
            public void onStartTrackingTouch(SeekBar seekBar) {}

            @Override
            public void onStopTrackingTouch(SeekBar seekBar) {}
        });

        btnIsoAuto.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                cameraController.setIso(-1);
                tvValIso.setText("AUTO");
                tvIsoSliderVal.setText("AUTO");
                btnIsoAuto.setBackgroundResource(R.drawable.bg_chip_selected);
                btnIsoAuto.setTextColor(ContextCompat.getColor(MainActivity.this, R.color.accent_cyan));
                updateHudDisplay();
            }
        });

        // Shutter Slider and Auto button
        sbShutter.setOnSeekBarChangeListener(new SeekBar.OnSeekBarChangeListener() {
            @Override
            public void onProgressChanged(SeekBar seekBar, int progress, boolean fromUser) {
                if (!fromUser) return;
                CameraCapabilities caps = cameraController.getCurrentCapabilities();
                if (caps == null || !caps.isManualSensorSupported()) return;

                long minNs = caps.getMinExposureNs();
                long maxNs = caps.getMaxExposureNs();
                double logMin = Math.log(minNs);
                double logMax = Math.log(maxNs);
                long rawNs = (long) Math.exp(logMin + (progress / 1000.0) * (logMax - logMin));
                long snappedNs = snapShutterSpeed(rawNs, minNs, maxNs);

                cameraController.setShutterSpeed(snappedNs);
                String formatted = formatShutterSpeed(snappedNs);
                tvValShutter.setText(formatted);
                tvShutterSliderVal.setText(formatted);

                btnShutterAuto.setBackgroundResource(R.drawable.bg_chip_normal);
                btnShutterAuto.setTextColor(ContextCompat.getColor(MainActivity.this, R.color.text_primary));
                updateHudDisplay();
                checkIntervalSettingsWarning();
            }

            @Override
            public void onStartTrackingTouch(SeekBar seekBar) {}

            @Override
            public void onStopTrackingTouch(SeekBar seekBar) {}
        });

        btnShutterAuto.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                cameraController.setShutterSpeed(-1L);
                tvValShutter.setText("AUTO");
                tvShutterSliderVal.setText("AUTO");
                btnShutterAuto.setBackgroundResource(R.drawable.bg_chip_selected);
                btnShutterAuto.setTextColor(ContextCompat.getColor(MainActivity.this, R.color.accent_cyan));
                updateHudDisplay();
                checkIntervalSettingsWarning();
            }
        });

        // White Balance Slider and Auto button
        sbWb.setOnSeekBarChangeListener(new SeekBar.OnSeekBarChangeListener() {
            @Override
            public void onProgressChanged(SeekBar seekBar, int progress, boolean fromUser) {
                if (!fromUser) return;
                int kelvin = 2000 + (int) ((progress / 1000.0) * 6000);
                kelvin = (kelvin / 50) * 50;

                cameraController.setKelvin(kelvin);
                tvValWb.setText(kelvin + "K");
                tvWbSliderVal.setText(formatKelvinLabel(kelvin));

                btnWbAuto.setBackgroundResource(R.drawable.bg_chip_normal);
                btnWbAuto.setTextColor(ContextCompat.getColor(MainActivity.this, R.color.text_primary));
                updateHudDisplay();
            }

            @Override
            public void onStartTrackingTouch(SeekBar seekBar) {}

            @Override
            public void onStopTrackingTouch(SeekBar seekBar) {}
        });

        btnWbAuto.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                cameraController.setAwbAuto();
                tvValWb.setText("AUTO");
                tvWbSliderVal.setText("AUTO AWB");
                btnWbAuto.setBackgroundResource(R.drawable.bg_chip_selected);
                btnWbAuto.setTextColor(ContextCompat.getColor(MainActivity.this, R.color.accent_cyan));
                updateHudDisplay();
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
        panelIsoControls.setVisibility(View.GONE);
        panelShutterControls.setVisibility(View.GONE);
        panelFocusControls.setVisibility(View.GONE);
        panelWbControls.setVisibility(View.GONE);
        hsvChips.setVisibility(View.GONE);

        CameraCapabilities caps = cameraController.getCurrentCapabilities();
        if (caps == null) return;

        switch (drawer) {
            case ISO:
                panelIsoControls.setVisibility(View.VISIBLE);
                setupIsoDrawer(caps);
                break;
            case SHUTTER:
                panelShutterControls.setVisibility(View.VISIBLE);
                setupShutterDrawer(caps);
                break;
            case FOCUS:
                panelFocusControls.setVisibility(View.VISIBLE);
                setupFocusDrawer(caps);
                break;
            case WB:
                panelWbControls.setVisibility(View.VISIBLE);
                setupWbDrawer(caps);
                break;
            default:
                closeDrawer();
                break;
        }
    }

    private void closeDrawer() {
        currentDrawer = ActiveDrawer.NONE;
        panelDrawer.setVisibility(View.GONE);
        panelIsoControls.setVisibility(View.GONE);
        panelShutterControls.setVisibility(View.GONE);
        panelFocusControls.setVisibility(View.GONE);
        panelWbControls.setVisibility(View.GONE);
        hsvChips.setVisibility(View.GONE);
    }

    private void setupIsoDrawer(CameraCapabilities caps) {
        if (!caps.isManualSensorSupported()) {
            btnIsoAuto.setEnabled(false);
            sbIso.setEnabled(false);
            tvIsoSliderVal.setText("Manual ISO not supported");
            return;
        }

        btnIsoAuto.setEnabled(true);
        sbIso.setEnabled(true);

        int minIso = caps.getMinIso();
        int maxIso = caps.getMaxIso();
        int curIso = cameraController.getCurrentIso();

        if (curIso <= 0) {
            btnIsoAuto.setBackgroundResource(R.drawable.bg_chip_selected);
            btnIsoAuto.setTextColor(ContextCompat.getColor(this, R.color.accent_cyan));
            tvIsoSliderVal.setText("AUTO");

            double logMin = Math.log(minIso);
            double logMax = Math.log(maxIso);
            int defProgress = (int) Math.round(((Math.log(800) - logMin) / (logMax - logMin)) * 1000);
            sbIso.setProgress(Math.max(0, Math.min(1000, defProgress)));
        } else {
            btnIsoAuto.setBackgroundResource(R.drawable.bg_chip_normal);
            btnIsoAuto.setTextColor(ContextCompat.getColor(this, R.color.text_primary));
            tvIsoSliderVal.setText("ISO " + curIso);

            double logMin = Math.log(minIso);
            double logMax = Math.log(maxIso);
            int progress = (int) Math.round(((Math.log(curIso) - logMin) / (logMax - logMin)) * 1000);
            sbIso.setProgress(Math.max(0, Math.min(1000, progress)));
        }
    }

    private void setupShutterDrawer(CameraCapabilities caps) {
        if (!caps.isManualSensorSupported()) {
            btnShutterAuto.setEnabled(false);
            sbShutter.setEnabled(false);
            tvShutterSliderVal.setText("Manual Shutter not supported");
            return;
        }

        btnShutterAuto.setEnabled(true);
        sbShutter.setEnabled(true);

        long minNs = caps.getMinExposureNs();
        long maxNs = caps.getMaxExposureNs();
        long curExp = cameraController.getCurrentExposureNs();

        if (curExp <= 0) {
            btnShutterAuto.setBackgroundResource(R.drawable.bg_chip_selected);
            btnShutterAuto.setTextColor(ContextCompat.getColor(this, R.color.accent_cyan));
            tvShutterSliderVal.setText("AUTO");

            double logMin = Math.log(minNs);
            double logMax = Math.log(maxNs);
            int defProgress = (int) Math.round(((Math.log(16_666_666L) - logMin) / (logMax - logMin)) * 1000);
            sbShutter.setProgress(Math.max(0, Math.min(1000, defProgress)));
        } else {
            btnShutterAuto.setBackgroundResource(R.drawable.bg_chip_normal);
            btnShutterAuto.setTextColor(ContextCompat.getColor(this, R.color.text_primary));
            tvShutterSliderVal.setText(formatShutterSpeed(curExp));

            double logMin = Math.log(minNs);
            double logMax = Math.log(maxNs);
            int progress = (int) Math.round(((Math.log(curExp) - logMin) / (logMax - logMin)) * 1000);
            sbShutter.setProgress(Math.max(0, Math.min(1000, progress)));
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

    private void setupWbDrawer(CameraCapabilities caps) {
        btnWbAuto.setEnabled(true);
        sbWb.setEnabled(true);

        int curKelvin = cameraController.getCurrentKelvin();

        if (curKelvin <= 0) {
            btnWbAuto.setBackgroundResource(R.drawable.bg_chip_selected);
            btnWbAuto.setTextColor(ContextCompat.getColor(this, R.color.accent_cyan));
            tvWbSliderVal.setText("AUTO AWB");
            sbWb.setProgress(500);
        } else {
            btnWbAuto.setBackgroundResource(R.drawable.bg_chip_normal);
            btnWbAuto.setTextColor(ContextCompat.getColor(this, R.color.text_primary));
            tvWbSliderVal.setText(formatKelvinLabel(curKelvin));

            int progress = (int) Math.round(((curKelvin - 2000) / 6000.0) * 1000);
            sbWb.setProgress(Math.max(0, Math.min(1000, progress)));
        }
    }

    private String formatShutterSpeed(long ns) {
        if (ns <= 0) return "AUTO";
        if (ns >= 1_000_000_000L) {
            double sec = ns / 1_000_000_000.0;
            if (Math.abs(sec - Math.round(sec)) < 0.05) {
                return Math.round(sec) + "s";
            } else {
                return String.format(Locale.US, "%.1fs", sec);
            }
        } else {
            long denom = Math.round(1_000_000_000.0 / ns);
            return "1/" + denom + "s";
        }
    }

    private long snapShutterSpeed(long rawNs, long minNs, long maxNs) {
        if (rawNs >= 1_000_000_000L) {
            double sec = rawNs / 1_000_000_000.0;
            if (sec >= 10.0) {
                sec = Math.round(sec);
            } else if (sec >= 3.0) {
                sec = Math.round(sec * 2.0) / 2.0;
            } else {
                sec = Math.round(sec * 4.0) / 4.0;
            }
            long snapped = (long) (sec * 1_000_000_000L);
            return Math.max(minNs, Math.min(snapped, maxNs));
        } else {
            double denom = 1_000_000_000.0 / rawNs;
            long cleanDenom;
            if (denom >= 1000) {
                cleanDenom = Math.round(denom / 250.0) * 250;
            } else if (denom >= 200) {
                cleanDenom = Math.round(denom / 50.0) * 50;
            } else if (denom >= 50) {
                cleanDenom = Math.round(denom / 10.0) * 10;
            } else if (denom >= 10) {
                cleanDenom = Math.round(denom / 2.0) * 2;
            } else {
                cleanDenom = Math.round(denom);
            }
            if (cleanDenom <= 0) cleanDenom = 1;
            long snapped = 1_000_000_000L / cleanDenom;
            return Math.max(minNs, Math.min(snapped, maxNs));
        }
    }

    private String formatKelvinLabel(int kelvin) {
        String desc;
        if (kelvin < 2600) desc = "Candle";
        else if (kelvin < 3400) desc = "Tungsten";
        else if (kelvin < 4500) desc = "Fluores.";
        else if (kelvin < 5800) desc = "Daylight";
        else if (kelvin < 6800) desc = "Cloudy";
        else if (kelvin < 7800) desc = "Shade";
        else desc = "Deep Shade";
        return kelvin + "K (" + desc + ")";
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
        String expStr = (expNs > 0) ? formatShutterSpeed(expNs) : "AUTO";

        float focus = cameraController.getCurrentFocusDistance();
        String focusStr;
        if (focus < 0f) {
            focusStr = "AF";
        } else if (focus == 0.0f) {
            focusStr = "MF ∞";
        } else {
            focusStr = String.format(Locale.US, "MF %.1fm", (1.0f / focus));
        }

        int kelvin = cameraController.getCurrentKelvin();
        String wbStr = (kelvin > 0) ? kelvin + "K" : "WB AUTO";

        String formatStr;
        switch (cameraController.getCaptureFormat()) {
            case RAW: formatStr = "RAW"; break;
            case JPEG: formatStr = "JPEG"; break;
            case RAW_AND_JPEG: default: formatStr = "RAW+JPG"; break;
        }

        tvHudStatus.setText(isoStr + " • " + expStr + " • " + focusStr + " • " + wbStr + " • " + formatStr);
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
    public boolean dispatchKeyEvent(KeyEvent event) {
        int keyCode = event.getKeyCode();
        if (keyCode == KeyEvent.KEYCODE_VOLUME_DOWN || keyCode == KeyEvent.KEYCODE_VOLUME_UP) {
            if (event.getAction() == KeyEvent.ACTION_DOWN) {
                if (event.getRepeatCount() == 0) {
                    triggerCaptureFromHardwareButton();
                }
                return true;
            } else if (event.getAction() == KeyEvent.ACTION_UP) {
                return true;
            }
        }
        return super.dispatchKeyEvent(event);
    }

    private void triggerCaptureFromHardwareButton() {
        if (isIntervalMode) {
            if (btnIntervalAction != null && btnIntervalAction.isEnabled()) {
                btnIntervalAction.performClick();
            }
        } else {
            if (btnShutterTrigger != null && btnShutterTrigger.isEnabled()) {
                btnShutterTrigger.performClick();
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
