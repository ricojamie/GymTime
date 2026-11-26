import type { Exercise, LogType, Workout, WorkoutSet } from '../db/types'
import { canonicalMuscle, compareSessions, completedData, DAY, estimateOf, groupBy, localDay, muscleNames, positive, shiftDays, weekStart } from './analyticsCore'
import type { TrainingSource } from './analyticsCore'
import { distanceToMeters } from './units'
export type { TrainingSource }

type MetricKind = 'ESTIMATED_ONE_REP_MAX' | 'WEIGHT' | 'WEIGHT_AT_REPS' | 'REPS' | 'DURATION' | 'DISTANCE' | 'CALORIES'
export interface InsightMetric { kind: MetricKind; label: string; unit: string; reps?: number }
interface Observation { text: string; muscle?: string; exerciseId?: number }
interface RecordValue { metric: InsightMetric; value: number; set: WorkoutSet }
interface InsightRecordEvent extends RecordValue {
  exerciseId: number; exerciseName: string; muscle: string; previousValue: number | null
  kind: 'BENCHMARK' | 'PERSONAL_RECORD'
}

const estimate: InsightMetric = { kind: 'ESTIMATED_ONE_REP_MAX', label: 'Estimated max', unit: 'lb' }
const weight: InsightMetric = { kind: 'WEIGHT', label: 'Heaviest weight', unit: 'lb' }
const reps: InsightMetric = { kind: 'REPS', label: 'Most reps', unit: 'reps' }
const duration: InsightMetric = { kind: 'DURATION', label: 'Longest duration', unit: 'seconds' }
const calories: InsightMetric = { kind: 'CALORIES', label: 'Most logged calories', unit: 'cal' }
const metricKey = (m: InsightMetric) => [m.kind, m.unit, m.reps ?? ''].join('/')

function distanceMetric(s: WorkoutSet): RecordValue | null {
  const measured = s.distanceUnit === 'STEPS' || s.distanceUnit === 'FLOORS'
  const unit = measured ? s.distanceUnit!.toLowerCase() : 'm'
  const value = measured ? s.distanceValue : s.distanceUnit && s.distanceValue !== null
    ? distanceToMeters(s.distanceValue, s.distanceUnit) : s.distanceMeters
  return positive(value) ? { set: s, metric: { kind: 'DISTANCE', label: 'Longest distance', unit }, value } : null
}

function setMetrics(set: WorkoutSet, type: LogType): RecordValue[] {
  const measure = (metric: InsightMetric, value: number): RecordValue => ({ metric, value, set })
  const w = set.weight !== null && Number.isFinite(set.weight) && set.weight >= 0 ? measure(weight, set.weight) : null
  const r = positive(set.reps) ? measure(reps, set.reps) : null
  const d = positive(set.durationSeconds) ? measure(duration, set.durationSeconds) : null
  switch (type) {
    case 'WEIGHT_REPS': {
      if (!w || !r) return []
      const e = estimateOf(set)
      const atReps = measure({kind:'WEIGHT_AT_REPS', label:'Weight at ' + r.value + ' reps', unit:'lb', reps:r.value}, w.value)
      return e === null ? [w, atReps, r] : [w, atReps, r, measure(estimate, e)]
    }
    case 'REPS_ONLY': return r ? [r] : []
    case 'DURATION': return d ? [d] : []
    case 'WEIGHT_TIME': return w && d ? [w, d] : []
    case 'WEIGHT_DISTANCE': { const dist = distanceMetric(set); return w && dist ? [w, dist] : [] }
    case 'DISTANCE_TIME': { const dist = distanceMetric(set); return d && dist ? [dist, d] : [] }
    case 'CALORIES_TIME': return d && positive(set.calories) ? [measure(calories, set.calories), d] : []
  }
}

function selectMetric(type: LogType, entries: RecordValue[]) {
  if (!entries.length) return null
  switch (type) {
    case 'WEIGHT_REPS': return entries.some(e => e.metric === estimate) ? estimate :
      entries.some(e => e.metric === weight && e.value > 0) ? weight : reps
    case 'REPS_ONLY': return reps
    case 'DURATION': case 'WEIGHT_TIME': return duration
    case 'CALORIES_TIME': return calories
    default: return entries.slice().reverse().find(e => e.metric.kind === 'DISTANCE')?.metric ?? null
  }
}

