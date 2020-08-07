package com.litus_animae.refitted;

import android.content.Intent;
import android.content.SharedPreferences;
import android.os.Bundle;
import android.util.Log;
import android.view.Menu;
import android.view.MenuInflater;
import android.view.MenuItem;

import androidx.appcompat.app.AppCompatActivity;
import androidx.databinding.DataBindingUtil;
import androidx.fragment.app.DialogFragment;
import androidx.fragment.app.FragmentTransaction;
import androidx.lifecycle.ViewModelProvider;

import com.litus_animae.refitted.databinding.ActivityExerciseDetailViewBinding;
import com.litus_animae.refitted.fragments.ButtonsFragment;
import com.litus_animae.refitted.fragments.CommonButtonsFragment;
import com.litus_animae.refitted.fragments.ConfigureButtonsDialogFragment;
import com.litus_animae.refitted.fragments.ExerciseHistoryDialogFragment;
import com.litus_animae.refitted.models.ExerciseViewModel;

import java.util.EnumSet;
import java.util.Locale;

import dagger.hilt.android.AndroidEntryPoint;

@AndroidEntryPoint
public class ExerciseDetailViewActivity extends AppCompatActivity
implements ConfigureButtonsDialogFragment.OnButtonConfigurationChangeListener {

    private static final String TAG = "ExerciseDetailViewActivity";
    private MenuItem switchToAlternateButton;
    private ActivityExerciseDetailViewBinding binding;
    private ExerciseViewModel model;
    private String workout;
    private int day;

    private ButtonsFragment lowerFragment;

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
                Log.d(TAG, "onOptionsItemSelected: handle 'switch to alternate'");
                model.swapToAlternate();
                return true;
            case R.id.config_button_layout:
                Log.d(TAG, "onOptionsItemSelected: handle 'enable 2.5'");
                ConfigureButtonsDialogFragment configureButtonsDialogFragment =
                        ConfigureButtonsDialogFragment.newInstance(lowerFragment.getCurrentLayout());
                configureButtonsDialogFragment.show(getSupportFragmentManager(), null);
                return true;
            case R.id.show_history:
                DialogFragment history = new ExerciseHistoryDialogFragment();
                history.show(getSupportFragmentManager(), null);
                return true;
            default:
                return false;
        }
    }

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        model = new ViewModelProvider(this).get(ExerciseViewModel.class);
        binding = DataBindingUtil.setContentView(this, R.layout.activity_exercise_detail_view);
        binding.setLifecycleOwner(this);

        binding.setLocale(Locale.getDefault());

        Intent intent = getIntent();

        day = intent.getIntExtra("day", 1);
        workout = intent.getStringExtra("workout");
        setTitle(getString(R.string.app_name) + getString(R.string.colon) + " " +
                workout + " " + getString(R.string.day) + " " + day);
        model.loadExercises(Integer.toString(day), workout);
        binding.setViewmodel(model);
        binding.exerciseDetailSwipeLayout.setOnRefreshListener(() ->
                model.loadExercises(Integer.toString(day), workout));
        model.isLoadingBool().observe(this, isLoading -> {
            if (isLoading) {
                binding.exerciseDetailSwipeLayout.setRefreshing(true);
            } else {
                binding.exerciseDetailSwipeLayout.setRefreshing(false);
            }
        });

        lowerFragment = CommonButtonsFragment.newInstance();
        FragmentTransaction transaction = getSupportFragmentManager().beginTransaction();
        transaction.replace(R.id.buttons_fragment_frame, lowerFragment);
        transaction.commit();
    }

    @Override
    protected void onResume() {
        super.onResume();
        SharedPreferences prefs = getSharedPreferences("RefittedMainPrefs", MODE_PRIVATE);

        SharedPreferences.Editor editor = prefs.edit();
        editor.putString("lastActivity", getClass().getName());
        editor.putInt("day", day);
        editor.putString("workout", workout);
        editor.apply();
    }

    @Override
    public void onButtonConfigurationChange(EnumSet<ConfigureButtonsDialogFragment.ButtonLayout> layout) {
        lowerFragment.onButtonConfigurationChange(layout);
    }
}
