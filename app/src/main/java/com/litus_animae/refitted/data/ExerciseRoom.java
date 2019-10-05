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

@Database(entities = {Exercise.class, ExerciseSet.class, SetRecord.class}, version = 3)
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

    public static final Migration MIGRATION_2_3 = new Migration(2,3) {
        @Override
        public void migrate(@NonNull SupportSQLiteDatabase database) {
            database.execSQL("ALTER TABLE `ExerciseSet` " +
                    "ADD COLUMN `repsRange` INTEGER NOT NULL default 0");
            database.execSQL("ALTER TABLE `ExerciseSet` " +
                    "ADD COLUMN `repsUnit` TEXT");
        }
    };
}
