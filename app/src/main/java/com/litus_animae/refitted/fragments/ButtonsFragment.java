package com.litus_animae.refitted.fragments;

import android.content.SharedPreferences;
import android.os.Bundle;
import android.view.View;
import android.widget.Switch;

import androidx.annotation.Nullable;
import androidx.fragment.app.Fragment;
import androidx.fragment.app.FragmentTransaction;
import androidx.lifecycle.ViewModelProviders;

import com.litus_animae.refitted.models.ExerciseViewModel;

import java.util.ArrayList;
import java.util.EnumSet;

import static android.content.Context.MODE_PRIVATE;

public abstract class ButtonsFragment extends Fragment implements
        ConfigureButtonsDialogFragment.OnButtonConfigurationChangeListener,
        WeightButtonConfigurationManager {

    ExerciseViewModel model;
    private boolean show25;
    private boolean show5;
    private boolean showMore;
    private boolean showAsDouble;

    public ButtonsFragment() {

    }

    @Override
    public void onButtonConfigurationChange(EnumSet<ConfigureButtonsDialogFragment.ButtonLayout> layout) {
        updateCurrentLayout(layout);
        updateWeightFragments();

        SharedPreferences prefs = requireContext().getSharedPreferences("RefittedMainPrefs", MODE_PRIVATE);

        SharedPreferences.Editor editor = prefs.edit();
        editor.putBoolean("enable25", show25);
        editor.putBoolean("enable5", show5);
        editor.putBoolean("enableMore", showMore);
        editor.putBoolean("enableDouble", showAsDouble);
        editor.apply();
    }

    abstract String getDisplayedWeight();

    abstract String getDisplayedReps();

    abstract View getCompleteSetButton();

    private void handleCompleteSet(View view) {
        model.completeSet(getDisplayedWeight(),
                getDisplayedReps());
    }

    protected abstract boolean isViewAddReps(View view);

    abstract View getRepsPositiveButton();

    abstract View getRepsNegativeButton();

    private void handleRepsClick(View view) {
        updateRepValue(isViewAddReps(view));
    }

    private void updateRepValue(boolean increase) {
        model.updateRepsDisplay(increase);
    }

    abstract View getNavigateLeftButton();

    abstract View getNavigateRightButton();

    private void handleNavigateLeft(View view) {
        model.navigateLeft();
    }

    private void handleNavigateRight(View view) {
        model.navigateRight();
    }

    @Override
    public void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        model = ViewModelProviders.of(requireActivity()).get(ExerciseViewModel.class);
    }

    @Override
    public void onResume() {
        super.onResume();
        SharedPreferences prefs = requireContext().getSharedPreferences("RefittedMainPrefs", MODE_PRIVATE);

        show25 = prefs.getBoolean("enable25", true);
        show5 = prefs.getBoolean("enable5", true);
        showMore = prefs.getBoolean("enableMore", true);
        showAsDouble = prefs.getBoolean("enableDouble", false);
        getDoubledValueSwitch().setChecked(showAsDouble);
        updateWeightFragments();

        getRepsPositiveButton().setOnClickListener(this::handleRepsClick);
        getRepsNegativeButton().setOnClickListener(this::handleRepsClick);
        getNavigateLeftButton().setOnClickListener(this::handleNavigateLeft);
        getNavigateRightButton().setOnClickListener(this::handleNavigateRight);
        getCompleteSetButton().setOnClickListener(this::handleCompleteSet);
        getDoubledValueSwitch().setOnCheckedChangeListener(this::handleOnDoubleChecked);
    }

    public EnumSet<ConfigureButtonsDialogFragment.ButtonLayout> getCurrentLayout() {
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
        return result;
    }

    private void updateCurrentLayout(EnumSet<ConfigureButtonsDialogFragment.ButtonLayout> newLayout) {
        show25 = newLayout.contains(ConfigureButtonsDialogFragment.ButtonLayout.show25);
        show5 = newLayout.contains(ConfigureButtonsDialogFragment.ButtonLayout.show5);
        showMore = newLayout.contains(ConfigureButtonsDialogFragment.ButtonLayout.showMore);
    }

    // FIXME, ask the child classes for fragment ids instead of handing them the transaction
    protected abstract void updateWeightFragments(FragmentTransaction transaction,
                                                  WeightButtonFragmentSet weightButtonFragmentSet);

    private void updateWeightFragments(){
        WeightButtonFragmentSet weightButtonFragmentSet = new WeightButtonFragmentSet();
        FragmentTransaction transaction = getChildFragmentManager().beginTransaction();
        updateWeightFragments(transaction, weightButtonFragmentSet);
        transaction.commit();
    }

    abstract Switch getDoubledValueSwitch();

    private void handleOnDoubleChecked(View view, boolean isChecked) {
        showAsDouble = isChecked;
        updateWeightFragments();
    }

    class WeightButtonFragmentSet {
        private WeightButton subFrag;
        private WeightButton addFrag;

        WeightButtonFragmentSet() {
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

            if (buttonValues.size() <= 1) {
                buttonsVisible = WeightButton.LAYOUT.button1;
            } else if (buttonValues.size() <= 2) {
                buttonsVisible = WeightButton.LAYOUT.button2;
            } else if (buttonValues.size() <= 3) {
                buttonsVisible = WeightButton.LAYOUT.button3;
            } else if (buttonValues.size() <= 4) {
                buttonsVisible = WeightButton.LAYOUT.button4;
            }

            Double[] buttonFinalValues = buttonValues.toArray(new Double[0]);

            subFrag = WeightButton.newInstance(buttonsVisible,
                    buttonFinalValues, false, showAsDouble);
            addFrag = WeightButton.newInstance(buttonsVisible,
                    buttonFinalValues, true, showAsDouble);
        }

        WeightButton getSubFrag() {
            return subFrag;
        }

        WeightButton getAddFrag() {
            return addFrag;
        }
    }
}
