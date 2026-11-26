import { db } from '../database'
import { WARMUP_MUSCLE_GROUP, normalizeMuscleName, type Exercise, type WorkoutSet } from '../types'
import { emptyDraft, normalizeDraft, validateSet } from '../../domain/loggingValidation'
import { distanceToMeters } from '../../domain/units'
import { requireActive, ensureWorkoutExercise } from './plans'

function sanitizedSet<T extends Omit<WorkoutSet, 'id'>>(row: T, exercise: Exercise): T {
  const draft = normalizeDraft(exercise.logType, row)
  const problem = validateSet(exercise.logType, draft)
  if (problem) throw new Error(problem)
  return { ...row, ...draft, distanceMeters: distanceToMeters(draft.distanceValue, draft.distanceUnit),
    isWarmup: draft.isWarmup || normalizeMuscleName(exercise.targetMuscle) === WARMUP_MUSCLE_GROUP }
}
export type NewSet = Partial<Omit<WorkoutSet,'id'>> & Pick<WorkoutSet,'workoutId'|'exerciseId'>
export function logSet(input: NewSet) {
  return db.transaction('rw', db.exercises, db.workouts, db.sets, db.workout_exercise_instances, async () => {
    const [workout, exercise, slot] = await Promise.all([
      requireActive(input.workoutId), db.exercises.get(input.exerciseId),
      db.workout_exercise_instances.where('[workoutId+exerciseId]').equals([input.workoutId, input.exerciseId]).filter(i => !i.isSkipped).first(),
    ])
    if (!exercise) throw new Error('Exercise no longer exists.')
    const row = { ...emptyDraft(), isComplete: true, distanceMeters: null, timestamp: Date.now(),
      supersetGroupId: slot?.supersetGroupId ?? null, supersetOrderIndex: slot?.supersetOrderIndex ?? 0,
      ...input, workoutId: workout.id }
    const valid = sanitizedSet(row, exercise)
    if (!slot) await ensureWorkoutExercise(input.workoutId, input.exerciseId)
    return db.sets.add(valid)
  })
}
export function updateSet(id: number, patch: Partial<Omit<WorkoutSet,'id'|'exerciseId'|'workoutId'>>) {
  return db.transaction('rw', db.exercises, db.workouts, db.sets, async () => {
    const old = await db.sets.get(id)
    if (!old) throw new Error('Set no longer exists.')
    const exercise = await db.exercises.get(old.exerciseId)
    if (!exercise) throw new Error('Exercise no longer exists.')
    const row = {...old, ...patch, id, workoutId: old.workoutId, exerciseId: old.exerciseId}
    await db.sets.put(sanitizedSet(row, exercise))
  })
}
export function deleteSet(id: number) { return db.sets.delete(id) }
