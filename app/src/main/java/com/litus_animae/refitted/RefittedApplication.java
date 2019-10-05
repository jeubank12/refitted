package com.litus_animae.refitted;

import android.app.Application;

import com.bugsee.library.Bugsee;
import com.bugsee.library.LaunchOptions;

public class RefittedApplication extends Application {
    private static final String TAG = "RefittedApplication";
    @Override
    public void onCreate() {
        super.onCreate();
        LaunchOptions options = new LaunchOptions();
        options.General.setShakeToTrigger(false);
        options.General.setWifiOnlyUpload(true);
        Bugsee.launch(this, getString(R.string.bugsee_id), options);
    }
}
