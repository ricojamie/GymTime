import type { DistanceUnit, LogType, WorkoutSet } from '../db/types'
import { distanceToMeters, isConvertibleToMeters, metersToDistance } from './units'
import { calculateE1RM } from './oneRepMax'

export interface RecordEvent {
  setId: number
  workoutId: number
  exerciseId: number
  timestamp: number
  labels: string[]
  benchmark: boolean
}

const metricLabels: Record<LogType, string> = {
  WEIGHT_REPS: 'Heaviest set', WEIGHT_DISTANCE: 'Heaviest set', WEIGHT_TIME: 'Heaviest set',
  REPS_ONLY: 'Most reps', CALORIES_TIME: 'Most calories', DURATION: 'Longest set', DISTANCE_TIME: 'Farthest set',
}

export function recordMetric(set: WorkoutSet, type: LogType, unit: DistanceUnit = 'MILES'): number | null {
  let value: number | null
  switch (type) {
    case 'WEIGHT_REPS': case 'WEIGHT_DISTANCE': case 'WEIGHT_TIME':
      return set.weight !== null && Number.isFinite(set.weight) && set.weight >= 0 ? set.weight : null
    case 'REPS_ONLY': value = set.reps; break
    case 'DURATION': value = set.durationSeconds; break
    case 'CALORIES_TIME': value = set.calories; break
    case 'DISTANCE_TIME': {
      if (!isConvertibleToMeters(unit)) value = set.distanceUnit === unit ? set.distanceValue : null
      else {
        if (set.distanceUnit && !isConvertibleToMeters(set.distanceUnit)) return null
        const meters = distanceToMeters(set.distanceValue, set.distanceUnit) ?? set.distanceMeters
        value = meters === null ? null : metersToDistance(meters, unit)
      }
      break
    }
  }
  return value !== null && Number.isFinite(value) && value > 0 ? value : null
}

// Replay one exercise at a time. Equal timestamps use the same ID tiebreak as Room.
export function replayRecords(sets: readonly WorkoutSet[], type: LogType, unit: DistanceUnit = 'MILES'): RecordEvent[] {
  const ordered = sets.filter(s => s.isComplete && !s.isWarmup).sort((a,b) => a.timestamp - b.timestamp || a.id - b.id)
  const events: RecordEvent[] = []
  const byReps = new Map<number, number>()
  let highest = -Infinity, secondary = 0, e1rm = -Infinity
  for (const set of ordered) {
    const labels: string[] = []
    const value = recordMetric(set, type, unit)
    const tie = type === 'WEIGHT_REPS' ? set.reps ?? 0 : type === 'WEIGHT_TIME' ? set.durationSeconds ?? 0 : 0
    if (value !== null && (value > highest || (value === highest && tie > secondary))) {
      labels.push(metricLabels[type])
      highest = value
      secondary = tie
    }
    if (type === 'WEIGHT_REPS' && value !== null && set.reps && set.reps > 0) {
      let repBest = -Infinity
      for (const [reps, weight] of byReps) if (reps >= set.reps) repBest = Math.max(repBest, weight)
      const improved = value > repBest
      if (improved) labels.push('Best at ' + set.reps + ' reps')
      byReps.set(set.reps, Math.max(value, byReps.get(set.reps) ?? -Infinity))
      const estimate = calculateE1RM(value, set.reps)
      if (estimate !== null && estimate > e1rm) {
        if (improved) labels.push('Estimated 1RM')
        e1rm = estimate
      }
    }
    if (labels.length) events.push({setId: set.id, exerciseId: set.exerciseId, workoutId: set.workoutId,
      timestamp: set.timestamp, benchmark: events.length === 0, labels})
  }
  return events
}

// For a given rep count keep one heaviest set, then remove weights already achieved at more reps.
export function paretoBest(sets: readonly WorkoutSet[]): WorkoutSet[] {
  const candidates = sets.filter(s => s.isComplete && !s.isWarmup && s.weight !== null &&
    Number.isFinite(s.weight) && s.weight >= 0 && s.reps !== null && Number.isInteger(s.reps) && s.reps > 0)
    .sort((a,b) => b.reps! - a.reps! || b.weight! - a.weight! || a.timestamp - b.timestamp || a.id - b.id)
  let best = -Infinity
  return candidates.filter(set => {
    if (set.weight! <= best) return false
    best = set.weight!
    return true
  }).reverse()
}
