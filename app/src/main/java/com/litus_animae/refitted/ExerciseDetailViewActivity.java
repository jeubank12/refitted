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
import androidx.fragment.app.FragmentTransaction;
import androidx.lifecycle.ViewModelProviders;

import com.litus_animae.refitted.databinding.ActivityExerciseDetailViewBinding;
import com.litus_animae.refitted.fragments.WeightButton;
import com.litus_animae.refitted.models.ExerciseViewModel;

import java.util.Arrays;
import java.util.Locale;

public class ExerciseDetailViewActivity extends AppCompatActivity {

    private static final String TAG = "ExerciseDetailViewActivity";
    private MenuItem switchToAlternateButton;
    private boolean show25 = true;
    private ActivityExerciseDetailViewBinding binding;
    private ExerciseViewModel model;
    private String workout;
    private int day;

    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
        MenuInflater inflater = getMenuInflater();
        inflater.inflate(R.menu.alternate_menu, menu);
        switchToAlternateButton = menu.findItem(R.id.switch_to_alternate_menu_item);
        MenuItem enable25 = menu.findItem(R.id.enable_25);
        enable25.setChecked(show25);

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
            case R.id.enable_25:
                Log.d(TAG, "onOptionsItemSelected: handle 'enable 2.5'");
                show25 = !show25;
                item.setChecked(show25);
                updateWeightFragments();
                return true;
            default:
                return false;
        }
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
        model.loadExercises(Integer.toString(day), workout);
        binding.setViewmodel(model);
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

    private class WeightButtonFragmentSet {
        private WeightButton subFrag;
        private WeightButton addFrag;

        public WeightButton getSubFrag() {
            return subFrag;
        }

        public WeightButton getAddFrag() {
            return addFrag;
        }

        public WeightButtonFragmentSet() {
            WeightButton.LAYOUT buttonsVisible = WeightButton.LAYOUT.button5;
            double[] buttonValues = new double[]{2.5, 5, 10, 25, 45};
            if (!show25) {
                buttonsVisible = WeightButton.LAYOUT.button4;
                buttonValues = Arrays.copyOfRange(buttonValues, 1, 5);
            }
            subFrag = WeightButton.newInstance(buttonsVisible,
                    buttonValues, false);
            addFrag = WeightButton.newInstance(buttonsVisible,
                    buttonValues, true);
        }
    }
}
