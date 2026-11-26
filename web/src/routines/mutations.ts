import { db } from '../db/database'
import type { RoutineExercise } from '../db/types'

export type DayExerciseDraft = Omit<RoutineExercise, 'id' | 'routineDayId' | 'orderIndex'>
const planTables = [db.routines, db.routine_days, db.routine_exercises, db.exercises]
const orderedDays = (id: number) => db.routine_days.where('routineId').equals(id).sortBy('orderIndex')
const dayExercises = (id: number) => db.routine_exercises.where('routineDayId').equals(id).sortBy('orderIndex')

function checkedName(name: string) {
  if (!name.trim()) throw new Error('Enter a name.')
  return name.trim()
}

export function newPlanRow(exerciseId: number): DayExerciseDraft {
  return { exerciseId, targetSets: 3, targetRepsMin: null, targetRepsMax: null,
    targetRestSeconds: null, notes: null, supersetGroupId: null, supersetOrderIndex: 0 }
}

export function createRoutine(name: string) {
  return db.transaction('rw', db.routines, async () => {
    if (await db.routines.count() >= 10) throw new Error('You can have up to 10 routines.')
    return db.routines.add({ name: checkedName(name), isActive: false, nextDayOrderIndex: 0 })
  })
}

export async function renameRoutine(id: number, name: string) {
  if (!await db.routines.update(id, { name: checkedName(name) })) throw new Error('Routine no longer exists.')
}

export async function saveDay(routineId: number, dayId: number | null, inputName: string, rows: DayExerciseDraft[]) {
  const name = checkedName(inputName)
  if (!rows.length) throw new Error('Add at least one exercise.')
  return db.transaction('rw', planTables, async () => {
    if (!await db.routines.get(routineId)) throw new Error('Routine no longer exists.')
    const days = await orderedDays(routineId)
    if (dayId !== null && !days.some(day => day.id === dayId)) throw new Error('Day no longer exists.')
    if (dayId === null && days.length >= 10) throw new Error('A routine can have up to 10 days.')
    if ((await db.exercises.bulkGet(rows.map(row => row.exerciseId))).some(exercise => !exercise))
      throw new Error('An exercise was deleted. Remove it from this day.')
    for (const row of rows) {
      if (!Number.isInteger(row.targetSets) || row.targetSets < 1) throw new Error('Enter at least one set per exercise.')
      if ([row.targetRepsMin, row.targetRepsMax].some(value => value !== null && (!Number.isInteger(value) || value < 1))) throw new Error('Rep targets must be positive whole numbers.')
      if (row.targetRepsMin !== null && row.targetRepsMax !== null && row.targetRepsMin > row.targetRepsMax) throw new Error('Minimum reps must be no higher than maximum reps.')
      if (row.targetRestSeconds !== null && (!Number.isInteger(row.targetRestSeconds) || row.targetRestSeconds < 0)) throw new Error('Rest must be a nonnegative whole number.')
    }
    const targetId = dayId ?? await db.routine_days.add({
      routineId, name, orderIndex: (days.at(-1)?.orderIndex ?? -1) + 1,
    })
    if (dayId !== null) {
      await db.routine_days.update(dayId, { name })
      await db.routine_exercises.where('routineDayId').equals(dayId).delete()
    }
    await db.routine_exercises.bulkAdd(rows.map((row, orderIndex) => ({
      exerciseId: row.exerciseId, targetSets: row.targetSets, targetRepsMin: row.targetRepsMin,
      targetRepsMax: row.targetRepsMax, targetRestSeconds: row.targetRestSeconds, notes: row.notes,
      supersetGroupId: row.supersetGroupId, supersetOrderIndex: row.supersetOrderIndex,
      routineDayId: targetId, orderIndex,
    })))
    return targetId
  })
}

function copyGroups(rows: RoutineExercise[]) {
  const groups = new Map<string, string>()
  return rows.map(row => {
    const old = row.supersetGroupId
    if (old && !groups.has(old)) groups.set(old, crypto.randomUUID())
    return { ...row, supersetGroupId: old ? groups.get(old)! : null }
  })
}

async function copyDay(sourceId: number, targetRoutineId: number, name: string, orderIndex: number) {
  const newId = await db.routine_days.add({ routineId: targetRoutineId, name, orderIndex })
  const exercises = copyGroups(await dayExercises(sourceId))
  await db.routine_exercises.bulkAdd(exercises.map(({ id: _oldId, ...row }) => ({ ...row, routineDayId: newId })))
  return newId
}

