package com.yxc.websocketclientdemo;

import org.litepal.LitePal;

public class Application extends android.app.Application {
    @Override
    public void onCreate() {
        super.onCreate();
        LitePal.initialize(this);
    }
}
