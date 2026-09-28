package com.qrscanner;

import android.app.Activity;
import android.content.ContentValues;
import android.content.Context;
import android.content.Intent;
import android.graphics.Bitmap;
import android.net.Uri;
import android.os.Build;
import android.os.Environment;
import android.os.Handler;
import android.os.Looper;
import android.provider.MediaStore;
import android.widget.Toast;

import androidx.appcompat.app.AlertDialog;
import androidx.core.content.FileProvider;

import org.apache.poi.ss.usermodel.BorderStyle;
import org.apache.poi.ss.usermodel.Cell;
import org.apache.poi.ss.usermodel.CellStyle;
import org.apache.poi.ss.usermodel.FillPatternType;
import org.apache.poi.ss.usermodel.Font;
import org.apache.poi.ss.usermodel.HorizontalAlignment;
import org.apache.poi.ss.usermodel.IndexedColors;
import org.apache.poi.ss.usermodel.Row;
import org.apache.poi.ss.usermodel.Sheet;
import org.apache.poi.ss.usermodel.Workbook;
import org.apache.poi.xssf.usermodel.XSSFClientAnchor;
import org.apache.poi.xssf.usermodel.XSSFDrawing;
import org.apache.poi.xssf.usermodel.XSSFSheet;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;

import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.OutputStream;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.Locale;

public final class ExcelExporter {

    public static final int MAX_IMAGES = 400;

    private static final int BARCODE_WIDTH = 600;
    private static final int BARCODE_HEIGHT = 200;
    private static final int QR_SIZE = 400;

    private static final int IMAGE_COL = 3;
    private static final int IMAGE_COL_WIDTH = 45;
    private static final short BARCODE_ROW_HEIGHT = 1600;
    private static final short QR_ROW_HEIGHT = 4800;

    private static final String MIME =
        "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet";

    private static final Handler MAIN = new Handler(Looper.getMainLooper());

    private interface Job {
        void run() throws Exception;
    }

    private ExcelExporter() {}

    public static boolean requiresImageConfirm(int count) {
        return count > MAX_IMAGES;
    }

    // ======================== 导出入口 ========================

    public static void exportScanRecords(Context context, List<ScanRecord> records, String projectName) {
        final List<ScanRecord> snapshot = new ArrayList<>(records);
        final String name = projectName;
        run(context, () -> {
            try (Workbook workbook = new XSSFWorkbook()) {
                buildScanRecords(workbook, context, snapshot);
                publish(context, workbook, name);
            }
        });
    }

    public static void exportCodes(Context context, String format, List<String> items, boolean qr) {
        if (items == null || items.isEmpty()) return;
        final List<String> snapshot = new ArrayList<>(items);
        final String name = format;
        final boolean isQr = qr;
        run(context, () -> {
            try (Workbook workbook = new XSSFWorkbook()) {
                buildCodes(workbook, context, name, snapshot, isQr);
                publish(context, workbook, isQr ? "QRCodes" : "Barcodes");
            }
        });
    }

    public static void exportGenerateRecords(Context context, List<GenerateRecord> records) {
        final List<GenerateRecord> snapshot = new ArrayList<>(records);
        run(context, () -> {
            try (Workbook workbook = new XSSFWorkbook()) {
                buildGenerateRecords(workbook, context, snapshot);
                publish(context, workbook, "GenerateRecords");
            }
        });
    }

    // ======================== 工作表 ========================

