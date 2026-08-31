package com.qrscanner;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;

public class ScanBroadcastReceiver extends BroadcastReceiver {

    public static ScanCallback callback;

    public interface ScanCallback {
        void onScan(String data);
    }

    @Override
    public void onReceive(Context context, Intent intent) {
        if (intent == null) return;
        String data = null;
        String action = intent.getAction();

        if ("com.honeywell.scan.broadcast".equals(action)) {
            data = intent.getStringExtra("data");
        } else if ("android.intent.ACTION_DECODE_DATA".equals(action)) {
            data = intent.getStringExtra("barcode_string");
        }

        if (data != null && !data.isEmpty() && callback != null) {
            callback.onScan(data);
        }
    }
}
