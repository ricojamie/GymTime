import { useState } from 'react'
import { Link, useNavigate, useParams, useSearchParams } from 'react-router-dom'
import { useLiveQuery } from 'dexie-react-hooks'
import { ArrowLeft, ArrowRight, Link2, Play } from 'lucide-react'
import { db } from '../db/database'
import { startWorkout } from '../db/repositories'
import { planBlocks, planLabel } from '../routines/mutations'
import './routines.css'

export default function RoutineStart() {
  const id = Number(useParams().id)
  const [search] = useSearchParams()
  const requested = search.get('dayId')
  const navigate = useNavigate()
  const [error, setError] = useState('')
  const [busy, setBusy] = useState(false)
  const data = useLiveQuery(async () => {
    const [routine,days,active] = await Promise.all([
      db.routines.get(id), db.routine_days.where('routineId').equals(id).sortBy('orderIndex'),
      db.workouts.filter(w => w.endTime === null).first(),
    ])
    const day = requested !== null ? days.find(d => d.id === Number(requested)) :
      days.find(d => d.orderIndex === routine?.nextDayOrderIndex) ?? days[0]
    const rows = day ? await db.routine_exercises.where('routineDayId').equals(day.id).sortBy('orderIndex') : []
    const exercises = await db.exercises.bulkGet(rows.map(row => row.exerciseId))
    return { routine,day,active,rows,days, names: new Map(exercises.flatMap(ex => ex ? [[ex.id,ex] as const] : [])) }
  }, [id,requested])
  if (!data) return <main className="screen rt" role="status">Loading plan…</main>
  const {routine,day,active,rows,names,days} = data
  const back = '/routines/' + id
  if (!routine || !day) return <main className="screen rt"><h1>Day unavailable</h1><Link to={routine ? back : '/routines'}>View routine</Link></main>
  return <main className="screen rt">
    <Link className="rt-back" to={back}><ArrowLeft size={18}/>{routine.name}</Link>
    <div><span className="eyebrow">Day {days.findIndex(d => d.id === day.id) + 1} of {days.length}</span><h1>{day.name}</h1></div>
    <dl className="surface rt-stat-grid"><div><dt>Exercises</dt><dd>{rows.length}</dd></div><div><dt>Planned sets</dt><dd>{rows.reduce((total,row) => total+row.targetSets,0)}</dd></div></dl>
    <h2>Day plan</h2>
    <div className="surface rt-list">{planBlocks(rows).map((block, index) => <div className="rt-block" key={block[0].id}>
      <span className="eyebrow">{block.length > 1 ? 'Superset ' : 'Exercise '}{String(index+1).padStart(2,'0')}</span>
      {block.map(row => <div className="rt-line" key={row.id}><span><strong>{names.get(row.exerciseId)?.name ?? 'Deleted exercise'}</strong>
        <small>{names.get(row.exerciseId)?.targetMuscle} · {row.targetRestSeconds ?? names.get(row.exerciseId)?.defaultRestSeconds ?? 90}s rest</small>
        {row.notes && <small>{row.notes}</small>}</span><span>{block.length > 1 && <Link2 size={14}/>} {planLabel(row)}</span></div>)}
    </div>)}</div>
    {error && <p role="alert" className="rt-error">{error}</p>}
    {active ? <Link to="/workout/active" className="btn primary">Resume current workout<ArrowRight size={18}/></Link> :
    <button className="btn primary" disabled={busy || !rows.length} onClick={async () => {
      if (busy) return
      setBusy(true)
      try { await startWorkout({routineDayId:day.id}); navigate('/workout/active',{replace:true}) }
      catch (failure) { setError((failure as Error).message); setBusy(false) }
    }}><Play size={18}/>{busy ? 'Starting…' : 'Start workout'}</button>}
  </main>
}
