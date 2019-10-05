package com.litus_animae.refitted;

import android.content.Intent;
import android.content.SharedPreferences;
import android.os.Bundle;
import android.util.Log;
import android.view.Menu;
import android.view.MenuInflater;
import android.view.MenuItem;
import android.view.View;

import androidx.appcompat.app.AppCompatActivity;
import androidx.databinding.DataBindingUtil;
import androidx.fragment.app.DialogFragment;
import androidx.fragment.app.FragmentTransaction;
import androidx.lifecycle.ViewModelProviders;

import com.bugsee.library.Bugsee;
import com.bugsee.library.data.IssueSeverity;
import com.litus_animae.refitted.databinding.ActivityExerciseDetailViewBinding;
import com.litus_animae.refitted.fragments.ConfigureButtonsDialogFragment;
import com.litus_animae.refitted.fragments.ExerciseHistoryDialogFragment;
import com.litus_animae.refitted.fragments.WeightButton;
import com.litus_animae.refitted.models.ExerciseViewModel;

import java.util.ArrayList;
import java.util.EnumSet;
import java.util.Locale;

public class ExerciseDetailViewActivity extends AppCompatActivity implements ConfigureButtonsDialogFragment.OnButtonConfigurtionChangeListener {

    private static final String TAG = "ExerciseDetailViewActivity";
    private MenuItem switchToAlternateButton;
    private boolean show25 = true;
    private boolean show5 = true;
    private boolean showMore = true;
    private boolean showAsDouble = false;
    private ActivityExerciseDetailViewBinding binding;
    private ExerciseViewModel model;
    private String workout;
    private int day;

    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
        MenuInflater inflater = getMenuInflater();
        inflater.inflate(R.menu.alternate_menu, menu);
        switchToAlternateButton = menu.findItem(R.id.switch_to_alternate_menu_item);
//        MenuItem enable25 = menu.findItem(R.id.enable_25);
//        enable25.setChecked(show25);

