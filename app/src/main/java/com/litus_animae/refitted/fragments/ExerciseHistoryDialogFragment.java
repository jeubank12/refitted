package com.litus_animae.refitted.fragments;

import android.app.Dialog;
import android.content.Context;
import android.content.DialogInterface;
import android.os.Bundle;
import android.util.Log;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ArrayAdapter;
import android.widget.CompoundButton;
import android.widget.RadioButton;
import android.widget.RadioGroup;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.appcompat.app.AlertDialog;
import androidx.fragment.app.DialogFragment;
import androidx.lifecycle.ViewModelProviders;
import androidx.recyclerview.widget.RecyclerView;

import com.litus_animae.refitted.R;
import com.litus_animae.refitted.models.Exercise;
import com.litus_animae.refitted.models.ExerciseRecord;
import com.litus_animae.refitted.models.ExerciseViewModel;
import com.litus_animae.refitted.models.SetRecord;

import java.text.DateFormat;
import java.text.DecimalFormat;
import java.text.NumberFormat;
import java.util.List;
import java.util.Locale;

public class ExerciseHistoryDialogFragment extends DialogFragment {
    private static final String TAG = "ExerciseHistoryDialogFr";
    private static final DateFormat dateFormat = DateFormat.getDateInstance(DateFormat.SHORT);
    private static final NumberFormat weightFormat = new DecimalFormat("##.#");

    private SetRecordAdapter setRecordAdapter;

    @NonNull
    @Override
    public Dialog onCreateDialog(@Nullable Bundle savedInstanceState) {
        ExerciseViewModel model;
        if (getActivity() != null) {
            model = ViewModelProviders.of(getActivity()).get(ExerciseViewModel.class);
        } else {
            Log.e(TAG, "onCreate: already detached, getting dummy model...");
            model = ViewModelProviders.of(this).get(ExerciseViewModel.class);
        }
        AlertDialog.Builder builder = new AlertDialog.Builder(getActivity());
        setRecordAdapter = new SetRecordAdapter(getActivity(), model.getCurrentRecord().getAllSets(), 0);
        builder.setTitle(R.string.prev_weight)
//                .setPositiveButton("TODO", new DialogInterface.OnClickListener() {
//                    @Override
//                    public void onClick(DialogInterface dialog, int which) {
//
//                    }
//                })
                .setNegativeButton("Cancel", new DialogInterface.OnClickListener() {
                    @Override
                    public void onClick(DialogInterface dialog, int which) {

                    }
                })
                .setSingleChoiceItems(setRecordAdapter, 0, new DialogInterface.OnClickListener() {
                    @Override
                    public void onClick(DialogInterface dialog, int which) {

                    }
                });
        return builder.create();
    }

    @Override
    public void onAttach(Context context) {
        super.onAttach(context);
    }

    private class SetRecordAdapter extends ArrayAdapter<SetRecord> {

        private RadioButton lastChecked;
        private int selected;

        public SetRecordAdapter(@NonNull Context context, @NonNull List<SetRecord> objects, int initialSelection) {
            super(context, R.layout.previous_set_view, objects);
            this.selected = initialSelection;
        }

        @NonNull
        @Override
        public View getView(int position, @Nullable View convertView, @NonNull ViewGroup parent) {
            View result = convertView;
            SetRecordHolder holder;

            if (result == null) {
                result = getLayoutInflater().inflate(R.layout.previous_set_view, parent, false);
                holder = new SetRecordHolder(result);
                holder.dateValue = result.findViewById(R.id.prev_set_date_value);
                holder.repsValue = result.findViewById(R.id.prev_set_reps_value);
                holder.weightValue = result.findViewById(R.id.prev_set_weight_value);
                result.setTag(holder);
            } else {
                holder = (SetRecordHolder) convertView.getTag();
            }

            if (position >= this.getCount() || position < 0) {
                throw new IllegalArgumentException("position out of bounds");
            }

            SetRecord record = this.getItem(position);

            holder.dateValue.setText(dateFormat.format(record.getCompleted()));
            holder.repsValue.setText(String.format(Locale.getDefault(), "%d", record.getReps()));
            holder.weightValue.setText(weightFormat.format(record.getWeight()));
            holder.position = position;

            return result;
        }
    }

    private class SetRecordHolder extends RecyclerView.ViewHolder {

        public SetRecordHolder(@NonNull View itemView) {
            super(itemView);
        }

        public TextView dateValue;
        public TextView repsValue;
        public TextView weightValue;
        public int position;
    }
}
