import { useMemo, useState, type CSSProperties } from 'react'
import { Link, useNavigate, useSearchParams } from 'react-router-dom'
import { useLiveQuery } from 'dexie-react-hooks'
import { Archive, ArrowLeft, ArrowRight, CalendarDays, ChevronRight, FolderPlus, MoreHorizontal, Play, Repeat2, Search, Star, Trash2, Trophy, X } from 'lucide-react'
import { BottomSheet } from '../components/primitives'
import { SwoleGator, useIsGatorMode } from '../components/mascot'
import { db } from '../db/database'
import { deleteWorkout, reopenWorkout, repeatWorkout } from '../db/repositories'
import type { WeightUnit, Workout, WorkoutSet } from '../db/types'
import { buildWorkoutShare } from '../domain/workoutShare'
import { formatWeight, weightLabel } from '../domain/units'
import { addWorkoutToRoutine, useHistoryData, type HistoryData } from '../history/sessionData'
import { dateLabel, describeWorkout, groupBy, monthLabel, setText } from '../history/presentation'
import { ShareActions } from '../history/ShareActions'
import './history.css'

export default function History() {
  const data = useHistoryData()
  const gatorMode = useIsGatorMode()
  const [query, setQuery] = useState('')
  const [params, setParams] = useSearchParams()
  const selected = Number(params.get('workoutId') || params.get('workout'))
  const rows = useMemo(() => {
    if (!data) return []
    const exercises = new Map(data.exercises.map(ex => [ex.id, ex]))
    const byWorkout = groupBy(data.sets, set => set.workoutId)
    return data.workouts.filter(w => w.endTime !== null).sort((a,b) => b.startTime - a.startTime || b.id - a.id).map(w => {
      const sets = byWorkout.get(w.id) || []
      const stats = describeWorkout(w, sets, exercises)
      const search = [stats.title, w.routineNameSnapshot, w.routineDayNameSnapshot, stats.muscles.join(' '), dateLabel(w.startTime), monthLabel(w.startTime), ...new Set(sets.map(s => exercises.get(s.exerciseId)?.name))].join(' ').toLocaleLowerCase()
      return {w, stats, search}
    })
  }, [data])
  const filtered = useMemo(() => {
    const tokens = query.trim().toLocaleLowerCase().split(/\s+/).filter(Boolean)
    if (!tokens.length) return rows
    return rows.filter(row => tokens.every(t => row.search.includes(t)))
  }, [rows, query])
  const months = useMemo(() => groupBy(filtered, row => monthLabel(row.w.startTime)), [filtered])
  const current = rows.find(row => row.w.id === selected)
  const unit = data?.prefs.preferredWeightUnit || 'lbs'

  return <main className="screen his-screen">
    <header className="his-heading"><h1>History</h1><span>{data && rows.length + (rows.length === 1 ? ' session' : ' sessions')}</span></header>
    <label className="his-search"><Search size={19} aria-hidden="true"/><input aria-label="Search history" type="search" placeholder="Search workouts" value={query} onChange={e => setQuery(e.target.value)}/>{query && <button type="button" className="icon-button" aria-label="Clear search" onClick={() => setQuery('')}><X size={17}/></button>}</label>
    {!data ? <p role="status">Loading history…</p> : !filtered.length ? <section className="his-empty surface">
      {gatorMode && !rows.length ? <SwoleGator pose="spotter" size={96} interactive /> : (rows.length ? <Search size={28}/> : <Archive size={28}/>)}
      <h2>{rows.length ? 'No matching workouts' : 'No completed workouts yet'}</h2>
      {rows.length ? <button className="btn" onClick={() => setQuery('')}>Clear search</button> : <Link className="btn primary" to={data.workouts.some(w => w.endTime === null) ? '/workout/active' : '/exercises/select?workoutMode=true'}>{data.workouts.some(w => w.endTime === null) ? 'Resume workout' : 'Start workout'}<ArrowRight size={16}/></Link>}
    </section> : Array.from(months, ([month, list], monthIndex) => <section key={month} className="his-month stagger-in" style={{ '--stagger-i': Math.min(monthIndex, 6) } as CSSProperties}>
      <h2 className="eyebrow">{month}</h2>
      <div className="surface his-timeline">{list.map(({w,stats}, itemIndex) => {
        const date = new Date(w.startTime)
        return <button type="button" className="his-session stagger-in" style={{ '--stagger-i': Math.min(itemIndex, 10) } as CSSProperties} aria-haspopup="dialog" key={w.id} onClick={() => setParams({workoutId:String(w.id)})}>
          <span className="his-date" aria-label={dateLabel(w.startTime)}><strong>{String(date.getDate()).padStart(2,'0')}</strong><small>{date.toLocaleDateString(undefined,{weekday:'short'})}</small></span>
          <span className="his-session-text"><strong>{stats.title}</strong>
            {stats.muscles.length > 0 && <span>{stats.muscles.slice(0,3).join(' · ')}{stats.muscles.length > 3 && ' +' + (stats.muscles.length - 3)}</span>}
            <small>{stats.sets} sets · {stats.duration}{stats.volume > 0 && ' · ' + formatWeight(stats.volume, unit) + ' ' + weightLabel(unit)}</small>
          </span>
          {w.rating != null && <span className="his-rating" aria-label={'Rating ' + w.rating + ' out of 5'}><Star size={13}/>{w.rating}</span>}<ChevronRight size={17}/>
        </button>
      })}</div>
    </section>)}
    {data && current && <WorkoutDetails key={current.w.id} workout={current.w} data={data} onClose={() => setParams({}, {replace:true})}/>}
  </main>
}

