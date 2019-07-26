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
import androidx.lifecycle.ViewModelProviders;

import com.litus_animae.refitted.databinding.ActivityExerciseDetailViewBinding;
import com.litus_animae.refitted.models.ExerciseViewModel;

import java.util.Locale;

public class ExerciseDetailViewActivity extends AppCompatActivity {

    private static final String TAG = "ExerciseDetailViewActivity";
    private MenuItem switchToAlternateButton;
    private ActivityExerciseDetailViewBinding binding;
    private ExerciseViewModel model;
    private String workout;
    private int day;

    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
        MenuInflater inflater = getMenuInflater();
        inflater.inflate(R.menu.alternate_menu, menu);
        switchToAlternateButton = menu.findItem(R.id.switch_to_alternate_menu_item);

        model.getExercise().observe(this, exerciseSet ->
                switchToAlternateButton.setVisible(exerciseSet.hasAlternate()));
        return true;
    }

    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        switch (item.getItemId()) {
            case R.id.switch_to_alternate_menu_item:
                model.SwapToAlternate();
                return true;
            default:
                return super.onOptionsItemSelected(item);
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
        day = intent.getIntExtra("day", 1);
        workout = intent.getStringExtra("workout");
        model.loadExercises(Integer.toString(day), workout);
        binding.setViewmodel(model);
    }

    @Override
    protected void onResume() {
        super.onResume();
        binding.repsDisplayView.clearFocus();
        binding.weightDisplayView.clearFocus();

        SharedPreferences prefs = getSharedPreferences("RefittedMainPrefs", MODE_PRIVATE);
        SharedPreferences.Editor editor = prefs.edit();
        editor.putString("lastActivity", getClass().getName());
        editor.putInt("day", day);
        editor.putString("workout", workout);
        editor.apply();
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
        model.UpdateWeightDisplay(change);
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
        model.UpdateRepsDisplay(increase);
    }

    public void HandleNavigateLeft(View view) {
        model.NavigateLeft();
    }

    public void HandleNavigateRight(View view) {
        model.NavigateRight();
    }

    public void HandleCompleteSet(View view) {
        model.CompleteSet(binding.weightDisplayView.getText().toString(),
                binding.repsDisplayView.getText().toString());
    }

    // TODO implement on change for weight and reps then re-enable edit
}
