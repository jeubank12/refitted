package com.litus_animae.refitted.fragments;

import android.content.Context;
import android.net.Uri;
import android.os.Bundle;

import androidx.databinding.DataBindingUtil;
import androidx.fragment.app.Fragment;
import androidx.lifecycle.ViewModelProviders;

import android.util.Log;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;

import com.litus_animae.refitted.R;
import com.litus_animae.refitted.databinding.FragmentWeightButton5Binding;
import com.litus_animae.refitted.models.ExerciseViewModel;

import java.text.DecimalFormat;
import java.text.NumberFormat;
import java.util.Locale;


/**
 * A simple {@link Fragment} subclass.
 * Use the {@link WeightButton#newInstance} factory method to
 * create an instance of this fragment.
 */
public class WeightButton extends Fragment implements View.OnClickListener {
    private static final String TAG = "WeightButton";

    public enum LAYOUT {
        button5
    }

    private static final String ARG_PARAM1 = "layout";
    private static final String ARG_PARAM2 = "button values";
    private static final String ARG_PARAM3 = "is positive";

    private LAYOUT layoutResource;
    private double[] buttonValues;
    private boolean isPositive;
    private ExerciseViewModel model;

    public WeightButton() {
        // Required empty public constructor
    }

    /**
     * Use this factory method to create a new instance of
     * this fragment using the provided parameters.
     *
     * @param layoutType Parameter 1.
     * @param values     Parameter 2.
     * @param isPositive
     * @return A new instance of fragment WeightButton.
     */
    // TODO: Rename and change types and number of parameters
    public static WeightButton newInstance(LAYOUT layoutType, double[] values, boolean isPositive) {
        WeightButton fragment = new WeightButton();
        Bundle args = new Bundle();
        args.putString(ARG_PARAM1, layoutType.toString());
        args.putSerializable(ARG_PARAM2, values);
        args.putBoolean(ARG_PARAM3, isPositive);
        fragment.setArguments(args);
        return fragment;
    }

    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        if (getArguments() != null) {
            layoutResource = LAYOUT.valueOf(getArguments().getString(ARG_PARAM1));
            buttonValues = (double[]) getArguments().getSerializable(ARG_PARAM2);
            isPositive = getArguments().getBoolean(ARG_PARAM3);
        } else {
            // defaults
            layoutResource = LAYOUT.button5;
            buttonValues = new double[] {2.5, 5, 10, 25, 45};
            isPositive = false;
        }
        model = ViewModelProviders.of(getActivity()).get(ExerciseViewModel.class);
    }

    @Override
    public View onCreateView(LayoutInflater inflater, ViewGroup container,
                             Bundle savedInstanceState) {
        // Inflate the layout for this fragment
        switch (layoutResource) {
            case button5:
            default:
                FragmentWeightButton5Binding binding = FragmentWeightButton5Binding
                        .inflate(inflater, container, false);
                NumberFormat df = new DecimalFormat((isPositive ? "" : "-") + "##.#");
                binding.button11.setOnClickListener(this);
                binding.button11.setText(df.format(buttonValues[0]));
                binding.button12.setOnClickListener(this);
                binding.button12.setText(df.format(buttonValues[1]));
                binding.button21.setOnClickListener(this);
                binding.button21.setText(df.format(buttonValues[2]));
                binding.button22.setOnClickListener(this);
                binding.button22.setText(df.format(buttonValues[3]));
                binding.button3.setOnClickListener(this);
                binding.button3.setText(df.format(buttonValues[4]));
                binding.setViewmodel(model);
                return binding.getRoot();
        }
    }

    @Override
    public void onClick(View view) {
        int sign = isPositive ? 1 : -1;
        switch (view.getId()) {
            case R.id.button1_1:
                // TODO get value
                model.UpdateWeightDisplay(2.5 * sign);
                break;
            case R.id.button1_2:
                // TODO get value
                model.UpdateWeightDisplay(5 * sign);
                break;
            case R.id.button2_1:
                // TODO get value
                model.UpdateWeightDisplay(10 * sign);
                break;
            case R.id.button2_2:
                // TODO get value
                model.UpdateWeightDisplay(25 * sign);
                break;
            case R.id.button3:
                // TODO get value
                model.UpdateWeightDisplay(45 * sign);
                break;
            default:
                Log.e(TAG, "HandleWeightClick: event from unknown source: " + view.getId());

        }
    }

    @Override
    public void onDetach() {
        super.onDetach();
    }
}
