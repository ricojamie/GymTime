import { useMemo, useState } from 'react'
import { useLiveQuery } from 'dexie-react-hooks'
import { ArrowUpRight, ChevronRight, Flame, Gauge } from 'lucide-react'
import { db } from '../../db/database'
import type { Preferences } from '../../db/types'
import { calculateStreak, calculateBestStreak } from '../../domain/streakCalculator'
import { calculateWeeklyVolume } from '../../domain/weeklyVolume'
import { calculateStrengthMomentum, type MuscleMomentum } from '../../domain/strengthMomentum'
import { fromLbs, weightLabel } from '../../domain/units'
import { AnimatedNumber, BottomSheet } from '../primitives'
import type { CSSProperties, ReactNode } from 'react'

const formatVolume = (value: number, prefs: Preferences) =>
  fromLbs(value, prefs.preferredWeightUnit).toLocaleString(undefined, {maximumFractionDigits: 0})
const signedPercent = (n: number) => `${n > 0 ? '+' : ''}${n.toFixed(1)}%`
const muscleLabel = (muscle: string) => muscle === 'Abs' ? 'Core' : muscle
const localDate = (d: number) => new Date(d).toLocaleDateString(undefined, {month: 'short', day: 'numeric'})

function ReadoutRow({label, children}: {label: string; children: ReactNode}) {
  return <div className="home-detail-row"><span>{label}</span><strong>{children}</strong></div>
}

function tickTiming(order: number): CSSProperties {
  return {'--tick-delay': `${order * 18}ms`} as CSSProperties
}

function Calibration({count, lit, rest = false}: {count: number; lit: number; rest?: boolean}) {
  return <span className={`home-calibration ${rest ? 'home-rest' : ''}`} aria-hidden="true">
    {Array.from({length: count}, (_, i) => <i key={i}>{i < lit && <b className={i === lit - 1 ? 'is-tip' : undefined} style={tickTiming(i)}/>}</i>)}
  </span>
}

function StrengthTicks({ muscle }: { muscle: MuscleMomentum }) {
  const delta = muscle.percentChange
  const count = delta === null ? 0 : Math.min(3, Math.ceil(Math.abs(delta) / 5))
  return <span className="home-strength-ticks" aria-hidden="true">
    {Array.from({length: 7}, (_, i) => {
      const offset = i - 3
      const lit = delta !== null && (offset === 0 || Math.sign(delta) === Math.sign(offset) && Math.abs(offset) <= count)
      return <i key={i} style={tickTiming(Math.abs(offset))} className={`${offset === 0 ? 'center ' : ''}${lit ? 'lit' : ''}`}/>
    })}
  </span>
}

