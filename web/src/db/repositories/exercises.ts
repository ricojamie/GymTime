import { db } from '../database'
import { normalizeMuscleName, type Exercise } from '../types'
import { dissolveOrphanWorkoutGroups, ungrouped } from './plans'

async function checkStarLimit(id?: number) {
  const count = await db.exercises.filter(e => e.isStarred && e.id !== id).count()
  if (count >= 3) throw new Error('You can track up to 3 starred exercises.')
}
export function saveExercise(input: Pick<Exercise,'name'|'targetMuscle'|'logType'> & Partial<Exercise>) {
  return db.transaction('rw', db.exercises, db.muscle_groups, db.sets, async () => {
    const previous = input.id === undefined ? undefined : await db.exercises.get(input.id)
    if (input.id !== undefined && !previous) throw new Error('Exercise no longer exists.')
    const name = input.name.trim()
    const requested = normalizeMuscleName(input.targetMuscle)
    if (!name || !requested) throw new Error('Enter an exercise name and body part.')
    const existingGroup = await db.muscle_groups.where('name').equalsIgnoreCase(requested).first()
    const targetMuscle = existingGroup?.name ?? requested
    if (!existingGroup) await db.muscle_groups.add({name:targetMuscle})
    const next = {defaultDistanceUnit: 'MILES' as const, isCustom:true, notes:null, defaultRestSeconds:90,
      isStarred:false, repTarget:null, ...previous, ...input, name, targetMuscle}
    if (!Number.isInteger(next.defaultRestSeconds) || next.defaultRestSeconds < 0) throw new Error('Rest must be a non-negative whole number.')
    if (next.repTarget !== null && (!Number.isInteger(next.repTarget) || next.repTarget <= 0)) throw new Error('Rep target must be a positive whole number.')
    if (next.isStarred && !previous?.isStarred) await checkStarLimit(next.id)
    return db.exercises.put(next)
  })
}
export function toggleStar(id: number) {
  return db.transaction('rw', db.exercises, async () => {
    const e = await db.exercises.get(id)
    if (!e) throw new Error('Exercise no longer exists.')
    if (!e.isStarred) await checkStarLimit(id)
    await db.exercises.update(id, {isStarred: !e.isStarred})
    return !e.isStarred
  })
}
export function deleteExercise(id: number) {
  return db.transaction('rw', db.exercises, db.sets, db.routine_exercises, db.workout_exercise_instances, async () => {
    const routineRows = db.routine_exercises.where('exerciseId').equals(id)
    const instanceRows = db.workout_exercise_instances.where('exerciseId').equals(id)
    const routineDays = new Set((await routineRows.toArray()).filter(r => r.supersetGroupId).map(r => r.routineDayId))
    const workoutIds = new Set((await instanceRows.toArray()).filter(r => r.supersetGroupId).map(r => r.workoutId))
    await routineRows.delete()
    await instanceRows.delete()
    await db.sets.where('exerciseId').equals(id).delete()
    await db.exercises.delete(id)
    for (const dayId of routineDays) {
      const groups = new Map<string, number[]>()
      for (const item of await db.routine_exercises.where('routineDayId').equals(dayId).toArray()) {
        if (!item.supersetGroupId) continue
        const ids = groups.get(item.supersetGroupId) ?? []
        ids.push(item.id)
        groups.set(item.supersetGroupId, ids)
      }
      for (const ids of groups.values()) if (ids.length === 1) await db.routine_exercises.update(ids[0], ungrouped)
    }
    for (const workoutId of workoutIds) await dissolveOrphanWorkoutGroups(workoutId)
  })
}