    private static void buildScanRecords(Workbook workbook, Context context, List<ScanRecord> records) {
        Sheet sheet = workbook.createSheet("Scan Records");
        sheet.setColumnWidth(0, 8 * 256);
        sheet.setColumnWidth(1, 50 * 256);
        sheet.setColumnWidth(2, 20 * 256);
        sheet.setColumnWidth(3, 22 * 256);
        sheet.setColumnWidth(4, 30 * 256);

        CellStyle headerStyle = headerStyle(workbook);
        CellStyle evenStyle = bodyStyle(workbook, true);
        CellStyle oddStyle = bodyStyle(workbook, false);

        Row headerRow = sheet.createRow(0);
        String[] headers = {"#", "QR/Barcode Content", "Format", "Scan Time", "Remark"};
        for (int i = 0; i < headers.length; i++) {
            Cell cell = headerRow.createCell(i);
            cell.setCellValue(headers[i]);
            cell.setCellStyle(headerStyle);
        }
        headerRow.setHeight((short) 500);

        List<ScanRecord> sorted = new ArrayList<>(records);
        sorted.sort((a, b) -> a.getSeq() - b.getSeq());
        for (int i = 0; i < sorted.size(); i++) {
            ScanRecord r = sorted.get(i);
            Row row = sheet.createRow(i + 1);
            CellStyle style = (i % 2 == 0) ? oddStyle : evenStyle;
            row.setHeight((short) 400);
            cell(row, 0, String.valueOf(r.getSeq()), style);
            cell(row, 1, r.getContent(), style);
            cell(row, 2, formatLabel(context, r.getFormat()), style);
            cell(row, 3, r.getTime(), style);
            cell(row, 4, (r.isBlocked() ? "[拦截] " : "") + r.getRemark(), style);
        }

        cell(sheet.createRow(sorted.size() + 2), 0,
            "导出时间: " + now() + "  |  共 " + sorted.size() + " 条", oddStyle);
    }

    private static void buildCodes(Workbook workbook, Context context, String format,
                                   List<String> items, boolean qr) throws Exception {
        XSSFSheet sheet = (XSSFSheet) workbook.createSheet("Codes");
        sheet.setColumnWidth(0, 8 * 256);
        sheet.setColumnWidth(1, 18 * 256);
        sheet.setColumnWidth(2, 50 * 256);
        sheet.setColumnWidth(IMAGE_COL, IMAGE_COL_WIDTH * 256);

        CellStyle headerStyle = headerStyle(workbook);
        CellStyle evenStyle = bodyStyle(workbook, true);
        CellStyle oddStyle = bodyStyle(workbook, false);

        Row headerRow = sheet.createRow(0);
        String[] headers = {"#", "Format", "Content", "Barcode"};
        for (int i = 0; i < headers.length; i++) {
            Cell cell = headerRow.createCell(i);
            cell.setCellValue(headers[i]);
            cell.setCellStyle(headerStyle);
        }
        headerRow.setHeight((short) 500);

        boolean withImages = items.size() <= MAX_IMAGES;
        XSSFDrawing drawing = withImages ? sheet.createDrawingPatriarch() : null;

        for (int i = 0; i < items.size(); i++) {
            String content = items.get(i);
            Row row = sheet.createRow(i + 1);
            CellStyle style = (i % 2 == 0) ? oddStyle : evenStyle;
            row.setHeight(withImages ? (qr ? QR_ROW_HEIGHT : BARCODE_ROW_HEIGHT) : (short) 400);
            cell(row, 0, String.valueOf(i + 1), style);
            cell(row, 1, formatLabel(context, format), style);
            cell(row, 2, content, style);

            if (withImages) {
                Bitmap bitmap = BarcodeFactory.create(content, format,
                    qr ? QR_SIZE : BARCODE_WIDTH, qr ? QR_SIZE : BARCODE_HEIGHT);
                byte[] png = toPng(bitmap);
                bitmap.recycle();
                int index = workbook.addPicture(png, Workbook.PICTURE_TYPE_PNG);
                XSSFClientAnchor anchor = new XSSFClientAnchor(0, 0, 0, 0,
                    IMAGE_COL, i + 1, IMAGE_COL + 1, i + 2);
                drawing.createPicture(anchor, index);
            }
        }

        if (!withImages) {
            cell(sheet.createRow(items.size() + 2), 0,
                "条数超过 " + MAX_IMAGES + "，已省略条码图片，仅导出内容", oddStyle);
        }
    }

