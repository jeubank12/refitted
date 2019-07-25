package com.litus_animae.refitted;

import android.app.Application;
import android.util.Log;

import com.instabug.library.Instabug;
import com.instabug.library.invocation.InstabugInvocationEvent;

public class InstaBugApplication extends Application implements Thread.UncaughtExceptionHandler {
    private static final String TAG = "InstaBugApplication";
    @Override
    public void onCreate() {
        super.onCreate();
        Thread.setDefaultUncaughtExceptionHandler(this);

        new Instabug.Builder(this, "a48b9536a802102f8a395ae7fcd8e20c")
                .setInvocationEvents(InstabugInvocationEvent.FLOATING_BUTTON)
                .build();

    }

    @Override
    public void uncaughtException(Thread t, Throwable e) {
        Log.e(TAG, "uncaughtException: thread: " + t.getName(), e);
    }
}
