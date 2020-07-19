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

@Database(entities = {Exercise.class, ExerciseSet.class, SetRecord.class}, version = 4)
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

    public static final Migration MIGRATION_3_4 = new Migration(3,4) {
        @Override
        public void migrate(@NonNull SupportSQLiteDatabase database) {
            database.beginTransaction();
            database.execSQL("ALTER TABLE `ExerciseSet` RENAME TO _exercise_set_old");
            database.execSQL("CREATE TABLE `ExerciseSet` (" +
                    "`day` TEXT NOT NULL, " +
                    "`step` TEXT NOT NULL, " +
                    "`workout` TEXT NOT NULL, " +
                    "`id` TEXT NOT NULL, " +
                    "`name` TEXT NOT NULL, " +
                    "`note` TEXT NOT NULL, " +
                    "`reps` INTEGER NOT NULL, " +
                    "`sets` INTEGER NOT NULL, " +
                    "`toFailure` INTEGER NOT NULL, " +
                    "`rest` INTEGER NOT NULL, " +
                    "`repsUnit` TEXT NOT NULL, " +
                    "`repsRange` INTEGER NOT NULL, " +
                    "PRIMARY KEY(`day`, `step`, `workout`), " +
                    "FOREIGN KEY(`name`, `workout`) REFERENCES `Exercise`(`exercise_name`, `exercise_workout`) ON UPDATE NO ACTION ON DELETE NO ACTION )");
            database.execSQL("INSERT INTO `ExerciseSet` (day, step, workout, id, name, note, reps, sets, toFailure, rest, repsUnit, repsRange) " +
                    "SELECT day, step, workout, " +
                    "coalesce(id, ``), coalesce(name,``), coalesce(note, ``), " +
                    "reps, sets, toFailure, rest, repsUnit, repsRange " +
                    "FROM _exercise_set_old");
            database.execSQL("DROP TABLE _exercise_set_old");
            database.setTransactionSuccessful();
            database.endTransaction();
        }
    };
}
