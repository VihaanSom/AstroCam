package com.astrocam;

import android.content.ContentResolver;
import android.content.ContentValues;
import android.content.Context;
import android.hardware.camera2.CameraCharacteristics;
import android.hardware.camera2.CaptureResult;
import android.hardware.camera2.DngCreator;
import android.media.Image;
import android.net.Uri;
import android.os.Build;
import android.os.Environment;
import android.os.Handler;
import android.os.Looper;
import android.provider.MediaStore;
import android.util.Log;
import androidx.exifinterface.media.ExifInterface;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.OutputStream;
import java.nio.ByteBuffer;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Locale;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public class PhotoSaver {

    private static final String TAG = "PhotoSaver";
    private static final String FOLDER_NAME = "DCIM/AstroCam";

    private final Context context;
    private final ExecutorService diskExecutor;
    private final Handler mainHandler;

    public interface OnSaveListener {
        void onPhotoSaved(Uri uri, String filename, boolean isRaw, long sizeBytes);
        void onError(String error, Throwable t);
    }

    public PhotoSaver(Context context) {
        this.context = context.getApplicationContext();
        this.diskExecutor = Executors.newSingleThreadExecutor();
        this.mainHandler = new Handler(Looper.getMainLooper());
    }

    public void saveJpeg(final Image image, final int orientation, final OnSaveListener listener) {
        diskExecutor.execute(new Runnable() {
            @Override
            public void run() {
                String timestamp = new SimpleDateFormat("yyyyMMdd_HHmmss_SSS", Locale.US).format(new Date());
                String filename = "AstroCam_" + timestamp + ".jpg";

                Uri fileUri = null;
                OutputStream out = null;
                boolean success = false;
                long writtenBytes = 0;

                try {
                    ByteBuffer buffer = image.getPlanes()[0].getBuffer();
                    byte[] bytes = new byte[buffer.remaining()];
                    buffer.get(bytes);

                    ContentResolver resolver = context.getContentResolver();
                    ContentValues values = new ContentValues();
                    values.put(MediaStore.Images.Media.DISPLAY_NAME, filename);
                    values.put(MediaStore.Images.Media.MIME_TYPE, "image/jpeg");
                    values.put(MediaStore.Images.Media.ORIENTATION, orientation);

                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                        values.put(MediaStore.Images.Media.RELATIVE_PATH, FOLDER_NAME);
                        values.put(MediaStore.Images.Media.IS_PENDING, 1);
                        fileUri = resolver.insert(MediaStore.Images.Media.EXTERNAL_CONTENT_URI, values);
                        if (fileUri != null) {
                            out = resolver.openOutputStream(fileUri);
                        }
                    } else {
                        File dcim = Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DCIM);
                        File astroDir = new File(dcim, "AstroCam");
                        if (!astroDir.exists()) {
                            astroDir.mkdirs();
                        }
                        File file = new File(astroDir, filename);
                        values.put(MediaStore.Images.Media.DATA, file.getAbsolutePath());
                        out = new FileOutputStream(file);
                        fileUri = resolver.insert(MediaStore.Images.Media.EXTERNAL_CONTENT_URI, values);
                    }

                    if (out != null) {
                        out.write(bytes);
                        out.flush();
                        writtenBytes = bytes.length;
                        success = true;
                    }

                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q && fileUri != null) {
                        values.clear();
                        values.put(MediaStore.Images.Media.IS_PENDING, 0);
                        resolver.update(fileUri, values, null, null);
                    }

                } catch (Exception e) {
                    Log.e(TAG, "Error saving JPEG image", e);
                    postError(listener, "Failed to save JPEG: " + e.getMessage(), e);
                    return;
                } finally {
                    if (out != null) {
                        try {
                            out.close();
                        } catch (IOException ignored) {}
                    }
                    image.close();
                }

                if (success && fileUri != null) {
                    final Uri finalUri = fileUri;
                    final long finalBytes = writtenBytes;
                    mainHandler.post(new Runnable() {
                        @Override
                        public void run() {
                            if (listener != null) {
                                listener.onPhotoSaved(finalUri, filename, false, finalBytes);
                            }
                        }
                    });
                } else {
                    postError(listener, "Failed to write JPEG file to storage.", null);
                }
            }
        });
    }

    public void saveRawDng(final Image rawImage,
                           final CameraCharacteristics characteristics,
                           final CaptureResult captureResult,
                           final int orientation,
                           final OnSaveListener listener) {
        diskExecutor.execute(new Runnable() {
            @Override
            public void run() {
                String timestamp = new SimpleDateFormat("yyyyMMdd_HHmmss_SSS", Locale.US).format(new Date());
                String filename = "AstroCam_" + timestamp + ".dng";

                Uri fileUri = null;
                OutputStream out = null;
                DngCreator dngCreator = null;
                boolean success = false;
                long writtenBytes = 0;

                try {
                    int exifOrientation = ExifInterface.ORIENTATION_NORMAL;
                    switch (orientation) {
                        case 90:
                            exifOrientation = ExifInterface.ORIENTATION_ROTATE_90;
                            break;
                        case 180:
                            exifOrientation = ExifInterface.ORIENTATION_ROTATE_180;
                            break;
                        case 270:
                            exifOrientation = ExifInterface.ORIENTATION_ROTATE_270;
                            break;
                        default:
                            exifOrientation = ExifInterface.ORIENTATION_NORMAL;
                            break;
                    }

                    dngCreator = new DngCreator(characteristics, captureResult);
                    dngCreator.setOrientation(exifOrientation);

                    ContentResolver resolver = context.getContentResolver();
                    ContentValues values = new ContentValues();
                    values.put(MediaStore.Images.Media.DISPLAY_NAME, filename);
                    values.put(MediaStore.Images.Media.MIME_TYPE, "image/x-adobe-dng");

                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                        values.put(MediaStore.Images.Media.RELATIVE_PATH, FOLDER_NAME);
                        values.put(MediaStore.Images.Media.IS_PENDING, 1);
                        fileUri = resolver.insert(MediaStore.Images.Media.EXTERNAL_CONTENT_URI, values);
                        if (fileUri != null) {
                            out = resolver.openOutputStream(fileUri);
                        }
                    } else {
                        File dcim = Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DCIM);
                        File astroDir = new File(dcim, "AstroCam");
                        if (!astroDir.exists()) {
                            astroDir.mkdirs();
                        }
                        File file = new File(astroDir, filename);
                        values.put(MediaStore.Images.Media.DATA, file.getAbsolutePath());
                        out = new FileOutputStream(file);
                        fileUri = resolver.insert(MediaStore.Images.Media.EXTERNAL_CONTENT_URI, values);
                    }

                    if (out != null) {
                        dngCreator.writeImage(out, rawImage);
                        out.flush();
                        success = true;
                    }

                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q && fileUri != null) {
                        values.clear();
                        values.put(MediaStore.Images.Media.IS_PENDING, 0);
                        resolver.update(fileUri, values, null, null);
                    }

                } catch (Exception e) {
                    Log.e(TAG, "Error saving RAW DNG image", e);
                    postError(listener, "Failed to save RAW (DNG): " + e.getMessage(), e);
                    return;
                } finally {
                    if (dngCreator != null) {
                        try {
                            dngCreator.close();
                        } catch (Exception ignored) {}
                    }
                    if (out != null) {
                        try {
                            out.close();
                        } catch (IOException ignored) {}
                    }
                    rawImage.close();
                }

                if (success && fileUri != null) {
                    final Uri finalUri = fileUri;
                    mainHandler.post(new Runnable() {
                        @Override
                        public void run() {
                            if (listener != null) {
                                listener.onPhotoSaved(finalUri, filename, true, 0);
                            }
                        }
                    });
                } else {
                    postError(listener, "Failed to write RAW DNG to storage.", null);
                }
            }
        });
    }

    private void postError(final OnSaveListener listener, final String msg, final Throwable t) {
        mainHandler.post(new Runnable() {
            @Override
            public void run() {
                if (listener != null) {
                    listener.onError(msg, t);
                }
            }
        });
    }

    public void release() {
        diskExecutor.shutdown();
    }
}
