package com.litus_animae.refitted.fragments;

import android.app.Dialog;
import android.content.Context;
import android.os.Bundle;
import android.util.Log;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.appcompat.app.AlertDialog;
import androidx.fragment.app.DialogFragment;
import androidx.fragment.app.Fragment;

import com.litus_animae.refitted.R;

import java.util.Arrays;
import java.util.EnumSet;
import java.util.HashMap;
import java.util.Map;

/**
 * A simple {@link Fragment} subclass.
 * Activities that contain this fragment must implement the
 * {@link OnButtonConfigurationChangeListener} interface
 * to handle interaction events.
 * Use the {@link ConfigureButtonsDialogFragment#newInstance} factory method to
 * create an instance of this fragment.
 */
public class ConfigureButtonsDialogFragment extends DialogFragment {
    private static final String TAG = "ConfigureButtonsDialogF";

    public static final EnumSet<ButtonLayout> ALL_OPTS = EnumSet.allOf(ButtonLayout.class);

    private static final String ARG_PARAM1 = "selectedLayout";
    private Map<String, ButtonLayout> layoutDescriptions = new HashMap<>();
    private EnumSet<ButtonLayout> buttonLayout;

    private OnButtonConfigurationChangeListener mListener;

    public ConfigureButtonsDialogFragment() {
        // Required empty public constructor
    }

    /**
     * Use this factory method to create a new instance of
     * this fragment using the provided parameters.
     *
     * @param layout Parameter 1.
     * @return A new instance of fragment ConfigureButtonsDialogFragment.
     */
    public static ConfigureButtonsDialogFragment newInstance(EnumSet<ButtonLayout> layout) {
        ConfigureButtonsDialogFragment fragment = new ConfigureButtonsDialogFragment();
        Bundle args = new Bundle();
        args.putInt(ARG_PARAM1, getFlagsFromSet(layout));
        fragment.setArguments(args);
        return fragment;
    }

    private static EnumSet<ButtonLayout> getSetFromFlags(int flags) {
        EnumSet<ButtonLayout> result = EnumSet.noneOf(ButtonLayout.class);
        for (int biterator = 1; biterator <= ButtonLayout.maxValue(); biterator *= 2) {
            if ((flags & biterator) > 0) {
                result.add(ButtonLayout.valueOf(flags & biterator));
            }
        }
        return result;
    }

    private static boolean[] getSelectedFromSet(EnumSet<ButtonLayout> layout) {
        boolean[] result = new boolean[ButtonLayout.values().length];
        for (int i = 0; i < ButtonLayout.values().length; i++) {
            result[i] = layout.contains(ButtonLayout.valueOf((int) Math.pow(2, i)));
        }
        return result;
    }

    private static int getFlagsFromSet(EnumSet<ButtonLayout> layout) {
        int result = 0;
        for (ButtonLayout option : layout) {
            result += option.getValue();
        }
        return result;
    }

    @NonNull
    @Override
    public Dialog onCreateDialog(@Nullable Bundle savedInstanceState) {
        AlertDialog.Builder builder = new AlertDialog.Builder(requireActivity());
        layoutDescriptions.put(requireContext().getString(R.string.enable_2_5_lbs), ButtonLayout.show25);
        layoutDescriptions.put(requireContext().getString(R.string.enable_5_lbs), ButtonLayout.show5);
        layoutDescriptions.put(requireContext().getString(R.string.enable_more_lbs), ButtonLayout.showMore);
        layoutDescriptions.put(requireContext().getString(R.string.enable_double_lbs), ButtonLayout.showAsDouble);
        CharSequence[] display = new CharSequence[]{
                requireContext().getString(R.string.enable_2_5_lbs),
                requireContext().getString(R.string.enable_5_lbs),
                requireContext().getString(R.string.enable_more_lbs)
                //,requireContext().getString(R.string.enable_double_lbs)
        };
        EnumSet<ButtonLayout> resultLayout = EnumSet.copyOf(buttonLayout);
        builder.setTitle(R.string.prev_weight)
                .setPositiveButton("OK", (dialog, which) -> mListener.onButtonConfigurationChange(resultLayout))
                .setNegativeButton("Cancel", (dialog, which) -> mListener.onButtonConfigurationChange(buttonLayout))
                .setMultiChoiceItems(display, getSelectedFromSet(buttonLayout), (dialog, which, isChecked) -> {
                    if (isChecked) {
                        Log.d(TAG, "onClick: adding " + layoutDescriptions.get(display[which].toString()));
                        resultLayout.add(layoutDescriptions.get(display[which].toString()));
                    } else {
                        Log.d(TAG, "onClick: removing " + layoutDescriptions.get(display[which].toString()));
                        resultLayout.remove(layoutDescriptions.get(display[which].toString()));
                    }
                });
        return builder.create();
    }

    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        if (getArguments() != null) {
            buttonLayout = getSetFromFlags(getArguments().getInt(ARG_PARAM1));
        }
    }

    @Override
    public void onAttach(@NonNull Context context) {
        super.onAttach(context);
        if (context instanceof OnButtonConfigurationChangeListener) {
            mListener = (OnButtonConfigurationChangeListener) context;
        } else {
            throw new RuntimeException(context.toString()
                    + " must implement OnButtonConfigurationChangeListener");
        }
    }

    @Override
    public void onDetach() {
        super.onDetach();
        mListener = null;
    }

    public enum ButtonLayout {
        show25(1),
        show5(2),
        showMore(4),
        showAsDouble(8);

        private static Map<Integer, ButtonLayout> map = new HashMap<>();

        static {
            for (ButtonLayout option : ButtonLayout.values()) {
                map.put(option.value, option);
            }
        }

        private int value;

        ButtonLayout(int value) {
            this.value = value;
        }

        public static int maxValue() {
            Integer[] keys = map.keySet().toArray(new Integer[0]);
            Arrays.sort(keys);
            return keys[keys.length - 1];
        }

        public static ButtonLayout valueOf(int option) {
            return map.get(option);
        }

        public int getValue() {
            return value;
        }
    }

    /**
     * This interface must be implemented by activities that contain this
     * fragment to allow an interaction in this fragment to be communicated
     * to the activity and potentially other fragments contained in that
     * activity.
     * <p>
     * See the Android Training lesson <a href=
     * "http://developer.android.com/training/basics/fragments/communicating.html"
     * >Communicating with Other Fragments</a> for more information.
     */
    public interface OnButtonConfigurationChangeListener {
        void onButtonConfigurationChange(EnumSet<ButtonLayout> layout);
    }

}