    private static void buildGenerateRecords(Workbook workbook, Context context, List<GenerateRecord> records) {
        Sheet sheet = workbook.createSheet("Generate Records");
        sheet.setColumnWidth(0, 8 * 256);
        sheet.setColumnWidth(1, 16 * 256);
        sheet.setColumnWidth(2, 16 * 256);
        sheet.setColumnWidth(3, 10 * 256);
        sheet.setColumnWidth(4, 30 * 256);
        sheet.setColumnWidth(5, 40 * 256);

        CellStyle headerStyle = headerStyle(workbook);
        CellStyle evenStyle = bodyStyle(workbook, true);
        CellStyle oddStyle = bodyStyle(workbook, false);

        Row headerRow = sheet.createRow(0);
        String[] headers = {"#", "Type", "Format", "Count", "Generate Time", "Content"};
        for (int i = 0; i < headers.length; i++) {
            Cell cell = headerRow.createCell(i);
            cell.setCellValue(headers[i]);
            cell.setCellStyle(headerStyle);
        }
        headerRow.setHeight((short) 500);

        for (int i = 0; i < records.size(); i++) {
            GenerateRecord r = records.get(i);
            Row row = sheet.createRow(i + 1);
            CellStyle style = (i % 2 == 0) ? oddStyle : evenStyle;
            row.setHeight((short) 400);
            cell(row, 0, String.valueOf(i + 1), style);
            cell(row, 1, r.isQr() ? "QR Code" : "Barcode", style);
            cell(row, 2, formatLabel(context, r.getFormat()), style);
            cell(row, 3, String.valueOf(r.getItems().size()), style);
            cell(row, 4, r.getTime(), style);
            cell(row, 5, preview(r.getItems()), style);
        }
    }

    // ======================== 保存与分享 ========================

    private static void publish(Context context, Workbook workbook, String prefix)
            throws Exception {
        String safePrefix = prefix.replaceAll("[\\\\/:*?\"<>|]", "_");
        String fileName = safePrefix + "_" + stamp() + ".xlsx";

        ByteArrayOutputStream buffer = new ByteArrayOutputStream();
        workbook.write(buffer);

        final Uri uri = save(context, fileName, buffer.toByteArray());
        MAIN.post(() -> {
            if (!isUsable(context)) return;
            new AlertDialog.Builder(context)
                .setTitle(R.string.export_ok_title)
                .setMessage(fileName)
                .setPositiveButton(R.string.share, (d, w) -> share(context, uri, fileName))
                .setNegativeButton(android.R.string.cancel, null)
                .show();
        });
    }

    private static Uri save(Context context, String fileName, byte[] data) throws IOException {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            ContentValues pending = new ContentValues();
            pending.put(MediaStore.MediaColumns.DISPLAY_NAME, fileName);
            pending.put(MediaStore.MediaColumns.MIME_TYPE, MIME);
            pending.put(MediaStore.MediaColumns.RELATIVE_PATH, Environment.DIRECTORY_DOWNLOADS);
            pending.put(MediaStore.MediaColumns.IS_PENDING, 1);

            Uri uri = context.getContentResolver()
                .insert(MediaStore.Downloads.EXTERNAL_CONTENT_URI, pending);
            if (uri == null) throw new IOException("MediaStore insert failed: " + fileName);

            try {
                try (OutputStream out = context.getContentResolver().openOutputStream(uri)) {
                    if (out == null) throw new IOException("openOutputStream failed: " + fileName);
                    out.write(data);
                }
            } catch (IOException | RuntimeException e) {
                context.getContentResolver().delete(uri, null, null);
                throw e;
            }

            ContentValues done = new ContentValues();
            done.put(MediaStore.MediaColumns.IS_PENDING, 0);
            context.getContentResolver().update(uri, done, null, null);
            return uri;
        }

