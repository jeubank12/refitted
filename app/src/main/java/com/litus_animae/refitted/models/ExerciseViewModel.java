package com.litus_animae.refitted.models;

import android.os.CountDownTimer;
import android.view.View;

import androidx.hilt.lifecycle.ViewModelInject;
import androidx.lifecycle.LiveData;
import androidx.lifecycle.MediatorLiveData;
import androidx.lifecycle.MutableLiveData;
import androidx.lifecycle.Transformations;
import androidx.lifecycle.ViewModel;

import com.litus_animae.refitted.R;
import com.litus_animae.refitted.data.ExerciseRepository;
import com.litus_animae.refitted.util.LogUtil;
import com.litus_animae.refitted.util.ParameterizedResource;
import com.litus_animae.refitted.util.ParameterizedStringArrayResource;
import com.litus_animae.refitted.util.ParameterizedStringResource;

import java.time.Instant;
import java.util.List;
import java.util.Locale;

public class ExerciseViewModel extends ViewModel {
    private static final String TAG = "ExerciseViewModel";
    public static final double defaultDbWeight = 25;
    public static final double defaultBbWeight = 45;
    public static final double defaultBodyweight = 45;
    private final LogUtil log;

    private ExerciseRepository exerciseRepo;
    private CountDownTimer timer;

    // region livedata
    private LiveData<ExerciseSet> currentExercise;
    private LiveData<ParameterizedResource> targetExerciseReps;
    private LiveData<Integer> isLoading;
    private MutableLiveData<Boolean> isLoadingBool;
    private LiveData<Integer> hasLeft;
    private MutableLiveData<Boolean> hasLeftBool;
    private LiveData<Integer> hasRight;
    private MutableLiveData<Boolean> hasRightBool;
    private MediatorLiveData<List<ExerciseSet>> exerciseSets = new MediatorLiveData<>();
    private LiveData<List<ExerciseRecord>> exerciseRecords;
    private MutableLiveData<Integer> exerciseIndex = new MutableLiveData<>();
    private MediatorLiveData<String> weightDisplayValue = new MediatorLiveData<>();
    private MediatorLiveData<String> repsDisplayValue = new MediatorLiveData<>();

    private MutableLiveData<CountDownTimer> timerMutableLiveData = new MutableLiveData<>();

    private LiveData<ExerciseRecord> currentRecord = Transformations.switchMap(exerciseIndex, index ->
            Transformations.map(exerciseRecords, records -> {
                if (index == null || records == null || index >= records.size()) {
                    return null;
                }
                return records.get(index);
            }));

    // TODO make disabled for a second after click
    private LiveData<Boolean> completeSetButtonEnabled = Transformations.switchMap(currentRecord, record -> {
        if (record == null) {
            MutableLiveData<Boolean> result = new MutableLiveData<>();
            result.setValue(false);
            return result;
        }
        return Transformations.switchMap(currentExercise, exercise -> {
            return Transformations.switchMap(record.getSetsCount(), setsCompleted -> {
                return Transformations.map(timerMutableLiveData, timer -> {
                    if (timer == null) {
                        return setsCompleted < exercise.getSets();
                    }
                    return true;
                });

            });
        });
    });

    private MutableLiveData<Double> restRemaining = new MutableLiveData<>();
    private MutableLiveData<Integer> restMax = new MutableLiveData<>();
    private LiveData<Integer> restProgress = Transformations.switchMap(restMax, max -> {
        return Transformations.map(restRemaining, rest -> (int) (max - rest * 1000));
    });
    // TODO new text
    private LiveData<ParameterizedResource> restValue = Transformations.map(restRemaining, rest ->
            new ParameterizedStringResource(R.string.seconds_rest_phrase, new Double[]{
                    rest
            }));

    // FIXME optimize the layers of transformations
    private LiveData<ParameterizedResource> completeSetMessage;
    private MediatorLiveData<Boolean> showAsDouble = new MediatorLiveData<>();

    private Instant startLoad;
    // endregion
    private LiveData<Boolean> isBarbellExercise;

