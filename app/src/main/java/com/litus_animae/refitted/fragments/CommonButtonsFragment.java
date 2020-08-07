package com.litus_animae.refitted.fragments;


import android.os.Bundle;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Switch;

import androidx.annotation.NonNull;
import androidx.fragment.app.Fragment;
import androidx.lifecycle.Transformations;

import com.litus_animae.refitted.R;
import com.litus_animae.refitted.databinding.CommonButtonsFragmentBinding;

import dagger.hilt.android.AndroidEntryPoint;

/**
 * A simple {@link Fragment} subclass.
 * Use the {@link CommonButtonsFragment#newInstance} factory method to
 * create an instance of this fragment.
 */
@AndroidEntryPoint
public class CommonButtonsFragment extends ButtonsFragment {

    private static final String TAG = "CommonButtonsFragment";

    private CommonButtonsFragmentBinding binding;

    public CommonButtonsFragment() {
        // Required empty public constructor
    }

    /**
     * Use this factory method to create a new instance of
     * this fragment using the provided parameters.
     *
     * @return A new instance of fragment CommonButtonsFragment.
     */
    public static CommonButtonsFragment newInstance() {
        return new CommonButtonsFragment();
    }

    @Override
    protected String getDisplayedWeight() {
        return binding.weightDisplayView.getText().toString();
    }

    @Override
    protected String getDisplayedReps() {
        return binding.repsDisplayView.getText().toString();
    }

    @Override
    View getCompleteSetButton() {
        return binding.completeSetButton;
    }

    @Override
    protected boolean isViewAddReps(View view) {
        switch (view.getId()) {
            case R.id.addRepButton:
                return true;
            case R.id.subRepButton:
                return false;
            default:
                Log.e(TAG, "handleRepsClick: event from unknown source: " + view.getId());
                return true;
        }
    }

    @Override
    View getRepsPositiveButton() {
        return binding.addRepButton;
    }

    @Override
    View getRepsNegativeButton() {
        return binding.subRepButton;
    }

    @Override
    View getNavigateLeftButton() {
        return binding.moveLeftButton;
    }

    @Override
    View getNavigateRightButton() {
        return binding.moveRightButton;
    }

    @Override
    protected int getSubFragId() {
        return R.id.sub_weight_fragment;
    }

    @Override
    protected int getAddFragId() {
        return R.id.add_weight_fragment;
    }

    @Override
    Switch getDoubledValueSwitch() {
        return binding.doubledSwitch;
    }

    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, ViewGroup container,
                             Bundle savedInstanceState) {
        binding = CommonButtonsFragmentBinding.inflate(inflater, container, false);
        binding.setLifecycleOwner(getViewLifecycleOwner());
        binding.setViewmodel(model);
        binding.setCompleteSetButtonText(Transformations.map(model.getCompleteSetMessage(),
                messageResources -> messageResources.getStringValue(requireContext())));
        binding.setRestValueText(Transformations.map(model.getRestValue(),
                messageResources -> messageResources.getStringValue(requireContext())));
        return binding.getRoot();
    }

    @Override
    public void onResume() {
        super.onResume();
        binding.repsDisplayView.clearFocus();
        binding.weightDisplayView.clearFocus();
    }
}
