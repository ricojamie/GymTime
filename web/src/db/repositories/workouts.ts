import { calculateBestStreak } from '../../domain/streakCalculator'
import { db, defaultPreferences } from '../database'
import type { Workout } from '../types'
import { blankSlot, workoutBlueprint, type BlueprintItem } from './plans'

export function getActiveWorkout() {
  // IndexedDB excludes null from indexes. Looking up endTime with equals(null) loses sessions.
  return db.workouts.orderBy('startTime').reverse().filter(w => w.endTime === null).first()
}
function blankWorkout(name?: string): Omit<Workout, 'id'> {
  return { startTime: Date.now(), endTime: null, name: name?.trim() || null, note: null, rating: null,
    ratingNote: null, routineDayId: null, routineId: null, routineNameSnapshot: null,
    routineDayNameSnapshot: null, startedFromRoutine: false }
}
export interface StartOptions { name?: string; exerciseIds?: number[]; routineDayId?: number }
export function startWorkout({name, exerciseIds = [], routineDayId}: StartOptions = {}) {
  return db.transaction('rw', [db.workouts, db.exercises, db.routines, db.routine_days, db.routine_exercises,
    db.workout_exercise_instances], async () => {
    const active = await getActiveWorkout()
    if (active) return active.id
    const workout = blankWorkout(name)
    let plan: BlueprintItem[]
    if (routineDayId !== undefined) {
      const day = await db.routine_days.get(routineDayId)
      if (!day) throw new Error('Routine day no longer exists.')
      const routine = await db.routines.get(day.routineId)
      if (!routine) throw new Error('Routine no longer exists.')
      const entries = await db.routine_exercises.where('routineDayId').equals(day.id).sortBy('orderIndex')
      if (!entries.length) throw new Error('Add an exercise to this day first.')
      Object.assign(workout, { name: name || day.name, routineDayId, routineId: routine.id,
        routineNameSnapshot: routine.name, routineDayNameSnapshot: day.name, startedFromRoutine: true })
      plan = entries.map(e => ({...blankSlot(e.exerciseId, e.orderIndex), routineExerciseId: e.id,
        plannedSets: e.targetSets, repMin: e.targetRepsMin, repMax: e.targetRepsMax,
        restSeconds: e.targetRestSeconds, notes: e.notes, supersetGroupId: e.supersetGroupId,
        supersetOrderIndex: e.supersetOrderIndex }))
    } else {
      plan = [...new Set(exerciseIds)].map(blankSlot)
    }
    const exercises = await db.exercises.bulkGet(plan.map(e => e.exerciseId))
    if (exercises.some(e => !e)) throw new Error('An exercise in this plan no longer exists.')
    const workoutId = await db.workouts.add(workout)
    await db.workout_exercise_instances.bulkAdd(plan.map(e => ({...e, workoutId})))
    return workoutId
  })
}

export function repeatWorkout(id: number) {
  return db.transaction('rw', db.workouts, db.sets, db.workout_exercise_instances, async () => {
    if (await getActiveWorkout()) throw new Error('Finish the active workout first.')
    const old = await db.workouts.get(id)
    if (!old) throw new Error('Workout no longer exists.')
    const plan = await workoutBlueprint(id)
    if (!plan.length) throw new Error('This workout has no exercises to repeat.')
    const workoutId = await db.workouts.add({...blankWorkout(old.name ?? undefined),
      routineNameSnapshot: old.routineNameSnapshot, routineDayNameSnapshot: old.routineDayNameSnapshot,
      startedFromRoutine: true })
    const groups = new Map<string, string>()
    const rows = plan.map(slot => {
      let group = null
      if (slot.supersetGroupId) {
        group = groups.get(slot.supersetGroupId) ?? crypto.randomUUID()
        groups.set(slot.supersetGroupId, group)
      }
      return {...slot, workoutId, supersetGroupId: group}
    })
    await db.workout_exercise_instances.bulkAdd(rows)
    return workoutId
  })
}