export function duplicateDay(routineId: number, dayId: number) {
  return db.transaction('rw', planTables, async () => {
    const days = await orderedDays(routineId)
    const source = days.find(day => day.id === dayId)
    if (!source) throw new Error('Day no longer exists.')
    if (days.length >= 10) throw new Error('A routine can have up to 10 days.')
    return copyDay(dayId, routineId, source.name + ' (copy)', (days.at(-1)?.orderIndex ?? -1) + 1)
  })
}

export function duplicateRoutine(id: number) {
  return db.transaction('rw', planTables, async () => {
    const source = await db.routines.get(id)
    if (!source) throw new Error('Routine no longer exists.')
    const targetId = await createRoutine(source.name + ' (copy)')
    const days = await orderedDays(id)
    for (const [index, day] of days.entries()) await copyDay(day.id, targetId, day.name, index)
    return targetId
  })
}

export function setNextDay(routineId: number, dayId: number) {
  return db.transaction('rw', db.routines, db.routine_days, async () => {
    const day = await db.routine_days.get(dayId)
    if (day?.routineId !== routineId) throw new Error('Day no longer exists.')
    await db.routines.update(routineId, { nextDayOrderIndex: day.orderIndex })
  })
}

export function moveDay(routineId: number, dayId: number, direction: -1 | 1) {
  return db.transaction('rw', db.routines, db.routine_days, async () => {
    const days = await orderedDays(routineId)
    const routine = await db.routines.get(routineId)
    const index = days.findIndex(day => day.id === dayId)
    const neighbor = days[index + direction]
    if (!routine || index < 0 || !neighbor) return
    const day = days[index]
    const next = days.find(item => item.orderIndex === routine.nextDayOrderIndex) ?? days[0]
    if (day.orderIndex === neighbor.orderIndex) {
      const reordered = [...days]
      ;[reordered[index], reordered[index + direction]] = [reordered[index + direction], reordered[index]]
      await Promise.all(reordered.map((item, orderIndex) => db.routine_days.update(item.id, { orderIndex })))
      const nextIndex = reordered.findIndex(item => item.id === next.id)
      if (nextIndex >= 0) await db.routines.update(routineId, { nextDayOrderIndex: nextIndex })
      return
    }
    await db.routine_days.update(day.id, { orderIndex: neighbor.orderIndex })
    await db.routine_days.update(neighbor.id, { orderIndex: day.orderIndex })
    if (next.id === day.id || next.id === neighbor.id) await db.routines.update(routineId, {
      nextDayOrderIndex: next.id === day.id ? neighbor.orderIndex : day.orderIndex,
    })
  })
}

export function removeDay(routineId: number, dayId: number) {
  return db.transaction('rw', planTables, async () => {
    const [routine, days] = await Promise.all([db.routines.get(routineId), orderedDays(routineId)])
    if (!routine || !days.some(day => day.id === dayId)) throw new Error('Day no longer exists.')
    await db.routine_exercises.where('routineDayId').equals(dayId).delete()
    await db.routine_days.delete(dayId)
    const remaining = days.filter(day => day.id !== dayId)
    const next = remaining.find(day => day.orderIndex >= routine.nextDayOrderIndex) ?? remaining[0]
    await db.routines.update(routineId, { nextDayOrderIndex: next?.orderIndex ?? 0 })
  })
}

export function planBlocks<T extends Pick<DayExerciseDraft, 'supersetGroupId'>>(rows: T[]): T[][] {
  const blocks: T[][] = []
  for (const row of rows) {
    const prior = blocks.at(-1)
    if (row.supersetGroupId && prior?.[0].supersetGroupId === row.supersetGroupId) prior.push(row)
    else blocks.push([row])
  }
  return blocks
}

export function planLabel(row: DayExerciseDraft) {
  const { targetRepsMin: min, targetRepsMax: max, targetSets } = row
  if (min !== null && max !== null) return targetSets + ' × ' + (min === max ? min : min + '–' + max)
  if (min !== null) return targetSets + ' × ' + min + '+'
  if (max !== null) return targetSets + ' × ≤' + max
  return targetSets + (targetSets === 1 ? ' set' : ' sets')
}
