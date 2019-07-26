package com.litus_animae.refitted.models;

import android.app.Application;
import android.os.CountDownTimer;
import android.util.Log;
import android.view.View;

import androidx.annotation.NonNull;
import androidx.lifecycle.AndroidViewModel;
import androidx.lifecycle.LiveData;
import androidx.lifecycle.MediatorLiveData;
import androidx.lifecycle.MutableLiveData;
import androidx.lifecycle.Transformations;

import com.litus_animae.refitted.R;
import com.litus_animae.refitted.data.ExerciseRepository;

import java.util.List;
import java.util.Locale;

public class ExerciseViewModel extends AndroidViewModel {
    private static final String TAG = "ExerciseViewModel";
    private static final double defaultWeight = 25;

    private ExerciseRepository exerciseRepo;

    private LiveData<ExerciseSet> exerciseMutableLiveData;
    private LiveData<Integer> isLoading;
    private MutableLiveData<Boolean> isLoadingBool;
    private LiveData<Integer> hasLeft;
    private MutableLiveData<Boolean> hasLeftBool;
    private LiveData<Integer> hasRight;
    private MutableLiveData<Boolean> hasRightBool;
    private MediatorLiveData<List<ExerciseSet>> exerciseSets = new MediatorLiveData<>();
    private MediatorLiveData<List<ExerciseRecord>> exerciseRecords = new MediatorLiveData<>();
    private MutableLiveData<Integer> exerciseIndex = new MutableLiveData<>();
    private MutableLiveData<String> completeSetMessage = new MutableLiveData<>();
    private CountDownTimer timer;
    private MutableLiveData<Integer> restMax = new MutableLiveData<>();
    private MutableLiveData<Integer> restProgress = new MutableLiveData<>();
    private MutableLiveData<String> restValue = new MutableLiveData<>();
    private MediatorLiveData<String> weightDisplayValue = new MediatorLiveData<>();
    private MediatorLiveData<String> repsDisplayValue = new MediatorLiveData<>();

    public ExerciseViewModel(@NonNull Application application) {
        super(application);
        exerciseRepo = new ExerciseRepository(application);

        isLoadingBool = new MutableLiveData<>();
        isLoadingBool.setValue(true);
        isLoading = Transformations.map(isLoadingBool, isLoad -> isLoad ? View.VISIBLE : View.GONE);

        SetupLeftRightTransforms();

        exerciseIndex.setValue(0);
        exerciseSets.addSource(exerciseRepo.getExercises(),
                exercises -> exerciseSets.setValue(exercises));
        exerciseRecords.addSource(exerciseRepo.getRecords(),
                records -> {
                    exerciseRecords.setValue(records);
                    UpdateVisibleExercise(exerciseIndex.getValue());
                });

        SetupWeightAndRepsTransforms();

        exerciseMutableLiveData = Transformations.switchMap(exerciseSets,
                set -> Transformations.map(exerciseIndex,
                        this::UpdateVisibleExercise));
    }

