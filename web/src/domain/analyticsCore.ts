import type { Exercise, Workout, WorkoutSet } from '../db/types'
import { normalizeMuscleName } from '../db/types'
import { calculateE1RM } from './oneRepMax'

export interface TrainingSource {
  exercises: readonly Exercise[]
  workouts: readonly Workout[]
  sets: readonly WorkoutSet[]
  muscleNames?: readonly string[]
}

export const DAY = 86_400_000
export const primaryMuscles = ['Chest', 'Back', 'Shoulders', 'Biceps', 'Triceps', 'Abs', 'Legs']

export function canonicalMuscle(name: string) {
  const value = normalizeMuscleName(name).trim()
  return /^(?:core|abs)$/i.test(value) ? 'Abs' : value || 'Uncategorized'
}

export function muscleNames(source: TrainingSource) {
  const names = new Map<string, string>()
  for (const raw of [...primaryMuscles, ...(source.muscleNames ?? []), ...source.exercises.map(e => e.targetMuscle)]) {
    const name = canonicalMuscle(raw)
    if (name !== 'Warmups' && !names.has(name.toLowerCase())) names.set(name.toLowerCase(), name)
  }
  const extra = [...names.values()].filter(name => !primaryMuscles.includes(name))
  return [...primaryMuscles, ...extra.sort((a, b) => a.localeCompare(b))]
}

export function groupBy<T, K>(items: readonly T[], key: (item: T) => K) {
  const map = new Map<K, T[]>()
  for (const item of items) {
    const id = key(item)
    const entries = map.get(id)
    if (entries) entries.push(item)
    else map.set(id, [item])
  }
  return map
}

export function completedData(source: TrainingSource, now: number) {
  const workouts = new Map(source.workouts.filter(w =>
    w.endTime !== null && w.endTime >= w.startTime && w.endTime <= now && w.startTime < now,
  ).map(w => [w.id, w]))
  const exercises = new Map(source.exercises.filter(e => canonicalMuscle(e.targetMuscle) !== 'Warmups').map(e => [e.id, e]))
  const sets = source.sets.filter(s => workouts.has(s.workoutId) && exercises.has(s.exerciseId) &&
    s.isComplete && !s.isWarmup && s.timestamp < now).sort((a, b) => a.timestamp - b.timestamp || a.id - b.id)
  return { workouts, exercises, sets }
}

export const positive = (v: number | null): v is number => v !== null && Number.isFinite(v) && v > 0
export const volumeOf = (s: WorkoutSet) => positive(s.weight) && positive(s.reps) ? s.weight * s.reps : 0
export const estimateOf = (s: WorkoutSet) => calculateE1RM(s.weight ?? 0, s.reps ?? 0)
export const round1 = (value: number) => Math.round(value * 10) / 10
export function median(values: readonly number[]) {
  const sorted = [...values].sort((a, b) => a - b)
  const mid = Math.floor(sorted.length / 2)
  return sorted.length % 2 ? sorted[mid] : (sorted[mid - 1] + sorted[mid]) / 2
}

export function localDay(timestamp: number) {
  const d = new Date(timestamp)
  return Date.UTC(d.getFullYear(), d.getMonth(), d.getDate()) / DAY
}

export function weekStart(timestamp: number, day = 1) {
  const d = new Date(timestamp)
  d.setHours(0, 0, 0, 0)
  d.setDate(d.getDate() - ((d.getDay() - day + 7) % 7))
  return +d
}

export function shiftDays(timestamp: number, days: number) {
  const d = new Date(timestamp)
  d.setDate(d.getDate() + days)
  return +d
}

export interface ScoredSession { timestampMs: number; value: number }
export type ComparisonStatus = 'READY' | 'STALE' | 'BUILDING_BASELINE' | 'NO_COMPARABLE_DATA'
export interface Comparison {
  status: ComparisonStatus
  recentSessionCount: number
  baselineSessionCount: number
  recentMedian: number | null
  baselineMedian: number | null
  percentChange: number | null
}

export function compareSessions(points: readonly ScoredSession[], now: number): Comparison {
  const withinYear = points.filter(p => p.timestampMs >= now - 365 * DAY && p.timestampMs <= now)
    .sort((a, b) => b.timestampMs - a.timestampMs)
  const count = Math.min(3, Math.floor(withinYear.length / 2))
  const latest = withinYear[0]
  const status = !latest ? 'NO_COMPARABLE_DATA' : now - latest.timestampMs > 42 * DAY ? 'STALE' :
    count < 2 ? 'BUILDING_BASELINE' : 'READY'
  if (status !== 'READY') {
    return { status, recentSessionCount: count, baselineSessionCount: count, recentMedian: null, baselineMedian: null, percentChange: null }
  }
  const recentMedian = median(withinYear.slice(0, count).map(p => p.value))
  const baselineMedian = median(withinYear.slice(count, count * 2).map(p => p.value))
  return { status, recentSessionCount: count, baselineSessionCount: count, recentMedian, baselineMedian,
    percentChange: baselineMedian > 0 ? round1(100 * (recentMedian - baselineMedian) / baselineMedian) : null }
}
