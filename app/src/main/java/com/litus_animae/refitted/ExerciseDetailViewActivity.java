package com.litus_animae.refitted;

import android.os.Handler;
import android.os.Message;
import android.support.annotation.NonNull;
import android.support.v7.app.AppCompatActivity;
import android.os.Bundle;
import android.util.Log;
import android.widget.TextView;

import com.litus_animae.refitted.models.Exercise;
import com.litus_animae.refitted.threads.GetExerciseRunnable;
import com.litus_animae.refitted.threads.PassUpExceptionThreadFactory;

import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.ThreadFactory;

public class ExerciseDetailViewActivity extends AppCompatActivity implements
        Handler.Callback,
Thread.UncaughtExceptionHandler{

    private static final String TAG = "ExerciseDetailViewActivity";
    private ExecutorService threadPoolService;
    private Handler detailViewHandler;
    private TextView nameView;
    private TextView descriptionView;

    @Override
    protected void onStop() {
        Log.d(TAG, "onStop: called");
        super.onStop();

        threadPoolService.shutdownNow();
    }

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_exercise_detail_view);

        detailViewHandler = new Handler(this);
        threadPoolService = Executors.newCachedThreadPool();

        nameView = findViewById(R.id.exerciseNameView);
        descriptionView = findViewById(R.id.exerciseDescriptionView);

        threadPoolService.submit(new GetExerciseRunnable(this, detailViewHandler,
                "Chest_Alternate DB press (Neutral Grip)", "AX1"));
    }

    @Override
    public boolean handleMessage(Message msg) {
        switch (msg.what) {
            case Constants.EXERCISE_LOAD_SUCCESS:
                Log.d(TAG, "handleMessage: succeeded loading exercise");
                Exercise ex = (Exercise) msg.getData().getSerializable("exercise_load");
                nameView.setText(ex.getName());
                descriptionView.setText(ex.getDescription());
                return true;
            case Constants.EXERCISE_LOAD_FAIL:
                Log.d(TAG, "handleMessage: failed to load exercise");
                return true;
        }
        return false;
    }

    @Override
    public void uncaughtException(Thread t, Throwable e) {
        Log.e(TAG, "uncaughtException: " + e.getMessage(), e);
    }
}
