import { useEffect, useRef, useState, type CSSProperties, type FormEvent } from 'react'
import { useLiveQuery } from 'dexie-react-hooks'
import { ArrowLeft, Check, ChevronDown, ChevronRight, PencilRuler } from 'lucide-react'
import { useBlocker, useNavigate, useParams, useSearchParams } from 'react-router-dom'
import { db } from '../db/database'
import { saveExercise } from '../db/repositories'
import { DISTANCE_UNITS, LOG_TYPES, type DistanceUnit, type Exercise, type LogType } from '../db/types'
import { BottomSheet } from '../components/primitives'
import { SwoleGator, useIsGatorMode } from '../components/mascot'
import { typeLabels } from './ExerciseSelect'
import './library.css'

const unitLabels: Record<DistanceUnit, string> = { METERS: 'm', KILOMETERS: 'km', YARDS: 'yd', FEET: 'ft', MILES: 'mi', STEPS: 'steps', FLOORS: 'floors' }
type Draft = { name: string; muscle: string; type: LogType; unit: DistanceUnit; rest: string; target: string; notes: string }
function makeDraft(exercise: Exercise | undefined, name: string, muscle: string): Draft {
  return { name: exercise?.name ?? name, muscle: exercise?.targetMuscle ?? muscle, type: exercise?.logType ?? 'WEIGHT_REPS',
    unit: exercise?.defaultDistanceUnit ?? 'MILES', rest: String(exercise?.defaultRestSeconds ?? 90),
    target: exercise?.repTarget?.toString() ?? '', notes: exercise?.notes ?? '' }
}
function safeReturn(path: string | null) {
  if (!path || !path.startsWith('/') || path.startsWith('//') || path.includes('\\')) return '/library'
  return path
}
function example(d: Draft, kg: boolean) {
  const weight = kg ? '60 kg' : '135 lb'
  const distance = '1 ' + unitLabels[d.unit]
  const reps = (d.target && Number(d.target) > 0 ? d.target : '8') + ' reps'
  const values: Record<LogType, string> = {
    WEIGHT_REPS: weight + ' × ' + reps, REPS_ONLY: reps, DURATION: '00:45', WEIGHT_DISTANCE: weight + ' · ' + distance,
    DISTANCE_TIME: distance + ' · 08:00', WEIGHT_TIME: weight + ' · 00:45', CALORIES_TIME: '100 kcal · 08:00',
  }
  return values[d.type]
}
export default function ExerciseForm() {
  const { id } = useParams()
  const [params] = useSearchParams()
  const navigate = useNavigate()
  const gatorMode = useIsGatorMode()
  const parsedId = id !== undefined ? Number(id) : null
  const validId = parsedId !== null && Number.isSafeInteger(parsedId) && parsedId > 0 ? parsedId : null
  const result = useLiveQuery(async () => ({
    exercise: validId !== null ? await db.exercises.get(validId) : undefined,
    exercises: await db.exercises.toArray(),
    muscles: await db.muscle_groups.orderBy('name').toArray(),
    prefs: await db.preferences.get('default'),
  }), [validId])
  if (!result) return <main className="screen" aria-busy="true">Loading exercise…</main>
  if (id && !result.exercise) return <main className="screen lib-page">
    <section className="lib-empty surface p-6 stagger-in" style={{ '--stagger-i': 0 } as CSSProperties}>
      {gatorMode && <SwoleGator pose="spotter" size={76} interactive />}
      <h1>Exercise unavailable</h1>
      <button type="button" className="btn" onClick={() => navigate('/library')}>Back to library</button>
    </section>
  </main>
  return <Editor key={id ?? 'create'} exercise={result.exercise} exercises={result.exercises} muscles={result.muscles.map(m => m.name)}
    kg={result.prefs?.preferredWeightUnit === 'kg'} gatorMode={gatorMode} returnTo={safeReturn(params.get('returnTo'))}
    initialName={params.get('name')?.trim() ?? ''} initialMuscle={params.get('muscle') ?? ''}/>
}
function Editor({ exercise, exercises, muscles, kg, gatorMode, returnTo, initialName, initialMuscle }: {
  exercise?: Exercise; exercises: Exercise[]; muscles: string[]; kg: boolean; gatorMode: boolean; returnTo: string; initialName: string; initialMuscle: string
}) {
  const navigate = useNavigate()
  const [initial] = useState(() => makeDraft(exercise, initialName, muscles.includes(initialMuscle) ? initialMuscle : ''))
  const [draft, setDraft] = useState(initial)
  const [expandedTypes, setExpandedTypes] = useState(LOG_TYPES.indexOf(initial.type) >= 3)
  const [setup, setSetup] = useState(false)
  const [previewTick, setPreviewTick] = useState(0)
  const [busy, setBusy] = useState(false)
  const [error, setError] = useState('')
  const nameRef = useRef<HTMLInputElement>(null)
  const muscleRef = useRef<HTMLFieldSetElement>(null)
  const saveLock = useRef(false)
  const allowLeave = useRef(false)
  const dirty = Object.keys(initial).some(key => initial[key as keyof Draft] !== draft[key as keyof Draft])
  const blocker = useBlocker(() => dirty && !allowLeave.current)
  useEffect(() => {
    if (!dirty) return
    const warn = (e: BeforeUnloadEvent) => { if (!allowLeave.current) { e.preventDefault(); e.returnValue = '' } }
    window.addEventListener('beforeunload', warn)
    return () => window.removeEventListener('beforeunload', warn)
  }, [dirty])
  function update<K extends keyof Draft>(key: K, value: Draft[K]) {
    setError('')
    if (key === 'type' || key === 'unit' || key === 'target') setPreviewTick(n => n + 1)
    setDraft(prev => ({ ...prev, [key]: value }))
  }
  const query = draft.name.toLocaleLowerCase().trim()
  const similar = query.length < 3 ? [] : exercises.filter(e => e.id !== exercise?.id && (e.name.toLocaleLowerCase().includes(query) || query.includes(e.name.toLocaleLowerCase()))).slice(0, 3)
  const distance = draft.type.includes('DISTANCE')
  const reps = draft.type === 'WEIGHT_REPS' || draft.type === 'REPS_ONLY'
  function returnWith(id: number) {
    const url = new URL(returnTo, window.location.origin)
    if (!exercise) url.searchParams.set('createdExerciseId', String(id))
    allowLeave.current = true
    navigate(url.pathname + url.search + url.hash, { replace: true })
  }
  async function save(e: FormEvent) {
    e.preventDefault()
    if (saveLock.current) return
    if (!draft.name.trim()) { setError('Enter an exercise name.'); nameRef.current?.focus(); return }
    if (!draft.muscle) { setError('Choose a target muscle.'); muscleRef.current?.querySelector('button')?.focus(); return }
    if (!Number.isSafeInteger(Number(draft.rest)) || Number(draft.rest) <= 0) { setError('Enter a positive whole number of rest seconds.'); setSetup(true); return }
    if (reps && draft.target && (!Number.isSafeInteger(Number(draft.target)) || Number(draft.target) <= 0)) { setError('Enter a positive whole rep target.'); setSetup(true); return }
    saveLock.current = true; setBusy(true); setError('')
    try {
      const id = await saveExercise({ ...(exercise ? { id: exercise.id } : { isCustom: true }), name: draft.name.trim(), targetMuscle: draft.muscle,
        logType: draft.type, defaultDistanceUnit: draft.unit, notes: draft.notes.trim() || null, defaultRestSeconds: Number(draft.rest),
        repTarget: reps && draft.target ? Number(draft.target) : null })
      returnWith(id)
    } catch (err) { setError(err instanceof Error ? err.message : 'Could not save this exercise. Try again.') }
    finally { saveLock.current = false; setBusy(false) }
  }
  return <main className="screen lib-page">
    <header className="lib-page-head lib-sticky-head">
      <button type="button" className="icon-button" aria-label={returnTo.startsWith('/workout/builder') ? 'Back to builder' : 'Back to exercises'} onClick={() => navigate(returnTo, { replace: true })}><ArrowLeft/></button>
      <h1>{exercise ? 'Edit exercise' : 'New exercise'}</h1>
      {gatorMode && <SwoleGator pose="badge" size={32} label="" className="ml-auto shrink-0" />}
    </header>
    <form className="lib-form stagger-in" style={{ '--stagger-i': 0 } as CSSProperties} noValidate onSubmit={save}>
      <label className="lib-field"><span>Exercise name</span><input ref={nameRef} required value={draft.name} autoComplete="off" onChange={e => update('name', e.target.value)}/></label>
      {similar.length > 0 && <div className="lib-similar lib-reveal-in"><span className="eyebrow">Similar exercises</span>{similar.map(ex =>
        exercise ? <span key={ex.id}>{ex.name}</span> : <button type="button" key={ex.id} onClick={() => returnWith(ex.id)}>Use {ex.name}<ChevronRight size={15}/></button>)}</div>}
      <fieldset ref={muscleRef}><legend>Target muscle</legend><div className="lib-options">
        {muscles.map(m => <button type="button" key={m} className="lib-chip" aria-pressed={draft.muscle === m} onClick={() => update('muscle', m)}>{m}</button>)}
      </div></fieldset>
      <fieldset><legend>Log type</legend><div className="lib-options">
        {(expandedTypes ? LOG_TYPES : LOG_TYPES.slice(0, 3)).map(t => <button type="button" key={t} className="lib-chip" aria-pressed={draft.type === t} onClick={() => update('type', t)}>{typeLabels[t]}</button>)}
      </div><button className="lib-more" type="button" aria-expanded={expandedTypes} onClick={() => setExpandedTypes(!expandedTypes)}>{expandedTypes ? 'Fewer types' : 'More log types'}<ChevronDown size={16}/></button></fieldset>
      {distance && <fieldset className="lib-reveal-in"><legend>Distance unit</legend><div className="lib-options">
        {DISTANCE_UNITS.map(u => <button type="button" className="lib-chip" key={u} aria-pressed={draft.unit === u} onClick={() => update('unit', u)}>{unitLabels[u]}</button>)}
      </div></fieldset>}
      <div className="lib-example surface">
        {previewTick > 0 && <span key={previewTick} className="lib-example-sheen" aria-hidden="true" />}
        <div>
          <span className="eyebrow">Example set</span>
          <output key={previewTick} className={previewTick > 0 ? 'lib-example-value is-popping' : 'lib-example-value'}>{example(draft, kg)}</output>
        </div>
        {gatorMode ? <SwoleGator pose="spotter" size={44} label="" /> : <PencilRuler aria-hidden="true" size={25}/>}
      </div>
      <section className="lib-extra"><button type="button" aria-expanded={setup} className="lib-sheet-row" onClick={() => setSetup(!setup)}>Rest, targets & notes<ChevronDown size={17}/></button>
        {setup && <div className="lib-extra-fields lib-reveal-in"><label className="lib-field"><span>Rest time (seconds)</span><input type="number" inputMode="numeric" min="1" step="1" value={draft.rest} onChange={e => update('rest', e.target.value)}/></label>
          {reps && <label className="lib-field"><span>Rep target</span><input type="number" inputMode="numeric" min="1" step="1" value={draft.target} onChange={e => update('target', e.target.value)}/></label>}
          <label className="lib-field"><span>Exercise notes</span><textarea rows={3} value={draft.notes} onChange={e => update('notes', e.target.value)}/></label>
        </div>}
      </section>
      {error && <p className="lib-error" role="alert">{error}</p>}
      <button className="btn primary lib-submit" type="submit" disabled={busy}><Check size={18}/>{busy ? 'Saving…' : 'Save exercise'}</button>
    </form>
    {blocker.state === 'blocked' && <BottomSheet onClose={() => blocker.reset?.()} title="Discard changes?">
      <p>Your unsaved edits will be lost.</p><div className="lib-actions"><button type="button" className="btn" onClick={() => blocker.reset?.()}>Keep editing</button><button type="button" className="btn lib-danger" onClick={() => { allowLeave.current = true; blocker.proceed?.() }}>Discard changes</button></div>
    </BottomSheet>}
  </main>
}
