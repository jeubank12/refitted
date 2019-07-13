package com.litus_animae.refitted;

import android.os.Handler;
import android.os.Message;
import android.support.v7.app.AppCompatActivity;
import android.os.Bundle;
import android.util.Log;
import android.widget.TextView;

import com.litus_animae.refitted.models.Exercise;
import com.litus_animae.refitted.models.ExerciseSet;
import com.litus_animae.refitted.threads.GetExerciseRunnable;

import java.util.Locale;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public class ExerciseDetailViewActivity extends AppCompatActivity implements
        Handler.Callback{

    private static final String TAG = "ExerciseDetailViewActivity";
    private ExecutorService threadPoolService;
    private Handler detailViewHandler;
    private TextView nameView;
    private TextView descriptionView;
    private TextView noteView;
    private TextView repsView;
    private TextView weightView;
    private TextView restView;

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
        noteView = findViewById(R.id.exerciseNotesView);
        repsView = findViewById(R.id.repsDisplayView);
        weightView = findViewById(R.id.weightDisplayView);
        restView = findViewById(R.id.restTimeView);

        threadPoolService.submit(new GetExerciseRunnable(this, detailViewHandler,
                "1.2", "AX1"));
    }

    @Override
    protected void onResume() {
        super.onResume();
        repsView.clearFocus();
        weightView.clearFocus();
    }

    @Override
    public boolean handleMessage(Message msg) {
        switch (msg.what) {
            case Constants.EXERCISE_LOAD_SUCCESS:
                Log.d(TAG, "handleMessage: succeeded loading exercise");
                ExerciseSet set = (ExerciseSet) msg.getData().getSerializable("exercise_load");
                if (set == null){
                    Log.e(TAG, "handleMessage: exercise failed to serialize");
                    return false;
                }
                Exercise ex = set.getExercise();
                nameView.setText(ex.getName());
                descriptionView.setText(ex.getDescription());
                noteView.setText(set.getNote());
                repsView.setText(String.format(Locale.getDefault(), "%d", set.getReps()));
                weightView.setText(String.format(Locale.getDefault(), "%.1f", 25.0));
                restView.setText(String.format(Locale.getDefault(), "%.1f", (double)set.getRest()));
                return true;
            case Constants.EXERCISE_LOAD_FAIL:
                Log.d(TAG, "handleMessage: failed to load exercise");
                return true;
        }
        return false;
    }
}