    private void SetupWeightAndRepsTransforms() {
        LiveData<String> weightSeedValue = Transformations.switchMap(exerciseRecords, records ->
                Transformations.map(exerciseIndex, index ->
                        FormatWeightDisplay(records.get(index).getSetsCount() > 0 ?
                                records.get(index).getSet(-1).getWeight() :
                                defaultWeight)));
        LiveData<String> repsSeedValue = Transformations.switchMap(exerciseRecords, records ->
                Transformations.map(exerciseIndex, index ->
                        FormatRepsDisplay(records.get(index).getSetsCount() > 0 ?
                                records.get(index).getSet(-1).getReps() :
                                records.get(index).getTargetSet().getReps())));
        weightDisplayValue.addSource(weightSeedValue, v ->
                weightDisplayValue.setValue(v));
        weightDisplayValue.addSource(weightDisplayValue, v ->
        {
            // is this going to be heavy?
            Log.d(TAG, "SetupWeightAndRepsTransforms: reviewing changing weightDisplayValue");
            double value;
            try {
                value = Double.parseDouble(v);
            } catch (Exception ex) {
                Log.e(TAG, "SetupWeightAndRepsTransforms: ", ex);
                value = 0;
            }
            if (value != Math.round(value * 2) / 2.0) {
                Log.d(TAG, "SetupWeightAndRepsTransforms: had to reformat weightDisplayValue");
                weightDisplayValue.setValue(FormatWeightDisplay(value));
            }
        });
        repsDisplayValue.addSource(repsSeedValue, v ->
                repsDisplayValue.setValue(v));
        repsDisplayValue.addSource(repsDisplayValue, v ->
        {
            // is this going to be heavy?
            Log.d(TAG, "SetupWeightAndRepsTransforms: reviewing changing repsDisplayValue");
            int value;
            try {
                value = Integer.parseInt(v);
            } catch (Exception ex) {
                Log.e(TAG, "SetupWeightAndRepsTransforms: ", ex);
                value = 0;
            }
            if (value < 0) {
                Log.d(TAG, "SetupWeightAndRepsTransforms: had to reformat repsDisplayValue");
                repsDisplayValue.setValue(FormatRepsDisplay(value));
            }
        });
    }

    private void SetupLeftRightTransforms() {
        hasLeftBool = new MutableLiveData<>();
        hasLeftBool.setValue(false);
        hasLeft = Transformations.map(hasLeftBool, enable -> enable ? View.VISIBLE : View.GONE);

        hasRightBool = new MutableLiveData<>();
        hasRightBool.setValue(true);
        hasRight = Transformations.map(hasRightBool, enable -> enable ? View.VISIBLE : View.GONE);
    }

    @Override
    protected void onCleared() {
        super.onCleared();
        exerciseRepo.Shutdown();
    }

    public void loadExercises(String day, String workoutId) {
        isLoadingBool.setValue(true);
        exerciseRepo.LoadExercises(day, workoutId);
    }

    public void UpdateWeightDisplay(double change) {
        // leaving this as a warning as I don't know when this would be null
        double value = Double.parseDouble(weightDisplayValue.getValue()) + change;
        if (value < 0) {
            SetWeightDisplay(0);
        } else {
            SetWeightDisplay(value);
        }
    }

    private void SetWeightDisplay(double value) {
        weightDisplayValue.setValue(FormatWeightDisplay(value));
    }

    private static String FormatWeightDisplay(double value) {
        value = Math.round(value * 2) / 2.0;
        if (value < 0) {
            value = 0;
        }
        return String.format(Locale.getDefault(), "%.1f", value);
    }

    public void UpdateRepsDisplay(boolean increase) {
        // leaving this as a warning as I don't know when this would be null
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
        repsDisplayValue.setValue(FormatRepsDisplay(value));
    }

    private static String FormatRepsDisplay(int value) {
        if (value < 0) {
            value = 0;
        }
        return String.format(Locale.getDefault(), "%d", value);
    }

