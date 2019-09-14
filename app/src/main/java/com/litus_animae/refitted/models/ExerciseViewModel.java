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
    private CountDownTimer timer;

    // region livedata
    private LiveData<ExerciseSet> exerciseMutableLiveData;
    private LiveData<String> targetExerciseReps;
    private LiveData<Integer> isLoading;
    private MutableLiveData<Boolean> isLoadingBool;
    private LiveData<Integer> hasLeft;
    private MutableLiveData<Boolean> hasLeftBool;
    private LiveData<Integer> hasRight;
    private MutableLiveData<Boolean> hasRightBool;
    private MediatorLiveData<List<ExerciseSet>> exerciseSets = new MediatorLiveData<>();
    private MediatorLiveData<List<ExerciseRecord>> exerciseRecords = new MediatorLiveData<>();
    private MutableLiveData<Integer> exerciseIndex = new MutableLiveData<>();
    private MutableLiveData<Double> restRemaining = new MutableLiveData<>();
    private MutableLiveData<Integer> restMax = new MutableLiveData<>();
    private LiveData<Integer> restProgress = Transformations.switchMap(restMax, max -> {
        return Transformations.map(restRemaining, rest -> (int) (max - rest * 1000));
    });
    private LiveData<String> restValue = Transformations.map(restRemaining, rest ->
            String.format(Locale.getDefault(), "%.1f%s %s",
                    rest, getString(R.string.seconds_abbrev),
                    getString(R.string.rest)));
    private MediatorLiveData<String> weightDisplayValue = new MediatorLiveData<>();
    private MediatorLiveData<String> repsDisplayValue = new MediatorLiveData<>();

    private MutableLiveData<CountDownTimer> timerMutableLiveData = new MutableLiveData<>();
    private LiveData<ExerciseRecord> currentRecord = Transformations.switchMap(exerciseIndex, index -> {
        return Transformations.map(exerciseRecords, records -> {
            if (index == null || records == null || index >= records.size()) {
                return null;
            }
            return records.get(index);
        });
    });

    private LiveData<Boolean> completeSetButtonEnabled = Transformations.switchMap(currentRecord, record -> {
        return Transformations.switchMap(exerciseMutableLiveData, exercise -> {
            return Transformations.switchMap(record.getSetsCount(), setsCompleted -> {
                return Transformations.map(timerMutableLiveData, timer -> {
                    if (timer == null){
                        return setsCompleted < exercise.getSets();
                    }
                    return true;
                });

            });
        });
    });

    private LiveData<String> completeSetMessage = Transformations.switchMap(currentRecord, currentRecord -> {
        if (currentRecord == null) {
            Log.w(TAG, "completeSetMessage: currentRecord was null");
        }
        Log.i(TAG, "completeSetMessage: setting switch value 1");
        return Transformations.switchMap(timerMutableLiveData, timer -> {
            if (timer == null) {
                Log.i(TAG, "completeSetMessage: setting switch value 2");
                return Transformations.switchMap(currentRecord.getSetsCount(), completeSetsCount -> {
                    Log.i(TAG, "completeSetMessage: setting switch value 4");
                    return Transformations.map(exerciseMutableLiveData, exercise -> {
                        Log.i(TAG, "completeSetMessage: setting switch value 5");
                        restMax.setValue(exercise.getRest() * 1000);
                        restRemaining.setValue((double) exercise.getRest());

                        if (completeSetsCount == exercise.getSets()) {
                            return getString(R.string.complete_exercise);
                        } else {
                            return getString(R.string.complete_set) +
                                    String.format(Locale.getDefault(), " %d %s %d",
                                            // using the LiveData here because the value may have changed
                                            completeSetsCount + 1,
                                            getString(R.string.word_of), exercise.getSets());
                        }
                    });
                });
            } else {
                Log.i(TAG, "completeSetMessage: setting switch value 3");
                MutableLiveData<String> result = new MutableLiveData<>();
                result.setValue(getString(R.string.cancel_rest));
                return result;
            }
        });
    });
    // endregion

    public ExerciseViewModel(@NonNull Application application) {
        super(application);
        exerciseRepo = new ExerciseRepository(application);

        isLoadingBool = new MutableLiveData<>();
        isLoadingBool.setValue(true);
        isLoading = Transformations.map(isLoadingBool, isLoad -> isLoad ? View.VISIBLE : View.GONE);

        setupLeftRightTransforms();

        exerciseIndex.setValue(0);
        exerciseSets.addSource(exerciseRepo.getExercises(),
                exercises -> exerciseSets.setValue(exercises));
        exerciseRecords.addSource(exerciseRepo.getRecords(),
                records -> {
                    exerciseRecords.setValue(records);
                    updateVisibleExercise(exerciseIndex.getValue());
                });
        exerciseMutableLiveData = Transformations.switchMap(exerciseSets,
                set -> Transformations.map(exerciseIndex,
                        this::updateVisibleExercise));

        setupWeightAndRepsTransforms();
        timerMutableLiveData.setValue(null);
    }

    private void setupWeightAndRepsTransforms() {
        targetExerciseReps = Transformations.map(exerciseMutableLiveData, exercise ->
        {
            if (exercise == null) {
                return "";
            }
            if (exercise.getReps() < 0) {
                return getString(R.string.to_failure);
            }
            if (exercise.isToFailure()) {
                return String.format(Locale.getDefault(), "%d %s",
                        exercise.getReps(), getString(R.string.to_failure_note));
            }
            return String.format(Locale.getDefault(), "%d", exercise.getReps());
        });
        // FIXME this should monitor the mutableexercise rather than the index
        LiveData<String> weightSeedValue = Transformations.switchMap(exerciseRecords, records ->
                Transformations.switchMap(exerciseIndex, index -> {
                    ExerciseRecord r = records.get(index);
                    return Transformations.map(r.getLatestSet(), latestSet -> {
                        if (latestSet != null) {
                            return formatWeightDisplay(latestSet.getWeight());
                        }
                        return formatWeightDisplay(defaultWeight);
                    });
                }));
        LiveData<String> repsSeedValue = Transformations.switchMap(exerciseRecords, records ->
                // FIXME this should monitor the mutableexercise rather than the index
                Transformations.switchMap(exerciseIndex, index -> {
                    if (index >= records.size()) {
                        MutableLiveData<String> emptyResult = new MutableLiveData<>();
                        emptyResult.setValue(formatRepsDisplay(0));
                        return emptyResult;
                    }
                    return Transformations.switchMap(records.get(index).getSetsCount(), count -> {
                        if (count > 0) {
                            return Transformations.map(records.get(index).getSet(-1),
                                    latestSet -> formatRepsDisplay(latestSet.getReps()));
                        }
                        MutableLiveData<String> result = new MutableLiveData<>();
                        result.setValue(formatRepsDisplay(records.get(index).getTargetSet().getReps()));
                        return result;
                    });
                }));
        weightDisplayValue.addSource(weightSeedValue, v ->
                weightDisplayValue.setValue(v));
        weightDisplayValue.addSource(weightDisplayValue, v ->
        {
            // is this going to be heavy?
            Log.d(TAG, "setupWeightAndRepsTransforms: reviewing changing weightDisplayValue");
            double value;
            try {
                value = Double.parseDouble(v);
            } catch (Exception ex) {
                Log.e(TAG, "setupWeightAndRepsTransforms: ", ex);
                value = 0;
            }
            if (value != Math.round(value * 2) / 2.0) {
                Log.d(TAG, "setupWeightAndRepsTransforms: had to reformat weightDisplayValue");
                weightDisplayValue.setValue(formatWeightDisplay(value));
            }
        });
        repsDisplayValue.addSource(repsSeedValue, v ->
                repsDisplayValue.setValue(v));
        repsDisplayValue.addSource(repsDisplayValue, v ->
        {
            // is this going to be heavy?
            Log.d(TAG, "setupWeightAndRepsTransforms: reviewing changing repsDisplayValue");
            int value;
            try {
                value = Integer.parseInt(v);
            } catch (Exception ex) {
                Log.e(TAG, "setupWeightAndRepsTransforms: ", ex);
                value = 0;
            }
            if (value < 0) {
                Log.d(TAG, "setupWeightAndRepsTransforms: had to reformat repsDisplayValue");
                repsDisplayValue.setValue(formatRepsDisplay(value));
            }
        });
    }

    private void setupLeftRightTransforms() {
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
        exerciseRepo.shutdown();
    }

    public void loadExercises(String day, String workoutId) {
        isLoadingBool.setValue(true);
        exerciseRepo.loadExercises(day, workoutId);
    }

    public void updateWeightDisplay(double change) {
        // leaving this as a warning as I don't know when this would be null
        double value = Double.parseDouble(weightDisplayValue.getValue()) + change;
        if (value < 0) {
            setWeightDisplay(0);
        } else {
            setWeightDisplay(value);
        }
    }

    private void setWeightDisplay(double value) {
        weightDisplayValue.setValue(formatWeightDisplay(value));
    }

    private static String formatWeightDisplay(double value) {
        value = Math.round(value * 2) / 2.0;
        if (value < 0) {
            value = 0;
        }
        return String.format(Locale.getDefault(), "%.1f", value);
    }

    public void updateRepsDisplay(boolean increase) {
        // leaving this as a warning as I don't know when this would be null
        int value = Integer.parseInt(repsDisplayValue.getValue());
        if (increase) {
            setRepsDisplay(value + 1);
        } else if (value > 0) {
            setRepsDisplay(value - 1);
        } else {
            setRepsDisplay(0);
        }
    }

    private void setRepsDisplay(int value) {
        repsDisplayValue.setValue(formatRepsDisplay(value));
    }

    private static String formatRepsDisplay(int value) {
        if (value < 0) {
            value = 0;
        }
        return String.format(Locale.getDefault(), "%d", value);
    }

    private boolean checkForAlternateExerciseSet(int index, ExerciseSet e) {
        boolean result = false;
        if (!e.hasAlternate() && e.getStep().endsWith(".a")) {
            Log.d(TAG, "checkForAlternateExerciseSet: setting up new alternate for .a");
            // leaving this as a warning as I don't know when this would be null
            e.setAlternate(exerciseSets.getValue().get(index + 1));
            e.getAlternate().setActive(false);
            e.getAlternate().setAlternate(e);
            result = true;
        } else if (!e.isActive() && e.getStep().endsWith(".a")) {
            Log.d(TAG, "checkForAlternateExerciseSet: navigated to .a, but .b is active");
            exerciseIndex.setValue(index + 1);
            e = e.getAlternate();
        } else if (!e.isActive() && e.getStep().endsWith(".b")) {
            Log.d(TAG, "checkForAlternateExerciseSet: navigated to .b, but .a is active");
            exerciseIndex.setValue(index - 1);
            e = e.getAlternate();
        }
        e.setActive(true);
        return result;
    }

    private ExerciseSet updateVisibleExercise(int index) {
        final List<ExerciseSet> copyExerciseSets = exerciseSets.getValue();
        if (copyExerciseSets == null || copyExerciseSets.size() < 1) {
            Log.d(TAG, "updateVisibleExercise: exerciseSets is not yet set, returning default");
            return new ExerciseSet();
        }
        // leaving this as a warning as I don't know when this would be null
        if (isLoadingBool.getValue()) {
            isLoadingBool.setValue(false);
        }
        Log.d(TAG, "updateVisibleExercise: updating to index " + index);
        ExerciseSet e = copyExerciseSets.get(index);
        boolean wasChanged = false;
        if (e.hasAlternate() || e.getStep().endsWith(".a")) {
            Log.d(TAG, "updateVisibleExercise: checking for alternate...");
            wasChanged = checkForAlternateExerciseSet(index, e);
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

        //setTimerText(e);
        if (wasChanged) {
            exerciseSets.setValue(copyExerciseSets);
        }
        return e;
    }

    private String getString(int resourceId) {
        return getApplication().getResources().getString(resourceId);
    }

    public void swapToAlternate() {
        final List<ExerciseSet> copyExerciseSet = exerciseSets.getValue();
        // leaving this as a warning as I don't know when this would be null
        copyExerciseSet.get(exerciseIndex.getValue()).setActive(false);
        exerciseSets.setValue(copyExerciseSet);
    }

    public void navigateLeft() {
        // leaving this as a warning as I don't know when this would be null
        int index = exerciseIndex.getValue();
        if (index < 1) {
            Log.e(TAG, "handleNavigateLeft: already furthest left");
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

    public void navigateRight() {
        // leaving this as a warning as I don't know when this would be null
        int index = exerciseIndex.getValue();
        List<ExerciseSet> copyExerciseSets = exerciseSets.getValue();
        if (index >= copyExerciseSets.size() - 1) {
            Log.e(TAG, "handleNavigateLeft: already furthest right");
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

    public void completeSet(String weight, String reps) {
        // leaving this as a warning as I don't know when this would be null
        final int index = exerciseIndex.getValue();
        final ExerciseSet exerciseSet = exerciseSets.getValue().get(index);
        // if there is a timer, then this is a cancel button
        if (timer != null) {
            timer.cancel();
            timer = null;
            restRemaining.setValue((double)restMax.getValue());
            timerMutableLiveData.setValue(null);
            //setTimerText(exerciseSet);
            return;
        }

        // FIXME this won't be correct if not observed, but it would only be necessary if not observed either, double negative
        if (!completeSetButtonEnabled.getValue()){
            Log.w(TAG, "completeSet: someone isn't using the enabled value");
            return;
        }

        SetRecord newRecord = new SetRecord(exerciseSet,
                Double.parseDouble(weight), Integer.parseInt(reps));
        exerciseRepo.storeSetRecord(newRecord);

        timer = new CountDownTimer(
                exerciseSet.getRest() * 1000,
                50) {
            @Override
            public void onTick(long millisUntilFinished) {
                //updateRestTimerProgress(millisUntilFinished / 1000.0);
                restRemaining.setValue(millisUntilFinished / 1000.0);
            }

            @Override
            public void onFinish() {
                timer = null;
                //setTimerText(exerciseSets.getValue().get(exerciseIndex.getValue()));
                timerMutableLiveData.setValue(null);
            }
        };
        //setTimerText(exerciseSet);
        timerMutableLiveData.setValue(timer);
        timer.start();
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

    public LiveData<String> getTargetExerciseReps() {
        return targetExerciseReps;
    }

    public LiveData<Boolean> getCompleteSetButtonEnabled() {
        return completeSetButtonEnabled;
    }
    // endregion getters
}
