package com.litus_animae.refitted;

import android.content.Intent;

import androidx.databinding.DataBindingUtil;
import androidx.databinding.ObservableBoolean;
import androidx.databinding.ObservableField;
import androidx.databinding.ObservableInt;

import android.os.CountDownTimer;
import android.os.Handler;
import android.os.Message;

import androidx.appcompat.app.AppCompatActivity;
import androidx.lifecycle.ViewModelProvider;
import androidx.lifecycle.ViewModelProviders;

import android.os.Bundle;
import android.util.Log;
import android.view.Menu;
import android.view.MenuInflater;
import android.view.MenuItem;
import android.view.View;
import android.widget.TextView;

import com.litus_animae.refitted.databinding.ActivityExerciseDetailViewBinding;
import com.litus_animae.refitted.models.ExerciseRecord;
import com.litus_animae.refitted.models.ExerciseSet;
import com.litus_animae.refitted.models.ExerciseViewModel;
import com.litus_animae.refitted.models.SetRecord;
import com.litus_animae.refitted.threads.CloseDatabaseRunnable;
import com.litus_animae.refitted.threads.GetExerciseRunnable;

import java.util.ArrayList;
import java.util.Locale;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public class ExerciseDetailViewActivity extends AppCompatActivity {

    private static final String TAG = "ExerciseDetailViewActivity";
    private MenuItem switchToAlternateButton;
    private ActivityExerciseDetailViewBinding binding;
    private ExerciseViewModel model;

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
        model.loadExercises(Integer.toString(intent.getIntExtra("day", 1)),
                intent.getStringExtra("workout"));
        binding.setViewmodel(model);
    }

    @Override
    protected void onResume() {
        super.onResume();
        binding.repsDisplayView.clearFocus();
        binding.weightDisplayView.clearFocus();
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
