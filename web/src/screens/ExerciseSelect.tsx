import { useEffect, useMemo, useRef, useState, type CSSProperties } from 'react'
import { useLiveQuery } from 'dexie-react-hooks'
import { useLocation, useNavigate, useSearchParams } from 'react-router-dom'
import { ArrowLeft, ArrowUpDown, Check, ChevronRight, Ellipsis, Link2, Pencil, Plus, Search, Star, Trash2, X } from 'lucide-react'
import { db } from '../db/database'
import { deleteExercise, ensureWorkoutExercise, getActiveWorkout, groupWorkoutExercises, startWorkout, swapWorkoutExercise, toggleStar } from '../db/repositories'
import type { Exercise, LogType } from '../db/types'
import { BottomSheet } from '../components/primitives'
import { SwoleGator, useIsGatorMode } from '../components/mascot'
import './library.css'

export const typeLabels: Record<LogType, string> = {
  WEIGHT_REPS: 'Weight + reps', REPS_ONLY: 'Reps only', DURATION: 'Time only',
  WEIGHT_DISTANCE: 'Weight + distance', DISTANCE_TIME: 'Distance + time',
  WEIGHT_TIME: 'Weight + time', CALORIES_TIME: 'Calories + time',
}
const sorts = ['Recently used', 'A–Z', 'All-time sets', '90-day sets'] as const
type Sort = typeof sorts[number]
type Mode = 'browse' | 'swap' | 'build_superset' | 'add_to_superset'
const normalize = (value: string) => value.toLowerCase().normalize('NFKD').replace(/[\u0300-\u036f]/g, '').replace(/[^\p{L}\p{N}]+/gu, ' ').trim()
const isPositiveSafeId = (n: number) => Number.isSafeInteger(n) && n > 0
const positiveId = (value: string | null) => { const n = Number(value); return isPositiveSafeId(n) ? n : 0 }
const toMessage = (err: unknown) => err instanceof Error ? err.message : 'Could not save this change. Try again.'

function readList(key: string): unknown[] {
  try { const value: unknown = JSON.parse(sessionStorage.getItem(key) || '[]'); return Array.isArray(value) ? value : [] }
  catch { return [] }
}
function saveList(key: string, list: unknown[]) {
  try { sessionStorage.setItem(key, JSON.stringify(list)) } catch { /* Filters are optional when storage is full. */ }
}

