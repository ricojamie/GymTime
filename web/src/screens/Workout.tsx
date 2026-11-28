import { useEffect, useRef, useState, type CSSProperties } from 'react'
import { useLiveQuery } from 'dexie-react-hooks'
import { ArrowLeft, ArrowRight, ArrowRightLeft, Check, ChevronRight, Link2, Minus, MoreHorizontal, Plus } from 'lucide-react'
import { useNavigate, useSearchParams } from 'react-router-dom'
import { db } from '../db/database'
import { finishWorkout, getActiveWorkout, skipWorkoutExercise } from '../db/repositories'
import { normalizeMuscleName } from '../db/types'
import type { WorkoutExerciseInstance, WorkoutSet } from '../db/types'
import { AnimatedNumber, BottomSheet, CalibrationBar } from '../components/primitives'
import { IronMascot } from '../components/mascot'
import { stopRestTimer } from '../store/restTimer'
import './workout.css'

async function readSession() {
  return db.transaction('r', db.workouts, db.sets, db.exercises, db.workout_exercise_instances, async () => {
    const active = await getActiveWorkout()
    if (!active) return null
    const [plan, sets] = await Promise.all([
      db.workout_exercise_instances.where('workoutId').equals(active.id).sortBy('orderIndex'),
      db.sets.where('workoutId').equals(active.id).sortBy('timestamp'),
    ])
    const groups = new Map<number, WorkoutSet[]>()
    for (const set of sets) {
      const group = groups.get(set.exerciseId)
      if (group) group.push(set)
      else groups.set(set.exerciseId, [set])
    }
    const items: { id: number; instance?: WorkoutExerciseInstance }[] =
      plan.filter(item => !item.isSkipped).map(instance => ({ id: instance.exerciseId, instance }))
    const plannedIds = new Set(items.map(item => item.id))
    for (const id of groups.keys()) if (!plannedIds.has(id)) items.push({ id })
    const catalog = await db.exercises.bulkGet(items.map(item => item.id))
    const rows = items.flatMap(({ id, instance }, index) => {
      const exercise = catalog[index]
      if (!exercise) return []
      const logged = groups.get(id) || []
      const completed = logged.filter(set => set.isComplete)
      const isWarmupExercise = normalizeMuscleName(exercise.targetMuscle) === 'Warmups'
      const working = isWarmupExercise ? 0 : completed.filter(set => !set.isWarmup).length
      return [{
        exercise, instance, working, warmups: completed.length - working, started: logged.length > 0,
        lastLogged: completed.at(-1)?.timestamp || 0, target: isWarmupExercise ? 0 : (instance?.plannedSets || 0),
        superset: instance?.supersetGroupId || logged.find(set => set.supersetGroupId)?.supersetGroupId || null,
      }]
    })
    return { active, rows }
  })
}

function SessionClock({ start }: { start: number }) {
  const [now, setNow] = useState(Date.now)
  useEffect(() => {
    const tick = () => setNow(Date.now())
    let timer: number | undefined
    const sync = () => {
      if (timer !== undefined) { window.clearInterval(timer); timer = undefined }
      if (document.visibilityState !== 'hidden') {
        tick()
        timer = window.setInterval(tick, 1000)
      }
    }
    sync()
    document.addEventListener('visibilitychange', sync)
    return () => {
      if (timer !== undefined) window.clearInterval(timer)
      document.removeEventListener('visibilitychange', sync)
    }
  }, [])
  const seconds = Math.max(0, Math.floor((now - start) / 1000))
  const minutes = Math.floor(seconds / 60) % 60
  const hours = Math.floor(seconds / 3600)
  const elapsed = [hours, minutes, seconds % 60].map(n => String(n).padStart(2, '0')).join(':')
  return <time role="timer" aria-label="Workout elapsed time" dateTime={'PT' + seconds + 'S'}>{elapsed}</time>
}

