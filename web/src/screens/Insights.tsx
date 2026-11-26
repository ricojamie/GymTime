import { useMemo, useState, type CSSProperties } from 'react'
import { Link } from 'react-router-dom'
import { ArrowRight, BookOpen, CalendarDays, ChevronDown, ChevronRight, Star, Trophy } from 'lucide-react'
import { calculateTrainingInsights } from '../domain/trainingInsights'
import { formatWeight, weightLabel } from '../domain/units'
import type { WeightUnit } from '../db/types'
import { AnimatedNumber, BottomSheet } from '../components/primitives'
import { SwoleGator, useIsGatorMode } from '../components/mascot/SwoleGator'
import ProgressSheet from '../components/logger/ProgressSheet'
import InsightLineChart from '../components/charts/InsightLineChart'
import WeeklyRhythmBars from '../components/charts/WeeklyRhythmBars'
import SpectrumTicks from '../components/charts/SpectrumTicks'
import { fullDate, numberFormat, shortDate, useInsightsSource, useReportClock } from './insightsData'
import './insights.css'

type Dashboard = ReturnType<typeof calculateTrainingInsights>
type Lift = Dashboard['exercises'][number]
type Evidence = { kind: 'guide' } | { kind: 'muscle'; name: string } | { kind: 'exercise'; id: number; records: boolean }

function metricValue(value: number, unit: string, display: WeightUnit) {
  if (/^lbs?$/.test(unit)) return formatWeight(value, display) + ' ' + weightLabel(display)
  if (unit === 'seconds' || unit === 's') {
    const total = Math.round(value)
    return Math.floor(total / 60) + ':' + String(total % 60).padStart(2, '0')
  }
  return numberFormat.format(value) + ' ' + unit
}

function LiftProgress({ lift, unit, open }: { lift: Lift; unit: WeightUnit; open: () => void }) {
  const { metric, chartPoints, comparison, chartSessionCount } = lift.progress
  const format = (value: number) => metricValue(value, metric?.unit ?? '', unit)
  return <>
    {metric && <p className="il-minor-label">{metric.label}</p>}
    <InsightLineChart points={chartPoints} formatValue={format} label={metric?.label ?? 'Session progress'} />
    {chartSessionCount > 0 && <>
      {comparison.percentChange != null && comparison.recentMedian != null && comparison.baselineMedian != null ? <div className="il-comparison">
        <strong>{comparison.percentChange > 0 ? '+' : ''}{numberFormat.format(comparison.percentChange)}%</strong>
        <span>Recent median {format(comparison.recentMedian)} · earlier {format(comparison.baselineMedian)}{comparison.status === 'STALE' ? ' · Last session over 6 weeks ago' : ''}</span>
      </div> : <p className="il-note">{lift.progress.explanation}</p>}
      <details className="il-details"><summary>Chart values <span>{chartPoints.length} of {chartSessionCount} sessions <ChevronDown size={15}/></span></summary>
        <div className="il-table-wrap"><table><thead><tr><th>Session</th><th>{metric?.label ?? 'Value'}</th></tr></thead><tbody>
          {[...chartPoints].reverse().map(p => <tr key={p.workoutId}><td>{fullDate.format(p.timestampMs)}</td><td>{format(p.value)}</td></tr>)}
        </tbody></table></div>
      </details>
    </>}
    <button className="il-linkrow" onClick={open}>Full history &amp; records <ArrowRight size={17}/></button>
  </>
}

function Guide({ data, close }: { data: Dashboard; close: () => void }) {
  const period = data.period
  return <BottomSheet title="How to read this" onClose={close}>
    <dl className="il-guide">
      <dt>Comparison periods</dt><dd>{fullDate.format(period.currentStartMs)} to {fullDate.format(period.currentEndMs)} against the previous {period.windowDays} days. Each window includes its start and stops just before its end.</dd>
      <dt>Working sets</dt><dd>Only valid, completed working sets in finished workouts count. An active day is the local start date of a finished workout with qualifying sets.</dd>
      <dt>Lift progress</dt><dd>Each point is a session best. For 1–15 loaded reps, estimated max equals weight × (1 + reps ÷ 30). Other exercises use their own logged metric. Charts show up to 80 points. Full history includes every session.</dd>
      <dt>Median comparison</dt><dd>The latest comparable sessions are compared to the same number of preceding sessions. The median limits the influence of a single unusual session.</dd>
      <dt>Body parts &amp; rhythm</dt><dd>Sets go to the exercise’s primary muscle. Core and Abs are grouped together. Weeks begin on Monday. Dashed baselines mark partial weeks.</dd>
      <dt>Recent records</dt><dd>A first qualifying effort sets a benchmark. A later effort that beats an earlier comparable best is a personal record.</dd>
    </dl>
  </BottomSheet>
}