// Uniform integer indices keep both endpoints. The underlying history is always unsampled.
export function sampleChart<T>(points: T[], maximum = 80): T[] {
  if (points.length <= maximum) return points.slice()
  if (maximum < 2) return maximum === 1 ? points.slice(-1) : []
  return Array.from({length:maximum}, (_, i) => points[Math.floor(i * (points.length - 1) / (maximum - 1))])
}

function exerciseInsights(exercise: Exercise, rows: WorkoutSet[], workouts: Map<number, Workout>,
  period: ReturnType<typeof createPeriod>, events: InsightRecordEvent[]) {
  const muscle = canonicalMuscle(exercise.targetMuscle)
  const records = new Map<string, RecordValue>()
  const entries: RecordValue[] = []
  const validSets: WorkoutSet[] = []
  for (const set of rows) {
    const metrics = setMetrics(set, exercise.logType)
    if (!metrics.length) continue
    validSets.push(set)
    for (const entry of metrics) {
      entries.push(entry)
      const key = metricKey(entry.metric)
      const prior = records.get(key)
      if (prior && entry.value <= prior.value) continue
      records.set(key, entry)
      if (set.timestamp < period.currentStartMs || entry.metric === estimate || entry.value === 0) continue
      events.push({ ...entry, exerciseId:exercise.id, exerciseName:exercise.name, muscle,
        kind: prior ? 'PERSONAL_RECORD' : 'BENCHMARK', previousValue: prior?.value ?? null })
    }
  }
  const metric = selectMetric(exercise.logType, entries)
  const key = metric && metricKey(metric)
  const selected = entries.filter(e => metricKey(e.metric) === key)
  const byWorkout = groupBy(validSets, s => s.workoutId)
  const history = [...byWorkout].map(([workoutId, sets]) => ({
    workoutId, workoutName:workouts.get(workoutId)!.name, timestampMs:sets[sets.length - 1].timestamp, sets,
  })).sort((a, b) => b.timestampMs - a.timestampMs || b.workoutId - a.workoutId)
  const points = [...groupBy(selected, e => e.set.workoutId)].map(([workoutId, values]) => {
    const best = values.reduce((a,b) => b.value >= a.value ? b : a)
    const sets = byWorkout.get(workoutId)!
    return {workoutId, timestampMs:sets[sets.length - 1].timestamp, value:best.value, set:best.set}
  }).sort((a,b) => a.timestampMs - b.timestampMs || a.workoutId - b.workoutId)
  const current = points.filter(p => p.timestampMs >= period.currentStartMs)
  const comparable = metric === estimate || (exercise.logType === 'REPS_ONLY' && metric === reps)
  const comparison = compareSessions(comparable ? points : [], period.currentEndMs)
  const currentWorkingSets = validSets.filter(s => s.timestamp >= period.currentStartMs).length
  const previousWorkingSets = validSets.filter(s => s.timestamp >= period.previousStartMs && s.timestamp < period.currentStartMs).length
  const explanation = metric === estimate ? 'Estimated max = weight × (1 + reps ÷ 30), using 1–15 reps. Each point is one session’s best estimate.' :
    metric ? metric.label + ' per completed session.' : 'No completed working sets yet.'
  return { id:exercise.id, name:exercise.name, muscle, logType:exercise.logType, isStarred:exercise.isStarred,
    currentWorkingSets, previousWorkingSets, totalWorkingSets:validSets.length,
    latestTimestampMs:history[0]?.timestampMs ?? null, history, records:[...records.values()],
    progress:{ metric, chartPoints:sampleChart(current), chartSessionCount:current.length, comparison, explanation } }
}
export type InsightExercise = ReturnType<typeof exerciseInsights>

function createPeriod(windowDays: 28 | 84, now: number) {
  const span = windowDays * DAY
  return {windowDays, currentStartMs:now - span, currentEndMs:now, previousStartMs:now - 2 * span, previousEndMs:now - span}
}