    @ViewModelInject
    public ExerciseViewModel(ExerciseRepository exerciseRepo, LogUtil logUtil) {
        this.exerciseRepo = exerciseRepo;
        this.log = logUtil;

        isLoadingBool = new MutableLiveData<>();
        isLoadingBool.setValue(true);
        isLoading = Transformations.map(isLoadingBool, isLoad -> isLoad ? View.VISIBLE : View.GONE);

        setupLeftRightTransforms();

        setupCompleteSetMessage();

        exerciseIndex.setValue(0);
        exerciseSets.addSource(this.exerciseRepo.getExercises(),
                exercises -> {
                    Instant endLoad = java.time.Instant.now();
                    exerciseSets.setValue(exercises);
                    if (startLoad != null) {
                        startLoad = null;
                    }
                });
        exerciseRecords = this.exerciseRepo.getRecords();
        currentExercise = Transformations.switchMap(exerciseSets,
                set -> Transformations.switchMap(exerciseRecords,
                        records -> Transformations.map(exerciseIndex,
                                this::updateVisibleExercise)));

        setupWeightAndRepsTransforms();
        timerMutableLiveData.setValue(null);
        showAsDouble.setValue(false);
        isBarbellExercise = Transformations.map(currentExercise, targetSet -> {
            if (targetSet == null) {
                return false;
            }
            if (targetSet.getRepsUnit().equalsIgnoreCase("minutes") ||
                    targetSet.getRepsUnit().equalsIgnoreCase("seconds")) {
                return false;
            }
            if (targetSet.getExerciseName().toLowerCase().contains("db") ||
                    targetSet.getExerciseName().toLowerCase().contains("dumbbell")) {
                return false;
            }
            if (targetSet.getExerciseName().toLowerCase().contains("bb") ||
                    targetSet.getExerciseName().toLowerCase().contains("barbell") ||
                    targetSet.getExerciseName().toLowerCase().contains("press")) {
                return true;
            }
            if (targetSet.getNote().toLowerCase().contains("db") ||
                    targetSet.getNote().toLowerCase().contains("dumbbell")) {
                return false;
            }
            return targetSet.getNote().toLowerCase().contains("bb") ||
                    targetSet.getNote().toLowerCase().contains("barbell") ||
                    targetSet.getNote().toLowerCase().contains("press");
        });
        showAsDouble.addSource(isBarbellExercise, isBarbell -> {
            if (isBarbell != null) {
                showAsDouble.setValue(isBarbell);
            } else {
                showAsDouble.setValue(false);
            }
        });
    }

    private void setupCompleteSetMessage() {
        completeSetMessage = completeSetMessage = Transformations.switchMap(currentRecord, record -> {
            if (record == null) {
                log.w(TAG, "completeSetMessage: record was null");
                MutableLiveData<ParameterizedResource> result = new MutableLiveData<>();
                result.setValue(new ParameterizedStringResource(R.string.complete_set));
                return result;
            }
            return Transformations.switchMap(timerMutableLiveData, timer -> {
                if (timer == null) {
                    return Transformations.switchMap(record.getSetsCount(), completeSetsCount -> {
                        return Transformations.map(currentExercise, exercise -> {
                            restMax.setValue(exercise.getRest() * 1000);
                            restRemaining.setValue((double) exercise.getRest());

                            if (completeSetsCount == exercise.getSets()) {
                                return new ParameterizedStringResource(R.string.complete_exercise);
                            } else {
                                // TODO if time unit, display "Start Circuit"
//                            if (exercise.getRepsUnit() != null && (exercise.getRepsUnit().equalsIgnoreCase("minutes") ||
//                                    exercise.getRepsUnit().equalsIgnoreCase("seconds"))) {
//                                return "TODO Start Exercise";
//                            }
                                if (exercise.getStep().contains(".1")) {
                                    // TODO determine if in sync with part 2
                                    return new ParameterizedStringResource(R.string.complete_superset_part_1,
                                            new Integer[]{
                                                    // using the LiveData here because the value may have changed
                                                    completeSetsCount + 1,
                                                    exercise.getSets()
                                            });
                                }
                                return new ParameterizedStringResource(R.string.complete_set_of_workout,
                                        new Integer[]{
                                                completeSetsCount + 1,
                                                exercise.getSets()
                                        });
                            }
                        });
                    });
                } else {
                    return Transformations.map(currentExercise, exercise -> {
                        //restMax.setValue(exercise.getRest() * 1000);
                        //restRemaining.setValue((double) exercise.getRest());

//                    if (exercise.getRepsUnit() != null && (exercise.getRepsUnit().equalsIgnoreCase("minutes") ||
//                            exercise.getRepsUnit().equalsIgnoreCase("seconds"))) {
//                        return "TODO Start Rest Early";
//                    }
//                    if (exercise.getStep().endsWith("a")){
//                        return "Complete superset then come back";
//                    }
                        return new ParameterizedStringResource(R.string.cancel_rest);
                    });
                }
            });
        });
    }

    private static String formatWeightDisplay(double value) {
        value = Math.round(value * 2) / 2.0;
        if (value < 0) {
            value = 0;
        }
        return String.format(Locale.getDefault(), "%.1f", value);
    }

    private static String formatRepsDisplay(int value) {
        if (value < 0) {
            value = 0;
        }
        return String.format(Locale.getDefault(), "%d", value);
    }

