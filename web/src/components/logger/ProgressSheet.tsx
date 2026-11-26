import { useMemo, useState } from 'react'
import { useLiveQuery } from 'dexie-react-hooks'
import { ChevronDown } from 'lucide-react'
import { db, defaultPreferences } from '../../db/database'
import type { Exercise, WeightUnit, Workout, WorkoutSet } from '../../db/types'
import { calculateE1RM } from '../../domain/oneRepMax'
import { paretoBest, recordMetric } from '../../domain/records'
import { distanceLabels, formatWeight, weightLabel } from '../../domain/units'
import { groupBy, setText } from '../../history/presentation'
import { BottomSheet } from '../primitives'
import InsightLineChart from '../charts/InsightLineChart'
import './tools.css'

type Tab = 'progress' | 'records' | 'history'
type Period = '1M' | '3M' | '6M' | 'All'
type Props = { exerciseId: number; excludeWorkoutId?: number; initialTab?: Tab; onClose: () => void }
type Session = { workout: Workout; sets: WorkoutSet[] }
const periods: Period[] = ['1M', '3M', '6M', 'All']
const dayLabel = new Intl.DateTimeFormat(undefined, { month: 'short', day: 'numeric', year: 'numeric' })
const timeLabel = new Intl.DateTimeFormat(undefined, { hour:'numeric', minute:'2-digit' })
const metricNames: Record<Exercise['logType'], string> = {
  WEIGHT_REPS: 'Heaviest set', WEIGHT_DISTANCE: 'Heaviest set', WEIGHT_TIME: 'Heaviest set',
  REPS_ONLY: 'Most reps', DURATION: 'Longest set', DISTANCE_TIME: 'Farthest set', CALORIES_TIME: 'Most calories',
}

function chartText(value: number, exercise: Exercise, unit: WeightUnit) {
  switch (exercise.logType) {
    case 'DURATION': return Math.floor(value / 60) + ':' + String(value % 60).padStart(2, '0')
    case 'CALORIES_TIME': return Math.round(value * 10) / 10 + ' cal'
    case 'DISTANCE_TIME': return Math.round(value * 100) / 100 + ' ' + distanceLabels[exercise.defaultDistanceUnit]
    case 'REPS_ONLY': return value + ' reps'
    default: return formatWeight(value, unit) + ' ' + weightLabel(unit)
  }
}

function bestWorkingSet(sets: WorkoutSet[], exercise: Exercise) {
  let best: WorkoutSet | null = null
  let maximum = -Infinity
  let secondary = -Infinity
  for (const set of sets) {
    const value = recordMetric(set, exercise.logType, exercise.defaultDistanceUnit)
    const next = (exercise.logType === 'WEIGHT_REPS' ? set.reps : exercise.logType === 'WEIGHT_TIME' ? set.durationSeconds : 0) ?? 0
    if (value !== null && (value > maximum || (value === maximum && next >= secondary))) {
      maximum = value
      secondary = next
      best = set
    }
  }
  return best
}

function periodStart(period: Period) {
  if (period === 'All') return -Infinity
  const date = new Date()
  const day = date.getDate()
  date.setDate(1)
  date.setMonth(date.getMonth() - parseInt(period, 10))
  const lastDay = new Date(date.getFullYear(), date.getMonth() + 1, 0).getDate()
  date.setDate(Math.min(day, lastDay))
  date.setHours(0, 0, 0, 0)
  return +date
}

