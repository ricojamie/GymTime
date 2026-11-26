import { useState, type CSSProperties } from 'react'
import { Link, useLocation, useNavigate, useParams } from 'react-router-dom'
import { useLiveQuery } from 'dexie-react-hooks'
import { ArrowLeft, ArrowUpRight, ChevronRight, Layers3, Plus } from 'lucide-react'
import { SwoleGator, useIsGatorMode } from '../components/mascot'
import { db } from '../db/database'
import type { Routine } from '../db/types'
import { createRoutine, renameRoutine } from '../routines/mutations'
import './routines.css'

function parsePositiveId(value: string | undefined): number | null {
  if (value === undefined) return null
  const n = Number(value)
  return Number.isSafeInteger(n) && n > 0 ? n : null
}

function RoutineNameForm({ routine }: { routine?: Routine }) {
  const navigate = useNavigate()
  const [name, setName] = useState(routine?.name ?? '')
  const [error, setError] = useState('')
  const [saving, setSaving] = useState(false)
  const back = routine ? '/routines/' + routine.id : '/routines'

  return (
    <main className="screen rt">
      <Link className="rt-back" to={back}>
        <ArrowLeft size={18} aria-hidden="true" /> Routines
      </Link>
      <h1>{routine ? 'Rename routine' : 'New routine'}</h1>
      <form
        className="rt-form"
        onSubmit={async event => {
          event.preventDefault()
          if (saving || !name.trim()) return
          setSaving(true)
          try {
            if (routine) await renameRoutine(routine.id, name)
            const id = routine?.id ?? (await createRoutine(name))
            navigate('/routines/' + id, { replace: true })
          } catch (failure) {
            setError((failure as Error).message)
            setSaving(false)
          }
        }}
      >
        <label>
          Routine name
          <input
            autoFocus
            required
            value={name}
            onChange={e => setName(e.target.value)}
            placeholder="e.g. Upper / Lower"
          />
        </label>
        {error && (
          <p role="alert" className="rt-error">
            {error}
          </p>
        )}
        <button className="btn primary" disabled={saving || !name.trim()}>
          {routine ? 'Save name' : 'Create routine'}
          <ArrowUpRight size={18} aria-hidden="true" />
        </button>
      </form>
    </main>
  )
}

export default function Routines({ embedded = false }: { embedded?: boolean }) {
  const { id } = useParams()
  const { pathname } = useLocation()
  const gatorMode = useIsGatorMode()
  const isNew = !embedded && pathname.endsWith('/new')
  const isEdit = !embedded && pathname.endsWith('/edit')
  const editId = isEdit ? parsePositiveId(id) : null
  const invalidEditId = isEdit && editId === null

  const data = useLiveQuery(async () => {
    if (isNew || invalidEditId) return null
    const [routines, days] = await Promise.all([db.routines.toArray(), db.routine_days.toArray()])
    return {
      routines: routines.sort((a, b) => Number(b.isActive) - Number(a.isActive) || a.name.localeCompare(b.name)),
      days,
    }
  }, [isNew, invalidEditId])

  if (isNew) return <RoutineNameForm key="create" />

  if (isEdit) {
    if (invalidEditId) {
      return (
        <main className="screen rt">
          <section className="surface rt-empty">
            <Layers3 size={30} aria-hidden="true" />
            <h1>Routine unavailable</h1>
            <p className="rt-muted">This routine link is invalid or no longer exists.</p>
            <Link className="btn rt-outline" to="/routines">
              View routines
            </Link>
          </section>
        </main>
      )
    }
    if (!data) return <p role="status">Loading routine…</p>
    const routine = data.routines.find(row => row.id === editId)
    return routine ? (
      <RoutineNameForm key={routine.id} routine={routine} />
    ) : (
      <main className="screen rt">
        <section className="surface rt-empty">
          <Layers3 size={30} aria-hidden="true" />
          <h1>Routine unavailable</h1>
          <p className="rt-muted">This routine may have been deleted.</p>
          <Link className="btn rt-outline" to="/routines">
            View routines
          </Link>
        </section>
      </main>
    )
  }

  const Region = embedded ? 'section' : 'main'
  const atLimit = (data?.routines.length ?? 0) >= 10

  return (
    <Region className={embedded ? 'rt rt-embedded' : 'screen rt'}>
      {!embedded && (
        <Link className="rt-back" to="/library">
          <ArrowLeft size={18} aria-hidden="true" /> Library
        </Link>
      )}
      <div className="rt-head">
        <div className="rt-title-group">
          {embedded ? <h2>Routines</h2> : <h1>Routines</h1>}
          {!embedded && gatorMode && <SwoleGator pose="badge" size={30} interactive />}
        </div>
        <Link
          className="btn rt-outline"
          aria-disabled={atLimit}
          to="/routines/new"
          onClick={e => {
            if (atLimit) e.preventDefault()
          }}
        >
          <Plus size={16} aria-hidden="true" /> New routine
        </Link>
      </div>
      {!data ? (
        <p role="status">Loading routines…</p>
      ) : !data.routines.length ? (
        <div className="surface rt-empty">
          {gatorMode ? (
            <SwoleGator pose="hero" size={104} interactive />
          ) : (
            <Layers3 size={32} aria-hidden="true" />
          )}
          <h3>No routines yet</h3>
          <p className="rt-muted">Group workout days into a split so your next session is one tap away.</p>
          <Link className="btn primary" to="/routines/new">
            <Plus size={16} aria-hidden="true" /> Create routine
          </Link>
        </div>
      ) : (
        <div className="surface rt-list">
          {data.routines.map((routine, index) => {
            const days = data.days
              .filter(day => day.routineId === routine.id)
              .sort((a, b) => a.orderIndex - b.orderIndex)
            const next = days.find(day => day.orderIndex === routine.nextDayOrderIndex) ?? days[0]
            return (
              <Link
                key={routine.id}
                className="rt-list-link stagger-in"
                style={{ '--stagger-i': Math.min(index, 8) } as CSSProperties}
                to={'/routines/' + routine.id}
              >
                <span>
                  <strong>{routine.name}</strong>
                  <small>
                    {days.length} {days.length === 1 ? 'day' : 'days'}
                    {routine.isActive && next ? ' · Next ' + next.name : ''}
                  </small>
                </span>
                {routine.isActive && <span className="rt-status">Active</span>}
                <ChevronRight size={18} aria-hidden="true" />
              </Link>
            )
          })}
        </div>
      )}
    </Region>
  )
}

