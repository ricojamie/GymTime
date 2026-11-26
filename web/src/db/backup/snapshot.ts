import { db } from '../database'
import type { Snapshot } from './format'

/** Call inside the caller's transaction so every table belongs to one database revision. */
export async function readSnapshot(): Promise<Snapshot> {
  const [
    muscle_groups,
    exercises,
    routines,
    routine_days,
    workouts,
    routine_exercises,
    sets,
    workout_exercise_instances,
  ] = await Promise.all([
    db.muscle_groups.toArray(),
    db.exercises.toArray(),
    db.routines.toArray(),
    db.routine_days.toArray(),
    db.workouts.toArray(),
    db.routine_exercises.toArray(),
    db.sets.toArray(),
    db.workout_exercise_instances.toArray(),
  ])
  return {
    muscle_groups,
    exercises,
    routines,
    routine_days,
    workouts,
    routine_exercises,
    sets,
    workout_exercise_instances,
  }
}
