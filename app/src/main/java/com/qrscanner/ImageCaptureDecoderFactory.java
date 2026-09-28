package com.qrscanner;

import android.graphics.Bitmap;

import com.google.zxing.BarcodeFormat;
import com.google.zxing.DecodeHintType;
import com.google.zxing.LuminanceSource;
import com.google.zxing.MultiFormatReader;
import com.google.zxing.Result;
import com.journeyapps.barcodescanner.Decoder;
import com.journeyapps.barcodescanner.DecoderFactory;

import java.util.Collection;
import java.util.EnumMap;
import java.util.Map;

public class ImageCaptureDecoderFactory implements DecoderFactory {

    private static final int MAX_CAPTURE_WIDTH = 900;

    private static volatile Bitmap pendingBitmap;
    private static volatile String pendingFormat;

    private final Collection<BarcodeFormat> formats;

    public ImageCaptureDecoderFactory(Collection<BarcodeFormat> formats) {
        this.formats = formats;
    }

    @Override
    public Decoder createDecoder(Map<DecodeHintType, ?> baseHints) {
        Map<DecodeHintType, Object> hints = new EnumMap<>(DecodeHintType.class);
        if (baseHints != null) hints.putAll(baseHints);
        if (formats != null && !formats.isEmpty()) {
            hints.put(DecodeHintType.POSSIBLE_FORMATS, formats);
        }
        MultiFormatReader reader = new MultiFormatReader();
        reader.setHints(hints);
        return new CapturingDecoder(reader);
    }

    public static Bitmap consumeBitmap() {
        Bitmap bitmap = pendingBitmap;
        pendingBitmap = null;
        return bitmap;
    }

    public static String consumeFormat() {
        String format = pendingFormat;
        pendingFormat = null;
        return format;
    }

    private static void capture(LuminanceSource source, Result result) {
        pendingFormat = result.getBarcodeFormat() != null
            ? result.getBarcodeFormat().name() : "";
        pendingBitmap = null;
        try {
            int width = source.getWidth();
            int height = source.getHeight();
            if (width <= 0 || height <= 0) return;

            byte[] row = new byte[width];
            int[] pixels = new int[width * height];
            for (int y = 0; y < height; y++) {
                source.getRow(y, row);
                int offset = y * width;
                for (int x = 0; x < width; x++) {
                    int lum = row[x] & 0xFF;
                    pixels[offset + x] = 0xFF000000 | (lum << 16) | (lum << 8) | lum;
                }
            }
            Bitmap bitmap = Bitmap.createBitmap(pixels, width, height, Bitmap.Config.ARGB_8888);
            if (width > MAX_CAPTURE_WIDTH) {
                Bitmap scaled = BarcodeFactory.scale(bitmap, MAX_CAPTURE_WIDTH);
                bitmap.recycle();
                bitmap = scaled;
            }
            pendingBitmap = bitmap;
        } catch (Exception ignored) {
        }
    }

    private static class CapturingDecoder extends Decoder {

        CapturingDecoder(MultiFormatReader reader) {
            super(reader);
        }

        @Override
        public Result decode(LuminanceSource source) {
            Result result = super.decode(source);
            if (result != null) capture(source, result);
            return result;
        }
    }
}
