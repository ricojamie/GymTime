import { useMemo, useState } from 'react'
import { Link } from 'react-router-dom'
import { ArrowLeft, ChevronRight, Trophy } from 'lucide-react'
import { calculateMonthlyReport } from '../domain/monthlyReport'
import { formatWeight, weightLabel } from '../domain/units'
import ProgressSheet from '../components/logger/ProgressSheet'
import { numberFormat, useInsightsSource, useReportClock } from './insightsData'
import './insights.css'

export default function MonthlyReport() {
  const source = useInsightsSource()
  const now = useReportClock()
  const [recordId, setRecordId] = useState<number | null>(null)
  const report = useMemo(() => source && calculateMonthlyReport(source, now), [source, now])
  if (!source || !report) return <section className="screen il-page" aria-busy="true"><h1>Monthly report</h1></section>
  const priorDate = new Date(report.periodStart)
  priorDate.setMonth(priorDate.getMonth() - 1)
  const monthName = new Intl.DateTimeFormat(undefined,{month:'long'})
  const prior = monthName.format(priorDate)
  const weight = (lbs: number) => formatWeight(lbs,source.unit) + ' ' + weightLabel(source.unit)
  return <section className="screen il-page">
    <header className="il-page-head"><Link to="/insights" className="icon-button" aria-label="Back to training insights"><ArrowLeft size={20}/></Link><h1>Monthly report</h1></header>
    <section className="il-month-summary surface">
      <h2>{report.monthLabel}</h2><p>{report.narrative}</p>
      <div className="il-telemetry"><dl>
        <div><dt>Workouts</dt><dd>{report.workoutCount}</dd></div>
        <div><dt>Working sets</dt><dd>{report.totalWorkingSets}</dd></div>
        <div><dt>Active days</dt><dd>{report.trainingDays}</dd></div>
      </dl></div>
    </section>
    <div className="il-dashboard-grid">
      <section className="il-panel surface"><h2>Compared with {prior}</h2><div className="il-table-wrap">
        <table><thead><tr><th>Metric</th><th>{monthName.format(report.periodStart)}</th><th>{prior}</th></tr></thead><tbody>
          <tr><td>Volume</td><td>{weight(report.totalVolume)}</td><td>{weight(report.previousTotalVolume)}</td></tr>
          <tr><td>Workouts</td><td>{report.workoutCount}</td><td>{report.previousWorkoutCount}</td></tr>
          <tr><td>Working sets</td><td>{report.totalWorkingSets}</td><td>{report.previousWorkingSets}</td></tr>
        </tbody></table>
      </div><dl className="il-report-facts">
        <div><dt>Exercises trained</dt><dd>{report.exerciseCount}</dd></div>
        <div><dt>Active weeks</dt><dd>{report.activeWeeks} / {report.weeksInPeriod}</dd></div>
        <div><dt>Average rating</dt><dd>{report.averageRating == null ? 'Unrated' : numberFormat.format(report.averageRating) + ' / 5'}</dd></div>
      </dl></section>
      <section className="il-panel surface"><h2>Most trained</h2>{report.topMuscles.length ? <ol className="il-muscle-rank">{report.topMuscles.slice(0,5).map((m,i) => <li key={m.muscle}>
        <span>{String(i + 1).padStart(2,'0')}</span><div><strong>{m.muscle}</strong><i style={{width:(100 * m.setCount / (report.topMuscles[0].setCount || 1))+'%'}}/></div><b>{m.setCount} sets</b>
      </li>)}</ol> : <p className="il-empty">No working sets this month.</p>}</section>
      <section className="il-panel surface"><h2>Under six sets</h2>
        {report.undertrainedMuscles.length ? <ul className="il-muscle-names">{report.undertrainedMuscles.map(n => <li key={n}>{n}</li>)}</ul> : <p className="il-empty">No body parts below six sets.</p>}
      </section>
      <section className="il-panel surface"><h2>New personal records</h2>
        {report.newPRs.length ? report.newPRs.map((r, i) => <button className="il-record" key={r.exerciseId+'-'+i} onClick={() => setRecordId(r.exerciseId)}>
          <Trophy size={18} className="il-pr-mark"/><span><strong>{r.exerciseName}</strong><small>{weight(r.weight)} × {r.reps}</small></span><ChevronRight size={16}/>
        </button>) : <p className="il-empty">No new records this month.</p>}
      </section>
    </div>
    {recordId != null && <ProgressSheet exerciseId={recordId} initialTab="records" onClose={() => setRecordId(null)}/>}
  </section>
}
