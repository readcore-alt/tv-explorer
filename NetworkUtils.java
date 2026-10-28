package com.example.tvexplorer;

import android.content.Context;
import android.net.wifi.WifiManager;
import android.text.format.Formatter;

public class NetworkUtils {
    public static String getDeviceIpAddress(Context context) {
        WifiManager wm = (WifiManager) context.getApplicationContext().getSystemService(Context.WIFI_SERVICE);
        if (wm != null && wm.getConnectionInfo() != null) {
            return Formatter.formatIpAddress(wm.getConnectionInfo().getIpAddress());
        }
        return "127.0.0.1";
    }
}
