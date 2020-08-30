package com.litus_animae.refitted.data.room

import androidx.room.Database
import androidx.room.RoomDatabase
import androidx.room.TypeConverters
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase
import com.litus_animae.refitted.data.Converters
import com.litus_animae.refitted.models.Exercise
import com.litus_animae.refitted.models.ExerciseSet
import com.litus_animae.refitted.models.RoomExerciseSet
import com.litus_animae.refitted.models.SetRecord

@Database(entities = [Exercise::class, RoomExerciseSet::class, SetRecord::class], version = 4)
@TypeConverters(Converters::class)
abstract class ExerciseRoom : RoomDatabase() {
    abstract fun getExerciseDao(): ExerciseDao

    companion object {
        val MIGRATION_1_2: Migration = object : Migration(1, 2) {
            override fun migrate(database: SupportSQLiteDatabase) {
                database.execSQL("CREATE TABLE IF NOT EXISTS `SetRecord` " +
                        "(`weight` REAL NOT NULL, `reps` INTEGER NOT NULL, `workout` TEXT, " +
                        "`target_set` TEXT, `completed` INTEGER NOT NULL, `exercise` TEXT NOT NULL, " +
                        "PRIMARY KEY(`exercise`, `completed`))")
            }
        }
        val MIGRATION_2_3: Migration = object : Migration(2, 3) {
            override fun migrate(database: SupportSQLiteDatabase) {
                database.execSQL("ALTER TABLE `ExerciseSet` " +
                        "ADD COLUMN `repsRange` INTEGER NOT NULL default 0")
                database.execSQL("ALTER TABLE `ExerciseSet` " +
                        "ADD COLUMN `repsUnit` TEXT")
            }
        }
        val MIGRATION_3_4: Migration = object : Migration(3, 4) {
            override fun migrate(database: SupportSQLiteDatabase) {
                database.beginTransaction()
                database.execSQL("ALTER TABLE `ExerciseSet` RENAME TO _exercise_set_old")
                database.execSQL("CREATE TABLE `ExerciseSet` (" +
                        "`day` TEXT NOT NULL, " +
                        "`step` TEXT NOT NULL, " +
                        "`workout` TEXT NOT NULL, " +
                        "`name` TEXT NOT NULL, " +
                        "`note` TEXT NOT NULL, " +
                        "`reps` INTEGER NOT NULL, " +
                        "`sets` INTEGER NOT NULL, " +
                        "`toFailure` INTEGER NOT NULL, " +
                        "`rest` INTEGER NOT NULL, " +
                        "`repsUnit` TEXT NOT NULL, " +
                        "`repsRange` INTEGER NOT NULL, " +
                        "PRIMARY KEY(`day`, `step`, `workout`), " +
                        "FOREIGN KEY(`name`, `workout`) REFERENCES `Exercise`(`exercise_name`, `exercise_workout`) ON UPDATE NO ACTION ON DELETE NO ACTION )")
                database.execSQL("INSERT INTO `ExerciseSet` (day, step, workout, name, note, reps, sets, toFailure, rest, repsUnit, repsRange) " +
                        "SELECT day, step, workout, " +
                        "coalesce(name,``), coalesce(note, ``), " +
                        "reps, sets, toFailure, rest, repsUnit, repsRange " +
                        "FROM _exercise_set_old")
                database.execSQL("DROP TABLE _exercise_set_old")
                database.setTransactionSuccessful()
                database.endTransaction()
            }
        }
    }
}