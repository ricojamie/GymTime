import { canonicalMuscle, compareSessions, completedData, DAY, estimateOf, groupBy, median, muscleNames, positive, round1, shiftDays, volumeOf, weekStart } from './analyticsCore'
import type { ComparisonStatus, TrainingSource } from './analyticsCore'

export type MomentumDirection = 'STRONG_UP' | 'UP' | 'FLAT' | 'DOWN' | 'STRONG_DOWN' | 'NO_BASELINE'
export interface ExerciseMomentum {
  exerciseId: number; exerciseName: string; muscle: string
  percentChange: number | null; recentValue: number | null; baselineValue: number | null
  recentSessionCount: number; baselineSessionCount: number; latestSessionTimestamp: number
  status: ComparisonStatus; confidence: 'STANDARD' | 'LOW'
}
export interface MuscleMomentum {
  muscle: string; percentChange: number | null; direction: MomentumDirection
  status: ComparisonStatus; confidence: 'STANDARD' | 'LOW'
  contributingExercises: ExerciseMomentum[]; improvingContributors: ExerciseMomentum[]
  decliningContributors: ExerciseMomentum[]; hasMixedContributors: boolean
  currentWeekVolume: number; previousWeekVolume: number; latestSessionTimestamp: number | null
}

function direction(percent: number | null): MomentumDirection {
  if (percent === null) return 'NO_BASELINE'
  if (percent >= 5) return 'STRONG_UP'
  if (percent >= 2) return 'UP'
  if (percent <= -5) return 'STRONG_DOWN'
  return percent <= -2 ? 'DOWN' : 'FLAT'
}

export function calculateStrengthMomentum(source: TrainingSource, now = Date.now()) {
  const { sets, exercises } = completedData(source, now)
  const strengthRows = sets.filter(s => s.timestamp >= now - 365 * DAY)
  const contributions: ExerciseMomentum[] = []
  const weeklyTotals = new Map<string, [number, number]>()
  const week = weekStart(now, 0)
  const previousWeek = shiftDays(week, -7)
  for (const [exerciseId, rows] of groupBy(strengthRows, s => s.exerciseId)) {
    const exercise = exercises.get(exerciseId)!
    const muscle = canonicalMuscle(exercise.targetMuscle)
    if (muscle.toLowerCase() === 'cardio') continue
    const key = muscle.toLowerCase()
    const totals = weeklyTotals.get(key) ?? [0, 0]
    for (const s of rows) {
      if (s.timestamp >= previousWeek) totals[s.timestamp >= week ? 0 : 1] += volumeOf(s)
    }
    weeklyTotals.set(key, totals)
    if (exercise.logType !== 'WEIGHT_REPS' && exercise.logType !== 'REPS_ONLY') continue
    const scores = [...groupBy(rows, s => s.workoutId).values()].flatMap(group => {
      const values = group.map(s => exercise.logType === 'REPS_ONLY' ? s.reps : estimateOf(s)).filter(positive)
      return values.length ? [{ timestampMs: group[group.length - 1].timestamp, value: values.reduce((a, b) => Math.max(a, b)) }] : []
    }).sort((a, b) => b.timestampMs - a.timestampMs)
    if (!scores.length) continue
    const c = compareSessions(scores, now)
    contributions.push({ exerciseId, exerciseName: exercise.name, muscle, percentChange: c.percentChange,
      status: c.status, recentValue: c.recentMedian, baselineValue: c.baselineMedian, recentSessionCount: c.recentSessionCount,
      baselineSessionCount: c.baselineSessionCount, latestSessionTimestamp: scores[0].timestampMs,
      confidence: c.recentSessionCount === 3 ? 'STANDARD' : 'LOW' })
  }
  const byMuscle = groupBy(contributions, c => c.muscle.toLowerCase())
  const muscles: MuscleMomentum[] = muscleNames(source).filter(m => m.toLowerCase() !== 'cardio').map(muscle => {
    const key = muscle.toLowerCase()
    const all = byMuscle.get(key) ?? []
    const ready = all.filter(c => c.percentChange !== null).sort((a, b) => Math.abs(b.percentChange!) - Math.abs(a.percentChange!))
    const percentChange = ready.length ? round1(median(ready.map(c => Math.max(-20, Math.min(20, c.percentChange!))))) : null
    const improving = ready.filter(c => c.percentChange! >= 2).sort((a, b) => b.percentChange! - a.percentChange!)
    const declining = ready.filter(c => c.percentChange! <= -2).sort((a, b) => a.percentChange! - b.percentChange!)
    const [currentWeekVolume, previousWeekVolume] = weeklyTotals.get(key) ?? [0, 0]
    const contributingExercises = ready.length ? ready : all
    return { muscle, percentChange, direction: direction(percentChange), contributingExercises,
      improvingContributors: improving, decliningContributors: declining, hasMixedContributors: Boolean(improving.length && declining.length),
      currentWeekVolume, previousWeekVolume, latestSessionTimestamp: contributingExercises.length ?
        contributingExercises.reduce((n, e) => Math.max(n, e.latestSessionTimestamp), 0) : null,
      status: ready.length ? 'READY' : all.length && all.every(c => c.status === 'STALE') ? 'STALE' : 'BUILDING_BASELINE',
      confidence: ready.length && ready.every(c => c.confidence === 'STANDARD') ? 'STANDARD' : 'LOW' }
  })
  return { muscles,
    topImproving: muscles.filter(m => (m.percentChange ?? 0) >= 2).sort((a, b) => b.percentChange! - a.percentChange!).slice(0, 3),
    topDeclining: muscles.filter(m => (m.percentChange ?? 0) <= -2).sort((a, b) => a.percentChange! - b.percentChange!).slice(0, 3) }
}
