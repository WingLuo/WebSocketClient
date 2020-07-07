package com.yxc.websocketclientdemo;

import android.content.Context;

import org.litepal.LitePal;

public class Application extends android.app.Application {
    private static Application context;
    public static Context getContext() {
        return context;
    }
    @Override
    public void onCreate() {
        super.onCreate();
        context=this;
        LitePal.initialize(this);
    }
}
