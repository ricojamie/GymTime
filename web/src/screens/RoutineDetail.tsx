import { useEffect, useRef, useState, type CSSProperties, type ReactNode } from 'react'
import { Link, useNavigate, useParams } from 'react-router-dom'
import { useLiveQuery } from 'dexie-react-hooks'
import {
  ArrowDown,
  ArrowLeft,
  ArrowUp,
  Check,
  ChevronDown,
  Copy,
  Ellipsis,
  Flag,
  Layers3,
  Link2,
  Pencil,
  Play,
  Plus,
  Trash2,
} from 'lucide-react'
import { IronMascot } from '../components/mascot'
import { db } from '../db/database'
import { deleteRoutine, setActiveRoutine } from '../db/repositories'
import { BottomSheet } from '../components/primitives'
import { duplicateDay, duplicateRoutine, moveDay, planLabel, removeDay, setNextDay } from '../routines/mutations'
import { calculateRoutineStats } from '../domain/routineStats'
import { formatWeight, weightLabel } from '../domain/units'
import './routines.css'

const dateLabel = (epoch: number) =>
  new Date(epoch).toLocaleDateString(undefined, { month: 'short', day: 'numeric', year: 'numeric' })

function parsePositiveId(value: string | undefined): number | null {
  if (value === undefined) return null
  const n = Number(value)
  return Number.isSafeInteger(n) && n > 0 ? n : null
}

function Stats({ id }: { id: number }) {
  const data = useLiveQuery(async () => {
    const workouts = await db.workouts.where('routineId').equals(id).toArray()
    const workoutIds = workouts.map(w => w.id)
    const [sets, exercises, prefs] = await Promise.all([
      workoutIds.length > 0 ? db.sets.where('workoutId').anyOf(workoutIds).toArray() : Promise.resolve([]),
      db.exercises.toArray(),
      db.preferences.get('default'),
    ])
    return { summary: calculateRoutineStats({ workouts, sets, exercises }, id), unit: prefs?.preferredWeightUnit ?? 'lbs' }
  }, [id])

  if (!data) return <p role="status">Loading stats…</p>
  const { summary: s, unit } = data

  return (
    <section className="rt-stats">
      <dl className="surface rt-stat-grid stagger-in" style={{ '--stagger-i': 0 } as CSSProperties}>
        {[
          ['Workouts', s.timesCompleted],
          ['Avg. duration', s.avgDurationMinutes !== null ? Math.round(s.avgDurationMinutes) + ' min' : '—'],
          ['Total volume', formatWeight(s.totalVolume, unit) + ' ' + weightLabel(unit)],
          ['Workouts / wk · 28d', s.workoutsPerWeekRecent?.toFixed(1) ?? '—'],
        ].map(([name, value]) => (
          <div key={name}>
            <dt>{name}</dt>
            <dd>{value}</dd>
          </div>
        ))}
      </dl>
      <h2>Exercise trends</h2>
      {!s.exerciseTrends.length ? (
        <p className="rt-muted">No logged lifts in this routine yet.</p>
      ) : (
        <div className="surface rt-list stagger-in" style={{ '--stagger-i': 1 } as CSSProperties}>
          {s.exerciseTrends.map((lift, index) => (
            <div
              className="rt-line stagger-in"
              style={{ '--stagger-i': Math.min(index, 8) } as CSSProperties}
              key={lift.exerciseId}
            >
              <span>
                <strong>{lift.exerciseName}</strong>
                <small>
                  {lift.lastWeight != null && lift.lastReps != null
                    ? formatWeight(lift.lastWeight, unit) + ' ' + weightLabel(unit) + ' × ' + lift.lastReps
                    : lift.lastBestLabel}
                </small>
              </span>
              <span
                className={
                  'rt-stat-value' +
                  (lift.e1rmDelta != null && lift.e1rmDelta > 0
                    ? ' rt-delta-up'
                    : lift.e1rmDelta != null && lift.e1rmDelta < 0
                      ? ' rt-delta-down'
                      : '')
                }
              >
                {lift.e1rmDelta == null
                  ? '—'
                  : (lift.e1rmDelta >= 0 ? '+' : '−') + formatWeight(Math.abs(lift.e1rmDelta), unit) + ' ' + weightLabel(unit)}
                <small>Est. 1RM change</small>
              </span>
            </div>
          ))}
        </div>
      )}
    </section>
  )
}

