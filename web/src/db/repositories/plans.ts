import { db } from '../database'
import type { WorkoutExerciseInstance } from '../types'

export type BlueprintItem = Omit<WorkoutExerciseInstance, 'id' | 'workoutId'>
export const ungrouped = { supersetGroupId: null, supersetOrderIndex: 0 }
export function blankSlot(exerciseId: number, orderIndex: number): BlueprintItem {
  return { exerciseId, orderIndex, routineExerciseId: null, plannedSets: null, repMin: null,
    repMax: null, restSeconds: null, notes: null, ...ungrouped, isSkipped: false, addedDuringWorkout: false }
}
export async function requireActive(id: number) {
  const workout = await db.workouts.get(id)
  if (!workout || workout.endTime !== null) throw new Error('This workout is no longer active.')
  return workout
}

export async function ensureWorkoutExercise(workoutId: number, exerciseId: number) {
  return db.transaction('rw', db.exercises, db.workouts, db.workout_exercise_instances, async () => {
    await requireActive(workoutId)
    if (!await db.exercises.get(exerciseId)) throw new Error('Exercise no longer exists.')
    const slots = db.workout_exercise_instances
    const existing = await slots.where('[workoutId+exerciseId]').equals([workoutId, exerciseId])
      .filter(slot => !slot.isSkipped).first()
    if (existing) return existing.id
    const last = await slots.where('workoutId').equals(workoutId).sortBy('orderIndex')
    const order = (last.at(-1)?.orderIndex ?? -1) + 1
    return slots.add({ ...blankSlot(exerciseId, order), workoutId, addedDuringWorkout: true })
  })
}

export async function dissolveOrphanWorkoutGroups(workoutId: number) {
  const members = await db.workout_exercise_instances.where('workoutId').equals(workoutId).toArray()
  const groups = new Map<string, number[]>()
  for (const slot of members) {
    if (slot.isSkipped || !slot.supersetGroupId) continue
    const ids = groups.get(slot.supersetGroupId) ?? []
    ids.push(slot.id)
    groups.set(slot.supersetGroupId, ids)
  }
  for (const ids of groups.values()) {
    if (ids.length === 1) await db.workout_exercise_instances.update(ids[0], ungrouped)
  }
}
export async function groupWorkoutExercises(workoutId: number, exerciseIds: number[]) {
  const ids = [...new Set(exerciseIds)]
  if (ids.length < 2 || ids.length > 10) throw new Error('Choose 2 to 10 exercises.')
  return db.transaction('rw', db.workouts, db.exercises, db.workout_exercise_instances, async () => {
    const group = crypto.randomUUID()
    for (const [index, id] of ids.entries()) {
      const slotId = await ensureWorkoutExercise(workoutId, id)
      await db.workout_exercise_instances.update(slotId, {supersetGroupId: group, supersetOrderIndex: index})
    }
    await dissolveOrphanWorkoutGroups(workoutId)
    return group
  })
}

export async function ungroupWorkoutExercises(workoutId: number, groupId: string) {
  return db.transaction('rw', db.workouts, db.workout_exercise_instances, async () => {
    await requireActive(workoutId)
    await db.workout_exercise_instances.where('workoutId').equals(workoutId)
      .filter(i => i.supersetGroupId === groupId).modify(ungrouped)
  })
}

async function editableSlot(instanceId: number) {
  const slot = await db.workout_exercise_instances.get(instanceId)
  if (!slot || slot.isSkipped) throw new Error('This exercise is no longer in the plan.')
  await requireActive(slot.workoutId)
  const count = await db.sets.where('[workoutId+exerciseId]').equals([slot.workoutId, slot.exerciseId]).count()
  if (count > 0) throw new Error('This exercise already has logged sets.')
  return slot
}
export function skipWorkoutExercise(instanceId: number) {
  return db.transaction('rw', db.workouts, db.sets, db.workout_exercise_instances, async () => {
    const slot = await editableSlot(instanceId)
    await db.workout_exercise_instances.update(slot.id, { isSkipped: true, ...ungrouped })
    if (slot.supersetGroupId) await dissolveOrphanWorkoutGroups(slot.workoutId)
  })
}
export function swapWorkoutExercise(instanceId: number, exerciseId: number) {
  return db.transaction('rw', db.workouts, db.exercises, db.sets, db.workout_exercise_instances, async () => {
    const slot = await editableSlot(instanceId)
    if (slot.exerciseId === exerciseId) return
    if (!await db.exercises.get(exerciseId)) throw new Error('Exercise no longer exists.')
    const existing = await db.workout_exercise_instances.where('[workoutId+exerciseId]')
      .equals([slot.workoutId, exerciseId]).filter(i => !i.isSkipped).count()
    if (existing) throw new Error('This exercise is already in the plan.')
    await db.workout_exercise_instances.update(instanceId, { exerciseId, routineExerciseId: null, addedDuringWorkout: true })
  })
}
export async function workoutBlueprint(workoutId: number): Promise<BlueprintItem[]> {
  const slots = await db.workout_exercise_instances.where('workoutId').equals(workoutId)
    .filter(s => !s.isSkipped).sortBy('orderIndex')
  const sets = await db.sets.where('workoutId').equals(workoutId).sortBy('timestamp')
  const groups = new Map<number, typeof sets>()
  for (const set of sets) {
    const group = groups.get(set.exerciseId) ?? []
    group.push(set)
    groups.set(set.exerciseId, group)
  }
  const plan: BlueprintItem[] = slots.map(({id: _id, workoutId: _wid, ...s}) => ({
    ...s, routineExerciseId: null, addedDuringWorkout: false,
  }))
  for (const [exerciseId, history] of groups) {
    const working = history.filter(s => !s.isWarmup)
    const reps = working.flatMap(s => s.reps === null ? [] : [s.reps])
    let item = plan.find(s => s.exerciseId === exerciseId)
    if (!item) {
      item = { ...blankSlot(exerciseId, plan.length), supersetGroupId: history[0].supersetGroupId,
        supersetOrderIndex: history[0].supersetOrderIndex }
      plan.push(item)
    }
    if (item.plannedSets === null) {
      item.plannedSets = Math.max(1, working.length)
      item.repMin ??= reps.length ? Math.min(...reps) : null
      item.repMax ??= reps.length ? Math.max(...reps) : null
    }
  }
  const groupCounts = new Map<string, number>()
  for (const item of plan) if (item.supersetGroupId) groupCounts.set(item.supersetGroupId, (groupCounts.get(item.supersetGroupId) ?? 0) + 1)
  return plan.map((item, orderIndex) => ({
    ...item, orderIndex, ...(item.supersetGroupId && groupCounts.get(item.supersetGroupId) === 1 ? ungrouped : {}),
  }))
}
