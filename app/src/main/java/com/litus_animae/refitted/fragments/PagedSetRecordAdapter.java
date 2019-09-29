package com.litus_animae.refitted.fragments;

import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.paging.PagedListAdapter;
import androidx.recyclerview.widget.DiffUtil;
import androidx.recyclerview.widget.RecyclerView;

import com.litus_animae.refitted.R;
import com.litus_animae.refitted.models.SetRecord;

import java.text.DateFormat;
import java.text.DecimalFormat;
import java.text.NumberFormat;
import java.util.Locale;
import java.util.Objects;

public class PagedSetRecordAdapter extends PagedListAdapter<SetRecord, SetRecordHolder> {

    PagedSetRecordAdapter() {
        super(DIFF_CALLBACK);
    }

    @NonNull
    @Override
    public SetRecordHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        View view = LayoutInflater.from(parent.getContext()).inflate(R.layout.previous_set_view, parent, false);
        return new SetRecordHolder(view);
    }

    @Override
    public void onBindViewHolder(@NonNull SetRecordHolder holder, int position) {
        SetRecord record = getItem(position);
        if (record != null){
            holder.bindTo(record, position);
        } else {
            holder.clear();
        }
    }

    private static DiffUtil.ItemCallback<SetRecord> DIFF_CALLBACK =
            new DiffUtil.ItemCallback<SetRecord>() {
                @Override
                public boolean areItemsTheSame(@NonNull SetRecord oldItem, @NonNull SetRecord newItem) {
                    return oldItem.getCompleted().equals(newItem.getCompleted());
                }

                @Override
                public boolean areContentsTheSame(@NonNull SetRecord oldItem, @NonNull SetRecord newItem) {
                    return oldItem.equals(newItem);
                }
            };
}

class SetRecordHolder extends RecyclerView.ViewHolder {
    private static final DateFormat dateFormat = DateFormat.getDateInstance(DateFormat.SHORT);
    private static final NumberFormat weightFormat = new DecimalFormat("##.#");

    SetRecordHolder(@NonNull View itemView) {
        super(itemView);
        dateValue = itemView.findViewById(R.id.prev_set_date_value);
        repsValue = itemView.findViewById(R.id.prev_set_reps_value);
        weightValue = itemView.findViewById(R.id.prev_set_weight_value);
        itemView.setTag(this);
    }

    TextView dateValue;
    TextView repsValue;
    TextView weightValue;
    int position;

    void bindTo(SetRecord record, int position){
        dateValue.setText(dateFormat.format(record.getCompleted()));
        repsValue.setText(String.format(Locale.getDefault(), "%d", record.getReps()));
        weightValue.setText(weightFormat.format(record.getWeight()));
        this.position = position;
    }

    void clear(){
        dateValue.setText("");
        repsValue.setText("");
        weightValue.setText("");
        position = -1;

    }
}
