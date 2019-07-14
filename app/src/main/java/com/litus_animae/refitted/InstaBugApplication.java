package com.litus_animae.refitted;

import android.app.Application;

import com.instabug.library.Instabug;
import com.instabug.library.invocation.InstabugInvocationEvent;

public class InstaBugApplication extends Application {
    @Override
    public void onCreate() {
        super.onCreate();

        new Instabug.Builder(this, "a48b9536a802102f8a395ae7fcd8e20c")
                .setInvocationEvents(InstabugInvocationEvent.FLOATING_BUTTON)
                .build();
    }
}
