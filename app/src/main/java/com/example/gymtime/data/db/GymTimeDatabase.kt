package com.example.gymtime.data.db

import androidx.room.Database
import androidx.room.RoomDatabase
import androidx.room.TypeConverters
import com.example.gymtime.data.db.dao.ExerciseDao
import com.example.gymtime.data.db.dao.MuscleGroupDao
import com.example.gymtime.data.db.dao.RoutineDao
import com.example.gymtime.data.db.dao.SetDao
import com.example.gymtime.data.db.dao.WorkoutDao
import com.example.gymtime.data.db.entity.Exercise
import com.example.gymtime.data.db.entity.MuscleGroup
import com.example.gymtime.data.db.entity.Routine
import com.example.gymtime.data.db.entity.RoutineDay
import com.example.gymtime.data.db.entity.RoutineExercise
import com.example.gymtime.data.db.entity.Set
import com.example.gymtime.data.db.entity.Workout

@Database(
    entities = [
        Exercise::class,
        Workout::class,
        Set::class,
        Routine::class,
        RoutineExercise::class,
        RoutineDay::class,
        MuscleGroup::class
    ],
    version = 8, // Added superset support
    exportSchema = false
)
@TypeConverters(Converters::class)
abstract class GymTimeDatabase : RoomDatabase() {
    abstract fun exerciseDao(): ExerciseDao
    abstract fun workoutDao(): WorkoutDao
    abstract fun setDao(): SetDao
    abstract fun routineDao(): RoutineDao
    abstract fun muscleGroupDao(): MuscleGroupDao
}
