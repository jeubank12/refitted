package com.litus_animae.refitted.data;

import androidx.annotation.NonNull;
import androidx.room.Database;
import androidx.room.RoomDatabase;
import androidx.room.TypeConverters;
import androidx.room.migration.Migration;
import androidx.sqlite.db.SupportSQLiteDatabase;

import com.litus_animae.refitted.models.Exercise;
import com.litus_animae.refitted.models.ExerciseSet;
import com.litus_animae.refitted.models.SetRecord;

@Database(entities = {Exercise.class, ExerciseSet.class, SetRecord.class}, version = 2)
@TypeConverters({Converters.class})
public abstract class ExerciseRoom extends RoomDatabase {
    public abstract ExerciseDao getExerciseDao();

    public static final Migration MIGRATION_1_2 = new Migration(1,2) {
        @Override
        public void migrate(@NonNull SupportSQLiteDatabase database) {
            database.execSQL("CREATE TABLE IF NOT EXISTS `SetRecord` " +
                    "(`weight` REAL NOT NULL, `reps` INTEGER NOT NULL, `workout` TEXT, " +
                    "`target_set` TEXT, `completed` INTEGER NOT NULL, `exercise` TEXT NOT NULL, " +
                    "PRIMARY KEY(`exercise`, `completed`))");
        }
    };
}