export function TrainingReadouts({prefs, dayKey, routine}: {prefs: Preferences; dayKey: string; routine: ReactNode}) {
  const [sheet, setSheet] = useState<'streak' | 'volume' | 'strength' | null>(null)
  const [muscle, setMuscle] = useState('Chest')
  const source = useLiveQuery(() => db.transaction('r', [db.sets, db.workouts, db.exercises, db.muscle_groups], async () => {
    const [sets, workouts, exercises, names] = await Promise.all([
      db.sets.toArray(), db.workouts.toArray(), db.exercises.toArray(), db.muscle_groups.toArray(),
    ])
    return {sets, workouts, exercises, muscleNames: names.map(m => m.name)}
  }), [])
  const data = useMemo(() => {
    if (!source) return null
    const now = Date.now()
    const completed = source.workouts.filter(w => w.endTime !== null && w.endTime <= now)
    const current = new Date(now)
    const year = current.getFullYear()
    const yearStart = +new Date(year, 0, 1)
    const priorStart = +new Date(year - 1, 0, 1)
    const priorEnd = new Date(year - 1, current.getMonth(), 1)
    priorEnd.setDate(Math.min(current.getDate(), new Date(year - 1, current.getMonth() + 1, 0).getDate()))
    priorEnd.setHours(current.getHours(), current.getMinutes(), current.getSeconds(), current.getMilliseconds())
    const sets = source.sets.filter(s => s.isComplete && !s.isWarmup && s.timestamp <= now)
    const yearVolume = (start: number, end: number) => sets.reduce((sum, s) => s.timestamp >= start && s.timestamp <= end && s.weight && s.weight > 0 && s.reps && s.reps > 0 ? sum + s.weight * s.reps : sum, 0)
    let sunday = new Date(now)
    sunday.setDate(sunday.getDate() - sunday.getDay())
    sunday.setHours(0, 0, 0, 0)
    const saturday = new Date(sunday)
    saturday.setDate(saturday.getDate() + 6)
    return {
      streak: calculateStreak(sets, prefs.restDaysPerWeek, now),
      best: Math.max(prefs.bestStreak, calculateBestStreak(sets, prefs.restDaysPerWeek, now)),
      ytdCount: completed.filter(w => (w.endTime ?? 0) >= yearStart).length,
      ytdVolume: yearVolume(yearStart, now), priorVolume: yearVolume(priorStart, +priorEnd),
      volume: calculateWeeklyVolume(sets, now), momentum: calculateStrengthMomentum(source, now).muscles,
      week: `${localDate(+sunday)} – ${localDate(+saturday)}`,
      exerciseMap: new Map(source.exercises.map(e => [e.id, e])),
    }
  }, [source, prefs.restDaysPerWeek, prefs.bestStreak, dayKey])
  if (!data || !source) return <div className="home-loading" role="status">Loading training history…</div>
  const {streak, volume, momentum} = data
  const selected = momentum.find(m => m.muscle === muscle) ?? momentum[0]
  const status = streak.streakDays === 0 ? 'Fresh start' : streak.state === 'ACTIVE' ? 'Trained today' : `${streak.skipsRemaining} rest left`
  const unit = weightLabel(prefs.preferredWeightUnit)
  const weekDelta = volume.lastWeek === 0 ? null : Math.round((volume.progressPercent - 1) * 100)
  function openMuscle(name: string) {
    setMuscle(name)
    setSheet('strength')
  }

  return <>
    <div className="home-readouts">
      <div className="home-metrics-column">
        <section className="home-stagger" style={{'--stagger-i': 2} as CSSProperties} aria-labelledby="home-training-metrics">
          <div className="home-section-heading"><h2 id="home-training-metrics">Training metrics</h2></div>
          <div className="home-surface home-metrics">
            <button className="home-metric hover-lift" onClick={() => setSheet('streak')}>
              <span className="home-metric-label"><Flame size={16}/> Iron streak</span>
              <span className="home-big-number"><AnimatedNumber value={streak.streakDays} format={n => Math.round(n).toString()} /> <small>{streak.streakDays === 1 ? 'day' : 'days'}</small></span>
              <span className="home-metric-state">{status}</span>
              <Calibration count={Math.max(1, streak.allowedSkipsPerWeek)} lit={streak.streakDays > 0 ? streak.skipsRemaining : 0} rest/>
              <span className="home-metric-meta">Best {data.best} · YTD {data.ytdCount}</span>
            </button>
            <button className="home-metric hover-lift" onClick={() => setSheet('volume')}>
              <span className="home-metric-label"><Gauge size={16}/> Weekly volume</span>
              <span className="home-big-number"><AnimatedNumber value={fromLbs(volume.currentWeek, prefs.preferredWeightUnit)} format={n => Math.round(n).toLocaleString()} /> <small>{unit}</small></span>
              <span className="home-metric-state">{weekDelta === null ? 'Baseline' : `${weekDelta > 0 ? '+' : ''}${weekDelta}% vs last week`}</span>
              <Calibration count={10} lit={Math.round(volume.progressPercent * 10)}/>
              <span className="home-metric-meta">Last week {formatVolume(volume.lastWeek, prefs)} {unit}</span>
            </button>
          </div>
        </section>
        <div className="home-routine-slot home-stagger" style={{'--stagger-i': 4} as CSSProperties}>{routine}</div>
      </div>
      <section className="home-spectrum home-stagger" style={{'--stagger-i': 3} as CSSProperties} aria-labelledby="home-spectrum-heading">
        <div className="home-section-heading"><h2 id="home-spectrum-heading">Strength spectrum</h2>
          <button className="home-plain-arrow" aria-label="Open strength trends" onClick={() => openMuscle(selected?.muscle ?? 'Chest')}><ArrowUpRight size={19}/></button>
        </div>
        <div className="home-surface home-spectrum-grid">
          {momentum.map((m, idx) => <button key={m.muscle} style={{'--stagger-i': idx + 3} as CSSProperties} className="home-spectrum-cell home-stagger" onClick={() => openMuscle(m.muscle)} aria-label={`${muscleLabel(m.muscle)} strength trend, ${m.percentChange === null ? 'building baseline' : signedPercent(m.percentChange)}`}>
            <span className="home-muscle-name"><i className={`home-status-dot ${m.status === 'READY' ? 'ready' : ''}`}/>{muscleLabel(m.muscle)}</span>
            <span className="home-muscle-readout"><StrengthTicks muscle={m}/><span>{m.percentChange === null ? '—' : signedPercent(m.percentChange)}</span></span>
          </button>)}
        </div>
      </section>
    </div>
    {sheet === 'streak' && <BottomSheet title="Iron streak" onClose={() => setSheet(null)}>
      <div className="home-sheet-content">
        <p className="home-sheet-number">{streak.streakDays} <small>{streak.streakDays === 1 ? 'day' : 'days'}</small></p>
        <ReadoutRow label="Rest days left this week">{streak.skipsRemaining} of {streak.allowedSkipsPerWeek}</ReadoutRow>
        <ReadoutRow label="All-time best">{data.best} days</ReadoutRow>
        <ReadoutRow label="Workouts this year">{data.ytdCount}</ReadoutRow>
        <ReadoutRow label="Volume this year">{formatVolume(data.ytdVolume, prefs)} {unit}</ReadoutRow>
        <ReadoutRow label="Same period last year">{formatVolume(data.priorVolume, prefs)} {unit}</ReadoutRow>
      </div>
    </BottomSheet>}
    {sheet === 'volume' && <BottomSheet title="Weekly volume" onClose={() => setSheet(null)}>
      <div className="home-sheet-content">
        <p className="home-sheet-number">{formatVolume(volume.currentWeek, prefs)} <small>{unit}</small></p>
        <ReadoutRow label="This week">{data.week}</ReadoutRow>
        <ReadoutRow label="Previous week">{formatVolume(volume.lastWeek, prefs)} {unit}</ReadoutRow>
        <ReadoutRow label="Change">{weekDelta === null ? 'Building baseline' : `${weekDelta > 0 ? '+' : ''}${weekDelta}%`}</ReadoutRow>
      </div>
    </BottomSheet>}
    {sheet === 'strength' && selected && <BottomSheet title="Strength trends" onClose={() => setSheet(null)}>
      <div className="home-sheet-content">
        <div className="home-muscle-filters" aria-label="Filter by muscle">
          {momentum.map(m => <button key={m.muscle} aria-pressed={m.muscle === selected.muscle} onClick={() => setMuscle(m.muscle)}>{muscleLabel(m.muscle)}</button>)}
        </div>
        <h3>{muscleLabel(selected.muscle)}</h3>
        <p className="home-sheet-number">{selected.percentChange === null ? '—' : signedPercent(selected.percentChange)}</p>
        {selected.contributingExercises.length === 0 && <p className="home-sheet-note">No comparable sessions logged for this muscle.</p>}
        {selected.contributingExercises.map(c => {
          const exercise = data.exerciseMap.get(c.exerciseId)
          const score = (v: number | null) => v === null ? '—' : exercise?.logType === 'REPS_ONLY' ? `${v} reps` : `${formatVolume(v, prefs)} ${unit}`
          return <div className="home-contributor" key={c.exerciseId}><strong>{c.exerciseName}</strong><span>{c.percentChange === null ? c.status === 'STALE' ? 'Last trained over 42 days ago' : 'Building baseline' : signedPercent(c.percentChange)}</span>
            <small>Earlier {score(c.baselineValue)} <ChevronRight size={13}/> Recent {score(c.recentValue)}</small></div>
        })}
        <details className="home-method"><summary>How strength trends work</summary>
          <p>IronLog compares the median of your most recent two or three sessions with an equal number of earlier sessions. Weighted lifts use estimated one-rep max. Bodyweight lifts use reps. A muscle’s change is the median of its exercise changes, each capped at ±20%.</p>
        </details>
      </div>
    </BottomSheet>}
  </>
}
