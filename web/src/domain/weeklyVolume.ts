import type { WorkoutSet } from '../db/types'
type VolumeSet = Pick<WorkoutSet, 'weight' | 'reps' | 'timestamp' | 'isComplete' | 'isWarmup'>

export function calculateWeeklyVolume(sets: readonly VolumeSet[], now = Date.now()) {
  const sunday = new Date(now)
  sunday.setHours(0, 0, 0, 0)
  sunday.setDate(sunday.getDate() - sunday.getDay())
  const previousSunday = new Date(sunday)
  previousSunday.setDate(sunday.getDate() - 7)
  let currentWeek = 0
  let lastWeek = 0
  for (const set of sets) {
    if (!set.isComplete || set.isWarmup || !(set.weight && set.weight > 0 && set.reps && set.reps > 0)) continue
    const volume = set.weight * set.reps
    if (!Number.isFinite(volume) || set.timestamp < previousSunday.getTime() || set.timestamp > now) continue
    if (set.timestamp >= sunday.getTime()) currentWeek += volume
    else lastWeek += volume
  }
  return {currentWeek, lastWeek, progressPercent: lastWeek > 0 ? currentWeek / lastWeek : 0,
    hasOverflowed: lastWeek > 0 && currentWeek > lastWeek}
}
