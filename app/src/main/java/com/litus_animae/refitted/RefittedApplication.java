package com.litus_animae.refitted;

import android.app.Application;

import dagger.hilt.android.HiltAndroidApp;

@HiltAndroidApp
public class RefittedApplication extends Application {
    private static final String TAG = "RefittedApplication";
    @Override
    public void onCreate() {
        super.onCreate();
    }
}