    private void setupWeightAndRepsTransforms() {
        targetExerciseReps = Transformations.map(currentExercise, exercise ->
        {
            // TODO kt, enforce exercise not null
            if (exercise.getReps() < 0) {
                return new ParameterizedStringResource(R.string.to_failure);
            }
            int resource = exercise.getRepsRange() > 0 ? R.array.exercise_reps_range : R.array.exercise_reps;
            int index = 0;
            // TODO kt, replace with empty
            if (!exercise.getRepsUnit().equals("")) {
                index += 2;
            }
            if (exercise.isToFailure()) {
                index += 1;
            }
            return new ParameterizedStringArrayResource(resource, index,
                    new Object[]{exercise.getReps(),
                            exercise.getReps() + exercise.getRepsRange(),
                            exercise.getRepsUnit()
                    });
        });
        LiveData<String> weightSeedValue = Transformations.switchMap(currentRecord,
                record -> {
                    if (record == null) {
                        MutableLiveData<String> result = new MutableLiveData<>();
                        result.setValue(formatWeightDisplay(defaultDbWeight));
                        return result;
                    }
                    return Transformations.map(record.getLatestSet(), latestSet -> {
                        if (latestSet != null) {
                            return formatWeightDisplay(latestSet.getWeight());
                        }
                        return formatWeightDisplay(determineSetDefaultWeight(record.getTargetSet()));
                    });
                });
        LiveData<String> repsSeedValue = Transformations.switchMap(currentRecord, record -> {
            if (record == null) {
                MutableLiveData<String> emptyResult = new MutableLiveData<>();
                emptyResult.setValue(formatRepsDisplay(0));
                return emptyResult;
            }
            return Transformations.switchMap(record.getSetsCount(), count -> {
                if (count > 0) {
                    return Transformations.map(record.getSet(-1),
                            latestSet -> formatRepsDisplay(latestSet.getReps()));
                }
                MutableLiveData<String> result = new MutableLiveData<>();
                // TODO target set should be a livedata
                if (record.getTargetSet().getRepsRange() > 0) {
                    result.setValue(formatRepsDisplay(
                            record.getTargetSet().getReps() +
                                    record.getTargetSet().getRepsRange()));
                } else {
                    // TODO if timed, default to last record reps if exists
                    result.setValue(formatRepsDisplay(record.getTargetSet().getReps()));
                }
                return result;
            });
        });
        weightDisplayValue.addSource(weightSeedValue, v ->
                weightDisplayValue.setValue(v));
        weightDisplayValue.addSource(weightDisplayValue, v ->
        {
            // is this going to be heavy?
            log.d(TAG, "setupWeightAndRepsTransforms: reviewing changing weightDisplayValue");
            double value;
            try {
                value = Double.parseDouble(v);
            } catch (Exception ex) {
                log.e(TAG, "setupWeightAndRepsTransforms: ", ex);
                value = 0;
            }
            if (value != Math.round(value * 2) / 2.0) {
                log.d(TAG, "setupWeightAndRepsTransforms: had to reformat weightDisplayValue");
                weightDisplayValue.setValue(formatWeightDisplay(value));
            }
        });
        repsDisplayValue.addSource(repsSeedValue, v ->
                repsDisplayValue.setValue(v));
        repsDisplayValue.addSource(repsDisplayValue, v ->
        {
            // is this going to be heavy?
            log.d(TAG, "setupWeightAndRepsTransforms: reviewing changing repsDisplayValue");
            int value;
            try {
                value = Integer.parseInt(v);
            } catch (Exception ex) {
                log.e(TAG, "setupWeightAndRepsTransforms: ", ex);
                value = 0;
            }
            if (value < 0) {
                log.d(TAG, "setupWeightAndRepsTransforms: had to reformat repsDisplayValue");
                repsDisplayValue.setValue(formatRepsDisplay(value));
            }
        });
    }

