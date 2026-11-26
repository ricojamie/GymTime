import { useEffect, useRef, useState, type CSSProperties } from 'react'
import { useLiveQuery } from 'dexie-react-hooks'
import { ArrowLeft, ArrowRight, Check, ChevronLeft, ChevronRight, Plus, Search, X } from 'lucide-react'
import { useNavigate, useSearchParams } from 'react-router-dom'
import { db } from '../db/database'
import { getActiveWorkout, startWorkout } from '../db/repositories'
import { useAdjacentReorder } from '../components/useAdjacentReorder'
import { SwoleGator, useIsGatorMode } from '../components/mascot'
import './workout.css'

type Draft = { ids: number[]; query: string; muscle: string }
const draftKey = 'ironlog.workout-builder'
const freshDraft: Draft = { ids: [], query: '', muscle: '' }

function readDraft(): Draft {
  try {
    const saved = JSON.parse(sessionStorage.getItem(draftKey) || 'null')
    if (!saved || !Array.isArray(saved.ids)) return freshDraft
    return {
      ids: [...new Set<number>(saved.ids.filter((id: unknown) => typeof id === 'number' && Number.isSafeInteger(id) && id > 0))],
      query: typeof saved.query === 'string' ? saved.query : '',
      muscle: typeof saved.muscle === 'string' ? saved.muscle : '',
    }
  } catch { return freshDraft }
}