function RoutineTarget({workout, title, onDone}: {workout:Workout;title:string;onDone:()=>void}) {
  const routines = useLiveQuery(() => db.routines.toArray(), [], [])
  const [routineId, setRoutine] = useState('')
  const [dayId, setDay] = useState('')
  const [name,setName] = useState(workout.routineDayNameSnapshot?.trim() || title)
  const days = useLiveQuery(() => db.routine_days.where('routineId').equals(Number(routineId)).sortBy('orderIndex'), [routineId], [])
  const [error, setError] = useState('')
  const [pending, setPending] = useState(false)
  return <form className="his-form" onSubmit={async e => {
    e.preventDefault()
    if (pending) return
    setPending(true)
    try { await addWorkoutToRoutine(workout.id,Number(routineId),dayId ? Number(dayId) : null,name); onDone() }
    catch (reason) { setError(reason instanceof Error ? reason.message : 'Couldn’t save the workout to this routine.') }
    finally { setPending(false) }
  }}>
    {routines.length === 0 ? <><p>No routines yet.</p><Link className="btn" to="/routines/new">Create routine</Link></> : <>
      <label>Routine<select className="his-input" value={routineId} required onChange={e => {setRoutine(e.target.value); setDay('');setError('')}}><option value="">Choose a routine</option>{routines.map(r => <option key={r.id} value={r.id}>{r.name}</option>)}</select></label>
      {routineId && <><label>Workout day<select className="his-input" value={dayId} onChange={e => setDay(e.target.value)}><option value="">Create a new day</option>{days.map(d => <option key={d.id} value={d.id}>Replace {d.name}</option>)}</select></label>
      {!dayId && <label>Day name<input required className="his-input" value={name} maxLength={140} onChange={e => setName(e.target.value)}/></label>}
      {dayId && <p>Saving replaces the planned exercises in {days.find(d => String(d.id) === dayId)?.name}.</p>}
      <button type="submit" className="btn primary" disabled={pending || (!dayId && !name.trim())}>{dayId ? 'Replace day' : 'Add workout day'}</button></>}
    </>}
    {error && <p role="alert">{error}</p>}
  </form>
}

function SetRows({sets,unit,prIds}: {sets:WorkoutSet[];unit:WeightUnit;prIds:Set<number>}) {
  const top = sets.reduce((max, row) => row.weight === null ? max : Math.max(max,row.weight), -Infinity)
  return <ol className="his-sets">{sets.map((s,index) => <li key={s.id}><span className="his-ordinal">{String(index+1).padStart(2,'0')}</span><div><strong>{setText(s,unit)}</strong>
    {s.rpe != null && <small>RPE {s.rpe}</small>}{s.note?.trim() && <p>{s.note}</p>}</div>
    {prIds.has(s.id) ? <span className="his-pr-badge"><Trophy size={14} aria-label="Personal record" className="his-pr"/>PR</span> : s.weight === top && top > 0 && !s.isWarmup ? <small>Top weight</small> : null}
  </li>)}</ol>
}

