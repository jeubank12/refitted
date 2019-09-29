package com.litus_animae.refitted;

import android.content.Intent;
import android.content.SharedPreferences;
import android.os.Bundle;
import android.util.Log;
import android.widget.Switch;

import com.google.android.material.appbar.CollapsingToolbarLayout;

import androidx.appcompat.app.AppCompatActivity;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;
import androidx.appcompat.widget.Toolbar;

import java.lang.ref.WeakReference;

public class WorkoutCalendarViewActivity extends AppCompatActivity {

    private static final String TAG = "WorkoutCalendarViewActi";

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);


        try {
            SharedPreferences prefs = getSharedPreferences("RefittedMainPrefs", MODE_PRIVATE);
            Class<?> activityClass = Class.forName(
                    prefs.getString("lastActivity", getClass().getName()));

            Intent intent = new Intent(this, activityClass);
            String workout = prefs.getString("workout", "-1");
            int day = prefs.getInt("day", -1);
            intent.putExtra("workout", workout);
            intent.putExtra("day", day);
            if (activityClass != getClass() && day > 0 && workout != "-1") {
                startActivity(intent);
            }
        } catch (ClassNotFoundException ex) {
            Log.e(TAG, "onCreate: bad class reference in shared preferences", ex);
        } catch (Exception ex){
            Log.e(TAG, "onCreate: shared preferences error", ex);
        }

        setContentView(R.layout.activity_workout_calendar_view);
        Toolbar toolbar = findViewById(R.id.toolbar);
        setSupportActionBar(toolbar);
        ((CollapsingToolbarLayout) findViewById(R.id.toolbar_layout))
                .setTitle("Athlean-X");

        Switch planSwitch = findViewById(R.id.switch1);

        RecyclerView list = findViewById(R.id.calendar_recycler);
        list.setAdapter(new CalendarAdapter(84, new WeakReference<>(planSwitch)));
        list.setLayoutManager(new LinearLayoutManager(this));
    }

    @Override
    protected void onResume() {
        super.onResume();

        SharedPreferences prefs = getSharedPreferences("RefittedMainPrefs", MODE_PRIVATE);
        SharedPreferences.Editor editor = prefs.edit();
        editor.putString("lastActivity", getClass().getName());
        editor.apply();
    }
}
