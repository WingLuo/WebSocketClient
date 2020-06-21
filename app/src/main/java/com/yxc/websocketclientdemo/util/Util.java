package com.yxc.websocketclientdemo.util;

import android.content.Context;
import android.widget.Toast;

public class Util {
    public static final String ws = "ws://121.36.133.160:8080/wbs/websocket/dd";

    public static void showToast(Context ctx, String msg) {
        Toast.makeText(ctx, msg, Toast.LENGTH_LONG).show();
    }
}
