package com.litus_animae.refitted;

import android.os.Bundle;
import com.google.android.material.appbar.CollapsingToolbarLayout;
import androidx.appcompat.app.AppCompatActivity;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;
import androidx.appcompat.widget.Toolbar;

public class WorkoutCalendarViewActivity extends AppCompatActivity {

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_workout_calendar_view);
        Toolbar toolbar = (Toolbar) findViewById(R.id.toolbar);
        setSupportActionBar(toolbar);
        ((CollapsingToolbarLayout) findViewById(R.id.toolbar_layout))
                .setTitle("AX1");
        RecyclerView list = findViewById(R.id.calendar_recycler);
        list.setAdapter(new CalendarAdapter(90));
        list.setLayoutManager(new LinearLayoutManager(this));
    }
}