    private boolean CheckForAlternateExerciseSet(int index, ExerciseSet e) {
        boolean result = false;
        if (!e.hasAlternate() && e.getStep().endsWith(".a")) {
            Log.d(TAG, "CheckForAlternateExerciseSet: setting up new alternate for .a");
            // leaving this as a warning as I don't know when this would be null
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
        final List<ExerciseSet> copyExerciseSets = exerciseSets.getValue();
        if (copyExerciseSets == null) {
            Log.d(TAG, "UpdateVisibleExercise: exerciseSets is not yet set, returning default");
            return new ExerciseSet();
        }
        // leaving this as a warning as I don't know when this would be null
        if (isLoadingBool.getValue()) {
            isLoadingBool.setValue(false);
        }
        Log.d(TAG, "UpdateVisibleExercise: updating to index " + index);
        ExerciseSet e = copyExerciseSets.get(index);
        boolean wasChanged = false;
        if (e.hasAlternate() || e.getStep().endsWith(".a")) {
            Log.d(TAG, "UpdateVisibleExercise: checking for alternate...");
            wasChanged = CheckForAlternateExerciseSet(index, e);
            // leaving this as a warning as I don't know when this would be null
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

        final List<ExerciseRecord> records = exerciseRecords.getValue();
        SetTimerText(e, records);
        if (wasChanged) {
            exerciseSets.setValue(copyExerciseSets);
        }
        return e;
    }

    private void SetTimerText(ExerciseSet e, List<ExerciseRecord> records) {
        // leaving this as a warning as I don't know when this would be null
        ExerciseRecord currentRecord = getExerciseRecord(e, records, exerciseIndex.getValue());
        if (timer == null) {
            if (currentRecord.getSetsCount() == e.getSets()) {
                completeSetMessage.setValue(getString(R.string.complete_exercise));
            } else {
                completeSetMessage.setValue(getString(R.string.complete_set) +
                        String.format(Locale.getDefault(), " %d %s %d",
                                // using the LiveData here because the value may have changed
                                currentRecord.getSetsCount() + 1,
                                getString(R.string.word_of), e.getSets()));
            }
            restMax.setValue(e.getRest() * 1000);
            UpdateRestTimerProgress(e.getRest());
        } else {
            completeSetMessage.setValue(getString(R.string.cancel_rest));
        }
    }

    private ExerciseRecord getExerciseRecord(ExerciseSet e, List<ExerciseRecord> records, int index) {
        ExerciseRecord currentRecord;
        if (records == null || records.size() < index) {
            currentRecord = new ExerciseRecord(e);
        } else {
            currentRecord = records.get(index);
        }
        return currentRecord;
    }

    private String getString(int resourceId) {
        return getApplication().getResources().getString(resourceId);
    }

    public void SwapToAlternate() {
        final List<ExerciseSet> copyExerciseSet = exerciseSets.getValue();
        // leaving this as a warning as I don't know when this would be null
        copyExerciseSet.get(exerciseIndex.getValue()).setActive(false);
        exerciseSets.setValue(copyExerciseSet);
    }

    public void NavigateLeft() {
        // leaving this as a warning as I don't know when this would be null
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
        // leaving this as a warning as I don't know when this would be null
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
        // leaving this as a warning as I don't know when this would be null
        final int index = exerciseIndex.getValue();
        final ExerciseSet exerciseSet = exerciseSets.getValue().get(index);
        final List<ExerciseRecord> records = exerciseRecords.getValue();
        final ExerciseRecord record = getExerciseRecord(exerciseSet, records, index);
        // if there is a timer, then this is a cancel button
        if (timer != null) {
            timer.cancel();
            timer = null;
            SetTimerText(exerciseSet, records);
            return;
        }

        // the logic inside to set the text should never be necessary, but we need the check
        if (record.getSetsCount() == exerciseSet.getSets()) {
            completeSetMessage.setValue(getString(R.string.complete_exercise));
            return;
        }

        SetRecord newRecord = new SetRecord(exerciseSet,
                Double.parseDouble(weight), Integer.parseInt(reps));
        exerciseRepo.StoreSetRecord(newRecord);
        record.addSet(newRecord);

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
                SetTimerText(exerciseSets.getValue().get(exerciseIndex.getValue()),
                        exerciseRecords.getValue());
            }
        };
        SetTimerText(exerciseSet, records);
        exerciseRecords.setValue(records);
        timer.start();
    }

    private void UpdateRestTimerProgress(double rest) {
        // leaving this as a warning as I don't know when this would be null
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

    public MutableLiveData<String> getWeightDisplayValue() {
        return weightDisplayValue;
    }

    public MutableLiveData<String> getRepsDisplayValue() {
        return repsDisplayValue;
    }
    // endregion getters
}
