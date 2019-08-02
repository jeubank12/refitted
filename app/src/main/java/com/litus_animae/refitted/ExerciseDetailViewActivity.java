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
                model.swapToAlternate();
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
        WeightButton subFrag = WeightButton.newInstance(WeightButton.LAYOUT.button5,
                new double[] {2.5,5,10,25,45}, false);
        WeightButton addFrag = WeightButton.newInstance(WeightButton.LAYOUT.button5,
                new double[] {2.5,5,10,25,45}, true);
        FragmentTransaction transaction = getSupportFragmentManager().beginTransaction();
        transaction.add(R.id.sub_weight_fragment, subFrag);
        transaction.add(R.id.add_weight_fragment, addFrag);
        transaction.commit();

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

    // TODO implement on change for weight and reps then re-enable edit
}