export default function Workout() {
  const session = useLiveQuery(readSession, [])
  const navigate = useNavigate()
  const [search, setSearch] = useSearchParams()
  const [finish, setFinish] = useState(false)
  const [editId, setEditId] = useState<number | null>(null)
  const [busy, setBusy] = useState(false)
  const [error, setError] = useState('')

  const locked = useRef(false)
  const dialogOpen = finish || editId !== null
  useEffect(() => {
    if (session && search.has('finish')) {
      setFinish(true)
      setSearch({}, { replace: true })
    }
  }, [search, session, setSearch])

  if (session === undefined) return <div className="screen wk-screen" aria-busy="true" aria-label="Loading workout" />
  if (!session) return <div className="screen wk-screen">
    <header className="wk-heading">
      <button className="icon-button" aria-label="Back to home" onClick={() => navigate('/')}><ArrowLeft /></button>
      <h1>Workout</h1>
      <IronMascot pose="badge" size={32} label="" className="wk-head-gator" />
    </header>
    <section className="wk-empty-card surface stagger-in">
      <IronMascot pose="spotter" size={88} interactive />
      <p className="wk-empty">No active workout</p>
      <button className="btn primary" onClick={() => navigate('/exercises/select?workoutMode=true')}>Start workout<ArrowRight size={18} /></button>
    </section>
  </div>

  const { active, rows } = session
  const name = active.name?.trim() || active.routineDayNameSnapshot?.trim() || "Today's workout"
  const targets = rows.filter(row => row.target > 0)
  const unresolved = targets.filter(row => row.working < row.target)
  const first = unresolved[0]
  const nextTarget = first?.superset
    ? unresolved.filter(row => row.superset === first.superset).reduce((best, row) => row.working < best.working ? row : best, first)
    : first
  const last = rows.reduce<typeof rows[number] | undefined>((prev, row) => row.lastLogged > (prev?.lastLogged || 0) ? row : prev, undefined)
  const next = nextTarget || rows.find(row => !row.started) || last || rows[0]
  const totals = {
    working: rows.reduce((sum, row) => sum + row.working, 0),
    warmups: rows.reduce((sum, row) => sum + row.warmups, 0),
    exercised: rows.filter(row => row.working > 0).length,
    planned: targets.reduce((sum, row) => sum + row.target, 0),
    done: targets.reduce((sum, row) => sum + Math.min(row.working, row.target), 0),
  }
  const editing = rows.find(row => row.instance?.id === editId)
  const addRoute = '/exercises/select?workoutId=' + active.id
  function openExercise(id: number) { navigate('/workout/' + active.id + '/exercise/' + id) }
  function closeDialog() {
    if (locked.current) return
    setEditId(null); setFinish(false); setError('')
  }

  async function commit(isFinish: boolean) {
    if (locked.current) return
    locked.current = true; setBusy(true); setError('')
    try {
      if (isFinish) {
        await finishWorkout(active.id)
        stopRestTimer()
        navigate('/summary/' + active.id, { replace: true, state: { justFinished: active.id } })
      } else if (editId !== null) {
        await skipWorkoutExercise(editId)
        setEditId(null)
      }
    } catch (cause) {
      setError(cause instanceof Error ? cause.message : 'Could not save this change. Try again.')
    } finally { locked.current = false; setBusy(false) }
  }

  return <div className="screen wk-screen">
    <header className="wk-heading wk-head">
      <button className="icon-button" aria-label="Back to home" onClick={() => navigate('/')}><ArrowLeft /></button>
      <div className="wk-head-title">
        <span className="wk-led wk-head-led" aria-hidden="true" />
        <h1>{name}</h1>
      </div>
      <IronMascot pose="badge" size={32} label="" className="wk-head-gator" />
    </header>
    <div className="wk-overview-grid">
      <section className="wk-readout surface stagger-in" style={{ '--stagger-i': 0 } as CSSProperties} aria-label="Session metrics">
        <div className="wk-timer-label"><span className="wk-led" aria-hidden="true" />Session elapsed</div>
        <SessionClock start={active.startTime} />
        <dl className="wk-metrics">
          <div><dt>Working sets</dt><dd><AnimatedNumber value={totals.working} /></dd></div>
          <div><dt>Exercises</dt><dd><AnimatedNumber value={totals.exercised} /></dd></div>
          <div><dt>{targets.length ? 'Targets met' : 'Warm-ups'}</dt><dd>{targets.length ? (targets.length - unresolved.length) + ' / ' + targets.length : <AnimatedNumber value={totals.warmups} />}</dd></div>
        </dl>
        {totals.planned > 0 && <div className="wk-calibration">
          <div className="wk-cal-label"><span>Plan progress</span><strong>{totals.done} / {totals.planned} sets</strong></div>
          <CalibrationBar label="Completed planned sets" value={totals.done} max={totals.planned} ticks={16} />
        </div>}
      </section>
      <div className="wk-flow">
        {next && <button className="wk-next surface hover-lift stagger-in" style={{ '--stagger-i': 1 } as CSSProperties} onClick={() => openExercise(next.exercise.id)}>
          <span><small>{next.started ? 'Continue' : 'Up next'}</small><strong>{next.exercise.name}</strong>
          <span className="wk-meta">{next.exercise.targetMuscle}{next.target > 0 && ' · ' + next.working + ' / ' + next.target + ' sets'}</span></span>
          <span className="wk-go"><ArrowRight size={23} className="wk-go-arrow" /></span>
        </button>}
        <section aria-labelledby="wk-plan-title">
          <div className="wk-section-title"><h2 id="wk-plan-title">{active.routineNameSnapshot || 'Session exercises'}</h2><span>{rows.length}</span></div>
          {rows.length === 0 && <div className="wk-order-empty">
            <IronMascot pose="spotter" size={44} label="" />
            <p className="wk-empty">No exercises added</p>
          </div>}
          <ol className="wk-timeline">
            {rows.map((row, i) => {
              const ex = row.exercise
              const done = row.target > 0 && row.working >= row.target
              return <li key={row.instance?.id ?? 'exercise-' + ex.id} className="wk-row-item stagger-in" style={{ '--stagger-i': Math.min(i, 10) } as CSSProperties}>
                <span key={done ? 'done' : 'num'} className={'wk-ticket' + (done ? ' done' : '')}>{done ? <Check size={14} aria-label="Target met" /> : String(i + 1).padStart(2, '0')}</span>
                <button className="wk-timeline-row" onClick={() => openExercise(ex.id)}>
                  <span className="wk-grow"><strong>{ex.name}</strong><small>
                    {ex.targetMuscle} · {row.target > 0
                      ? <span key={row.working + '/' + row.target} className={'wk-set-badge' + (done ? ' is-done' : row.working > 0 ? ' is-active' : '')}>{row.working} / {row.target} sets</span>
                      : <span key={row.working} className={'wk-set-badge' + (row.working > 0 ? ' is-active' : '')}>{row.working} {row.working === 1 ? 'set' : 'sets'}</span>}
                    {row.warmups > 0 && ' · ' + row.warmups + (row.warmups === 1 ? ' warm-up' : ' warm-ups')}
                  </small>{row.superset && <span className="wk-link"><Link2 size={14} /> Superset</span>}</span>
                  <ChevronRight size={17} className="wk-row-chev" aria-hidden="true" />
                </button>
                {row.instance && !row.started && <button className="icon-button wk-row-more" aria-label={'Options for ' + ex.name} onClick={() => { setError(''); setEditId(row.instance!.id) }}><MoreHorizontal size={20} /></button>}
              </li>
            })}
          </ol>
        </section>
      </div>
    </div>
    <footer className="wk-actions">
      <div className="wk-action-pair">
        <button className="btn wk-btn-add" onClick={() => navigate(addRoute)}><Plus size={18} className="wk-btn-plus" />Add exercise</button>
        <button className="btn primary wk-btn-finish" onClick={() => { setError(''); setFinish(true) }}>Finish<Check size={18} className="wk-btn-check" /></button>
      </div>
    </footer>
    {dialogOpen && <BottomSheet title={finish ? 'Finish workout?' : editing?.exercise.name || 'Exercise'} onClose={closeDialog}>
      <div className="wk-dialog-body">
        {finish ? <>
          <div className="wk-finish-gator"><IronMascot pose="stamp" size={68} label="" /></div>
          <p>{totals.working} working {totals.working === 1 ? 'set' : 'sets'} · {totals.exercised} {totals.exercised === 1 ? 'exercise' : 'exercises'}</p>
          <button className="btn primary" disabled={busy} onClick={() => commit(true)}>Finish workout</button>
        </> : editing?.instance && <>
          <button className="btn" onClick={() => navigate(addRoute + '&mode=swap&instanceId=' + editId)}><ArrowRightLeft size={18} />Swap exercise</button>
          <button className="btn" disabled={busy} onClick={() => commit(false)}><Minus size={18} />Remove from today</button>
        </>}
        {error && <p role="alert" className="wk-error">{error}</p>}
        <button className="btn" disabled={busy} onClick={closeDialog}>Cancel</button>
      </div>
    </BottomSheet>}
  </div>
}
