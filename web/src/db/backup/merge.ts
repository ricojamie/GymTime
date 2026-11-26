import { emptySnapshot } from './format'
import type { Snapshot, ValidatedBackup } from './format'
import type {
  Routine,
  RoutineDay,
  RoutineExercise,
  Workout,
  WorkoutSet,
  WorkoutExerciseInstance,
} from '../types'

type RowWithId = { id: number }
const fold = (s: string | null) => (s ?? '').trim().toLowerCase()
const byOrder = (a: { orderIndex: number; id: number }, b: { orderIndex: number; id: number }) =>
  a.orderIndex - b.orderIndex || a.id - b.id
function group<T, K>(rows: T[], key: (row: T) => K): Map<K, T[]> {
  const map = new Map<K, T[]>()
  for (const row of rows) {
    const k = key(row),
      list = map.get(k)
    if (list) list.push(row)
    else map.set(k, [row])
  }
  return map
}
function allocate(rows: RowWithId[]) {
  let max = rows.reduce((v, r) => Math.max(v, r.id), 0)
  return () => {
    if (!Number.isSafeInteger(++max)) throw new Error('Record ID limit reached')
    return max
  }
}
const routineGraph = (data: Snapshot) => {
  const days = group(data.routine_days, (d) => d.routineId),
    items = group(data.routine_exercises, (e) => e.routineDayId)
  return (r: Routine) =>
    (days.get(r.id) ?? [])
      .sort(byOrder)
      .map((day) => ({ day, exercises: (items.get(day.id) ?? []).sort(byOrder) }))
}
type DayGraph = { day: RoutineDay; exercises: RoutineExercise[] }[]
function signature(routine: Routine, graph: DayGraph, exerciseId: (id: number) => number) {
  return JSON.stringify([
    fold(routine.name),
    graph.map(({ day, exercises }) => [
      day.orderIndex,
      fold(day.name),
      exercises.map((e) => [
        e.orderIndex,
        exerciseId(e.exerciseId),
        e.targetSets,
        e.targetRepsMin,
        e.targetRepsMax,
        e.targetRestSeconds,
        e.notes,
      ]),
    ]),
  ])
}
const workoutKey = (w: Workout) => JSON.stringify([w.startTime, w.endTime, fold(w.name)])
const f32 = (n: number | null) => (n === null ? null : Math.fround(n))
const setKey = (s: WorkoutSet) =>
  JSON.stringify([
    s.workoutId,
    s.exerciseId,
    s.timestamp,
    f32(s.weight),
    f32(s.calories),
    s.reps,
    f32(s.rpe),
    s.durationSeconds,
    f32(s.distanceValue),
    s.distanceUnit,
    s.isWarmup,
    s.isComplete,
    s.note,
  ])
const instanceKey = (r: WorkoutExerciseInstance) =>
  JSON.stringify([
    r.workoutId,
    r.exerciseId,
    r.orderIndex,
    r.plannedSets,
    r.repMin,
    r.repMax,
    r.restSeconds,
    r.notes,
    r.isSkipped,
    r.addedDuringWorkout,
  ])
function mapGroup(
  maps: Map<number, Map<string, string>>,
  parent: number,
  original: string | null,
  existing?: string | null,
) {
  if (!original) return null
  let ids = maps.get(parent)
  if (!ids) {
    ids = new Map()
    maps.set(parent, ids)
  }
  if (!ids.has(original)) ids.set(original, existing ?? crypto.randomUUID())
  return ids.get(original)!
}