        File dir = context.getExternalFilesDir(Environment.DIRECTORY_DOWNLOADS);
        if (dir == null) dir = new File(context.getFilesDir(), "downloads");
        if (!dir.exists() && !dir.mkdirs()) throw new IOException("mkdirs failed: " + dir);
        File file = new File(dir, fileName);
        try (OutputStream out = new FileOutputStream(file)) {
            out.write(data);
        }
        return FileProvider.getUriForFile(context,
            context.getPackageName() + ".fileprovider", file);
    }

    private static void share(Context context, Uri uri, String fileName) {
        if (uri == null) return;
        Intent send = new Intent(Intent.ACTION_SEND);
        send.setType(MIME);
        send.putExtra(Intent.EXTRA_STREAM, uri);
        send.putExtra(Intent.EXTRA_SUBJECT, fileName);
        send.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION);
        context.startActivity(Intent.createChooser(send, context.getString(R.string.share)));
    }

    // ======================== 线程与工具 ========================

    private static void run(Context context, Job job) {
        if (!isUsable(context)) return;
        Toast.makeText(context, R.string.export_working, Toast.LENGTH_SHORT).show();
        new Thread(() -> {
            try {
                job.run();
            } catch (Throwable e) {
                final String message = e.getMessage();
                MAIN.post(() -> {
                    if (!isUsable(context)) return;
                    Toast.makeText(context, context.getString(R.string.export_failed,
                        message == null ? e.getClass().getSimpleName() : message),
                        Toast.LENGTH_LONG).show();
                });
            }
        }, "excel-export").start();
    }

    private static boolean isUsable(Context context) {
        if (!(context instanceof Activity)) return true;
        Activity activity = (Activity) context;
        return !activity.isFinishing() && !activity.isDestroyed();
    }

    private static void cell(Row row, int index, String value, CellStyle style) {
        Cell cell = row.createCell(index);
        cell.setCellValue(value == null ? "" : value);
        cell.setCellStyle(style);
    }

    private static String preview(List<String> items) {
        if (items.isEmpty()) return "";
        if (items.size() <= 3) return String.join(" / ", items);
        return String.join(" / ", items.subList(0, 3)) + " ... (+" + (items.size() - 3) + ")";
    }

    private static String formatLabel(Context context, String format) {
        if (format == null || format.isEmpty()) return "";
        return context.getString(BarcodeFactory.labelRes(format));
    }

    private static CellStyle headerStyle(Workbook workbook) {
        CellStyle style = workbook.createCellStyle();
        Font font = workbook.createFont();
        font.setBold(true);
        font.setColor(IndexedColors.WHITE.getIndex());
        style.setFont(font);
        style.setFillForegroundColor(IndexedColors.DARK_BLUE.getIndex());
        style.setFillPattern(FillPatternType.SOLID_FOREGROUND);
        style.setAlignment(HorizontalAlignment.CENTER);
        style.setBorderTop(BorderStyle.THIN);
        style.setBorderBottom(BorderStyle.THIN);
        style.setBorderLeft(BorderStyle.THIN);
        style.setBorderRight(BorderStyle.THIN);
        return style;
    }

    private static CellStyle bodyStyle(Workbook workbook, boolean alternate) {
        CellStyle style = workbook.createCellStyle();
        if (alternate) {
            style.setFillForegroundColor(IndexedColors.LIGHT_CORNFLOWER_BLUE.getIndex());
            style.setFillPattern(FillPatternType.SOLID_FOREGROUND);
        }
        style.setBorderTop(BorderStyle.THIN);
        style.setBorderBottom(BorderStyle.THIN);
        style.setBorderLeft(BorderStyle.THIN);
        style.setBorderRight(BorderStyle.THIN);
        return style;
    }

    private static byte[] toPng(Bitmap bitmap) throws Exception {
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        bitmap.compress(Bitmap.CompressFormat.PNG, 100, out);
        return out.toByteArray();
    }

    private static String stamp() {
        return new SimpleDateFormat("yyyyMMdd_HHmmss", Locale.getDefault()).format(new Date());
    }

    private static String now() {
        return new SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.getDefault()).format(new Date());
    }
}