export function ProgressSheet({ exerciseId, excludeWorkoutId, initialTab = 'progress', onClose }: Props) {
  const [tab, setTab] = useState<Tab>(initialTab)
  const [period, setPeriod] = useState<Period>('3M')
  const [limit, setLimit] = useState(12)
  const snapshot = useLiveQuery(async () => {
    const [exercise, prefs, sets] = await Promise.all([
      db.exercises.get(exerciseId), db.preferences.get('default'), db.sets.where('exerciseId').equals(exerciseId).sortBy('timestamp'),
    ])
    const byWorkout = groupBy(sets, set => set.workoutId)
    const workouts = await db.workouts.bulkGet(Array.from(byWorkout.keys()))
    const sessions:Session[] = []
    for (const workout of workouts) {
      if (workout && workout.id !== excludeWorkoutId && workout.endTime !== null) sessions.push({workout, sets: byWorkout.get(workout.id)!})
    }
    sessions.sort((a, b) => a.workout.startTime - b.workout.startTime || a.workout.id - b.workout.id)
    return { exercise, unit: prefs?.preferredWeightUnit ?? defaultPreferences.preferredWeightUnit, sessions }
  }, [exerciseId, excludeWorkoutId])
  const model = useMemo(() => {
    if (!snapshot?.exercise) return null
    const {sessions, exercise} = snapshot
    const valid = (s: WorkoutSet) => exercise.targetMuscle !== 'Warmups' && s.isComplete && !s.isWarmup
    const working = sessions.flatMap(session => session.sets.filter(valid))
    const e1rm = (s: WorkoutSet) => calculateE1RM(s.weight ?? 0, s.reps ?? 0)
    const useE1rm = exercise.logType === 'WEIGHT_REPS'
    const estimates = useE1rm ? working.flatMap(set => {const value=e1rm(set); return value === null ? [] : [{value,set}]}) : []
    const estimate = estimates.reduce<(typeof estimates)[number] | null>((best, next) => !best || next.value > best.value ? next : best, null)
    const cutoff = periodStart(period)
    const points = sessions.filter(s => s.workout.startTime >= cutoff).flatMap(({workout,sets}) => {
      let top: number | null = null
      for (const set of sets.filter(valid)) {
        const value = useE1rm ? (e1rm(set) ?? recordMetric(set, exercise.logType, exercise.defaultDistanceUnit)) : recordMetric(set, exercise.logType, exercise.defaultDistanceUnit)
        if (value !== null && (top === null || value > top)) top = value
      }
      return top === null ? [] : [{timestampMs:workout.startTime, workoutId:workout.id, value:top}]
    })
    const sampled = points.length <= 80 ? points : Array.from({length:80}, (_, i) => points[Math.round(i * (points.length - 1) / 79)])
    return {points, sampled, estimate, best:bestWorkingSet(working,exercise), frontier:exercise.logType === 'WEIGHT_REPS' ? paretoBest(working) : []}
  }, [snapshot, period])
  const exercise = snapshot?.exercise
  const unit = snapshot?.unit ?? 'lbs'
  return <BottomSheet title={exercise?.name ?? 'Exercise progression'} onClose={onClose}>
    {exercise && snapshot && model ? <div className="logger-tool">
      <div className="tool-tabs" role="group" aria-label="Exercise evidence">
        {(['progress','records','history'] as Tab[]).map(value => <button key={value} aria-pressed={tab === value} onClick={() => {setTab(value);setLimit(12)}}>{value[0].toUpperCase() + value.slice(1)}</button>)}
      </div>
      {tab === 'progress' && <>
        <div className="tool-section-heading"><h3 className="eyebrow">{model.estimate ? 'Estimated 1RM' : metricNames[exercise.logType]}</h3></div>
        <div className="progress-range" role="group" aria-label="Chart period">{periods.map(value => <button key={value} aria-pressed={period === value} onClick={() => {setPeriod(value);setLimit(12)}}>{value}</button>)}</div>
        <InsightLineChart key={period} label={model.estimate ? 'Estimated one-rep max' : metricNames[exercise.logType]}
          points={model.sampled} formatValue={value => chartText(value,exercise,unit)} />
        <section><div className="tool-section-heading"><h3 className="eyebrow">Sessions</h3><span>{model.points.length}</span></div>
          {model.points.slice(-limit).reverse().map(point => <div className="progress-result" key={point.workoutId}><span>{dayLabel.format(point.timestampMs)}</span><strong>{chartText(point.value, exercise,unit)}</strong></div>)}
          {model.points.length > limit && <button className="btn tool-more" onClick={() => setLimit(limit + 24)}>Show more sessions</button>}
        </section>
      </>}
      {tab === 'records' && <>
        {model.best ? <div className="progress-best"><span className="eyebrow">All-time best · {metricNames[exercise.logType]}</span><strong>{setText(model.best,unit)}</strong><time>{dayLabel.format(model.best.timestamp)}</time></div> : <p className="tool-empty">No completed working sets yet.</p>}
        {model.estimate && <div className="progress-result"><div><span className="eyebrow">Estimated 1RM</span><small>{setText(model.estimate.set,unit)}</small></div><strong>{chartText(model.estimate.value,exercise,unit)}</strong></div>}
        {!!model.frontier.length && <section><h3 className="eyebrow">Best by rep count</h3>
          {model.frontier.map(set => <div className="progress-result" key={set.id}><div>{set.reps} {set.reps === 1 ? 'rep' : 'reps'}<small>{dayLabel.format(set.timestamp)}</small></div><strong>{formatWeight(set.weight!,unit)} {weightLabel(unit)}</strong></div>)}
        </section>}
      </>}
      {tab === 'history' && <section aria-label="Workout history">
        {!snapshot.sessions.length && <p className="tool-empty">No completed sessions with this exercise yet.</p>}
        {snapshot.sessions.slice(-limit).reverse().map(({workout,sets},i) => <details key={workout.id} className="tool-disclosure" open={i === 0 ? true : undefined}>
          <summary><div><strong>{workout.name || workout.routineDayNameSnapshot || dayLabel.format(workout.startTime)}</strong><small>{(workout.name || workout.routineDayNameSnapshot) && (dayLabel.format(workout.startTime) + ' · ')}{sets.length} {sets.length === 1 ? 'set' : 'sets'}</small></div><ChevronDown size={16}/></summary>
          {sets.map((set, index) => <div key={set.id} className="progress-history-set"><span className="tool-number">{String(index + 1).padStart(2,'0')}</span><div><strong>{setText(set,unit)}</strong><small>{[timeLabel.format(set.timestamp), set.isWarmup && 'Warm-up', !set.isComplete && 'Incomplete', set.rpe != null && ('RPE ' + set.rpe)].filter(Boolean).join(' · ')}</small>{set.note && <p>{set.note}</p>}</div></div>)}
        </details>)}
        {snapshot.sessions.length > limit && <button className="btn tool-more" onClick={() => setLimit(limit + 24)}>Show more workouts</button>}
      </section>}
    </div> : <p className="tool-empty">{snapshot ? 'Exercise unavailable.' : 'Loading exercise…'}</p>}
  </BottomSheet>
}

export default ProgressSheet