export default function Builder() {
  const navigate = useNavigate()
  const gatorMode = useIsGatorMode()
  const [params, setParams] = useSearchParams()
  const [draft, setDraft] = useState(readDraft)
  const [busy, setBusy] = useState(false)
  const [error, setError] = useState('')
  const starting = useRef(false)
  const captureReorder = useAdjacentReorder()
  const data = useLiveQuery(() => db.transaction('r', db.exercises, db.workouts, async () => ({
    exercises: await db.exercises.toArray(),
    active: await getActiveWorkout(),
  })), [])
  const exercises = data?.exercises
  const active = data?.active
  const added = Number(params.get('createdExerciseId'))
  const catalog = exercises || []
  const lookup = new Map(catalog.map(ex => [ex.id, ex]))
  const selected = draft.ids.flatMap(id => lookup.has(id) ? [lookup.get(id)!] : [])
  const muscles = [...new Set(catalog.map(ex => ex.targetMuscle))].sort((a, b) => a.localeCompare(b))
  const tokens = draft.query.trim().toLocaleLowerCase().split(/\s+/).filter(Boolean)
  const visible = catalog.filter(ex =>
    (!draft.muscle || ex.targetMuscle === draft.muscle)
    && tokens.every(token => (ex.name + ' ' + ex.targetMuscle).toLocaleLowerCase().includes(token))
  ).sort((a, b) => a.name.localeCompare(b.name))

  useEffect(() => {
    if (!Number.isSafeInteger(added) || added < 1 || !exercises) return
    if (exercises.some(ex => ex.id === added)) {
      setDraft(prev => ({ ...prev, ids: prev.ids.includes(added) ? prev.ids : [...prev.ids, added], query: '', muscle: '' }))
    }
    setParams({}, { replace: true })
  }, [added, exercises, setParams])

  useEffect(() => {
    try { sessionStorage.setItem(draftKey, JSON.stringify(draft)) } catch { /* In-memory editing still works. */ }
  }, [draft])

  function toggle(id: number) {
    setDraft(prev => ({ ...prev, ids: prev.ids.includes(id) ? prev.ids.filter(existing => existing !== id) : [...prev.ids, id] }))
  }

  function move(id: number, delta: number, button: HTMLButtonElement) {
    captureReorder(button, delta)
    setDraft(prev => {
      const ids = prev.ids.filter(existing => lookup.has(existing))
      const from = ids.indexOf(id), to = from + delta
      if (from < 0 || to < 0 || to >= ids.length) return prev
      ids.splice(to, 0, ids.splice(from, 1)[0])
      return { ...prev, ids }
    })
  }

  async function begin() {
    if (starting.current) return
    if (active) { navigate('/workout/active'); return }
    if (!selected.length) return
    starting.current = true
    setBusy(true)
    setError('')
    try {
      const id = await startWorkout({ exerciseIds: selected.map(ex => ex.id) })
      const plan = await db.workout_exercise_instances.where('workoutId').equals(id).sortBy('orderIndex')
      // Another tab may already have claimed the active workout.
      const matchesDraft = plan.length === selected.length && plan.every((item, i) => item.exerciseId === selected[i].id)
      if (matchesDraft) {
        try { sessionStorage.removeItem(draftKey) } catch { /* Browser storage may be disabled. */ }
        navigate('/workout/' + id + '/exercise/' + selected[0].id, { replace: true })
      } else navigate('/workout/active', { replace: true })
    } catch (cause) {
      setError(cause instanceof Error ? cause.message : 'Could not start workout. Try again.')
    } finally { starting.current = false; setBusy(false) }
  }

  function createExercise() {
    const search = new URLSearchParams({ returnTo: '/workout/builder' })
    if (draft.query.trim()) search.set('name', draft.query.trim())
    navigate('/exercises/new?' + search)
  }

  return (
    <div className="screen wk-screen">
      <header className="wk-heading">
        <button className="icon-button" aria-label="Back to home" onClick={() => navigate('/')}><ArrowLeft /></button>
        <h1>Plan a workout</h1>
        {gatorMode && <SwoleGator pose="badge" size={32} label="" className="wk-head-gator" />}
      </header>
      <div className="wk-builder-grid">
        <section className="wk-order stagger-in" style={{ '--stagger-i': 0 } as CSSProperties} aria-labelledby="wk-order-title">
          <div className="wk-section-title"><h2 id="wk-order-title">Workout order</h2><span>{selected.length}</span></div>
          {selected.length === 0
            ? <div className="wk-order-empty">
                {gatorMode && <SwoleGator pose="spotter" size={44} label="" />}
                <p className="wk-empty">No exercises selected</p>
              </div>
            : <ol className="wk-strip">
                {selected.map((ex, i) => <li key={ex.id} className="surface" data-order-item>
                  <div className="wk-item-head"><span className="wk-index">{String(i + 1).padStart(2, '0')}</span><button className="icon-button" aria-label={'Remove ' + ex.name} onClick={() => toggle(ex.id)}><X size={17} /></button></div>
                  <strong>{ex.name}</strong>
                  <div className="wk-reorder">
                    <button className="icon-button" aria-label={'Move ' + ex.name + ' earlier'} disabled={i === 0} onClick={e => move(ex.id, -1, e.currentTarget)}><ChevronLeft size={18} /></button>
                    <button className="icon-button" aria-label={'Move ' + ex.name + ' later'} disabled={i === selected.length - 1} onClick={e => move(ex.id, 1, e.currentTarget)}><ChevronRight size={18} /></button>
                  </div>
                </li>)}
              </ol>}
        </section>
        <section className="wk-catalog stagger-in" style={{ '--stagger-i': 1 } as CSSProperties} aria-labelledby="wk-catalog-title">
          <div className="wk-section-title"><h2 id="wk-catalog-title">Exercises</h2><span>{visible.length}</span></div>
          <div className="wk-search">
            <Search size={18} aria-hidden="true" />
            <input type="search" value={draft.query} aria-label="Search exercises" placeholder="Search exercises" onChange={event => setDraft({ ...draft, query: event.target.value })} />
            {draft.query && <button type="button" className="icon-button wk-search-clear" aria-label="Clear search" onClick={() => setDraft(prev => ({ ...prev, query: '' }))}><X size={16} /></button>}
          </div>
          <div className="wk-filters" role="group" aria-label="Filter by muscle">
            {['', ...muscles].map(muscle => <button key={muscle} aria-pressed={draft.muscle === muscle} onClick={() => setDraft({ ...draft, muscle })}>{muscle || 'All muscles'}</button>)}
          </div>
          <div className="wk-catalog-list">
            {visible.map(ex => {
              const selectedIndex = selected.findIndex(item => item.id === ex.id)
              return <button key={ex.id} className="wk-ex-option" aria-pressed={selectedIndex >= 0} onClick={() => toggle(ex.id)}>
                <span className="wk-grow"><strong>{ex.name}</strong><small>{ex.targetMuscle}</small></span>
                <span className="wk-choice-mark">{selectedIndex >= 0 ? <span className="wk-selected"><Check size={16} />{String(selectedIndex + 1).padStart(2, '0')}</span> : <Plus size={18} />}</span>
              </button>
            })}
            {exercises && !visible.length && <div className="wk-order-empty">
              {gatorMode && <SwoleGator pose="spotter" size={50} label="" />}
              <p className="wk-empty">No matching exercises</p>
            </div>}
            <button className="wk-create" onClick={createExercise}><Plus size={18} /> Create exercise</button>
          </div>
        </section>
      </div>
      <footer className="wk-actions">
        {error && <p className="wk-error" role="alert">{error}</p>}
        <button className="btn primary" disabled={busy || (!active && !selected.length)} onClick={begin}>
          {active ? 'Resume active workout' : 'Start workout · ' + selected.length + (selected.length === 1 ? ' exercise' : ' exercises')}<ArrowRight size={19} />
        </button>
      </footer>
    </div>
  )
}
