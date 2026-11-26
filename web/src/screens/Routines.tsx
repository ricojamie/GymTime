import { useState } from 'react'
import { Link, useLocation, useNavigate, useParams } from 'react-router-dom'
import { useLiveQuery } from 'dexie-react-hooks'
import { ArrowLeft, ArrowUpRight, ChevronRight, Layers3, Plus } from 'lucide-react'
import { db } from '../db/database'
import type { Routine } from '../db/types'
import { createRoutine, renameRoutine } from '../routines/mutations'
import './routines.css'

function RoutineNameForm({ routine }: { routine?: Routine }) {
  const navigate = useNavigate()
  const [name, setName] = useState(routine?.name ?? '')
  const [error, setError] = useState('')
  const [saving, setSaving] = useState(false)
  const back = routine ? '/routines/' + routine.id : '/routines'
  return <main className="screen rt">
    <Link className="rt-back" to={back}><ArrowLeft size={18} /> Routines</Link>
    <h1>{routine ? 'Rename routine' : 'New routine'}</h1>
    <form className="rt-form" onSubmit={async event => {
      event.preventDefault()
      if (saving || !name.trim()) return
      setSaving(true)
      try {
        if (routine) await renameRoutine(routine.id, name)
        const id = routine?.id ?? await createRoutine(name)
        navigate('/routines/' + id, { replace: true })
      } catch (failure) { setError((failure as Error).message); setSaving(false) }
    }}>
      <label>Routine name<input autoFocus required value={name} onChange={e => setName(e.target.value)} placeholder="e.g. Upper / Lower" /></label>
      {error && <p role="alert" className="rt-error">{error}</p>}
      <button className="btn primary" disabled={saving || !name.trim()}>{routine ? 'Save name' : 'Create routine'}<ArrowUpRight size={18}/></button>
    </form>
  </main>
}

export default function Routines({ embedded = false }: { embedded?: boolean }) {
  const { id } = useParams()
  const { pathname } = useLocation()
  const data = useLiveQuery(async () => {
    const [routines, days] = await Promise.all([db.routines.toArray(), db.routine_days.toArray()])
    return { routines: routines.sort((a,b) => Number(b.isActive) - Number(a.isActive) || a.name.localeCompare(b.name)), days }
  }, [])
  if (!embedded && pathname.endsWith('/new')) return <RoutineNameForm key="create" />
  if (!embedded && pathname.endsWith('/edit')) {
    if (!data) return <p role="status">Loading routine…</p>
    const routine = data.routines.find(row => row.id === Number(id))
    return routine ? <RoutineNameForm key={routine.id} routine={routine} /> : <main className="screen rt"><h1>Routine unavailable</h1><Link to="/routines">View routines</Link></main>
  }
  const Region = embedded ? 'section' : 'main'
  return <Region className={embedded ? 'rt rt-embedded' : 'screen rt'}>
    {!embedded && <Link className="rt-back" to="/library"><ArrowLeft size={18}/> Library</Link>}
    <div className="rt-head">
      {embedded ? <h2>Routines</h2> : <h1>Routines</h1>}
      <Link className="btn rt-outline" aria-disabled={(data?.routines.length ?? 0) >= 10} to="/routines/new" onClick={e => { if ((data?.routines.length ?? 0) >= 10) e.preventDefault() }}><Plus size={16}/> New routine</Link>
    </div>
    {!data ? <p role="status">Loading routines…</p> : !data.routines.length ?
      <div className="surface rt-empty"><Layers3 size={32} aria-hidden="true"/><h3>No routines yet</h3></div>
      : <div className="surface rt-list">{data.routines.map(routine => {
        const days = data.days.filter(day => day.routineId === routine.id).sort((a,b) => a.orderIndex - b.orderIndex)
        const next = days.find(day => day.orderIndex === routine.nextDayOrderIndex) ?? days[0]
        return <Link key={routine.id} className="rt-list-link" to={'/routines/' + routine.id}>
          <span><strong>{routine.name}</strong><small>{days.length} {days.length === 1 ? 'day' : 'days'}{routine.isActive && next ? ' · Next ' + next.name : ''}</small></span>
          {routine.isActive && <span className="rt-status">Active</span>}<ChevronRight size={18}/>
        </Link>
      })}</div>}
  </Region>
}
