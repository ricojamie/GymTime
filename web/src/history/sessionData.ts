import { useLiveQuery } from 'dexie-react-hooks'
import { db } from '../db/database'
import { defaultPreferences } from '../db/types'
import { workoutBlueprint } from '../db/repositories'
import { saveDay } from '../routines/mutations'

export function useHistoryData() {
  return useLiveQuery(() => db.transaction('r', [db.workouts, db.sets, db.exercises, db.preferences], async () => {
    const [workouts, sets, exercises, pref] = await Promise.all([
      db.workouts.toArray(), db.sets.toArray(), db.exercises.toArray(), db.preferences.get('default'),
    ])
    return { workouts, sets, exercises, prefs: pref ?? defaultPreferences }
  }), [])
}
export type HistoryData = NonNullable<ReturnType<typeof useHistoryData>>

export async function addWorkoutToRoutine(workoutId: number, routineId: number, replaceId: number | null, name: string) {
  return db.transaction('rw', [db.workouts, db.sets, db.exercises, db.workout_exercise_instances,
    db.routines, db.routine_days, db.routine_exercises], async () => {
    const plan = await workoutBlueprint(workoutId)
    if (!plan.length) throw new Error('This workout has no exercises to add.')
    const day = replaceId === null ? null : await db.routine_days.get(replaceId)
    const groups = new Map<string, string>()
    const rows = plan.map(item => {
      if (item.supersetGroupId && !groups.has(item.supersetGroupId)) groups.set(item.supersetGroupId, crypto.randomUUID())
      return { exerciseId: item.exerciseId, targetSets: item.plannedSets ?? 3,
        targetRepsMin: item.repMin, targetRepsMax: item.repMax, targetRestSeconds: item.restSeconds, notes: item.notes,
        supersetGroupId: item.supersetGroupId ? groups.get(item.supersetGroupId)! : null,
        supersetOrderIndex: item.supersetOrderIndex }
    })
    return saveDay(routineId, replaceId, day?.name ?? name, rows)
  })
}