function buildMuscles(names: string[], exercises: InsightExercise[], now: number) {
  const grouped = groupBy(exercises.filter(e => e.totalWorkingSets), e => e.muscle.toLowerCase())
  return names.map(name => {
    const contributors = (grouped.get(name.toLowerCase()) ?? []).map(e => ({
      exerciseId:e.id, exerciseName:e.name, currentWorkingSets:e.currentWorkingSets, previousWorkingSets:e.previousWorkingSets,
      totalWorkingSets:e.totalWorkingSets, latestTimestampMs:e.latestTimestampMs,
    })).sort((a,b) => b.currentWorkingSets - a.currentWorkingSets || b.totalWorkingSets - a.totalWorkingSets || a.exerciseName.localeCompare(b.exerciseName))
    const latest = contributors.reduce<number|null>((ts,e) => e.latestTimestampMs === null ? ts : ts === null ? e.latestTimestampMs : Math.max(ts, e.latestTimestampMs), null)
    return { name, contributors, currentWorkingSets:contributors.reduce((n,e) => n + e.currentWorkingSets,0),
      previousWorkingSets:contributors.reduce((n,e) => n + e.previousWorkingSets,0), latestTimestampMs:latest,
      daysSinceLastLogged: latest === null ? null : localDay(now) - localDay(latest) }
  })
}
export type InsightMuscle = ReturnType<typeof buildMuscles>[number]

export function calculateTrainingInsights(source: TrainingSource, windowDays: 28 | 84 = 28, now = Date.now()) {
  const period = createPeriod(windowDays, now)
  const data = completedData(source, now)
  const grouped = groupBy(data.sets, s => s.exerciseId)
  const events: InsightRecordEvent[] = []
  const exercises = [...data.exercises.values()].map(e => exerciseInsights(e, grouped.get(e.id) ?? [], data.workouts, period, events))
    .sort((a, b) => Number(b.isStarred) - Number(a.isStarred) || b.currentWorkingSets - a.currentWorkingSets ||
      b.totalWorkingSets - a.totalWorkingSets || (b.latestTimestampMs ?? 0) - (a.latestTimestampMs ?? 0) || a.name.localeCompare(b.name))
  const muscles = buildMuscles(muscleNames(source), exercises, now)
  const ids = new Set(exercises.flatMap(e => e.history.map(h => h.workoutId)))
  const completed = [...data.workouts.values()].filter(w => ids.has(w.id))
  const current = completed.filter(w => w.startTime >= period.currentStartMs)
  const previous = completed.filter(w => w.startTime >= period.previousStartMs && w.startTime < period.currentStartMs)
  const thisWeekStartDate = weekStart(now)
  const weeks = []
  for (let week = weekStart(period.currentStartMs); week < now; week = shiftDays(week, 7)) {
    const next = shiftDays(week, 7)
    const startDate = Math.max(week, period.currentStartMs)
    const end = Math.min(next, now)
    weeks.push({startDate, endDate:end - 1, workouts:current.filter(w => w.startTime >= startDate && w.startTime < end).length,
      isCurrentWeek:week === thisWeekStartDate, isPartialWeek:startDate !== week || end !== next})
  }
  const cadence = {totalCompletedWorkouts:completed.length, currentWorkouts:current.length, previousWorkouts:previous.length,
    currentActiveDays:new Set(current.map(w => localDay(w.startTime))).size, previousActiveDays:new Set(previous.map(w => localDay(w.startTime))).size,
    currentWorkingSets:muscles.reduce((n,m) => n + m.currentWorkingSets,0), previousWorkingSets:muscles.reduce((n,m) => n + m.previousWorkingSets,0),
    thisWeekStartDate, thisWeekWorkouts:completed.filter(w => w.startTime >= thisWeekStartDate).length, weeks}
  events.sort((a,b) => b.set.timestamp - a.set.timestamp || Number(b.kind === 'PERSONAL_RECORD') - Number(a.kind === 'PERSONAL_RECORD') || a.set.id - b.set.id)
  const observations: Observation[] = []
  const latestPr = events.find(e => e.kind === 'PERSONAL_RECORD')
  if (latestPr) observations.push({ text: latestPr.exerciseName + ' has a new ' + latestPr.metric.label.toLowerCase() + ' record.', exerciseId:latestPr.exerciseId, muscle:latestPr.muscle })
  const most = muscles.reduce<InsightMuscle | undefined>((top,m) => !top || m.currentWorkingSets > top.currentWorkingSets ? m : top, undefined)
  if (most?.currentWorkingSets) observations.push({text:most.name + ' led with ' + most.currentWorkingSets + ' working sets.', muscle:most.name})
  const recentRecords = [...groupBy(events, e => e.set.id).values()].slice(0,6).map(group => group[0])
  return {period, cadence, exercises, muscles, recentRecords, observations}
}
export type TrainingInsightsDashboard = ReturnType<typeof calculateTrainingInsights>
