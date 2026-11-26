import type { TrainingSource } from './analyticsCore'
import { completedData, DAY, estimateOf, groupBy, positive, volumeOf } from './analyticsCore'

export function calculateRoutineStats(source: TrainingSource, routineId: number, now = Date.now()) {
  const {workouts, exercises, sets} = completedData(source, now)
  const routineSets = sets.filter(s => workouts.get(s.workoutId)?.routineId === routineId)
  const sessions = [...new Set(routineSets.map(s => s.workoutId))].map(id => workouts.get(id)!)
  const durations = sessions.map(w => Math.floor((w.endTime! - w.startTime) / 60_000)).filter(m => m >= 1 && m <= 360)
  const weighted = routineSets.filter(s => s.weight !== null && Number.isFinite(s.weight) && s.weight >= 0 && positive(s.reps))
  const exerciseTrends = [...groupBy(weighted, s => s.exerciseId)].map(([exerciseId, rows]) => {
    const sessions = [...groupBy(rows, s => s.workoutId)].map(([workoutId, sets]) => {
      const best = [...sets].sort((a,b) => (estimateOf(b) ?? 0) - (estimateOf(a) ?? 0) || b.weight! - a.weight! || b.reps! - a.reps!)[0]
      return { startTime:workouts.get(workoutId)!.startTime, workoutId, best, e1rm:estimateOf(best) }
    }).sort((a,b) => b.startTime - a.startTime || b.workoutId - a.workoutId)
    const latest = sessions[0].best
    const eligible = sessions.filter(s => s.e1rm !== null)
    const newest = eligible[0]
    const baseline = newest && eligible.find(s => s.startTime <= newest.startTime - 21 * DAY)
    return {exerciseId, exerciseName:exercises.get(exerciseId)!.name, lastBestLabel:latest.weight + ' × ' + latest.reps,
      lastWeight:latest.weight!, lastReps:latest.reps!, lastE1rm:newest?.e1rm ?? null,
      e1rmDelta:baseline && newest ? newest.e1rm! - baseline.e1rm! : null}
  }).sort((a,b) => a.exerciseName.localeCompare(b.exerciseName))
  const recentCount = sessions.filter(w => w.startTime >= now - 28 * DAY).length
  return { timesCompleted:sessions.length, lastPerformed:sessions.length ? sessions.reduce((v,w) => Math.max(v,w.startTime),0) : null,
    avgDurationMinutes:durations.length ? Math.trunc(durations.reduce((s,m) => s + m,0) / durations.length) : null,
    totalVolume:routineSets.reduce((n,s) => n + volumeOf(s),0), workoutsPerWeekRecent:recentCount ? recentCount / 4 : null, exerciseTrends }
}