export default function Insights() {
  const source = useInsightsSource()
  const now = useReportClock()
  const gatorMode = useIsGatorMode()
  const [days, setDays] = useState<28|84>(28)
  const [liftId, setLiftId] = useState<number | null>(null)
  const [evidence, setEvidence] = useState<Evidence | null>(null)
  const data = useMemo(() => source && calculateTrainingInsights(source, days, now), [source, days, now])
  const sorted = useMemo(() => [...(data?.exercises ?? [])].sort((a,b) =>
    Number(b.isStarred) - Number(a.isStarred) || b.totalWorkingSets - a.totalWorkingSets || a.name.localeCompare(b.name)
  ), [data])
  if (!source || !data) return <section className="screen il-page" aria-busy="true"><h1>Training insights</h1></section>
  const { cadence, muscles, recentRecords, observations } = data
  const lift = sorted.find(e => e.id === liftId) ?? sorted[0]
  const muscle = evidence?.kind === 'muscle' && muscles.find(m => m.name === evidence.name)
  const high = Math.max(1, ...muscles.map(m => m.currentWorkingSets))
  const delta = cadence.currentWorkouts - cadence.previousWorkouts
  const showExercise = (id: number, records = false) => setEvidence({ kind: 'exercise', id, records })
  const close = () => setEvidence(null)

  return <section className="screen il-page">
    <header className="il-page-head"><h1>Training insights</h1><button className="icon-button" aria-label="How to read this" title="How to read this" onClick={() => setEvidence({kind:'guide'})}><BookOpen size={20}/></button></header>
    <div className="il-period-line">
      <div className="il-segments" data-period={days} role="group" aria-label="Comparison period">{([28,84] as const).map(n => <button key={n} aria-pressed={days === n} onClick={() => setDays(n)}>{n/7} weeks</button>)}</div>
      <span key={days} className="il-period-range">{shortDate.format(data.period.currentStartMs)} – {shortDate.format(data.period.currentEndMs)}</span>
    </div>
    <section className="il-telemetry surface stagger-in" style={{ '--stagger-i': 0 } as CSSProperties} aria-label="Training overview">
      <dl>
        <div><dt>Workouts</dt><dd><AnimatedNumber value={cadence.currentWorkouts}/></dd><small>{delta > 0 ? '+' : ''}{delta} vs prior</small></div>
        <div><dt>Working sets</dt><dd><AnimatedNumber value={cadence.currentWorkingSets}/></dd><small>{cadence.previousWorkingSets} prior</small></div>
        <div><dt>Active days</dt><dd><AnimatedNumber value={cadence.currentActiveDays}/></dd><small>{cadence.previousActiveDays} prior</small></div>
      </dl>
      <div className="il-cadence" role="img" aria-label={cadence.currentActiveDays + ' active days out of ' + days}>
        <i key={days} style={{width: Math.min(100,100 * cadence.currentActiveDays / days) + '%'}}/>
      </div>
    </section>
    {observations.length > 0 && <section className="il-observations stagger-in" style={{ '--stagger-i': 1 } as CSSProperties} aria-label="Observations">{observations.slice(0,2).map((o, idx) => <button key={`${days}-${o.text}`} className="il-linkrow il-obs-enter" style={{ '--stagger-i': idx } as CSSProperties} onClick={() => {
      if (o.exerciseId != null) showExercise(o.exerciseId, true)
      else if (o.muscle) setEvidence({kind:'muscle',name:o.muscle})
    }}>{o.text}<ChevronRight size={16}/></button>)}</section>}
    <div className="il-dashboard-grid">
      <section className="il-panel surface stagger-in" style={{ '--stagger-i': 2 } as CSSProperties}>
        <h2>Lift progress</h2>
        {lift ? <>
          <label className="il-exercise-select">{lift.isStarred && <Star size={15}/>}<select aria-label="Exercise for lift progress" value={lift.id} onChange={e => setLiftId(Number(e.target.value))}>
            {sorted.map(ex => <option key={ex.id} value={ex.id}>{ex.name}</option>)}
          </select><ChevronDown size={16}/></label>
          <LiftProgress key={lift.id + '-' + days} lift={lift} unit={source.unit} open={() => showExercise(lift.id)}/>
        </> : <p className="il-empty">Complete a workout to see lift progress.</p>}
      </section>
      <section className="il-panel surface stagger-in" style={{ '--stagger-i': 3 } as CSSProperties}>
        <h2>Training rhythm</h2>
        <div className="il-rhythm-total"><strong><AnimatedNumber value={cadence.thisWeekWorkouts}/></strong><span>{cadence.thisWeekWorkouts === 1 ? 'workout' : 'workouts'} this week so far</span></div>
        <WeeklyRhythmBars weeks={cadence.weeks}/>
        <details className="il-details"><summary>Weekly counts <ChevronDown size={15}/></summary><div className="il-table-wrap"><table><thead><tr><th>Week of</th><th>Workouts</th></tr></thead><tbody>
          {cadence.weeks.map(w => <tr key={w.startDate}><td>{shortDate.format(w.startDate)}{w.isPartialWeek && <small> · partial</small>}</td><td>{w.workouts}</td></tr>)}
        </tbody></table></div></details>
        <Link to="/history" className="il-linkrow">Open history <ArrowRight size={17}/></Link>
      </section>
      <section className="il-panel surface stagger-in" style={{ '--stagger-i': 4 } as CSSProperties}><h2>Body parts</h2><div className="il-muscles">
        {muscles.map((m, idx) => <button key={`${days}-${m.name}`} className="il-muscle-row" style={{ '--stagger-i': Math.min(idx, 12) } as CSSProperties} onClick={() => setEvidence({kind:'muscle',name:m.name})}>
          <strong>{m.name}</strong><span>{m.currentWorkingSets} {m.currentWorkingSets === 1 ? 'set' : 'sets'} · {m.previousWorkingSets} prior</span>
          <SpectrumTicks value={m.currentWorkingSets} max={high}/>
          <small>{m.daysSinceLastLogged == null ? 'Never trained' : m.daysSinceLastLogged === 0 ? 'Trained today' : m.daysSinceLastLogged + 'd since trained'}</small>
        </button>)}
      </div></section>
      <div className="il-stack">
        <section className="il-panel surface stagger-in" style={{ '--stagger-i': 5 } as CSSProperties}>
          <div className="il-panel-head">
            <h2>Recent records</h2>
            {gatorMode && <SwoleGator pose="pr" size={34} label="" />}
          </div>
          {recentRecords.length ? recentRecords.slice(0,6).map((r, idx) => <button key={`${days}-${r.exerciseId}-${r.set.id}-${r.metric.label}`} className="il-record il-record-enter" style={{ '--stagger-i': Math.min(idx, 10) } as CSSProperties} onClick={() => showExercise(r.exerciseId,true)}>
            <span className={r.kind === 'PERSONAL_RECORD' ? 'il-pr-mark' : ''}><Trophy size={18}/></span>
            <span><strong>{r.exerciseName}</strong><small>{r.metric.label} · {r.kind === 'BENCHMARK' ? 'Benchmark' : 'PR'} · {shortDate.format(r.set.timestamp)}</small></span>
            <b>{metricValue(r.value, r.metric.unit, source.unit)}</b>
          </button>) : <p className="il-empty">No records in this period.</p>}
        </section>
        <Link to="/report/monthly" className="surface hover-lift il-month-link stagger-in" style={{ '--stagger-i': 6 } as CSSProperties}><CalendarDays size={21}/><span>Monthly report</span><ArrowRight size={18}/></Link>
      </div>
    </div>
    {evidence?.kind === 'guide' && <Guide data={data} close={close}/>}
    {muscle && <BottomSheet title={muscle.name} onClose={close}>
      <p className="il-evidence-total">{muscle.currentWorkingSets} working {muscle.currentWorkingSets === 1 ? 'set' : 'sets'} <span>· {muscle.previousWorkingSets} prior</span></p>
      {muscle.contributors.length ? <ul className="il-evidence-list">{muscle.contributors.map(c => <li key={c.exerciseId}><button className="il-linkrow" onClick={() => showExercise(c.exerciseId)}>
        <span>{c.exerciseName}<small>{c.currentWorkingSets} {c.currentWorkingSets === 1 ? 'set' : 'sets'} · {c.previousWorkingSets} prior</small></span><ChevronRight size={17}/></button></li>)}</ul> : <p className="il-empty">No working sets recorded for {muscle.name}.</p>}
    </BottomSheet>}
    {evidence?.kind === 'exercise' && <ProgressSheet exerciseId={evidence.id} initialTab={evidence.records ? 'records' : 'history'} onClose={close}/>}
  </section>
}
