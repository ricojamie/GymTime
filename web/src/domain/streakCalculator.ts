import type { WorkoutSet } from '../db/types'

type StreakSet = Pick<WorkoutSet, 'isComplete' | 'isWarmup' | 'timestamp'>
type State = 'ACTIVE' | 'RESTING' | 'BROKEN'

// Local calendar labels mapped to integer days preserve Sunday boundaries through daylight saving changes.
function civilDay(timestamp: number) {
  const date = new Date(timestamp)
  return Date.UTC(date.getFullYear(), date.getMonth(), date.getDate()) / 86_400_000
}

function weekday(day: number) {
  return ((day + 4) % 7 + 7) % 7
}

function evaluate(sets: readonly StreakSet[], allowedSkips: number, now: number) {
  const today = civilDay(now)
  const days = new Set<number>()
  for (const set of sets) {
    if (!set.isComplete || set.isWarmup) continue
    const day = civilDay(set.timestamp)
    if (day <= today) days.add(day)
  }
  const allowance = Number.isFinite(allowedSkips) ? Math.min(7, Math.max(0, Math.trunc(allowedSkips))) : 2
  const reset = new Date(now)
  reset.setDate(reset.getDate() + 7 - reset.getDay())
  reset.setHours(0, 0, 0, 0)
  const sorted = [...days].sort((a, b) => a - b)
  const earliest = sorted[0]
  let streakDays = 0, best = 0, misses = 0, count = 0, prior = 0
  let previousWeek: number | undefined
  // Traverse only weeks containing activity and the current week. An empty week breaks a streak
  // whenever fewer than seven rest days are allowed. This also bounds work for old imported dates.
  const weeks = new Set(sorted.map(day => day - weekday(day)))
  weeks.add(today - weekday(today))
  for (const sunday of weeks) {
    if (previousWeek !== undefined && sunday > previousWeek + 7 && allowance < 7) prior = 0
    count = 0
    misses = 0
    for (let day = Math.max(sunday, earliest ?? today + 1); day <= Math.min(sunday + 6, today); day++) {
      if (days.has(day)) count++
      else misses++
      if (misses <= allowance) best = Math.max(best, prior + count)
    }
    streakDays = misses > allowance ? 0 : prior + count
    prior = streakDays
    previousWeek = sunday
  }
  const state: State = misses > allowance ? 'BROKEN' : days.has(today) ? 'ACTIVE' : 'RESTING'
  return {best, result: {state, streakDays, skipsRemaining: Math.max(0, allowance - misses),
    allowedSkipsPerWeek: allowance, usedSkipsThisWeek: misses, nextResetDate: reset,
    brokeToday: misses === allowance + 1 && !days.has(today)}}
}

export function calculateStreak(sets: readonly StreakSet[], allowedSkips = 2, now = Date.now()) {
  return evaluate(sets, allowedSkips, now).result
}

export function calculateBestStreak(sets: readonly StreakSet[], allowedSkips = 2, now = Date.now()) {
  return evaluate(sets, allowedSkips, now).best
}