    private double determineSetDefaultWeight(ExerciseSet targetSet) {
        if (targetSet.getRepsUnit() != null && (targetSet.getRepsUnit().equalsIgnoreCase("minutes") ||
                targetSet.getRepsUnit().equalsIgnoreCase("seconds"))) {
            return defaultBodyweight;
        }
        if (targetSet.getExerciseName().toLowerCase().contains("db") ||
                targetSet.getExerciseName().toLowerCase().contains("dumbbell")) {
            return defaultDbWeight;
        }
        if (targetSet.getExerciseName().toLowerCase().contains("bb") ||
                targetSet.getExerciseName().toLowerCase().contains("barbell") ||
                targetSet.getExerciseName().toLowerCase().contains("press")) {
            return defaultBbWeight;
        }
        if (targetSet.getNote().toLowerCase().contains("db") ||
                targetSet.getNote().toLowerCase().contains("dumbbell")) {
            return defaultDbWeight;
        }
        if (targetSet.getNote().toLowerCase().contains("bb") ||
                targetSet.getNote().toLowerCase().contains("barbell") ||
                targetSet.getNote().toLowerCase().contains("press")) {
            return defaultBbWeight;
        }
        return defaultBodyweight;
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
        startLoad = java.time.Instant.now();
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

    private boolean checkForAlternateExerciseSet(int index, ExerciseSet e) {
        boolean result = false;
        if (!e.hasAlternate() && e.getStep().endsWith(".a")) {
            log.d(TAG, "checkForAlternateExerciseSet: setting up new alternate for .a");
            // leaving this as a warning as I don't know when this would be null
            e.setAlternate(exerciseSets.getValue().get(index + 1));
            e.getAlternate().setActive(false);
            e.getAlternate().setAlternate(e);
            result = true;
        } else if (!e.isActive() && e.getStep().endsWith(".a")) {
            log.d(TAG, "checkForAlternateExerciseSet: navigated to .a, but .b is active");
            exerciseIndex.setValue(index + 1);
            e = e.getAlternate();
        } else if (!e.isActive() && e.getStep().endsWith(".b")) {
            log.d(TAG, "checkForAlternateExerciseSet: navigated to .b, but .a is active");
            exerciseIndex.setValue(index - 1);
            e = e.getAlternate();
        }
        e.setActive(true);
        return result;
    }

    private ExerciseSet updateVisibleExercise(int index) {
        final List<ExerciseSet> copyExerciseSets = exerciseSets.getValue();
        if (copyExerciseSets == null || copyExerciseSets.size() < 1) {
            log.d(TAG, "updateVisibleExercise: exerciseSets is not yet set, returning default");
            return new ExerciseSet(new MutableExerciseSet());
        }
        // TODO might be able to remove this since the method is only called by livedata transformation
        // leaving this as a warning as I don't know when this would be null
        if (isLoadingBool.getValue()) {
            isLoadingBool.setValue(false);
        }
        log.d(TAG, "updateVisibleExercise: updating to index " + index);
        ExerciseSet e = copyExerciseSets.get(index);
        boolean wasChanged = false;
        if (e.hasAlternate() || e.getStep().endsWith(".a")) {
            log.d(TAG, "updateVisibleExercise: checking for alternate...");
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
            log.e(TAG, "handleNavigateLeft: already furthest left");
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
            log.e(TAG, "handleNavigateLeft: already furthest right");
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
        // TODO if this is a timed-exercise, detect whether we are in rest or execute
        if (timer != null) {
            timer.cancel();
            timer = null;
            // TODO if this is a timed-exercise, use unit instead of restmax
            restRemaining.setValue((double) restMax.getValue());
            timerMutableLiveData.setValue(null);
            //setTimerText(exerciseSet);
            return;
        }

        // FIXME this won't be correct if not observed, but it would only be necessary if not observed either, double negative
        if (!completeSetButtonEnabled.getValue()) {
            log.w(TAG, "completeSet: someone isn't using the enabled value");
            return;
        }

        SetRecord newRecord = new SetRecord(
                Double.parseDouble(weight),
                Integer.parseInt(reps),
                exerciseSet);
        exerciseRepo.storeSetRecord(newRecord);

        // TODO if this is superset part a then move to the next exercise
        if (exerciseSet.getStep().contains(".1")) {
            navigateRight();
            return;
        } else if (exerciseSet.getStep().contains(".2")) {
            // TODO don't navigate left if this is the last set
            navigateLeft();
        }

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
        return currentExercise;
    }

    public LiveData<Integer> getIsLoading() {
        return isLoading;
    }

    public LiveData<Boolean> getIsLoadingBool() {
        return isLoadingBool;
    }

    public LiveData<Integer> getHasLeft() {
        return hasLeft;
    }

    public LiveData<Integer> getHasRight() {
        return hasRight;
    }

    public LiveData<ParameterizedResource> getCompleteSetMessage() {
        return completeSetMessage;
    }

    public LiveData<Integer> getRestMax() {
        return restMax;
    }

    public LiveData<Integer> getRestProgress() {
        return restProgress;
    }

    public LiveData<ParameterizedResource> getRestValue() {
        return restValue;
    }

    public MutableLiveData<String> getWeightDisplayValue() {
        return weightDisplayValue;
    }

    public MutableLiveData<String> getRepsDisplayValue() {
        return repsDisplayValue;
    }

    public LiveData<ParameterizedResource> getTargetExerciseReps() {
        return targetExerciseReps;
    }

    public LiveData<Boolean> getCompleteSetButtonEnabled() {
        return completeSetButtonEnabled;
    }

    public LiveData<ExerciseRecord> getCurrentRecord() {
        return currentRecord;
    }

    public LiveData<Boolean> getIsBarbellExercise() {
        return isBarbellExercise;
    }

    public MutableLiveData<Boolean> getShowAsDouble() {
        return showAsDouble;
    }
    // endregion getters
}