function WorkoutDetails({ workout, data, onClose }: {workout:Workout;data:HistoryData;onClose:()=>void}) {
  const [panel, setPanel] = useState<'details'|'actions'|'routine'|'delete'>('details')
  const [message, setMessage] = useState('')
  const [busy,setBusy] = useState(false)
  const navigate = useNavigate()
  const exercises = useMemo(() => new Map(data.exercises.map(ex => [ex.id, ex])), [data.exercises])
  const sets = useMemo(() => data.sets.filter(s => s.workoutId === workout.id && s.isComplete).sort((a,b) => a.timestamp - b.timestamp || a.id-b.id), [data.sets, workout.id])
  const stats = describeWorkout(workout, sets, exercises)
  const share = useMemo(() => buildWorkoutShare(data,workout.id), [data, workout.id])
  const prIds = new Set(share?.exercises.flatMap(ex => ex.sets.filter(s => s.isPersonalRecord).map(s => s.id)))
  const active = data.workouts.find(w => w.endTime === null)
  const unit = data.prefs.preferredWeightUnit
  async function run(action:()=>Promise<void>) {
    if(busy) return
    setBusy(true);setMessage('')
    try { await action() }
    catch (err) { setMessage(err instanceof Error ? err.message : 'Couldn’t update this workout.') }
    finally { setBusy(false) }
  }
  const back = () => { setPanel('details');setMessage('') }
  return <BottomSheet title={panel === 'delete' ? 'Delete workout?' : panel === 'routine' ? 'Add to routine' : panel === 'actions' ? 'Workout actions' : stats.title} onClose={onClose}>
    <div className="his-detail">
      {panel !== 'details' && <button className="btn his-back" onClick={back}><ArrowLeft size={16}/>Workout details</button>}
      {panel === 'delete' && <><p>This permanently deletes this workout and its {sets.length} logged sets.</p><div className="his-share"><button className="btn" onClick={back}>Cancel</button><button className="btn his-danger" disabled={busy} onClick={() => void run(async () => {await deleteWorkout(workout.id);onClose()})}>Delete workout</button></div></>}
      {panel === 'routine' && <RoutineTarget workout={workout} title={stats.title} onDone={() => { setPanel('details');setMessage('Workout added to routine.') }}/>}
      {panel === 'actions' && <>
        {active ? <Link className="his-action" to="/workout/active"><Play size={18}/>Resume active workout</Link> : <button className="his-action" disabled={busy} onClick={() => void run(async () => {await reopenWorkout(workout.id); navigate('/workout/active')})}><Play size={18}/>Reopen session</button>}
        <button className="his-action" onClick={() => setPanel('routine')}><FolderPlus size={18}/>Add to routine</button>
        {share && <ShareActions share={share} prefs={data.prefs}/>}
        <button className="his-action his-danger-text" onClick={() => setPanel('delete')}><Trash2 size={18}/>Delete workout</button>
      </>}
      {panel === 'details' && <>
        <div className="his-detail-head"><span><CalendarDays size={16}/>{dateLabel(workout.startTime)}</span><button className="icon-button" aria-label="Workout actions" onClick={() => setPanel('actions')}><MoreHorizontal size={21}/></button></div>
        <dl className="his-metrics"><div><dt>Working sets</dt><dd>{stats.sets}</dd></div><div><dt>Duration</dt><dd>{stats.duration}</dd></div><div><dt>Volume</dt><dd>{formatWeight(stats.volume,unit)} <small>{weightLabel(unit)}</small></dd></div></dl>
        {workout.rating !== null && <p className="his-rating"><Star size={16}/>{workout.rating} / 5</p>}
        {[...new Set([workout.note, workout.ratingNote].filter(Boolean))].map(n => <p key={n} className="his-note">{n}</p>)}
        {Array.from(groupBy(sets, s => s.exerciseId), ([id, group]) => {
          const warm = group.filter(s => s.isWarmup)
          return <section key={id}><h3>{exercises.get(id)?.name || 'Exercise'}</h3>
            {warm.length > 0 && <details className="his-warm"><summary><ChevronRight size={15} aria-hidden="true"/>{warm.length} warm-up {warm.length === 1 ? 'set' : 'sets'}</summary><SetRows sets={warm} unit={unit} prIds={prIds}/></details>}
            <SetRows sets={group.filter(s => !s.isWarmup)} unit={unit} prIds={prIds}/>
          </section>
        })}
        {active ? <Link className="btn" to="/workout/active"><Play size={16}/>Resume active workout</Link> : <button className="btn primary" disabled={busy} onClick={() => void run(async () => {await repeatWorkout(workout.id);navigate('/workout/active')})}><Repeat2 size={17}/>Repeat workout</button>}
      </>}
      {message && <p role="status" className="his-message">{message}</p>}
    </div>
  </BottomSheet>
}
