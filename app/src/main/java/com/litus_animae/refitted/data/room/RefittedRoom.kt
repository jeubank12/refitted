package com.litus_animae.refitted.data.room

import androidx.room.Database
import androidx.room.RoomDatabase
import androidx.room.TypeConverters
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase
import com.litus_animae.refitted.models.*

@Database(
  entities = [Exercise::class, RoomExerciseSet::class, SetRecord::class, WorkoutPlan::class, SavedState::class],
  version = 12
)
@TypeConverters(Converters::class)
abstract class RefittedRoom : RoomDatabase() {
  abstract fun getExerciseDao(): ExerciseDao
  abstract fun getWorkoutPlanDao(): WorkoutPlanDao
  abstract fun getSavedStateDao(): SavedStateDao

  companion object {
    val MIGRATION_1_2: Migration = object : Migration(1, 2) {
      override fun migrate(db: SupportSQLiteDatabase) {
        db.execSQL(
          "CREATE TABLE IF NOT EXISTS `SetRecord` " +
            "(`weight` REAL NOT NULL, " +
            "`reps` INTEGER NOT NULL, " +
            "`workout` TEXT, " +
            "`target_set` TEXT, " +
            "`completed` INTEGER NOT NULL, " +
            "`exercise` TEXT NOT NULL, " +
            "PRIMARY KEY(`exercise`, `completed`))"
        )
      }
    }
    val MIGRATION_2_3: Migration = object : Migration(2, 3) {
      override fun migrate(db: SupportSQLiteDatabase) {
        db.execSQL(
          "ALTER TABLE `ExerciseSet` " +
            "ADD COLUMN `repsRange` INTEGER NOT NULL default 0"
        )
        db.execSQL(
          "ALTER TABLE `ExerciseSet` " +
            "ADD COLUMN `repsUnit` TEXT"
        )
      }
    }
    val MIGRATION_3_4: Migration = object : Migration(3, 4) {
      override fun migrate(db: SupportSQLiteDatabase) {
        db.beginTransaction()
        db.execSQL("DROP TABLE `ExerciseSet`")
        db.execSQL(
          "CREATE TABLE `ExerciseSet` (" +
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
            "FOREIGN KEY(`name`, `workout`) REFERENCES `Exercise`(`exercise_name`, `exercise_workout`) ON UPDATE NO ACTION ON DELETE NO ACTION )"
        )
        db.execSQL("CREATE INDEX IF NOT EXISTS `index_exerciseset_name_workout` ON `exerciseset` (`name`, `workout`)")
        db.execSQL("ALTER TABLE `SetRecord` RENAME TO _set_record_old")
        db.execSQL(
          "CREATE TABLE IF NOT EXISTS `SetRecord` " +
            "(`weight` REAL NOT NULL, " +
            "`reps` INTEGER NOT NULL, " +
            "`workout` TEXT NOT NULL, " +
            "`target_set` TEXT NOT NULL, " +
            "`completed` INTEGER NOT NULL, " +
            "`exercise` TEXT NOT NULL, " +
            "PRIMARY KEY(`exercise`, `completed`))"
        )
        db.execSQL(
          "INSERT INTO `SetRecord` (weight, reps, workout, target_set, completed, exercise) " +
            "SELECT weight, reps, COALESCE(workout, ''), COALESCE(target_set, ''), completed, exercise " +
            "FROM _set_record_old"
        )
        db.execSQL("DROP TABLE _set_record_old")
        db.setTransactionSuccessful()
        db.endTransaction()
      }
    }
    val MIGRATION_4_5: Migration = object : Migration(4, 5) {
      override fun migrate(db: SupportSQLiteDatabase) {
        db.execSQL(
          "CREATE TABLE `workouts` (" +
            "`workout` TEXT NOT NULL PRIMARY KEY" +
            ")"
        )
      }
    }
    val MIGRATION_5_6: Migration = object : Migration(5, 6) {
      override fun migrate(db: SupportSQLiteDatabase) {
        db.execSQL(
          "ALTER TABLE `workouts` " +
            "ADD COLUMN `totalDays` INT NOT NULL DEFAULT 84"
        )
        db.execSQL(
          "ALTER TABLE `workouts` " +
            "ADD COLUMN `lastViewedDay` INT NOT NULL DEFAULT 1"
        )
        db.execSQL(
          "CREATE TABLE `SavedState` (" +
            "`key` TEXT NOT NULL PRIMARY KEY," +
            "`value` TEXT NOT NULL" +
            ")"
        )
      }
    }
    val MIGRATION_6_7: Migration = object : Migration(6, 7) {
      override fun migrate(db: SupportSQLiteDatabase) {
        db.execSQL(
          "ALTER TABLE `workouts` " +
            "ADD COLUMN `workoutStartDate` INT NOT NULL DEFAULT 1643673600" // 2/1/22 midnight
        )
      }
    }
    val MIGRATION_7_8: Migration = object : Migration(7, 8) {
      override fun migrate(db: SupportSQLiteDatabase) {
        db.execSQL(
          "ALTER TABLE `workouts` " +
            "ADD COLUMN `restDays` TEXT NOT NULL DEFAULT ''"
        )
      }
    }
    val MIGRATION_8_9: Migration = object : Migration(8, 9) {
      override fun migrate(db: SupportSQLiteDatabase) {
        db.execSQL(
          "ALTER TABLE `exerciseset` " +
            "ADD COLUMN `timeLimit` INT"
        )
        db.execSQL(
          "ALTER TABLE `exerciseset` " +
            "ADD COLUMN `timeLimitUnit` TEXT"
        )
      }
    }
    val MIGRATION_9_10: Migration = object : Migration(9, 10) {
      override fun migrate(db: SupportSQLiteDatabase) {
        db.execSQL("DROP TABLE IF EXISTS `exerciseset`")
        db.execSQL(
          """
          |CREATE TABLE `exerciseset` (
          |    `workout` TEXT NOT NULL, 
          |    `day` TEXT NOT NULL, 
          |    `step` TEXT NOT NULL, 
          |    `primaryStep` INTEGER NOT NULL, 
          |    `superSetStep` INTEGER, 
          |    `alternateStep` TEXT, 
          |    `name` TEXT NOT NULL, 
          |    `note` TEXT NOT NULL, 
          |    `reps` INTEGER NOT NULL, 
          |    `sets` INTEGER NOT NULL, 
          |    `toFailure` INTEGER NOT NULL, 
          |    `rest` INTEGER NOT NULL, 
          |    `repsUnit` TEXT NOT NULL, 
          |    `repsRange` INTEGER NOT NULL, 
          |    `timeLimit` INTEGER, 
          |    `timeLimitUnit` TEXT, 
          |    PRIMARY KEY(`day`, `step`, `workout`), 
          |    FOREIGN KEY(`name`, `workout`) 
          |        REFERENCES `Exercise`(`exercise_name`, `exercise_workout`) 
          |        ON UPDATE NO ACTION 
          |        ON DELETE NO ACTION 
          |)""".trimMargin()
        )
        db.execSQL("CREATE INDEX `index_exerciseset_name_workout` ON `exerciseset` (`name`, `workout`)")
      }
    }

    val MIGRATION_10_11: Migration = object : Migration(10, 11) {
      override fun migrate(db: SupportSQLiteDatabase) {
        db.execSQL(
          "ALTER TABLE `workouts` " +
            "ADD COLUMN `description` TEXT NOT NULL DEFAULT ''"
        )
        db.execSQL(
          "ALTER TABLE `workouts` " +
            "ADD COLUMN `globalAlternateLabels` TEXT NOT NULL DEFAULT ''"
        )
        db.execSQL(
          "ALTER TABLE `workouts` " +
            "ADD COLUMN `globalAlternate` INT"
        )
      }
    }

    val MIGRATION_11_12: Migration = object : Migration(11, 12) {
      override fun migrate(db: SupportSQLiteDatabase) {
        db.execSQL(
          "ALTER TABLE `exerciseset` " +
            "ADD COLUMN `repsSequence` TEXT NOT NULL DEFAULT ''"
        )
      }
    }
  }
}