package com.litus_animae.refitted;

import android.content.Intent;
import android.databinding.DataBindingUtil;
import android.databinding.ObservableBoolean;
import android.databinding.ObservableField;
import android.databinding.ObservableInt;
import android.os.CountDownTimer;
import android.os.Handler;
import android.os.Message;
import android.support.v7.app.AppCompatActivity;
import android.os.Bundle;
import android.util.Log;
import android.view.Menu;
import android.view.MenuInflater;
import android.view.MenuItem;
import android.view.View;
import android.widget.TextView;

import com.instabug.library.Instabug;
import com.instabug.library.invocation.InstabugInvocationEvent;
import com.litus_animae.refitted.databinding.ActivityExerciseDetailViewBinding;
import com.litus_animae.refitted.models.Exercise;
import com.litus_animae.refitted.models.ExerciseRecord;
import com.litus_animae.refitted.models.ExerciseSet;
import com.litus_animae.refitted.models.SetRecord;
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
    private MenuItem switchToAlternateButton;
    private ActivityExerciseDetailViewBinding binding;
    private ObservableBoolean leftButtonVisibility = new ObservableBoolean(false);
    private ObservableBoolean rightButtonVisibility = new ObservableBoolean(false);
    private ObservableField<String> completeSetButtonText;
    private ArrayList<ExerciseSet> exerciseSets;
    private ArrayList<ExerciseRecord> exerciseRecords;
    private int exerciseIndex;
    private CountDownTimer timer;

    @Override
    protected void onStop() {
        Log.d(TAG, "onStop: called");
        super.onStop();

        threadPoolService.shutdownNow();
        if (timer != null){
            timer.cancel();
        }
    }

    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
        MenuInflater inflater = getMenuInflater();
        inflater.inflate(R.menu.alternate_menu, menu);
        switchToAlternateButton = menu.findItem(R.id.switch_to_alternate_menu_item);
        return true;
    }

    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        switch(item.getItemId()){
            case R.id.switch_to_alternate_menu_item:
                ExerciseSet e = exerciseSets.get(exerciseIndex);
                e.setActive(false);
                CheckForAlternateExerciseSet(e);
                UpdateVisibleExercise();
                return true;
            default:
                return super.onOptionsItemSelected(item);
        }
    }

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        binding = DataBindingUtil.setContentView(this, R.layout.activity_exercise_detail_view);
        binding.setLocale(Locale.getDefault());
        binding.setHasLeft(leftButtonVisibility);
        binding.setHasRight(rightButtonVisibility);
        completeSetButtonText = new ObservableField<>(getString(R.string.complete_set));
        binding.setCompleteSetButtonText(completeSetButtonText);

        detailViewHandler = new Handler(this);
        threadPoolService = Executors.newCachedThreadPool();

        repsView = binding.repsDisplayView;
        weightView = binding.weightDisplayView;
        restView = binding.restTimeView;

        Intent intent = getIntent();
        GetWorkoutsForDay(Integer.toString(intent.getIntExtra("day", 1)),
                intent.getStringExtra("workout"));
    }

    private void GetWorkoutsForDay(String day, String workoutName) {
        leftButtonVisibility.set(false);
        rightButtonVisibility.set(false);
        binding.loadingOverlay.setVisibility(View.VISIBLE);
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
                exerciseRecords = msg.getData().getParcelableArrayList("exercise_records");
                if (exerciseSets == null || exerciseSets.isEmpty()) {
                    Log.e(TAG, "handleMessage: exercise failed to serialize");
                    return false;
                }
                exerciseIndex = 0;
                CheckForAlternateExerciseSet(exerciseSets.get(0));
                UpdateVisibleExercise();
                binding.loadingOverlay.setVisibility(View.GONE);
                return true;
            case Constants.EXERCISE_LOAD_FAIL:
                binding.loadingOverlay.setVisibility(View.GONE);
                Log.d(TAG, "handleMessage: failed to load exercise");
                return true;
        }
        binding.loadingOverlay.setVisibility(View.GONE);
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

    public void HandleNavigateLeft(View view) {
        if (exerciseIndex < 1) {
            Log.e(TAG, "HandleNavigateLeft: already furthest left");
            exerciseIndex = 0;
        } else {
            ExerciseSet e = exerciseSets.get(exerciseIndex);
            if (e.getStep().endsWith(".b")){
                // TODO write tests for this
                // if the first step, then there will be a '.a'
                if (exerciseIndex != 1) {
                    exerciseIndex = exerciseIndex - 2;
                }
            } else {
                exerciseIndex--;
            }
        }
        UpdateVisibleExercise();
    }

    public void HandleNavigateRight(View view) {
        if (exerciseIndex >= exerciseSets.size() - 1) {
            Log.e(TAG, "HandleNavigateLeft: already furthest right");
            exerciseIndex = exerciseSets.size() - 1;
        } else {
            ExerciseSet e = exerciseSets.get(exerciseIndex);
            if (e.getStep().endsWith(".a")){
                // if the last step, then there will be a '.b'
                if (exerciseIndex != exerciseSets.size() - 2) {
                    exerciseIndex = Math.min(exerciseIndex + 2, exerciseSets.size() - 1);
                }
            } else {
                exerciseIndex++;
            }
        }
        UpdateVisibleExercise();
    }

    private void UpdateVisibleExercise() {
        ExerciseSet e = exerciseSets.get(exerciseIndex);
        if (e.hasAlternate() || e.getStep().endsWith(".a")) {
            e = CheckForAlternateExerciseSet(e);
            switchToAlternateButton.setVisible(true);
            leftButtonVisibility.set(exerciseIndex > (e.getStep().endsWith(".b") ? 1 : 0));
            rightButtonVisibility.set(exerciseIndex < exerciseSets.size() -
                    (e.getStep().endsWith(".a") ? 2 : 1));
        } else {
            switchToAlternateButton.setVisible(false);
            leftButtonVisibility.set(exerciseIndex > 0);
            rightButtonVisibility.set(exerciseIndex < exerciseSets.size() - 1);
        }
        if (exerciseRecords.get(exerciseIndex).getSetsCount() == e.getSets()) {
            completeSetButtonText.set(getString(R.string.complete_exercise));
        } else {
            completeSetButtonText.set(getString(R.string.complete_set) +
                    String.format(Locale.getDefault(), " %d %s %d",
                            exerciseRecords.get(exerciseIndex).getSetsCount() + 1,
                            getString(R.string.word_of), e.getSets()));
        }
        if (timer == null) {
            binding.restProgressBar.setMax(e.getRest()*1000);
            UpdateRestTimerView(e.getRest());
        }
        binding.setExercise(e);
    }

    private ExerciseSet CheckForAlternateExerciseSet(ExerciseSet e) {
        if (!e.hasAlternate() && e.getStep().endsWith(".a")){
            e.setAlternate(exerciseSets.get(exerciseIndex + 1));
            e.getAlternate().setActive(false);
            e.getAlternate().setAlternate(e);
        } else if (!e.isActive() && e.getStep().endsWith(".a")){
            exerciseIndex++;
            e = e.getAlternate();
        } else if (!e.isActive()){
            exerciseIndex--;
            e = e.getAlternate();
        }
        e.setActive(true);
        return e;
    }

    private void UpdateRestTimerView(double rest) {
        binding.restProgressBar.setProgress((int)(binding.restProgressBar.getMax() - rest*1000));
        restView.setText(String.format(Locale.getDefault(), "%.1f%s %s",
                rest, getString(R.string.seconds_abbrev),
                getString(R.string.rest)));
    }

    public void HandleCompleteSet(View view) {
        if (exerciseRecords.get(exerciseIndex).getSetsCount() ==
                exerciseSets.get(exerciseIndex).getSets()) {
            completeSetButtonText.set(getString(R.string.complete_exercise));
            return;
        }
        view.setEnabled(false);
        exerciseRecords.get(exerciseIndex).addSet(
                new SetRecord(Double.parseDouble(weightView.getText().toString()),
                        Integer.parseInt(repsView.getText().toString())));
        UpdateVisibleExercise();
        if (timer != null){
            timer.cancel();
        }
        timer = new CountDownTimer(
                exerciseSets.get(exerciseIndex).getRest() * 1000,
                50) {
            @Override
            public void onTick(long millisUntilFinished) {
                UpdateRestTimerView(millisUntilFinished / 1000.0);
            }

            @Override
            public void onFinish() {
                timer = null;
                if (exerciseRecords.get(exerciseIndex).getSetsCount() ==
                        exerciseSets.get(exerciseIndex).getSets()) {
                    HandleNavigateRight(view);
                } else {
                    UpdateVisibleExercise();
                }
                view.setEnabled(true);
            }
        };
        timer.start();
    }

    // TODO implement on change for weight and reps then re-enable edit
}
