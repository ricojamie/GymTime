import { useState, type CSSProperties } from 'react'
import { Link, useNavigate, useParams, useSearchParams } from 'react-router-dom'
import { useLiveQuery } from 'dexie-react-hooks'
import { ArrowLeft, ArrowRight, Layers3, Link2, Play } from 'lucide-react'
import { SwoleGator, useIsGatorMode } from '../components/mascot'
import { db } from '../db/database'
import { startWorkout } from '../db/repositories'
import { planBlocks, planLabel } from '../routines/mutations'
import './routines.css'

function parsePositiveId(value: string | null | undefined): number | null {
  if (value === null || value === undefined || value === '') return null
  const n = Number(value)
  return Number.isSafeInteger(n) && n > 0 ? n : null
}

export default function RoutineStart() {
  const validId = parsePositiveId(useParams().id)
  const [search] = useSearchParams()
  const requestedRaw = search.get('dayId')
  const hasRequested = requestedRaw !== null
  const validRequestedId = hasRequested ? parsePositiveId(requestedRaw) : null
  const invalidParams = validId === null || (hasRequested && validRequestedId === null)
  const navigate = useNavigate()
  const gatorMode = useIsGatorMode()
  const [error, setError] = useState('')
  const [busy, setBusy] = useState(false)

  const data = useLiveQuery(async () => {
    if (invalidParams || validId === null) return null
    const [routine, days, active] = await Promise.all([
      db.routines.get(validId),
      db.routine_days.where('routineId').equals(validId).sortBy('orderIndex'),
      db.workouts.filter(w => w.endTime === null).first(),
    ])
    const day =
      validRequestedId !== null
        ? days.find(d => d.id === validRequestedId)
        : days.find(d => d.orderIndex === routine?.nextDayOrderIndex) ?? days[0]
    const rows = day ? await db.routine_exercises.where('routineDayId').equals(day.id).sortBy('orderIndex') : []
    const exerciseIds = rows.map(row => row.exerciseId)
    const exercises = exerciseIds.length > 0 ? await db.exercises.bulkGet(exerciseIds) : []
    return {
      routine,
      day,
      active,
      rows,
      days,
      names: new Map(exercises.flatMap(ex => (ex ? [[ex.id, ex] as const] : []))),
    }
  }, [invalidParams, validId, validRequestedId])

  if (invalidParams) {
    return (
      <main className="screen rt">
        <section className="surface rt-empty">
          <Layers3 size={30} aria-hidden="true" />
          <h1>Day unavailable</h1>
          <p className="rt-muted">This workout day link is invalid or no longer exists.</p>
          <Link className="btn rt-outline" to={validId !== null ? '/routines/' + validId : '/routines'}>
            {validId !== null ? 'View routine' : 'View routines'}
          </Link>
        </section>
      </main>
    )
  }

  if (!data) return <main className="screen rt" role="status">Loading plan…</main>

  const { routine, day, active, rows, names, days } = data
  const back = '/routines/' + validId
  if (!routine || !day) {
    return (
      <main className="screen rt">
        <section className="surface rt-empty">
          <Layers3 size={30} aria-hidden="true" />
          <h1>Day unavailable</h1>
          <p className="rt-muted">This workout day may have been deleted.</p>
          <Link className="btn rt-outline" to={routine ? back : '/routines'}>
            View routine
          </Link>
        </section>
      </main>
    )
  }

  return (
    <main className="screen rt">
      <Link className="rt-back" to={back}>
        <ArrowLeft size={18} aria-hidden="true" />
        {routine.name}
      </Link>
      <div className="rt-start-hero">
        <div className="rt-start-title">
          <span className="eyebrow">
            Day {days.findIndex(d => d.id === day.id) + 1} of {days.length}
          </span>
          <h1>{day.name}</h1>
        </div>
        {gatorMode && <SwoleGator pose="spotter" size={76} interactive className="rt-start-gator" />}
      </div>
      <dl className="surface rt-stat-grid stagger-in" style={{ '--stagger-i': 0 } as CSSProperties}>
        <div>
          <dt>Exercises</dt>
          <dd>{rows.length}</dd>
        </div>
        <div>
          <dt>Planned sets</dt>
          <dd>{rows.reduce((total, row) => total + row.targetSets, 0)}</dd>
        </div>
      </dl>
      <h2>Day plan</h2>
      <div className="surface rt-list stagger-in" style={{ '--stagger-i': 1 } as CSSProperties}>
        {planBlocks(rows).map((block, index) => (
          <div className="rt-block" data-superset={block.length > 1 ? 'true' : undefined} key={block[0].id}>
            <span className="eyebrow">
              {block.length > 1 ? 'Superset ' : 'Exercise '}
              {String(index + 1).padStart(2, '0')}
            </span>
            {block.map(row => (
              <div className="rt-line" key={row.id}>
                <span>
                  <strong>{names.get(row.exerciseId)?.name ?? 'Deleted exercise'}</strong>
                  <small>
                    {names.get(row.exerciseId)?.targetMuscle} ·{' '}
                    {row.targetRestSeconds ?? names.get(row.exerciseId)?.defaultRestSeconds ?? 90}s rest
                  </small>
                  {row.notes && <small>{row.notes}</small>}
                </span>
                <span className="rt-plan-badge">
                  {block.length > 1 && <Link2 size={14} aria-hidden="true" />} {planLabel(row)}
                </span>
              </div>
            ))}
          </div>
        ))}
      </div>
      {error && (
        <p role="alert" className="rt-error">
          {error}
        </p>
      )}
      {active ? (
        <Link to="/workout/active" className="btn primary">
          Resume current workout
          <ArrowRight size={18} aria-hidden="true" />
        </Link>
      ) : (
        <button
          type="button"
          className="btn primary"
          disabled={busy || !rows.length}
          onClick={async () => {
            if (busy) return
            setBusy(true)
            try {
              await startWorkout({ routineDayId: day.id })
              navigate('/workout/active', { replace: true })
            } catch (failure) {
              setError((failure as Error).message)
              setBusy(false)
            }
          }}
        >
          <Play size={18} aria-hidden="true" />
          {busy ? 'Starting…' : 'Start workout'}
        </button>
      )}
    </main>
  )
}

