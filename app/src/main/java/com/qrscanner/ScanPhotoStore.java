package com.qrscanner;

import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.media.MediaScannerConnection;
import android.os.Build;
import android.os.Environment;

import java.io.File;
import java.io.FileOutputStream;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.Date;
import java.util.List;
import java.util.Locale;

public final class ScanPhotoStore {

    private static final String DIR_NAME = "QRScanner";
    private static final int JPEG_QUALITY = 90;
    private static final int MAX_LIST = 500;

    private ScanPhotoStore() {}

    /** 照片目录：优先应用专属外置目录，其次内置私有目录。 */
    public static File photoDir(Context context) {
        File base = context.getExternalFilesDir(Environment.DIRECTORY_PICTURES);
        if (base == null) base = new File(context.getFilesDir(), Environment.DIRECTORY_PICTURES);
        return new File(base, DIR_NAME);
    }

    /** 供界面展示的可读路径。 */
    public static String displayPath(Context context) {
        return photoDir(context).getAbsolutePath();
    }

    public static String save(Context context, Bitmap bitmap) {
        if (bitmap == null) return "";
        File dir = photoDir(context);
        if (!dir.exists() && !dir.mkdirs()) return "";
        String stamp = new SimpleDateFormat("yyyyMMdd_HHmmss_SSS", Locale.getDefault())
            .format(new Date());
        File file = new File(dir, stamp + ".jpg");
        try (FileOutputStream out = new FileOutputStream(file)) {
            if (!bitmap.compress(Bitmap.CompressFormat.JPEG, JPEG_QUALITY, out)) return "";
        } catch (Exception e) {
            return "";
        }
        notifyMediaScanner(context, file);
        return file.getAbsolutePath();
    }

    /** 让系统相册能索引到该文件（仅对外置公开目录有意义，这里做无害尝试）。 */
    private static void notifyMediaScanner(Context context, File file) {
        try {
            MediaScannerConnection.scanFile(context.getApplicationContext(),
                new String[]{file.getAbsolutePath()}, new String[]{"image/jpeg"}, null);
        } catch (Exception ignored) {
        }
    }

    /** 按时间倒序列出照片，最新的在前。 */
    public static List<File> list(Context context) {
        List<File> result = new ArrayList<>();
        File dir = photoDir(context);
        File[] files = dir.listFiles();
        if (files == null) return result;
        for (File f : files) {
            if (f.isFile() && isImage(f.getName())) result.add(f);
        }
        result.sort(new Comparator<File>() {
            @Override
            public int compare(File a, File b) {
                return Long.compare(b.lastModified(), a.lastModified());
            }
        });
        return result.size() > MAX_LIST ? new ArrayList<>(result.subList(0, MAX_LIST)) : result;
    }

    private static boolean isImage(String name) {
        String lower = name.toLowerCase(Locale.ROOT);
        return lower.endsWith(".jpg") || lower.endsWith(".jpeg")
            || lower.endsWith(".png") || lower.endsWith(".webp");
    }

    public static boolean exists(String path) {
        return path != null && !path.isEmpty() && new File(path).isFile();
    }

    public static boolean delete(String path) {
        return exists(path) && new File(path).delete();
    }

    public static int count(Context context) {
        return list(context).size();
    }

    /** 带采样缩放解码，避免大图 OOM。 */
    public static Bitmap decodeSampled(String path, int reqWidth, int reqHeight) {
        if (!exists(path)) return null;
        try {
            BitmapFactory.Options bounds = new BitmapFactory.Options();
            bounds.inJustDecodeBounds = true;
            BitmapFactory.decodeFile(path, bounds);
            if (bounds.outWidth <= 0 || bounds.outHeight <= 0) return null;

            int sample = 1;
            int w = bounds.outWidth;
            int h = bounds.outHeight;
            while (w / (sample * 2) >= reqWidth && h / (sample * 2) >= reqHeight) {
                sample *= 2;
            }

            BitmapFactory.Options opts = new BitmapFactory.Options();
            opts.inSampleSize = sample;
            return BitmapFactory.decodeFile(path, opts);
        } catch (OutOfMemoryError e) {
            return null;
        } catch (Exception e) {
            return null;
        }
    }

    /** 目标 SDK 判断：仅用于区分是否需要 legacy 外部目录处理。 */
    public static boolean usesLegacyStorage() {
        return Build.VERSION.SDK_INT < Build.VERSION_CODES.Q;
    }
}