export function buildRestore(backup: ValidatedBackup, local: Snapshot, replace: boolean) {
  const source = backup.data,
    changes = emptySnapshot()
  let inserted = 0,
    updated = 0,
    skipped = 0
  const warnings = [...backup.warnings]
  const names = new Map(local.muscle_groups.map((g) => [fold(g.name), g.name]))
  function ensureMuscle(name: string) {
    const known = names.get(fold(name))
    if (known) return known
    names.set(fold(name), name)
    changes.muscle_groups.push({ name })
    inserted++
    return name
  }
  for (const muscle of source.muscle_groups) {
    if (names.has(fold(muscle.name))) skipped++
    else ensureMuscle(muscle.name)
  }
  ensureMuscle('Warmups')
  const exerciseIds = new Map<number, number>(),
    warmupIds = new Set<number>()
  const nextExercise = allocate(local.exercises),
    used = new Set<number>(),
    usedLocally = new Set(local.sets.map((s) => s.exerciseId))
  const byName = group(local.exercises, (e) => fold(e.name))
  for (const item of source.exercises) {
    const targetMuscle = ensureMuscle(item.targetMuscle)
    const matches = (byName.get(fold(item.name)) ?? []).filter((e) => !used.has(e.id))
    const m =
      matches.find(
        (e) => fold(e.targetMuscle) === fold(targetMuscle) && e.logType === item.logType,
      ) ?? matches[0]
    const id = m?.id ?? (replace ? item.id : nextExercise())
    exerciseIds.set(item.id, id)
    const row =
      m && usedLocally.has(m.id)
        ? {
            ...m,
            notes: m.notes ?? item.notes,
            isStarred: m.isStarred || item.isStarred,
            repTarget: m.repTarget ?? item.repTarget,
          }
        : { ...item, id, name: m?.name ?? item.name, targetMuscle }
    if (row.targetMuscle === 'Warmups') warmupIds.add(id)
    if (m) {
      used.add(id)
      if (Object.keys(row).every((k) => row[k as keyof typeof row] === m[k as keyof typeof row])) {
        skipped++
        continue
      }
      updated++
    } else inserted++
    changes.exercises.push(row)
  }

  const sourceGraph = routineGraph(source),
    localGraph = routineGraph(local)
  const sameRoutines = group([...local.routines].reverse(), (r) =>
    signature(r, localGraph(r), (id) => id),
  )
  const routineIds = new Map<number, number>(),
    dayIds = new Map<number, number>(),
    prescriptionIds = new Map<number, number>()
  const dayGroups = new Map<number, Map<string, string>>()
  const nextRoutine = allocate(local.routines),
    nextDay = allocate(local.routine_days),
    nextPrescription = allocate(local.routine_exercises)
  let active = local.routines.some((r) => r.isActive)
  for (const r of source.routines) {
    const graph = sourceGraph(r)
    const matched = sameRoutines.get(signature(r, graph, (id) => exerciseIds.get(id)!))?.pop()
    const rid = matched?.id ?? (replace ? r.id : nextRoutine())
    routineIds.set(r.id, rid)
    const matchedGraph = matched ? localGraph(matched) : []
    if (matched) skipped++
    else {
      const isActive = r.isActive && !active
      active ||= isActive
      changes.routines.push({ ...r, id: rid, isActive })
      inserted++
    }
    for (const [i, { day, exercises }] of graph.entries()) {
      const match = matchedGraph[i]
      const did = match?.day.id ?? (replace ? day.id : nextDay())
      dayIds.set(day.id, did)
      if (match) skipped++
      else {
        changes.routine_days.push({ ...day, id: did, routineId: rid })
        inserted++
      }
      for (const [j, p] of exercises.entries()) {
        const matchItem = match?.exercises[j]
        const id = matchItem?.id ?? (replace ? p.id : nextPrescription())
        prescriptionIds.set(p.id, id)
        if (matchItem) {
          skipped++
          continue
        }
        changes.routine_exercises.push({
          ...p,
          id,
          routineDayId: did,
          exerciseId: exerciseIds.get(p.exerciseId)!,
          supersetGroupId: replace ? p.supersetGroupId : mapGroup(dayGroups, did, p.supersetGroupId),
        })
        inserted++
      }
    }
  }

  const workoutIds = new Map<number, number>(),
    nextWorkout = allocate(local.workouts)
  const existing = group([...local.workouts].reverse(), workoutKey)
  let keepOpen: number | null = null
  if (!local.workouts.some((w) => w.endTime === null)) {
    const latest = source.workouts
      .filter((w) => w.endTime === null)
      .sort((a, b) => b.startTime - a.startTime)[0]
    keepOpen = latest?.id ?? null
  }
  const lastSets = new Map<number, number>()
  for (const s of source.sets)
    lastSets.set(s.workoutId, Math.max(s.timestamp, lastSets.get(s.workoutId) ?? s.timestamp))
  for (const w of source.workouts) {
    let match = existing.get(workoutKey(w))?.pop()
    let end = w.endTime
    if (!match && end === null && w.id !== keepOpen) {
      end = Math.max(w.startTime, lastSets.get(w.id) ?? w.startTime)
      match = existing.get(workoutKey({ ...w, endTime: end }))?.pop()
      if (!match) warnings.push('Closed extra active workout ' + (w.name ?? '#' + w.id))
    }
    const id = match?.id ?? (replace ? w.id : nextWorkout())
    workoutIds.set(w.id, id)
    if (match) {
      skipped++
      continue
    }
    changes.workouts.push({
      ...w,
      id,
      endTime: end,
      routineId: routineIds.get(w.routineId ?? 0) ?? null,
      routineDayId: dayIds.get(w.routineDayId ?? 0) ?? null,
    })
    inserted++
  }
  const groups = new Map<number, Map<string, string>>()
  const nextSet = allocate(local.sets),
    nextInstance = allocate(local.workout_exercise_instances)
  const existingSets = group(local.sets, setKey),
    existingInstances = group(local.workout_exercise_instances, instanceKey)
  for (const s of source.sets) {
    const exerciseId = exerciseIds.get(s.exerciseId)!
    const row = {
      ...s,
      exerciseId,
      workoutId: workoutIds.get(s.workoutId)!,
      isWarmup: s.isWarmup || warmupIds.has(exerciseId),
    }
    const match = existingSets.get(setKey(row))?.pop()
    if (match) {
      if (match.supersetGroupId)
        mapGroup(groups, row.workoutId, row.supersetGroupId, match.supersetGroupId)
      skipped++
    } else {
      changes.sets.push({ ...row, id: replace ? s.id : nextSet() })
      inserted++
    }
  }
  for (const p of source.workout_exercise_instances) {
    const row = {
      ...p,
      workoutId: workoutIds.get(p.workoutId)!,
      exerciseId: exerciseIds.get(p.exerciseId)!,
      routineExerciseId: prescriptionIds.get(p.routineExerciseId ?? 0) ?? null,
    }
    const match = existingInstances.get(instanceKey(row))?.pop()
    if (match) {
      if (match.supersetGroupId)
        mapGroup(groups, row.workoutId, row.supersetGroupId, match.supersetGroupId)
      skipped++
    } else {
      changes.workout_exercise_instances.push({ ...row, id: replace ? p.id : nextInstance() })
      inserted++
    }
  }
  if (!replace) {
    for (const row of changes.sets)
      row.supersetGroupId = mapGroup(groups, row.workoutId, row.supersetGroupId)
    for (const row of changes.workout_exercise_instances)
      row.supersetGroupId = mapGroup(groups, row.workoutId, row.supersetGroupId)
  }
  return { changes, report: { inserted, updated, skipped, warnings, counts: backup.counts } }
}
