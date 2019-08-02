package com.litus_animae.refitted.threads;

import android.content.Context;

import com.litus_animae.refitted.data.RoomDataService;

public class CloseDatabaseRunnable implements Runnable {

    private Context applicationContext;

    public CloseDatabaseRunnable(Context context){
        applicationContext = context.getApplicationContext();
    }

    @Override
    public void run() {
        RoomDataService.closeExerciseRoom(applicationContext);
    }
}