export function reopenWorkout(id: number) {
  return db.transaction('rw', db.workouts, db.preferences, async () => {
    const target = await db.workouts.get(id)
    if (!target) throw new Error('Workout no longer exists.')
    if (target.endTime === null) return
    if (await getActiveWorkout()) throw new Error('Finish the active workout first.')
    await db.workouts.update(id,{endTime:null})
    const prefs = await db.preferences.get('default')
    if (prefs && (prefs.lastBackupAtEpochMs ?? 0) < target.endTime) {
      await db.preferences.update('default', {workoutsSinceLastBackup: Math.max(0, prefs.workoutsSinceLastBackup - 1)})
    }
  })
}

export function finishWorkout(id: number, at = Date.now()) {
  return db.transaction('rw', [db.workouts, db.workout_exercise_instances, db.sets, db.routines, db.routine_days, db.preferences], async () => {
    const workout = await db.workouts.get(id)
    if (!workout || workout.endTime !== null) return
    const endTime = Math.max(at, workout.startTime)
    await db.workouts.update(id, {endTime})
    const prefs = await db.preferences.get('default') ?? defaultPreferences
    const best = calculateBestStreak(await db.sets.toArray(), prefs.restDaysPerWeek, at)
    await db.preferences.put({...prefs, bestStreak: Math.max(prefs.bestStreak, best),
      workoutsSinceLastBackup: prefs.workoutsSinceLastBackup + Number(endTime > (prefs.lastBackupAtEpochMs ?? 0))})
    if (!workout.startedFromRoutine || !workout.routineId || !workout.routineDayId) return
    const routine = await db.routines.get(workout.routineId)
    if (!routine?.isActive) return
    const newer = await db.workouts.where('routineId').equals(routine.id)
      .filter(w => w.endTime !== null && w.startTime > workout.startTime).first()
    if (newer) return
    const sets = await db.sets.where('workoutId').equals(id).toArray()
    const started = new Set(sets.map(s => s.exerciseId))
    if (!await db.workout_exercise_instances.where('workoutId').equals(id).filter(i => started.has(i.exerciseId)).first()) return
    const days = await db.routine_days.where('routineId').equals(routine.id).sortBy('orderIndex')
    const index = days.findIndex(d => d.id === workout.routineDayId)
    if (index >= 0) await db.routines.update(routine.id, {nextDayOrderIndex: days[(index + 1) % days.length].orderIndex})
  })
}

export function updateWorkout(id: number, patch: Partial<Pick<Workout,'name'|'note'|'rating'|'ratingNote'>>) {
  if (patch.rating != null && (!Number.isInteger(patch.rating) || patch.rating < 1 || patch.rating > 5)) throw new Error('Choose a rating between 1 and 5.')
  if (patch.ratingNote && patch.ratingNote.length > 2000) throw new Error('Session feedback is limited to 2,000 characters.')
  return db.workouts.update(id, patch)
}
export function deleteWorkout(id: number) {
  return db.transaction('rw', db.workouts, db.sets, db.workout_exercise_instances, db.preferences, async () => {
    const workout = await db.workouts.get(id)
    if (!workout) return
    await db.sets.where('workoutId').equals(id).delete()
    await db.workout_exercise_instances.where('workoutId').equals(id).delete()
    await db.workouts.delete(id)
    const prefs = await db.preferences.get('default')
    if (workout.endTime !== null && prefs && workout.endTime > (prefs.lastBackupAtEpochMs ?? 0)) {
      await db.preferences.update('default', {workoutsSinceLastBackup: Math.max(0, prefs.workoutsSinceLastBackup - 1)})
    }
  })
}
export function closeStaleWorkouts(now = Date.now()) {
  return db.transaction('rw', [db.workouts, db.workout_exercise_instances, db.sets, db.routines, db.routine_days, db.preferences], async () => {
    const ongoing = await db.workouts.filter(w => w.endTime === null).toArray()
    for (const w of ongoing) {
      const last = await db.sets.where('[workoutId+timestamp]').between([w.id, -Infinity], [w.id, Infinity]).last()
      if (now - (last?.timestamp ?? w.startTime) < 4 * 60 * 60 * 1000) continue
      if (last) await finishWorkout(w.id, last.timestamp)
      else await deleteWorkout(w.id)
    }
  })
}