type Menu = { kind: 'routine' | 'delete-routine' } | { kind: 'day' | 'delete-day'; dayId: number }

export default function RoutineDetail() {
  const validId = parsePositiveId(useParams().id)
  const navigate = useNavigate()
  const [tab, setTab] = useState<'days' | 'stats'>('days')
  const [tabFly, setTabFly] = useState<'none' | 'from-right' | 'from-left'>('none')
  const [expanded, setExpanded] = useState<number[]>([])
  const [menu, setMenu] = useState<Menu | null>(null)
  const [busy, setBusy] = useState(false)
  const [error, setError] = useState('')
  const [activePop, setActivePop] = useState<'idle' | 'on' | 'off'>('idle')
  const [nextPopId, setNextPopId] = useState<number | null>(null)
  const activeTimer = useRef<number | null>(null)
  const nextTimer = useRef<number | null>(null)

  useEffect(() => {
    return () => {
      if (activeTimer.current !== null) window.clearTimeout(activeTimer.current)
      if (nextTimer.current !== null) window.clearTimeout(nextTimer.current)
    }
  }, [])

  function triggerActivePop(nextState: 'on' | 'off') {
    if (activeTimer.current !== null) window.clearTimeout(activeTimer.current)
    setActivePop(nextState)
    activeTimer.current = window.setTimeout(() => {
      setActivePop('idle')
      activeTimer.current = null
    }, 480)
  }

  function triggerNextPop(dayId: number) {
    if (nextTimer.current !== null) window.clearTimeout(nextTimer.current)
    setNextPopId(dayId)
    nextTimer.current = window.setTimeout(() => {
      setNextPopId(null)
      nextTimer.current = null
    }, 480)
  }

  function switchTab(nextTab: 'days' | 'stats') {
    if (nextTab === tab) return
    setTabFly(nextTab === 'stats' ? 'from-right' : 'from-left')
    setTab(nextTab)
  }

  const data = useLiveQuery(async () => {
    if (validId === null) return null
    const [routine, days, exercises, workouts, active, count] = await Promise.all([
      db.routines.get(validId),
      db.routine_days.where('routineId').equals(validId).sortBy('orderIndex'),
      db.exercises.toArray(),
      db.workouts.where('routineId').equals(validId).toArray(),
      db.workouts.filter(w => w.endTime === null).first(),
      db.routines.count(),
    ])
    const dayIds = days.map(d => d.id)
    const rows =
      dayIds.length > 0 ? await db.routine_exercises.where('routineDayId').anyOf(dayIds).sortBy('orderIndex') : []
    return { routine, days, count, active, rows, workouts, names: new Map(exercises.map(e => [e.id, e.name])) }
  }, [validId])

  async function perform(action: () => Promise<unknown>) {
    if (busy) return
    setBusy(true)
    setError('')
    try {
      await action()
      setMenu(null)
    } catch (failure) {
      setError((failure as Error).message)
    } finally {
      setBusy(false)
    }
  }

  if (validId === null) {
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

  if (!data) return <main className="screen rt" role="status">Loading routine…</main>

  const { routine, days, names, workouts, rows, active } = data
  if (!routine) {
    return (
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

  const next = days.find(d => d.orderIndex === routine.nextDayOrderIndex) ?? days[0]
  const selected = menu && 'dayId' in menu ? days.find(d => d.id === menu.dayId) : undefined
  const base = '/routines/' + validId

  function action(label: string, icon: ReactNode, fn: () => void, disabled = false, danger = false) {
    return (
      <button
        type="button"
        className={'rt-action' + (danger ? ' rt-danger' : '')}
        onClick={fn}
        disabled={busy || disabled}
      >
        {icon}
        {label}
      </button>
    )
  }

  return (
    <main className="screen rt">
      <header className="rt-sticky-head">
        <Link className="rt-back" to="/routines">
          <ArrowLeft size={18} aria-hidden="true" /> Routines
        </Link>
        <div className="rt-head">
          <div className="rt-head-title" data-active-pop={activePop !== 'idle' ? activePop : undefined}>
            {routine.isActive && (
              <span className={'rt-status' + (activePop === 'on' ? ' rt-status-pop' : '')}>Active routine</span>
            )}
            <h1>{routine.name}</h1>
          </div>
          <button
            type="button"
            className="icon-button"
            aria-label="Routine options"
            onClick={() => {
              setError('')
              setMenu({ kind: 'routine' })
            }}
          >
            <Ellipsis />
          </button>
        </div>
      </header>
      <div className="rt-tabs" role="group" aria-label="Routine view" data-tab={tab}>
        {(['days', 'stats'] as const).map(t => (
          <button key={t} type="button" aria-pressed={tab === t} onClick={() => switchTab(t)}>
            {t === 'days' ? 'Workout days' : 'Stats & trends'}
          </button>
        ))}
      </div>
      {error && !menu && (
        <p role="alert" className="rt-error">
          {error}
        </p>
      )}
      <div key={tab} className="rt-tab-panel" data-fly={tabFly}>
        {tab === 'stats' ? (
          <Stats id={validId} />
        ) : (
          <div className="rt-tab-body">
            <div className="rt-head rt-section">
              <h2>Workout days</h2>
              <span className="rt-muted">{days.length} / 10</span>
            </div>
            {!days.length ? (
              <div className="surface rt-empty stagger-in" style={{ '--stagger-i': 0 } as CSSProperties}>
                <IronMascot pose="spotter" size={88} interactive />
                <p className="rt-muted">No workout days yet.</p>
              </div>
            ) : (
              <div className="rt-days">
                {days.map((day, index) => {
                  const items = rows.filter(row => row.routineDayId === day.id)
                  const sessions = workouts
                    .filter(w => w.routineDayId === day.id && w.endTime !== null)
                    .sort((a, b) => b.startTime - a.startTime)
                  const open = expanded.includes(day.id)
                  const isNext = next?.id === day.id
                  const isPoppingNext = nextPopId === day.id
                  return (
                    <section
                      className="surface rt-day stagger-in"
                      style={{ '--stagger-i': Math.min(index, 8) } as CSSProperties}
                      data-next={isNext ? 'true' : undefined}
                      data-next-pop={isPoppingNext ? 'true' : undefined}
                      key={day.id}
                    >
                      <div className="rt-head">
                        <span className="eyebrow">Day {String(index + 1).padStart(2, '0')}</span>
                        {isNext && (
                          <span className={'rt-status' + (isPoppingNext ? ' rt-status-pop' : '')}>Up next</span>
                        )}
                        <button
                          type="button"
                          className="icon-button"
                          onClick={() => {
                            setError('')
                            setMenu({ kind: 'day', dayId: day.id })
                          }}
                          aria-label={day.name + ' options'}
                        >
                          <Ellipsis size={20} />
                        </button>
                      </div>
                      <h3>{day.name}</h3>
                      <small>
                        {sessions.length} completed
                        {sessions[0] ? ' · Last ' + dateLabel(sessions[0].startTime) : ''}
                      </small>
                      <button
                        type="button"
                        className="rt-expand"
                        aria-expanded={open}
                        onClick={() => setExpanded(open ? expanded.filter(i => i !== day.id) : [...expanded, day.id])}
                      >
                        {items.length} {items.length === 1 ? 'exercise' : 'exercises'}
                        <ChevronDown size={16} className={open ? 'rt-up' : ''} aria-hidden="true" />
                      </button>
                      {items.length > 0 && (
                        <div
                          className="rt-day-collapse"
                          data-open={open ? 'true' : 'false'}
                          aria-hidden={!open}
                          inert={!open}
                        >
                          <div className="rt-day-collapse-inner">
                            <ol className="rt-prescription">
                              {items.map(item => (
                                <li key={item.id}>
                                  <span>
                                    {item.supersetGroupId && <Link2 size={14} aria-label="Superset" />}{' '}
                                    {names.get(item.exerciseId) ?? 'Deleted exercise'}
                                  </span>
                                  <b>{planLabel(item)}</b>
                                </li>
                              ))}
                            </ol>
                          </div>
                        </div>
                      )}
                      <button
                        type="button"
                        className={isNext ? 'btn primary rt-day-start' : 'btn rt-day-start'}
                        disabled={!!active || !items.length}
                        onClick={() => navigate(base + '/start?dayId=' + day.id)}
                      >
                        <Play size={15} aria-hidden="true" />
                        Start day
                      </button>
                    </section>
                  )
                })}
              </div>
            )}
            <button
              type="button"
              className="btn rt-outline rt-new-btn"
              disabled={days.length >= 10}
              onClick={() => navigate(base + '/days/new')}
            >
              <Plus size={16} aria-hidden="true" /> Add day
            </button>
          </div>
        )}
      </div>
      {menu && (
        <BottomSheet
          title={
            menu.kind === 'delete-routine'
              ? 'Delete routine?'
              : menu.kind === 'delete-day'
                ? 'Delete workout day?'
                : selected?.name ?? routine.name
          }
          onClose={() => {
            if (!busy) setMenu(null)
          }}
        >
          <div className="rt rt-menu">
            {menu.kind === 'routine' && (
              <>
                {action(routine.isActive ? 'Clear active routine' : 'Set active routine', <Flag />, () => {
                  const willBeActive = !routine.isActive
                  void perform(async () => {
                    await setActiveRoutine(willBeActive ? validId : null)
                    triggerActivePop(willBeActive ? 'on' : 'off')
                  })
                })}
                {action('Rename', <Pencil />, () => navigate(base + '/edit'))}
                {action(
                  'Duplicate routine',
                  <Copy />,
                  () => void perform(async () => navigate('/routines/' + (await duplicateRoutine(validId)))),
                  data.count >= 10,
                )}
                {action('Delete routine', <Trash2 />, () => setMenu({ kind: 'delete-routine' }), false, true)}
              </>
            )}
            {menu.kind === 'day' && selected && (
              <>
                {action('Edit day', <Pencil />, () => navigate(base + '/days/' + selected.id + '/edit'))}
                {action(
                  'Set as next',
                  <Check />,
                  () =>
                    void perform(async () => {
                      await setNextDay(validId, selected.id)
                      triggerNextPop(selected.id)
                    }),
                  next?.id === selected.id,
                )}
                {action(
                  'Duplicate day',
                  <Copy />,
                  () => void perform(() => duplicateDay(validId, selected.id)),
                  days.length >= 10,
                )}
                {action(
                  'Move up',
                  <ArrowUp />,
                  () => void perform(() => moveDay(validId, selected.id, -1)),
                  selected.id === days[0]?.id,
                )}
                {action(
                  'Move down',
                  <ArrowDown />,
                  () => void perform(() => moveDay(validId, selected.id, 1)),
                  selected.id === days.at(-1)?.id,
                )}
                {action(
                  'Delete day',
                  <Trash2 />,
                  () => setMenu({ kind: 'delete-day', dayId: selected.id }),
                  false,
                  true,
                )}
              </>
            )}
            {(menu.kind === 'delete-routine' || menu.kind === 'delete-day') && (
              <>
                <p>
                  Remove {selected?.name ?? routine.name}
                  {menu.kind === 'delete-routine' ? ' and its workout days' : ' from this routine'}? Logged workouts
                  stay in History.
                </p>
                {action('Cancel', <ArrowLeft />, () => setMenu(null))}
                {action(
                  menu.kind === 'delete-routine' ? 'Delete routine' : 'Delete day',
                  <Trash2 />,
                  () =>
                    void perform(async () => {
                      if (menu.kind === 'delete-routine') {
                        await deleteRoutine(validId)
                        navigate('/routines', { replace: true })
                      } else if (selected) {
                        await removeDay(validId, selected.id)
                      }
                    }),
                  false,
                  true,
                )}
              </>
            )}
            {error && (
              <p role="alert" className="rt-error">
                {error}
              </p>
            )}
          </div>
        </BottomSheet>
      )}
    </main>
  )
}

