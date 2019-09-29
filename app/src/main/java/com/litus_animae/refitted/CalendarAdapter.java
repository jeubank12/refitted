package com.litus_animae.refitted;

import android.content.Intent;
import androidx.databinding.DataBindingUtil;
import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Switch;

import com.litus_animae.refitted.databinding.CalendarRowLayoutBinding;

import java.lang.ref.WeakReference;
import java.util.Locale;

public class CalendarAdapter extends RecyclerView.Adapter<CalendarAdapter.ViewHolder> {

    private int daysCount;
    private final WeakReference<Switch> planSwitch;

    public CalendarAdapter(int daysCount, WeakReference<Switch> planSwitch) {
        this.daysCount = daysCount;
        this.planSwitch = planSwitch;
    }

    @NonNull
    @Override
    public ViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        LayoutInflater inflater = LayoutInflater.from(parent.getContext());
        CalendarRowLayoutBinding binding = DataBindingUtil.inflate(inflater,
                R.layout.calendar_row_layout, parent, false);
        return new ViewHolder(binding);
    }

    @Override
    public void onBindViewHolder(@NonNull ViewHolder holder, int position) {
        holder.binding.setDaysVisible(daysCount - position * 7);
        holder.binding.setMinDay(position * 7);
        holder.binding.setLocale(Locale.getDefault());
    }

    @Override
    public int getItemCount() {
        return (int) Math.ceil(daysCount / 7.0);
    }

    public class ViewHolder extends RecyclerView.ViewHolder implements View.OnClickListener {

        public CalendarRowLayoutBinding binding;

        public ViewHolder(CalendarRowLayoutBinding binding) {
            super(binding.getRoot());
            this.binding = binding;
            binding.day1button.setOnClickListener(this);
            binding.day2button.setOnClickListener(this);
            binding.day3button.setOnClickListener(this);
            binding.day4button.setOnClickListener(this);
            binding.day5button.setOnClickListener(this);
            binding.day6button.setOnClickListener(this);
            binding.day7button.setOnClickListener(this);
        }

        @Override
        public void onClick(View v) {
            int position = getAdapterPosition() * 7;
            Intent intent = new Intent(v.getContext(), ExerciseDetailViewActivity.class);
            Switch mPlanSwitch = planSwitch.get();
            if (mPlanSwitch != null && mPlanSwitch.isChecked()){
                intent.putExtra("workout", "Inferno Size");
            } else {
                intent.putExtra("workout", "AX1");
            }
            switch (v.getId()) {
                case R.id.day1button:
                    intent.putExtra("day", position + 1);
                    break;
                case R.id.day2button:
                    intent.putExtra("day", position + 2);
                    break;
                case R.id.day3button:
                    intent.putExtra("day", position + 3);
                    break;
                case R.id.day4button:
                    intent.putExtra("day", position + 4);
                    break;
                case R.id.day5button:
                    intent.putExtra("day", position + 5);
                    break;
                case R.id.day6button:
                    intent.putExtra("day", position + 6);
                    break;
                case R.id.day7button:
                    intent.putExtra("day", position + 7);
                    break;
                default:
                    return;
            }
            v.getContext().startActivity(intent);
        }
    }
}
