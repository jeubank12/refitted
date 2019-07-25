package com.litus_animae.refitted.models;

import android.app.Application;
import android.os.CountDownTimer;
import android.util.Log;
import android.view.View;

import androidx.annotation.NonNull;
import androidx.lifecycle.AndroidViewModel;
import androidx.lifecycle.LiveData;
import androidx.lifecycle.MutableLiveData;
import androidx.lifecycle.Transformations;

import com.litus_animae.refitted.R;
import com.litus_animae.refitted.threads.CloseDatabaseRunnable;
import com.litus_animae.refitted.threads.GetExerciseRunnable;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public class ExerciseViewModel extends AndroidViewModel {
    private static final String TAG = "ExerciseViewModel";
    private LiveData<ExerciseSet> exerciseMutableLiveData;
    private LiveData<Integer> isLoading;
    private MutableLiveData<Boolean> isLoadingBool;
    private LiveData<Integer> hasLeft;
    private MutableLiveData<Boolean> hasLeftBool;
    private LiveData<Integer> hasRight;
    private MutableLiveData<Boolean> hasRightBool;
    private MutableLiveData<List<ExerciseSet>> exerciseSets;
    private ExecutorService threadPoolService;
    private MutableLiveData<Integer> exerciseIndex = new MutableLiveData<>();
    private MutableLiveData<String> completeSetMessage = new MutableLiveData<>();
    private CountDownTimer timer;
    private ArrayList<ExerciseRecord> exerciseRecords;
    private MutableLiveData<Integer> restMax = new MutableLiveData<>();
    private MutableLiveData<Integer> restProgress = new MutableLiveData<>();
    private MutableLiveData<String> restValue = new MutableLiveData<>();
    private MutableLiveData<String> weightDisplayValue = new MutableLiveData<>();
    private MutableLiveData<String> repsDisplayValue = new MutableLiveData<>();

    public ExerciseViewModel(@NonNull Application application) {
        super(application);
        threadPoolService = Executors.newCachedThreadPool();

        isLoadingBool = new MutableLiveData<>();
        isLoadingBool.setValue(true);
        isLoading = Transformations.map(isLoadingBool, isLoad -> isLoad ? View.VISIBLE : View.GONE);

        hasLeftBool = new MutableLiveData<>();
        hasLeftBool.setValue(false);
        hasLeft = Transformations.map(hasLeftBool, enable -> enable ? View.VISIBLE : View.GONE);

        hasRightBool = new MutableLiveData<>();
        hasRightBool.setValue(true);
        hasRight = Transformations.map(hasRightBool, enable -> enable ? View.VISIBLE : View.GONE);

        exerciseIndex.setValue(0);
        exerciseSets = new MutableLiveData<>();
        exerciseMutableLiveData = Transformations.switchMap(exerciseSets,
                set -> Transformations.map(exerciseIndex,
                        this::UpdateVisibleExercise));
    }

    @Override
    protected void onCleared() {
        super.onCleared();
        threadPoolService.submit(new CloseDatabaseRunnable(getApplication()));
        threadPoolService.shutdown();
    }

    public void loadExercises(String day, String workoutId) {
        threadPoolService.submit(new GetExerciseRunnable(getApplication(), exerciseSets,
                day, workoutId));
    }

    private void UpdateWeightDisplay(double change) {
        double value = Double.parseDouble(weightDisplayValue.getValue()) + change;
        if (value < 0) {
            SetWeightDisplay(0);
        } else {
            SetWeightDisplay(value);
        }
    }

    private void SetWeightDisplay(double value) {
        weightDisplayValue.setValue(String.format(Locale.getDefault(), "%.1f", value));
    }

    private void UpdateRepsDisplay(boolean increase) {
        int value = Integer.parseInt(repsDisplayValue.getValue());
        if (increase) {
            SetRepsDisplay(value + 1);
        } else if (value > 0) {
            SetRepsDisplay(value - 1);
        } else {
            SetRepsDisplay(0);
        }
    }

    private void SetRepsDisplay(int value) {
        repsDisplayValue.setValue(String.format(Locale.getDefault(), "%d", value));
    }

    private boolean CheckForAlternateExerciseSet(int index, ExerciseSet e) {
        boolean result = false;
        if (!e.hasAlternate() && e.getStep().endsWith(".a")) {
            Log.d(TAG, "CheckForAlternateExerciseSet: setting up new alternate for .a");
            e.setAlternate(exerciseSets.getValue().get(index + 1));
            e.getAlternate().setActive(false);
            e.getAlternate().setAlternate(e);
            result = true;
        } else if (!e.isActive() && e.getStep().endsWith(".a")) {
            Log.d(TAG, "CheckForAlternateExerciseSet: navigated to .a, but .b is active");
            exerciseIndex.setValue(index + 1);
            e = e.getAlternate();
        } else if (!e.isActive() && e.getStep().endsWith(".b")) {
            Log.d(TAG, "CheckForAlternateExerciseSet: navigated to .b, but .a is active");
            exerciseIndex.setValue(index - 1);
            e = e.getAlternate();
        }
        e.setActive(true);
        return result;
    }

    private ExerciseSet UpdateVisibleExercise(int index) {
        if (exerciseRecords == null) {
            exerciseRecords = new ArrayList<>();
            for (ExerciseSet e : exerciseSets.getValue()) {
                exerciseRecords.add(new ExerciseRecord(e));
            }
        }

        final List<ExerciseSet> copyExerciseSets = exerciseSets.getValue();
        if (copyExerciseSets == null) {
            Log.d(TAG, "UpdateVisibleExercise: exerciseSets is not yet set, returning default");
            return new ExerciseSet();
        }
        if (isLoadingBool.getValue()) {
            isLoadingBool.setValue(false);
        }
        Log.d(TAG, "UpdateVisibleExercise: updating to index " + index);
        ExerciseSet e = copyExerciseSets.get(index);
        boolean wasChanged = false;
        if (e.hasAlternate() || e.getStep().endsWith(".a")) {
            Log.d(TAG, "UpdateVisibleExercise: checking for alternate...");
            wasChanged = CheckForAlternateExerciseSet(index, e);
            e = copyExerciseSets.get(exerciseIndex.getValue());
            //switchToAlternateButton.setVisible(true);
            hasLeftBool.setValue(index > (e.getStep().endsWith(".b") ? 1 : 0));
            hasRightBool.setValue(index < copyExerciseSets.size() -
                    (e.getStep().endsWith(".a") ? 2 : 1));
        } else {
            //switchToAlternateButton.setVisible(false);
            hasLeftBool.setValue(index > 0);
            hasRightBool.setValue(index < copyExerciseSets.size() - 1);
        }

        SetTimerText(e);
        if (wasChanged) {
            exerciseSets.setValue(copyExerciseSets);
        }
        // TODO set reps and weight
        SetRepsDisplay(exerciseRecords.get(index).getSetsCount() > 0 ?
                exerciseRecords.get(index).getSet(-1).getReps() :
                e.getReps());
        SetWeightDisplay(exerciseRecords.get(index).getSetsCount() > 0 ?
                exerciseRecords.get(index).getSet(-1).getWeight() :
                25);
        return e;
    }

    private void SetTimerText(ExerciseSet e) {
        if (timer == null) {
            if (exerciseRecords.get(exerciseIndex.getValue()).getSetsCount() == e.getSets()) {
                completeSetMessage.setValue(getString(R.string.complete_exercise));
            } else {
                completeSetMessage.setValue(getString(R.string.complete_set) +
                        String.format(Locale.getDefault(), " %d %s %d",
                                // using the LiveData here because the value may have changed
                                exerciseRecords.get(exerciseIndex.getValue()).getSetsCount() + 1,
                                getString(R.string.word_of), e.getSets()));
            }
            restMax.setValue(e.getRest() * 1000);
            UpdateRestTimerProgress(e.getRest());
        } else {
            completeSetMessage.setValue(getString(R.string.cancel_rest));
        }
    }

    private String getString(int resourceId) {
        return getApplication().getResources().getString(resourceId);
    }

    public void SwapToAlternate() {
        final List<ExerciseSet> copyExerciseSet = exerciseSets.getValue();
        copyExerciseSet.get(exerciseIndex.getValue()).setActive(false);
        exerciseSets.setValue(copyExerciseSet);
    }

    public void NavigateLeft() {
        int index = exerciseIndex.getValue();
        if (index < 1) {
            Log.e(TAG, "HandleNavigateLeft: already furthest left");
            exerciseIndex.setValue(0);
        } else {
            ExerciseSet e = exerciseSets.getValue().get(index);
            if (e.getStep().endsWith(".b")) {
                // TODO write tests for this
                // if the first step, then there will be a '.a'
                if (index != 1) {
                    exerciseIndex.setValue(index - 2);
                }
            } else {
                exerciseIndex.setValue(index - 1);
            }
        }
    }

    public void NavigateRight() {
        int index = exerciseIndex.getValue();
        List<ExerciseSet> copyExerciseSets = exerciseSets.getValue();
        if (index >= copyExerciseSets.size() - 1) {
            Log.e(TAG, "HandleNavigateLeft: already furthest right");
            exerciseIndex.setValue(copyExerciseSets.size() - 1);
        } else {
            ExerciseSet e = copyExerciseSets.get(index);
            if (e.getStep().endsWith(".a")) {
                // if the last step, then there will be a '.b'
                if (index != copyExerciseSets.size() - 2) {
                    exerciseIndex.setValue(Math.min(index + 2, copyExerciseSets.size() - 1));
                }
            } else {
                exerciseIndex.setValue(index + 1);
            }
        }
    }

    public void CompleteSet(String weight, String reps) {
        final int index = exerciseIndex.getValue();
        final ExerciseSet exerciseSet = exerciseSets.getValue().get(index);
        // if there is a timer, then this is a cancel button
        if (timer != null) {
            timer.cancel();
            timer = null;
            SetTimerText(exerciseSet);
            return;
        }

        // the logic inside to set the text should never be necessary, but we need the check
        if (exerciseRecords.get(index).getSetsCount() ==
                exerciseSet.getSets()) {
            completeSetMessage.setValue(getString(R.string.complete_exercise));
            return;
        }

        exerciseRecords.get(index).addSet(
                new SetRecord(Double.parseDouble(weight),
                        Integer.parseInt(reps)));

        timer = new CountDownTimer(
                exerciseSet.getRest() * 1000,
                50) {
            @Override
            public void onTick(long millisUntilFinished) {
                UpdateRestTimerProgress(millisUntilFinished / 1000.0);
            }

            @Override
            public void onFinish() {
                timer = null;
                SetTimerText(exerciseSets.getValue().get(exerciseIndex.getValue()));
            }
        };
        SetTimerText(exerciseSet);
        timer.start();
    }

    private void UpdateRestTimerProgress(double rest) {
        restProgress.setValue((int) (restMax.getValue() - rest * 1000));
        restValue.setValue(String.format(Locale.getDefault(), "%.1f%s %s",
                rest, getString(R.string.seconds_abbrev),
                getString(R.string.rest)));
    }

    // region getters
    public LiveData<ExerciseSet> getExercise() {
        return exerciseMutableLiveData;
    }

    public LiveData<Integer> getIsLoading() {
        return isLoading;
    }

    public LiveData<Integer> getHasLeft() {
        return hasLeft;
    }

    public LiveData<Integer> getHasRight() {
        return hasRight;
    }

    public LiveData<String> getCompleteSetMessage() {
        return completeSetMessage;
    }

    public LiveData<Integer> getRestMax() {
        return restMax;
    }

    public LiveData<Integer> getRestProgress() {
        return restProgress;
    }

    public LiveData<String> getRestValue() {
        return restValue;
    }

    public LiveData<String> getWeightDisplayValue() {
        return weightDisplayValue;
    }

    public LiveData<String> getRepsDisplayValue() {
        return repsDisplayValue;
    }
    // endregion getters
}
