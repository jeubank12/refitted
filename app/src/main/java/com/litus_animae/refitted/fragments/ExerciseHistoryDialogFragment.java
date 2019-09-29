package com.litus_animae.refitted.fragments;

import android.app.Dialog;
import android.content.DialogInterface;
import android.os.Bundle;
import android.util.Log;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.appcompat.app.AlertDialog;
import androidx.fragment.app.DialogFragment;
import androidx.lifecycle.LiveData;
import androidx.lifecycle.ViewModelProviders;
import androidx.paging.LivePagedListBuilder;
import androidx.paging.PagedList;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.litus_animae.refitted.R;
import com.litus_animae.refitted.models.ExerciseViewModel;
import com.litus_animae.refitted.models.SetRecord;

public class ExerciseHistoryDialogFragment extends DialogFragment {
    private static final String TAG = "ExerciseHistoryDialogFr";

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
        PagedSetRecordAdapter adapter = new PagedSetRecordAdapter();
        model.getCurrentRecord().observe(this, exerciseRecord -> {
            LiveData<PagedList<SetRecord>> recordList = new LivePagedListBuilder<>(exerciseRecord.getAllSets(), 10).build();
            recordList.observe(this, adapter::submitList);
        });
        RecyclerView recyclerView = new RecyclerView(getContext());
        recyclerView.setLayoutManager(new LinearLayoutManager(getContext()));
        recyclerView.setAdapter(adapter);
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
                .setView(recyclerView);
//                .setSingleChoiceItems((ListAdapter) adapter, 0, new DialogInterface.OnClickListener() {
//                    @Override
//                    public void onClick(DialogInterface dialog, int which) {
//
//                    }
//                });
        return builder.create();
    }
}

