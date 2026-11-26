import type { Exercise, WeightUnit, Workout, WorkoutSet } from '../db/types'
import { formatWeight, weightLabel, distanceLabels } from '../domain/units'

export { groupBy } from '../domain/analyticsCore'

export const includesWorkingSet = (s: WorkoutSet) => s.isComplete && !s.isWarmup
export const volumeOf = (s: WorkoutSet) => includesWorkingSet(s) && (s.weight ?? 0) > 0 && (s.reps ?? 0) > 0
  ? s.weight! * s.reps! : 0

export function durationLabel(workout: Workout) {
  if (workout.endTime == null) return 'Ongoing'
  const minutes = Math.floor(Math.max(0, workout.endTime - workout.startTime) / 60_000)
  if (minutes === 0) return '<1 min'
  if (minutes < 60) return minutes + ' min'
  return Math.floor(minutes / 60) + 'h ' + minutes % 60 + 'm'
}

export function describeWorkout(workout: Workout, sets: WorkoutSet[], exercises: Map<number, Exercise>) {
  const working = sets.filter(includesWorkingSet)
  const exerciseIds = new Set(working.map(s => s.exerciseId))
  const muscles = Array.from(new Set(Array.from(exerciseIds, id => exercises.get(id)?.targetMuscle).filter((m): m is string => Boolean(m)))).sort()
  return {
    sets: working.length, exercises: exerciseIds.size, muscles,
    volume: working.reduce((total, s) => total + volumeOf(s), 0),
    duration: durationLabel(workout),
    title: workout.name?.trim() || workout.routineDayNameSnapshot?.trim() || muscles.slice(0, 2).join(' + ') || 'Workout',
  }
}

export function setText(set: WorkoutSet, unit: WeightUnit) {
  const values: string[] = []
  if (set.weight != null) values.push(formatWeight(set.weight, unit) + ' ' + weightLabel(unit))
  if (set.reps != null) values.push(set.reps + ' ' + (set.reps === 1 ? 'rep' : 'reps'))
  if (set.distanceValue != null && set.distanceUnit) values.push(set.distanceValue + ' ' + distanceLabels[set.distanceUnit])
  else if (set.distanceMeters != null) values.push(set.distanceMeters + ' m')
  if (set.calories != null) values.push(set.calories + ' cal')
  if (set.durationSeconds != null) {
    const seconds = Math.floor(set.durationSeconds)
    values.push(Math.floor(seconds / 60) + ':' + String(seconds % 60).padStart(2, '0'))
  }
  return values.join(' · ') || '—'
}

export const monthLabel = (epoch: number) => new Date(epoch).toLocaleDateString(undefined, { month: 'long', year: 'numeric' })
export const dateLabel = (epoch: number) => new Date(epoch).toLocaleDateString(undefined, {weekday:'long', month:'short', day:'numeric', year:'numeric'})
