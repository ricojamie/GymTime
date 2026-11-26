import Dexie, { type EntityTable, type Table } from 'dexie'
import type { Exercise, MuscleGroup, Preferences, Routine, RoutineDay, RoutineExercise, Workout, WorkoutExerciseInstance, WorkoutSet } from './types'
import { defaultPreferences } from './types'
import { starterExercises, starterMuscles } from './seed'

import { warmupRules } from './warmupRules'

export { defaultPreferences } from './types'

export class IronLogDatabase extends Dexie {
  declare muscle_groups: Table<MuscleGroup, string>
  declare exercises: EntityTable<Exercise, 'id'>
  declare routines: EntityTable<Routine, 'id'>
  declare routine_days: EntityTable<RoutineDay, 'id'>
  declare routine_exercises: EntityTable<RoutineExercise, 'id'>
  declare workouts: EntityTable<Workout, 'id'>
  declare sets: EntityTable<WorkoutSet, 'id'>
  declare workout_exercise_instances: EntityTable<WorkoutExerciseInstance, 'id'>
  declare preferences: Table<Preferences, 'default'>

  constructor(name = 'IronLog') {
    super(name)
    this.use(warmupRules)
    this.version(1).stores({
      muscle_groups: 'name',
      exercises: '++id,name,targetMuscle',
      routines: '++id,name',
      routine_days: '++id,routineId,[routineId+orderIndex]',
      routine_exercises: '++id,routineDayId,exerciseId,[routineDayId+orderIndex]',
      workouts: '++id,startTime,endTime,routineId,routineDayId',
      sets: '++id,workoutId,exerciseId,timestamp,[workoutId+exerciseId],[exerciseId+timestamp],[workoutId+timestamp]',
      workout_exercise_instances: '++id,workoutId,exerciseId,[workoutId+exerciseId],[workoutId+orderIndex]',
      preferences: 'id',
    })
    this.on('populate', async tx => {
      await tx.table('muscle_groups').bulkAdd(starterMuscles.map(name => ({ name })))
      await tx.table('exercises').bulkAdd(starterExercises)
      await tx.table('preferences').add(defaultPreferences)
    })
  }
}

export const db = new IronLogDatabase()
export async function initializeDatabase() {
  await db.open()
  const { closeStaleWorkouts } = await import('./repositories/workouts')
  await closeStaleWorkouts()
}
