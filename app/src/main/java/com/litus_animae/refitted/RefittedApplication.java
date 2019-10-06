package com.litus_animae.refitted;

import android.app.Application;

import com.bugsee.library.Bugsee;

public class RefittedApplication extends Application {
    private static final String TAG = "RefittedApplication";
    @Override
    public void onCreate() {
        super.onCreate();
        Bugsee.launch(this, getString(R.string.bugsee_id));
    }
}
