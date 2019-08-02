package com.litus_animae.refitted.fragments;

import android.os.Bundle;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;

import androidx.fragment.app.Fragment;
import androidx.lifecycle.LifecycleOwner;
import androidx.lifecycle.LiveData;
import androidx.lifecycle.MutableLiveData;
import androidx.lifecycle.Transformations;
import androidx.lifecycle.ViewModelProviders;

import com.litus_animae.refitted.R;
import com.litus_animae.refitted.databinding.FragmentWeightButton4Binding;
import com.litus_animae.refitted.databinding.FragmentWeightButton5Binding;
import com.litus_animae.refitted.models.ExerciseViewModel;

import java.text.DecimalFormat;
import java.text.NumberFormat;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;


/**
 * A simple {@link Fragment} subclass. Presents a widget of buttons that
 * call {@link ExerciseViewModel#updateWeightDisplay(double)} with their value
 * Use the {@link WeightButton#newInstance} factory method to
 * create an instance of this fragment.
 */
public class WeightButton extends Fragment implements View.OnClickListener {
    private static final String TAG = "WeightButton";

    public enum LAYOUT {
        button5,button4
    }

    private static final String ARG_LAYOUT = "layout";
    private static final String ARG_VALUES = "button values";
    private static final String ARG_POSITIVE = "is positive";

    private LAYOUT layoutResource;
    private MutableLiveData<double[]> buttonValues = new MutableLiveData<>();
    private MutableLiveData<Boolean> isPositive = new MutableLiveData<>();
    private ExerciseViewModel model;
    private LiveData<String[]> buttonLabels;

    public WeightButton() {
        // Required empty public constructor
        buttonLabels = Transformations.switchMap(isPositive, pos -> {
            NumberFormat df = new DecimalFormat((pos ? "+" : "-") + "##.#");
            return Transformations.map(buttonValues, arr -> {
                ArrayList<String> result = new ArrayList<>(arr.length);
                for (double value : arr) {
                    result.add(df.format(value));
                }
                return result.toArray(new String[arr.length]);
            });
        });
    }

    /**
     * Use this factory method to create a new instance of
     * this fragment using the provided parameters.
     *
     * @param layoutType The layout which will be inflated
     * @param values     The values to display on the buttons and used on click
     *                   If the array length does not match the expected length for the
     *                   layout it will be either truncated or padded as appropriate
     * @param isPositive true if positive numbers, false for negative
     * @return A new instance of fragment WeightButton.
     */
    public static WeightButton newInstance(LAYOUT layoutType, double[] values, boolean isPositive) {
        WeightButton fragment = new WeightButton();
        Bundle args = new Bundle();
        args.putString(ARG_LAYOUT, layoutType.toString());
        args.putSerializable(ARG_VALUES, values);
        args.putBoolean(ARG_POSITIVE, isPositive);
        fragment.setArguments(args);
        return fragment;
    }

    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        model = ViewModelProviders.of(getActivity()).get(ExerciseViewModel.class);
        if (getArguments() != null) {
            setButtonValues(LAYOUT.valueOf(getArguments().getString(ARG_LAYOUT)),
                    (double[]) getArguments().getSerializable(ARG_VALUES));
            isPositive.setValue(getArguments().getBoolean(ARG_POSITIVE));
        } else {
            throw new IllegalStateException();
        }
    }

    private void setButtonValues(LAYOUT layout, double[] values) {
        double[] result;
        switch (layout) {
            case button5:
                result = Arrays.copyOf(values, 5);
                break;
            case button4:
                result = Arrays.copyOf(values, 4);
                break;
            default:
                throw new IllegalStateException();
        }
        layoutResource = layout;
        buttonValues.setValue(result);
    }

    @Override
    public View onCreateView(LayoutInflater inflater, ViewGroup container,
                             Bundle savedInstanceState) {
        BindingInterface genericBinding;
        // Inflate the layout for this fragment
        switch (layoutResource) {
            case button5:
                FragmentWeightButton5Binding bind5 = FragmentWeightButton5Binding
                        .inflate(inflater, container, false);
                genericBinding = getWeightButton5Interface(bind5);
                break;
            case button4:
                FragmentWeightButton4Binding bind4 = FragmentWeightButton4Binding
                        .inflate(inflater, container, false);
                genericBinding = getWeightButton4Interface(bind4);
                break;
            default:
                throw new IllegalStateException();
        }
        for (Button b : genericBinding.getButtons()) {
            b.setOnClickListener(this);
        }
        genericBinding.setLifecycleOwner(getViewLifecycleOwner());
        genericBinding.setViewmodel(model);
        genericBinding.setButtonLabels(buttonLabels);
        return genericBinding.getRoot();
    }

    private static BindingInterface getWeightButton5Interface(FragmentWeightButton5Binding binding) {
        BindingInterface genericBinding;
        genericBinding = new BindingInterface() {
            @Override
            public void setLifecycleOwner(LifecycleOwner owner) {
                binding.setLifecycleOwner(owner);
            }

            @Override
            public void setViewmodel(ExerciseViewModel model) {
                binding.setViewmodel(model);
            }

            @Override
            public void setButtonLabels(LiveData<String[]> labels) {
                binding.setButtonLabels(labels);
            }

            @Override
            public Collection<Button> getButtons() {
                return Arrays.asList(binding.button11, binding.button12,
                        binding.button21, binding.button22, binding.button3);
            }

            @Override
            public View getRoot() {
                return binding.getRoot();
            }
        };
        return genericBinding;
    }

    private static BindingInterface getWeightButton4Interface(FragmentWeightButton4Binding binding) {
        BindingInterface genericBinding;
        genericBinding = new BindingInterface() {
            @Override
            public void setLifecycleOwner(LifecycleOwner owner) {
                binding.setLifecycleOwner(owner);
            }

            @Override
            public void setViewmodel(ExerciseViewModel model) {
                binding.setViewmodel(model);
            }

            @Override
            public void setButtonLabels(LiveData<String[]> labels) {
                binding.setButtonLabels(labels);
            }

            @Override
            public Collection<Button> getButtons() {
                return Arrays.asList(binding.button11, binding.button12,
                        binding.button21, binding.button22);
            }

            @Override
            public View getRoot() {
                return binding.getRoot();
            }
        };
        return genericBinding;
    }

    @Override
    public void onClick(View view) {
        int sign = isPositive.getValue() ? 1 : -1;
        double[] values = buttonValues.getValue();
        switch (view.getId()) {
            case R.id.button1_1:
                model.updateWeightDisplay(values[0] * sign);
                break;
            case R.id.button1_2:
                model.updateWeightDisplay(values[1] * sign);
                break;
            case R.id.button2_1:
                model.updateWeightDisplay(values[2] * sign);
                break;
            case R.id.button2_2:
                model.updateWeightDisplay(values[3] * sign);
                break;
            case R.id.button3:
                model.updateWeightDisplay(values[4] * sign);
                break;
            default:
                Log.e(TAG, "HandleWeightClick: event from unknown source: " + view.getId());
        }
    }

    private interface BindingInterface {
        void setLifecycleOwner(LifecycleOwner owner);

        void setViewmodel(ExerciseViewModel model);

        void setButtonLabels(LiveData<String[]> labels);

        Collection<Button> getButtons();

        View getRoot();
    }
}
