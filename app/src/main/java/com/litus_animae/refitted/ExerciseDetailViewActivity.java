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
import com.litus_animae.refitted.models.Exercise;
import com.litus_animae.refitted.models.ExerciseSet;
import com.litus_animae.refitted.threads.GetExerciseRunnable;

import java.util.Locale;
import java.util.Observable;
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
    public final ObservableInt enableLeftNavigation = new ObservableInt(View.INVISIBLE);
    public final ObservableInt enableRightNavigation = new ObservableInt(View.INVISIBLE);

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
        binding.setHasLeft(enableLeftNavigation);
        binding.setHasRight(enableRightNavigation);

        detailViewHandler = new Handler(this);
        threadPoolService = Executors.newCachedThreadPool();

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
                if (set == null) {
                    Log.e(TAG, "handleMessage: exercise failed to serialize");
                    return false;
                }
                binding.setExercise(set);
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

    // TODO implement on change for weight and reps then re-enable edit
}