export default function ExerciseSelect({ embedded = false }: { embedded?: boolean }) {
  const navigate = useNavigate()
  const location = useLocation()
  const gatorMode = useIsGatorMode()
  const [params, setParams] = useSearchParams()
  const rawMode = params.get('mode')?.toLowerCase()
  const mode: Mode = rawMode === 'swap' || rawMode === 'build_superset' || rawMode === 'add_to_superset' ? rawMode : 'browse'
  const explicitWorkout = positiveId(params.get('workoutId'))
  const originExercise = positiveId(params.get('exerciseId'))
  const swapInstance = positiveId(params.get('instanceId'))
  const active = useLiveQuery(() => getActiveWorkout(), [])
  const workoutId = explicitWorkout || active?.id || 0
  const exercises = useLiveQuery(() => db.exercises.toArray(), [])
  const muscleGroups = useLiveQuery(() => db.muscle_groups.orderBy('name').toArray(), [])
  const instances = useLiveQuery(
    async () => (isPositiveSafeId(workoutId)
      ? db.workout_exercise_instances.where('workoutId').equals(workoutId).toArray()
      : []),
    [workoutId],
    [],
  )
  const stats = useLiveQuery(async () => {
    const byId = new Map<number, { all: number; recent: number; last: number }>()
    const cutoff = Date.now() - 90 * 86400000
    // Bounded getAll requests keep large Android imports off the slow per-row cursor path.
    let lastId = -1
    for (;;) {
      const batch = await db.sets.where('id').above(lastId).limit(1024).toArray()
      for (const set of batch) {
        if (!set.isComplete || set.isWarmup) continue
        const values = byId.get(set.exerciseId) ?? { all: 0, recent: 0, last: 0 }
        values.all++
        if (set.timestamp >= cutoff) values.recent++
        values.last = Math.max(values.last, set.timestamp)
        byId.set(set.exerciseId, values)
      }
      if (batch.length < 1024) break
      lastId = batch[batch.length - 1].id
    }
    return byId
  }, [])
  const group = instances.find(i => i.exerciseId === originExercise && !i.isSkipped)?.supersetGroupId
  const currentMembers = useMemo(() => instances.filter(i => group && i.supersetGroupId === group && !i.isSkipped).sort((a, b) => a.supersetOrderIndex - b.supersetOrderIndex).map(i => i.exerciseId), [instances, group])
  const sessionKey = 'ironlog:exercise-filters:' + (workoutId || 'start')
  const [muscles, setMuscles] = useState<string[]>([])
  useEffect(() => {
    setMuscles(embedded ? [] : readList(sessionKey).filter((x): x is string => typeof x === 'string'))
  }, [embedded, sessionKey])
  const pickKey = 'ironlog:superset:' + workoutId + ':' + originExercise
  const [picked, setPicked] = useState<number[]>(() => {
    if (mode !== 'build_superset') return []
    const stored = readList(pickKey).filter((x): x is number => typeof x === 'number' && isPositiveSafeId(x)).slice(0, 10)
    return stored.length ? stored : originExercise ? [originExercise] : []
  })
  const handledCreate = useRef(0)
  const [query, setQuery] = useState('')
  const [sort, setSort] = useState<Sort>(sorts[0])
  const [filterBurst, setFilterBurst] = useState(0)
  const [sortOpen, setSortOpen] = useState(false)
  const [actions, setActions] = useState<Exercise | null>(null)
  const [detail, setDetail] = useState<Exercise | null>(null)
  const [deleting, setDeleting] = useState<Exercise | null>(null)
  const [error, setError] = useState('')
  const [busy, setBusy] = useState(false)
  const lock = useRef(false)
  const originInstance = instances.find(i => i.id === swapInstance)
  const returnPath = location.pathname + location.search
  const selectedIds = picked.filter(id => exercises?.some(e => e.id === id))
  const multi = mode === 'build_superset'
  const heading = mode === 'swap' ? 'Swap exercise' : multi ? 'Build superset' : mode === 'add_to_superset' ? 'Add to superset' : 'Select exercise'

  const results = useMemo(() => {
    const terms = normalize(query).split(' ').filter(Boolean)
    const list = (exercises ?? []).filter(ex => {
      if (muscles.length && !muscles.includes(ex.targetMuscle)) return false
      if (mode === 'swap' && originInstance?.exerciseId === ex.id) return false
      if (mode === 'add_to_superset' && currentMembers.includes(ex.id)) return false
      const text = normalize([ex.name, ex.targetMuscle, typeLabels[ex.logType]].join(' '))
      return terms.every(t => text.includes(t) || text.replaceAll(' ', '').includes(t))
    })
    return list.sort((a, b) => {
      const field = sort === 'All-time sets' ? 'all' : sort === '90-day sets' ? 'recent' : 'last'
      const diff = sort === 'A–Z' ? 0 : (stats?.get(b.id)?.[field] ?? 0) - (stats?.get(a.id)?.[field] ?? 0)
      return diff || a.name.localeCompare(b.name) || a.id - b.id
    })
  }, [query, muscles, exercises, mode, originInstance, currentMembers, sort, stats])

  function changeMuscles(next: string[]) { setMuscles(next); setFilterBurst(n => n + 1); if (!embedded) saveList(sessionKey, next) }
  function changePick(ids: number[]) { setPicked(ids); saveList(pickKey, ids) }
  function setMode(next: 'browse' | 'build_superset') {
    const copy = new URLSearchParams(params)
    if (next === 'browse') copy.delete('mode'); else copy.set('mode', next)
    changePick(originExercise ? [originExercise] : [])
    setError(''); setParams(copy)
  }
  async function run(action: () => Promise<void>) {
    if (lock.current) return
    lock.current = true; setBusy(true); setError('')
    try { await action() } catch (e) { setError(toMessage(e)) }
    finally { lock.current = false; setBusy(false) }
  }
  async function getWorkout() {
    if (!isPositiveSafeId(explicitWorkout)) {
      const id = await startWorkout()
      if (!workoutId && !embedded) {
        saveList('ironlog:exercise-filters:' + id, muscles)
        saveList(sessionKey, [])
      }
      return id
    }
    const workout = await db.workouts.get(explicitWorkout)
    if (!workout || workout.endTime !== null) throw new Error('This workout has ended. Go back to your active workout.')
    return workout.id
  }
  function startExercise(ex: Exercise) {
    void run(async () => {
      const id = await getWorkout()
      await ensureWorkoutExercise(id, ex.id)
      navigate('/workout/' + id + '/exercise/' + ex.id)
    })
  }
  function choose(ex: Exercise) {
    if (embedded) { setDetail(ex); return }
    if (multi) {
      setError('')
      if (picked.includes(ex.id)) changePick(picked.filter(id => id !== ex.id))
      else if (selectedIds.length < 10) changePick([...selectedIds, ex.id])
      else setError('A superset can contain up to 10 exercises.')
      return
    }
    if (mode === 'swap') {
      void run(async () => {
        await getWorkout()
        if (!originInstance) throw new Error('Select a planned exercise to swap.')
        await swapWorkoutExercise(swapInstance, ex.id); navigate('/workout/active', { replace: true })
      }); return
    }
    if (mode === 'add_to_superset') {
      void run(async () => {
        if (!group || currentMembers.length < 2) throw new Error('This superset is no longer available.')
        const id = await getWorkout()
        await groupWorkoutExercises(id, [...currentMembers, ex.id])
        navigate('/workout/' + id + '/exercise/' + (originExercise || ex.id), { replace: true })
      }); return
    }
    startExercise(ex)
  }
  // Creating or reusing an exercise returns to the exact picker task that opened the form.
  useEffect(() => {
    const id = positiveId(params.get('createdExerciseId'))
    if (!id || handledCreate.current === id) return
    const ex = exercises?.find(item => item.id === id)
    if (!ex) return
    if ((mode === 'swap' || mode === 'add_to_superset') && !instances.length) return
    handledCreate.current = id
    const clean = new URLSearchParams(params)
    clean.delete('createdExerciseId')
    setParams(clean, { replace: true })
    if (multi && picked.includes(id)) return
    choose(ex)
  }, [params, exercises, instances, mode, multi, picked])

  function buildSuperset() {
    if (selectedIds.length < 2 || selectedIds.length > 10) return
    void run(async () => {
      const id = await getWorkout()
      await groupWorkoutExercises(id, selectedIds); changePick([])
      navigate('/workout/' + id + '/exercise/' + selectedIds[0], { replace: true })
    })
  }
  function toggleFavorite(ex: Exercise) {
    if (!ex.isStarred && (exercises?.filter(e => e.isStarred).length ?? 0) >= 3) { setError('Three exercises are starred. Unstar one to track another.'); return }
    void run(async () => { await toggleStar(ex.id) })
  }
  function edit(ex: Exercise) {
    navigate('/exercises/' + ex.id + '/edit?' + new URLSearchParams({ returnTo: returnPath }))
  }
  function create() {
    navigate('/exercises/new?' + new URLSearchParams({ returnTo: returnPath, name: query.trim(), ...(muscles.length === 1 ? { muscle: muscles[0] } : {}) }))
  }
  function goBack() {
    if (originExercise && workoutId) navigate('/workout/' + workoutId + '/exercise/' + originExercise)
    else navigate(workoutId ? '/workout/active' : '/')
  }
  const Container = embedded ? 'section' : 'main'
  return <Container className={embedded ? 'lib-browser' : 'screen lib-page lib-browser'}>
    {!embedded && <header className="lib-page-head" data-session-start={params.get('workoutMode') === 'true' && !explicitWorkout || undefined}>
      <button type="button" className="icon-button" aria-label="Back" onClick={goBack}><ArrowLeft/></button>
      <h1>{heading}</h1>
      {gatorMode && <SwoleGator pose="badge" size={32} label="" className="ml-auto shrink-0" />}
    </header>}
    <div className="lib-sticky-bar">
      <div className="lib-toolbar">
        <label className="lib-search">
          <Search size={18} aria-hidden="true"/>
          <input type="search" aria-label="Search exercises" placeholder="Search exercises" value={query} onChange={e => setQuery(e.target.value)}/>
          {query && <button type="button" className="icon-button" aria-label="Clear search" onClick={() => setQuery('')}><X size={16}/></button>}
        </label>
        <button type="button" className="lib-new" onClick={create} aria-label="Create exercise"><Plus size={19}/><span>New</span></button>
      </div>
      <div className="lib-filter-scroll" aria-label="Filter by muscle">
        <button type="button" className="lib-chip" aria-pressed={!muscles.length} onClick={() => changeMuscles([])}>All</button>
        {muscleGroups?.map(({ name }) => <button type="button" key={name} className="lib-chip" aria-pressed={muscles.includes(name)} onClick={() => changeMuscles(muscles.includes(name) ? muscles.filter(m => m !== name) : [...muscles, name])}>{name}</button>)}
      </div>
    </div>
    <div className="lib-list-head"><h2>{exercises ? results.length + ' exercises' : 'Exercises'}</h2>
      <button type="button" onClick={() => setSortOpen(true)}><ArrowUpDown size={15}/>{sort}</button>
    </div>
    {!embedded && mode === 'browse' && <button type="button" className="lib-superset-link" onClick={() => setMode('build_superset')}><Link2 size={16}/>Build superset<ChevronRight size={15}/></button>}
    {error && <div className="lib-error" role="alert">{error}<button type="button" aria-label="Dismiss message" onClick={() => setError('')}><X size={16}/></button></div>}
    {exercises && !results.length ? <div className="lib-empty surface p-6 stagger-in" style={{ '--stagger-i': 0 } as CSSProperties}>
      {gatorMode ? <SwoleGator pose="spotter" size={72} interactive /> : <Search size={20} aria-hidden="true"/>}
      <h3>No matching exercises</h3>
      <button type="button" className="btn" onClick={create}>Create exercise</button>
    </div> :
      <ul key={filterBurst} className="lib-exercises">
        {results.map((ex, idx) => {
          const rank = selectedIds.indexOf(ex.id)
          const isPicked = multi && rank >= 0
          return <li key={ex.id} className={'lib-ex-item stagger-in' + (isPicked ? ' is-picked' : '')} style={{ '--stagger-i': Math.min(idx, 10) } as CSSProperties}>
            <button type="button" className="lib-exercise" aria-pressed={multi ? rank !== -1 : undefined} disabled={busy} onClick={() => choose(ex)}>
              {multi && <span key={rank >= 0 ? 'picked-' + rank : 'unpicked'} className={'lib-rank' + (rank >= 0 ? ' picked' : '')}>{rank >= 0 ? rank + 1 : <Plus size={14}/>}</span>}
              <span className="lib-ex-copy"><strong>{ex.name}</strong><small>{ex.targetMuscle}<span aria-hidden="true"> · </span>{typeLabels[ex.logType]}</small></span>
              {!multi && <ChevronRight size={16} className="lib-ex-chev" aria-hidden="true" />}
            </button>
            <button type="button" className="lib-icon" disabled={busy} aria-label={(ex.isStarred ? 'Unstar ' : 'Star ') + ex.name} aria-pressed={ex.isStarred} onClick={() => toggleFavorite(ex)}><Star size={17} fill={ex.isStarred ? 'currentColor' : 'none'}/></button>
            <button type="button" className="lib-icon" aria-label={'Options for ' + ex.name} onClick={() => setActions(ex)}><Ellipsis size={18}/></button>
          </li>
        })}
      </ul>}
    {multi && <footer key={selectedIds.length > 0 ? 'dock-active' : 'dock-idle'} className={'lib-pick-dock fly-in-up' + (selectedIds.length > 0 ? ' has-picks' : '')}>
      {!!selectedIds.length && <div className="lib-filter-scroll">{selectedIds.map((id, i) => <button type="button" key={id} className="lib-chip lib-dock-chip" onClick={() => changePick(selectedIds.filter(v => v !== id))}>{i + 1} · {exercises?.find(e => e.id === id)?.name}<X size={14}/></button>)}</div>}
      <div className="lib-actions"><button type="button" className="btn" onClick={() => setMode('browse')}>Cancel</button><button type="button" className="btn primary" data-ready={selectedIds.length >= 2 ? 'true' : undefined} disabled={selectedIds.length < 2 || busy} onClick={buildSuperset}>Start superset · {selectedIds.length}</button></div>
    </footer>}
    {sortOpen && <BottomSheet title="Sort exercises" onClose={() => setSortOpen(false)}>
      {sorts.map(s => <button type="button" key={s} className="lib-sheet-row" onClick={() => { if (sort !== s) setFilterBurst(n => n + 1); setSort(s); setSortOpen(false) }}>{s}{sort === s && <Check size={19}/>}</button>)}
    </BottomSheet>}
    {actions && <BottomSheet title={actions?.name ?? 'Exercise options'} onClose={() => setActions(null)}>
      {actions && <><button type="button" className="lib-sheet-row" onClick={() => edit(actions)}>Edit exercise<Pencil size={17}/></button>
        <button type="button" className="lib-sheet-row lib-danger" onClick={() => { setDeleting(actions); setActions(null) }}>Delete exercise<Trash2 size={17}/></button></>}
    </BottomSheet>}
    {deleting && <BottomSheet title="Delete exercise?" onClose={() => setDeleting(null)}>
      {deleting && <><p>Deleting {deleting.name} removes its logged sets and routine entries.</p>
        {error && <p role="alert" className="lib-error">{error}</p>}
        <div className="lib-actions"><button type="button" className="btn" onClick={() => setDeleting(null)}>Keep exercise</button><button type="button" className="btn lib-danger" disabled={busy} onClick={() => void run(async () => { await deleteExercise(deleting.id); setDeleting(null) })}>Delete exercise</button></div></>}
    </BottomSheet>}
    {detail && <BottomSheet title={detail?.name ?? 'Exercise'} onClose={() => setDetail(null)}>
      {detail && <><div className="lib-details"><span>{detail.targetMuscle} · {typeLabels[detail.logType]}</span><span>{detail.defaultRestSeconds}s rest{detail.repTarget ? ' · ' + detail.repTarget + ' rep target' : ''}</span>{detail.notes && <p>{detail.notes}</p>}</div>
        {error && <p role="alert" className="lib-error">{error}</p>}
        <div className="lib-actions"><button type="button" className="btn" onClick={() => edit(detail)}>Edit exercise</button><button type="button" className="btn primary" disabled={busy} onClick={() => startExercise(detail)}>Log exercise</button></div></>}
    </BottomSheet>}
  </Container>
}
