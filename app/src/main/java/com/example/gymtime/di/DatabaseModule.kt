package com.example.gymtime.di

import android.content.Context
import androidx.room.Room
import com.example.gymtime.data.db.GymTimeDatabase
import com.example.gymtime.data.db.dao.ExerciseDao
import com.example.gymtime.data.db.dao.RoutineDao
import com.example.gymtime.data.db.dao.SetDao
import com.example.gymtime.data.db.dao.WorkoutDao
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
object DatabaseModule {

    @Provides
    @Singleton
    fun provideDatabase(@ApplicationContext context: Context): GymTimeDatabase {
        return Room.databaseBuilder(
            context,
            GymTimeDatabase::class.java,
            "gym_time_db"
        ).build()
    }

    @Provides
    fun provideExerciseDao(database: GymTimeDatabase): ExerciseDao = database.exerciseDao()

    @Provides
    fun provideWorkoutDao(database: GymTimeDatabase): WorkoutDao = database.workoutDao()

    @Provides
    fun provideSetDao(database: GymTimeDatabase): SetDao = database.setDao()

    @Provides
    fun provideRoutineDao(database: GymTimeDatabase): RoutineDao = database.routineDao()
}
