package com.litus_animae.refitted.fragments;

import android.app.Dialog;
import android.content.Context;
import android.content.DialogInterface;
import android.os.Bundle;

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
 * {@link OnButtonConfigurtionChangeListener} interface
 * to handle interaction events.
 * Use the {@link ConfigureButtonsDialogFragment#newInstance} factory method to
 * create an instance of this fragment.
 */
public class ConfigureButtonsDialogFragment extends DialogFragment {
    public static final EnumSet<ButtonLayout> ALL_OPTS = EnumSet.allOf(ButtonLayout.class);
    // TODO: Rename parameter arguments, choose names that match
    // the fragment initialization parameters, e.g. ARG_ITEM_NUMBER
    private static final String ARG_PARAM1 = "selectedLayout";
    private Map<String, ButtonLayout> layoutDescriptions = new HashMap<>();
    // TODO: Rename and change types of parameters
    private EnumSet<ButtonLayout> buttonLayout;

    private OnButtonConfigurtionChangeListener mListener;

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
        //layoutDescriptions.put(requireContext().getString(R.string.enable_double_lbs), ButtonLayout.showAsDouble);
        CharSequence[] display = layoutDescriptions.keySet().toArray(new CharSequence[0]);
        EnumSet<ButtonLayout> resultLayout = EnumSet.copyOf(buttonLayout);
        builder.setTitle(R.string.prev_weight)
                .setPositiveButton("OK", new DialogInterface.OnClickListener() {
                    @Override
                    public void onClick(DialogInterface dialog, int which) {
                        mListener.onButtonConfigurationChange(resultLayout);
                    }
                })
                .setNegativeButton("Cancel", new DialogInterface.OnClickListener() {
                    @Override
                    public void onClick(DialogInterface dialog, int which) {
                        mListener.onButtonConfigurationChange(buttonLayout);
                    }
                })
                .setMultiChoiceItems(display, getSelectedFromSet(buttonLayout), new DialogInterface.OnMultiChoiceClickListener() {
                    @Override
                    public void onClick(DialogInterface dialog, int which, boolean isChecked) {
                        if (isChecked) {
                            resultLayout.add(layoutDescriptions.get(display[which].toString()));
                        } else {
                            resultLayout.remove(layoutDescriptions.get(display[which].toString()));
                        }
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
    public void onAttach(Context context) {
        super.onAttach(context);
        if (context instanceof OnButtonConfigurtionChangeListener) {
            mListener = (OnButtonConfigurtionChangeListener) context;
        } else {
            throw new RuntimeException(context.toString()
                    + " must implement OnButtonConfigurtionChangeListener");
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
    public interface OnButtonConfigurtionChangeListener {
        // TODO: Update argument type and name
        void onButtonConfigurationChange(EnumSet<ButtonLayout> layout);
    }

}
