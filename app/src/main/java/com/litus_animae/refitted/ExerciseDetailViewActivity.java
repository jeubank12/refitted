package com.litus_animae.refitted;

import android.databinding.DataBindingUtil;
import android.databinding.ObservableInt;
import android.os.Handler;
import android.os.Message;
import android.support.v7.app.AppCompatActivity;
import android.os.Bundle;
import android.util.Log;
import android.view.View;
import android.widget.TextView;

import com.litus_animae.refitted.databinding.ActivityExerciseDetailViewBinding;
import com.litus_animae.refitted.models.ExerciseSet;
import com.litus_animae.refitted.threads.GetExerciseRunnable;

import java.util.ArrayList;
import java.util.Locale;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public class ExerciseDetailViewActivity extends AppCompatActivity implements
        Handler.Callback {

    private static final String TAG = "ExerciseDetailViewActivity";
    private ExecutorService threadPoolService;
    private Handler detailViewHandler;
    private TextView repsView;
    private TextView weightView;
    private TextView restView;
    private ActivityExerciseDetailViewBinding binding;
    private ObservableInt leftButtonVisibility = new ObservableInt(View.INVISIBLE);
    private ObservableInt rightButtonVisibility = new ObservableInt(View.INVISIBLE);
    private ArrayList<ExerciseSet> exerciseSets;
    private int exerciseIndex;

    @Override
    protected void onStop() {
        Log.d(TAG, "onStop: called");
        super.onStop();

        threadPoolService.shutdownNow();
    }

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        binding = DataBindingUtil.setContentView(this, R.layout.activity_exercise_detail_view);
        binding.setLocale(Locale.getDefault());
        binding.setHasLeft(leftButtonVisibility);
        binding.setHasRight(rightButtonVisibility);

        detailViewHandler = new Handler(this);
        threadPoolService = Executors.newCachedThreadPool();

        repsView = findViewById(R.id.repsDisplayView);
        weightView = findViewById(R.id.weightDisplayView);
        restView = findViewById(R.id.restTimeView);

        String day = "1";
        String workoutName = "AX1";
        GetWorkoutsForDay(day, workoutName);
    }

    private void GetWorkoutsForDay(String day, String workoutName) {
        leftButtonVisibility.set(View.INVISIBLE);
        rightButtonVisibility.set(View.INVISIBLE);
        threadPoolService.submit(new GetExerciseRunnable(this, detailViewHandler,
                day, workoutName));
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
                exerciseSets = msg.getData().getParcelableArrayList("exercise_load");
                if (exerciseSets == null || exerciseSets.isEmpty()) {
                    Log.e(TAG, "handleMessage: exercise failed to serialize");
                    return false;
                }
                exerciseIndex = 0;
                binding.setExercise(exerciseSets.get(0));
                leftButtonVisibility.set(View.INVISIBLE);
                rightButtonVisibility.set(exerciseSets.size() > 1 ? View.VISIBLE : View.INVISIBLE);
                return true;
            case Constants.EXERCISE_LOAD_FAIL:
                Log.d(TAG, "handleMessage: failed to load exercise");
                return true;
        }
        return false;
    }

    public void HandleWeightClick(View view) {
        switch (view.getId()) {
            case R.id.add2point5Button:
                UpdateWeightValue(2.5);
                break;
            case R.id.add5Button:
                UpdateWeightValue(5);
                break;
            case R.id.add10Button:
                UpdateWeightValue(10);
                break;
            case R.id.add25Button:
                UpdateWeightValue(25);
                break;
            case R.id.add45Button:
                UpdateWeightValue(45);
                break;
            case R.id.sub2point5Button:
                UpdateWeightValue(-2.5);
                break;
            case R.id.sub5Button:
                UpdateWeightValue(-5);
                break;
            case R.id.sub10Button:
                UpdateWeightValue(-10);
                break;
            case R.id.sub25Button:
                UpdateWeightValue(-25);
                break;
            case R.id.sub45Button:
                UpdateWeightValue(-45);
                break;
            default:
                Log.e(TAG, "HandleWeightClick: event from unknown source: " + view.getId());
        }
    }

    private void UpdateWeightValue(double change) {
        double value = Double.parseDouble(weightView.getText().toString()) + change;
        if (value < 0) {
            weightView.setText(String.format(Locale.getDefault(), "%.1f", 0.0));
        } else {
            weightView.setText(String.format(Locale.getDefault(), "%.1f", value));
        }
    }

    public void HandleRepsClick(View view) {
        switch (view.getId()) {
            case R.id.addRepButton:
                UpdateRepValue(true);
                break;
            case R.id.subRepButton:
                UpdateRepValue(false);
                break;
            default:
                Log.e(TAG, "HandleRepsClick: event from unknown source: " + view.getId());
        }
    }

    private void UpdateRepValue(boolean increase) {
        int value = Integer.parseInt(repsView.getText().toString());
        if (increase) {
            repsView.setText(String.format(Locale.getDefault(), "%d", value + 1));
        } else if (value > 0) {
            repsView.setText(String.format(Locale.getDefault(), "%d", value - 1));
        } else {
            repsView.setText(String.format(Locale.getDefault(), "%d", 0));
        }
    }

    public void HandleNavigateLeft(View view){
        if (exerciseIndex < 1){
            Log.e(TAG, "HandleNavigateLeft: already furthest left");
            exerciseIndex = 0;
        } else {
            exerciseIndex--;
        }
        UpdateVisibleExercise();
    }

    public void HandleNavigateRight(View view){
        if (exerciseIndex >= exerciseSets.size()){
            Log.e(TAG, "HandleNavigateLeft: already furthest right");
            exerciseIndex = exerciseSets.size() - 1;
        } else {
            exerciseIndex++;
        }
        UpdateVisibleExercise();
    }

    private void UpdateVisibleExercise() {
        leftButtonVisibility.set(exerciseIndex > 0 ? View.VISIBLE : View.INVISIBLE);
        rightButtonVisibility.set(exerciseIndex < exerciseSets.size() - 1 ?
                View.VISIBLE : View.INVISIBLE);
        binding.setExercise(exerciseSets.get(exerciseIndex));
    }

    public void HandleCompleteSet(View view){

    }

    // TODO implement on change for weight and reps then re-enable edit
}