        model.getExercise().observe(this, exerciseSet ->
                switchToAlternateButton.setVisible(exerciseSet.hasAlternate()));
        return true;
    }

    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        switch (item.getItemId()) {
            case R.id.switch_to_alternate_menu_item:
                Log.d(TAG, "onOptionsItemSelected: handle 'switch to alternate'");
                model.swapToAlternate();
                return true;
            case R.id.config_button_layout:
                Log.d(TAG, "onOptionsItemSelected: handle 'enable 2.5'");
                ConfigureButtonsDialogFragment configureButtonsDialogFragment =
                        ConfigureButtonsDialogFragment.newInstance(getCurrentLayout());
                configureButtonsDialogFragment.show(getSupportFragmentManager(), null);
                return true;
            case R.id.show_history:
                DialogFragment history = new ExerciseHistoryDialogFragment();
                history.show(getSupportFragmentManager(), null);
                return true;
            case R.id.report_bug:
                Bugsee.showReportDialog();
                return true;
            case R.id.feedback:
                Bugsee.showReportDialog(getString(R.string.improvement_prefix), getString(R.string.improvement_starter), IssueSeverity.VeryLow);
                return true;
            default:
                return false;
        }
    }

    private EnumSet<ConfigureButtonsDialogFragment.ButtonLayout> getCurrentLayout() {
        EnumSet<ConfigureButtonsDialogFragment.ButtonLayout> result =
                EnumSet.noneOf(ConfigureButtonsDialogFragment.ButtonLayout.class);
        if (show25) {
            result.add(ConfigureButtonsDialogFragment.ButtonLayout.show25);
        }
        if (show5) {
            result.add(ConfigureButtonsDialogFragment.ButtonLayout.show5);
        }
        if (showMore) {
            result.add(ConfigureButtonsDialogFragment.ButtonLayout.showMore);
        }
        if (showAsDouble) {
            result.add(ConfigureButtonsDialogFragment.ButtonLayout.showAsDouble);
        }
        return result;
    }

    private void updateCurrentLayout(EnumSet<ConfigureButtonsDialogFragment.ButtonLayout> newLayout) {
        show25 = newLayout.contains(ConfigureButtonsDialogFragment.ButtonLayout.show25);
        show5 = newLayout.contains(ConfigureButtonsDialogFragment.ButtonLayout.show5);
        showMore = newLayout.contains(ConfigureButtonsDialogFragment.ButtonLayout.showMore);
        showAsDouble = newLayout.contains(ConfigureButtonsDialogFragment.ButtonLayout.showAsDouble);
    }

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        model = ViewModelProviders.of(this).get(ExerciseViewModel.class);
        binding = DataBindingUtil.setContentView(this, R.layout.activity_exercise_detail_view);
        binding.setLifecycleOwner(this);

        binding.setLocale(Locale.getDefault());

        Intent intent = getIntent();
        setInitialWeightFragments();

        day = intent.getIntExtra("day", 1);
        workout = intent.getStringExtra("workout");
        setTitle(getString(R.string.app_name) + getString(R.string.colon) + " " +
                workout + " " + getString(R.string.day) + " " + day);
        model.loadExercises(Integer.toString(day), workout);
        binding.setViewmodel(model);
        binding.exerciseDetailSwipeLayout.setOnRefreshListener(() ->
                model.loadExercises(Integer.toString(day), workout));
        model.getIsLoadingBool().observe(this, isLoading -> {
            if (isLoading) {
                binding.exerciseDetailSwipeLayout.setRefreshing(true);
            } else {
                binding.exerciseDetailSwipeLayout.setRefreshing(false);
            }
        });
    }

    private void setInitialWeightFragments() {
        Log.d(TAG, "setInitialWeightFragments: will show the 2.5 button? " + show25);
        WeightButtonFragmentSet weightButtonFragmentSet = new WeightButtonFragmentSet();
        FragmentTransaction transaction = getSupportFragmentManager().beginTransaction();
        transaction.add(R.id.sub_weight_fragment, weightButtonFragmentSet.getSubFrag());
        transaction.add(R.id.add_weight_fragment, weightButtonFragmentSet.getAddFrag());
        transaction.commit();
    }

    private void updateWeightFragments() {
        Log.d(TAG, "updateWeightFragments: will show the 2.5 button? " + show25);
        WeightButtonFragmentSet weightButtonFragmentSet = new WeightButtonFragmentSet();
        FragmentTransaction transaction = getSupportFragmentManager().beginTransaction();
        transaction.replace(R.id.sub_weight_fragment, weightButtonFragmentSet.getSubFrag());
        transaction.replace(R.id.add_weight_fragment, weightButtonFragmentSet.getAddFrag());
        transaction.commit();
    }

    @Override
    protected void onResume() {
        super.onResume();
        binding.repsDisplayView.clearFocus();
        binding.weightDisplayView.clearFocus();

        SharedPreferences prefs = getSharedPreferences("RefittedMainPrefs", MODE_PRIVATE);

        show25 = prefs.getBoolean("enable25", true);
        show5 = prefs.getBoolean("enable5", true);
        showMore = prefs.getBoolean("enableMore", true);
        showAsDouble = prefs.getBoolean("enableDouble", false);
        updateWeightFragments();

        SharedPreferences.Editor editor = prefs.edit();
        editor.putString("lastActivity", getClass().getName());
        editor.putInt("day", day);
        editor.putString("workout", workout);
        editor.apply();
    }

    @Override
    protected void onPause() {
        super.onPause();

        SharedPreferences prefs = getSharedPreferences("RefittedMainPrefs", MODE_PRIVATE);

        SharedPreferences.Editor editor = prefs.edit();
        editor.putBoolean("enable25", show25);
        editor.putBoolean("enable5", show5);
        editor.putBoolean("enableMore", showMore);
        editor.putBoolean("enableDouble", showAsDouble);
        editor.apply();
    }

    public void handleRepsClick(View view) {
        switch (view.getId()) {
            case R.id.addRepButton:
                updateRepValue(true);
                break;
            case R.id.subRepButton:
                updateRepValue(false);
                break;
            default:
                Log.e(TAG, "handleRepsClick: event from unknown source: " + view.getId());
        }
    }

    private void updateRepValue(boolean increase) {
        model.updateRepsDisplay(increase);
    }

    public void handleNavigateLeft(View view) {
        model.navigateLeft();
    }

    public void handleNavigateRight(View view) {
        model.navigateRight();
    }

    public void handleCompleteSet(View view) {
        model.completeSet(binding.weightDisplayView.getText().toString(),
                binding.repsDisplayView.getText().toString());
    }

    @Override
    public void onButtonConfigurationChange(EnumSet<ConfigureButtonsDialogFragment.ButtonLayout> layout) {
        updateCurrentLayout(layout);
        updateWeightFragments();
    }

    private class WeightButtonFragmentSet {
        private WeightButton subFrag;
        private WeightButton addFrag;

        public WeightButtonFragmentSet() {
            WeightButton.LAYOUT buttonsVisible = WeightButton.LAYOUT.button5;
            ArrayList<Double> buttonValues = new ArrayList<>();
            //double[] buttonValues = new double[]{2.5, 5, 10, 25, 45};
            if (show25) {
                buttonValues.add(2.5);
            }
            if (show5) {
                buttonValues.add(5d);
            }
            if (showMore) {
                buttonValues.add(10d);
                buttonValues.add(25d);
                buttonValues.add(45d);
            }
            if (showAsDouble) {
                if (buttonValues.size() <= 1) {
                    buttonsVisible = WeightButton.LAYOUT.button1;
                } else if (buttonValues.size() <= 2) {
                    buttonsVisible = WeightButton.LAYOUT.button2;
                } else if (buttonValues.size() <= 3) {
                    buttonsVisible = WeightButton.LAYOUT.button3;
                } else if (buttonValues.size() <= 4) {
                    buttonsVisible = WeightButton.LAYOUT.button4;
                } else if (buttonValues.size() <= 5) {
                    buttonsVisible = WeightButton.LAYOUT.button5;
                }
            } else {
                if (buttonValues.size() <= 1) {
                    buttonsVisible = WeightButton.LAYOUT.button1;
                } else if (buttonValues.size() <= 2) {
                    buttonsVisible = WeightButton.LAYOUT.button2;
                } else if (buttonValues.size() <= 3) {
                    buttonsVisible = WeightButton.LAYOUT.button3;
                } else if (buttonValues.size() <= 4) {
                    buttonsVisible = WeightButton.LAYOUT.button4;
                } else if (buttonValues.size() <= 5) {
                    buttonsVisible = WeightButton.LAYOUT.button5;
                }
            }

            Double[] buttonFinalValues = buttonValues.toArray(new Double[0]);

            subFrag = WeightButton.newInstance(buttonsVisible,
                    buttonFinalValues, false);
            addFrag = WeightButton.newInstance(buttonsVisible,
                    buttonFinalValues, true);
        }

        public WeightButton getSubFrag() {
            return subFrag;
        }

        public WeightButton getAddFrag() {
            return addFrag;
        }
    }
}
