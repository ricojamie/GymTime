import { useEffect, useMemo, useRef, useState } from 'react'
import { useLiveQuery } from 'dexie-react-hooks'
import { useNavigate, useParams, useSearchParams } from 'react-router-dom'
import { create } from 'zustand'
import {
  Activity, AlignLeft, ArrowLeft, ArrowLeftRight, Check, ChevronDown, ChevronLeft, ChevronRight,
  CircleDot, Ellipsis, ListOrdered, NotebookPen, Pencil, Plus, SlidersHorizontal, Sparkles,
  Timer, Trash2, Trophy, Weight, X,
} from 'lucide-react'
import { db, defaultPreferences } from '../db/database'
import { DISTANCE_UNITS, normalizeMuscleName, type Exercise, type Preferences, type Workout, type WorkoutExerciseInstance, type WorkoutSet } from '../db/types'
import { deleteSet, ensureWorkoutExercise, finishWorkout, getActiveWorkout, logSet, saveExercise, updateSet } from '../db/repositories'
import { afterLog, emptyDraft, metricFields, normalizeDraft, validateSet, type SetDraft } from '../domain/loggingValidation'
import { recordMetric, replayRecords } from '../domain/records'
import { distanceLabels, distanceToMeters, formatWeight, fromLbs, toLbs, weightLabel } from '../domain/units'
import { BottomSheet } from '../components/primitives'
import { useIsGatorMode } from '../theme'
import Keypad, { clockValue, fieldLabels, type DialPin, type PadField } from '../components/logger/Keypad'
import PlateCalculatorSheet from '../components/logger/PlateCalculatorSheet'
import PrCelebration from '../components/logger/PrCelebration'
import ProgressSheet from '../components/logger/ProgressSheet'
import SmartLogSheet, { type SmartLogEntry } from '../components/logger/SmartLogSheet'
import RestTimerBar from '../components/logger/RestTimerBar'
import { startRestTimer, stopRestTimer, useRestTimerRemaining } from '../store/restTimer'
import '../components/logger/logger.css'

type LoggerSheet = 'menu' | 'progress' | 'plates' | 'smart' | 'notes' | 'finish' | 'delete' | null
type StepDir = 'next' | 'prev' | null
type Queue = { workoutId: number; entries: SmartLogEntry[]; index: number }
type Transient = { exitedGroups: string[]; queue: Queue | null; banner: {workoutId: number; exerciseName: string; labels: string[]; id: number; expiresAt: number} | null; stepDir: StepDir }
const useTransient = create<Transient>(() => ({ queue: null, banner: null, exitedGroups: [], stepDir: null }))
const scratch = new Map<string, SetDraft>()
let scratchWorkout = ''

type Context = {
  exercise: Exercise; workout: Workout; prefs: Preferences; plan: WorkoutExerciseInstance[]
  names: Map<number, string>; sets: WorkoutSet[]
}
function setText(s: WorkoutSet, exercise: Exercise, prefs: Preferences) {
  const pieces: string[] = []
  if (s.weight !== null) pieces.push(formatWeight(s.weight, prefs.preferredWeightUnit) + ' ' + weightLabel(prefs.preferredWeightUnit))
  if (s.reps !== null) pieces.push(s.reps + (s.weight === null ? ' reps' : ''))
  const strength = pieces.join(' × ')
  const cardio = [
    s.distanceValue !== null ? s.distanceValue + ' ' + distanceLabels[s.distanceUnit ?? exercise.defaultDistanceUnit] : '',
    s.calories !== null ? s.calories + ' kcal' : '',
    s.durationSeconds !== null ? clockValue(s.durationSeconds) : '',
  ].filter(Boolean)
  return [strength, ...cardio].filter(Boolean).join(' · ') || '—'
}
function pickDraft(row: Partial<SetDraft> | undefined, exercise: Exercise): SetDraft {
  const base = emptyDraft()
  if (row) {
    for (const key of Object.keys(base) as (keyof SetDraft)[]) {
      if (row[key] !== undefined) Object.assign(base, { [key]: row[key] })
    }
  }
  return { ...base, distanceUnit: base.distanceUnit ?? exercise.defaultDistanceUnit,
    isWarmup: normalizeMuscleName(exercise.targetMuscle) === 'Warmups' || base.isWarmup }
}

function TimerIndicator() {
  const seconds = useRestTimerRemaining()
  return <span className={'log-timer-indicator' + (seconds ? ' is-running' : '')} aria-label={seconds ? 'Rest timer ' + clockValue(seconds) : 'Rest timer idle'}>
    <span aria-hidden="true" />{seconds ? clockValue(seconds) : <Timer size={14} aria-hidden="true" />}</span>
}

function RecordBanner({ workoutId, isGator, audioEnabled }: { workoutId: number; isGator: boolean; audioEnabled: boolean }) {
  const banner = useTransient(s => s.banner)
  useEffect(() => {
    if (!banner) return
    const t = setTimeout(() => useTransient.setState({banner: null}), Math.max(0, banner.expiresAt - Date.now()))
    return () => clearTimeout(t)
  }, [banner])
  if (!banner || banner.workoutId !== workoutId || banner.expiresAt <= Date.now()) return null
  return <PrCelebration
    id={banner.id}
    labels={banner.labels}
    exerciseName={banner.exerciseName}
    isGator={isGator}
    audioEnabled={audioEnabled}
    onDismiss={() => useTransient.setState({ banner: null })}
  />
}

export default function Logger() {
  const { exerciseId: idParam, workoutId: sessionParam } = useParams()
  const [search] = useSearchParams()
  const nav = useNavigate()
  const eid = Number(idParam)
  const wid = Number(sessionParam || search.get('workoutId')) || null
  const data = useLiveQuery(async () => {
    if (!Number.isSafeInteger(eid) || eid < 1) return null
    const [exercise, workout, prefs] = await Promise.all([
      db.exercises.get(eid), wid ? db.workouts.get(wid) : getActiveWorkout(), db.preferences.get('default'),
    ])
    if (!exercise || !workout || workout.endTime !== null) return null
    const [all, sets] = await Promise.all([
      db.workout_exercise_instances.where('workoutId').equals(workout.id).toArray(),
      db.sets.where('exerciseId').equals(eid).toArray(),
    ])
    const plan = all.filter(p => !p.isSkipped).sort((a,b) => a.orderIndex - b.orderIndex || a.id - b.id)
    const ex = await db.exercises.bulkGet([...new Set(plan.map(p => p.exerciseId))])
    return { exercise, workout, prefs: prefs ?? defaultPreferences, plan,
      names: new Map(ex.filter((e): e is Exercise => !!e).map(e => [e.id, e.name])),
      sets: sets.sort((a,b) => a.timestamp - b.timestamp || a.id - b.id),
    } satisfies Context
  }, [eid, wid])
  if (data === undefined || (data && (data.exercise.id !== eid || (wid && data.workout.id !== wid)))) return <main className="screen log-loading" aria-busy="true">Loading exercise…</main>
  if (!data) return <main className="screen"><h1>Logger unavailable</h1><p>This exercise needs an active workout.</p><button className="btn" onClick={() => nav('/')}>Go to Home</button></main>
  return <LoggerSession key={data.workout.id + '/' + eid} {...data} />
}

function LoggerSession({exercise, workout, prefs, plan, names, sets}: Context) {
  const nav = useNavigate()
  const isGatorHook = useIsGatorMode()
  const isGator = isGatorHook || prefs.appIcon === 'swole_alligator'
  const wid = workout.id
  const eid = exercise.id
  const prefsUnit = prefs.preferredWeightUnit
  const fields = metricFields[exercise.logType]
  const forceWarmup = normalizeMuscleName(exercise.targetMuscle) === 'Warmups'
  const current = useMemo(() => sets.filter(s => s.workoutId === wid), [sets, wid])
  const prior = useMemo(() => sets.filter(s => s.workoutId !== wid && s.isComplete), [sets, wid])
  const working = current.filter(s => !s.isWarmup)
  const warmups = current.filter(s => s.isWarmup)
  const instance = plan.find(p => p.exerciseId === eid)
  const planGroup = instance?.supersetGroupId
  const groupKey = [wid,workout.startTime,planGroup].join('/')
  const exitedGroup = useTransient(s => s.exitedGroups.includes(groupKey))
  const group = exitedGroup ? null : planGroup
  const members = group ? plan.filter(p => p.supersetGroupId === group).sort((a,b) => a.supersetOrderIndex - b.supersetOrderIndex) : []
  const steppers = members.length > 1 ? members : plan
  const index = steppers.findIndex(p => p.exerciseId === eid)
  const defaultRest = instance?.restSeconds ?? exercise.defaultRestSeconds
  const recent = prior.filter(s => !s.isWarmup).at(-1) ?? prior.at(-1)
  const sessionKey = wid + '/' + workout.startTime
  const cacheKey = sessionKey + '/' + eid
  const queue = useTransient(s => s.queue?.workoutId === wid ? s.queue : null)
  const entry = queue?.entries[queue.index]
  const [draft, setDraft] = useState<SetDraft>(() => {
    if (scratchWorkout !== sessionKey) { scratch.clear(); scratchWorkout = sessionKey }
    if (entry?.exerciseId === eid) return pickDraft(entry, exercise)
    return scratch.get(cacheKey) ?? pickDraft(afterLog(pickDraft(current.at(-1) ?? recent, exercise), forceWarmup), exercise)
  })
  const [keypad, setKeypad] = useState<PadField | null>(null)
  const [sheet, setSheet] = useState<LoggerSheet>(null)
  const [editId, setEditId] = useState<number | null>(null)
  const beforeEdit = useRef<SetDraft | null>(null)
  const [deleteId, setDeleteId] = useState<number | null>(null)
  const [exitingSetId, setExitingSetId] = useState<number | null>(null)
  const [extras, setExtras] = useState(false)
  const [warmExpanded, setWarmExpanded] = useState(!working.length)
  const [noteText, setNoteText] = useState('')
  const [error, setError] = useState('')
  const [busy, setBusy] = useState(false)
  const pending = useRef(false)
  const mounted = useRef(true)
  const heading = useRef<HTMLHeadingElement>(null)
  const firstWorking = useRef(working.length > 0)
  const [announcement, setAnnouncement] = useState('')
  const [addedSetId, setAddedSetId] = useState<number | null>(null)
  const [justLogged, setJustLogged] = useState(0)
  const justLoggedTick = useRef(0)
  const [pulsedField, setPulsedField] = useState<{ field: PadField; tick: number } | null>(null)
  const fieldPulseTick = useRef(0)
  const keypadEditedField = useRef<PadField | null>(null)
  const [enterDir] = useState<StepDir>(() => useTransient.getState().stepDir)
  useEffect(() => () => { mounted.current = false }, [])
  useEffect(() => {
    if (!enterDir) return
    const timeout = window.setTimeout(() => {
      if (useTransient.getState().stepDir === enterDir) useTransient.setState({ stepDir: null })
    }, 60)
    return () => window.clearTimeout(timeout)
  }, [enterDir])
  useEffect(() => {
    if (!justLogged) return
    const timeout = window.setTimeout(() => setJustLogged(0), 520)
    return () => window.clearTimeout(timeout)
  }, [justLogged])
  useEffect(() => {
    if (!pulsedField) return
    const timeout = window.setTimeout(() => setPulsedField(null), 380)
    return () => window.clearTimeout(timeout)
  }, [pulsedField])
  const addedRowReady = addedSetId !== null && current.some(set => set.id === addedSetId)
  useEffect(() => {
    if (!addedRowReady) return
    const timeout = window.setTimeout(() => setAddedSetId(null), 780)
    return () => window.clearTimeout(timeout)
  }, [addedSetId, addedRowReady])
  const lastQueueEntry = useRef(entry)
  useEffect(() => {
    if (entry !== lastQueueEntry.current && entry?.exerciseId === eid) setDraft(pickDraft(entry, exercise))
    lastQueueEntry.current = entry
  }, [entry, eid, exercise])
  useEffect(() => {
    if (!editId) scratch.set(cacheKey, draft)
  }, [cacheKey, draft, editId])
  useEffect(() => {
    if (!firstWorking.current && working.length) setWarmExpanded(false)
    firstWorking.current = working.length > 0
  }, [working.length])
  const recordEvents = useMemo(() => replayRecords(sets, exercise.logType, exercise.defaultDistanceUnit), [sets, exercise.logType, exercise.defaultDistanceUnit])
  const markers = useMemo(() => new Set(recordEvents.filter(r => !r.benchmark).map(r => r.setId)), [recordEvents])
  const best = useMemo(() => {
    let bestValue = -Infinity
    let top: WorkoutSet | undefined
    for (const s of sets) {
      if (s.isWarmup || !s.isComplete) continue
      const metric = recordMetric(s, exercise.logType, exercise.defaultDistanceUnit)
      if (metric !== null && (metric > bestValue || (metric === bestValue && (s.reps ?? 0) > (top?.reps ?? 0)))) {
        top = s; bestValue = metric
      }
    }
    return top
  }, [sets, exercise.logType, exercise.defaultDistanceUnit])

  function triggerLoggedPulse() {
    justLoggedTick.current += 1
    setJustLogged(justLoggedTick.current)
  }
  function pulseField(field: PadField) {
    fieldPulseTick.current += 1
    setPulsedField({ field, tick: fieldPulseTick.current })
  }
  function gotoExercise(id: number, replace = false, dir: StepDir = null) {
    useTransient.setState({ stepDir: dir })
    nav('/log/' + id + '?workoutId=' + wid, { replace })
  }
  function patchDraft(patch: Partial<SetDraft>) { setDraft(f => ({...f, ...patch})); setError('') }
  function closeSheet() { setSheet(null); setError('') }
  function restoreEdit() {
    if (beforeEdit.current) setDraft(beforeEdit.current)
    beforeEdit.current = null; setEditId(null); setError(''); setKeypad(null)
  }
  function beginEdit(set: WorkoutSet) {
    setAddedSetId(null)
    if (!beforeEdit.current) beforeEdit.current = {...draft}
    setDraft(pickDraft(set, exercise)); setEditId(set.id); setError('')
    setExtras(Boolean(set.note || set.rpe !== null))
    heading.current?.scrollIntoView({block: 'start', behavior: 'instant'})
    heading.current?.focus({preventScroll: true})
  }
  async function guard(operation: () => Promise<void>) {
    if (pending.current) return
    pending.current = true; setBusy(true); setError('')
    try { await operation() }
    catch (e) { if (mounted.current) setError(e instanceof Error ? e.message : 'Could not save. Try again.') }
    finally { pending.current = false; if (mounted.current) setBusy(false) }
  }
  function openQueue(entries: SmartLogEntry[]) {
    if (!entries.length) return
    void guard(async () => {
      const first = entries[0]
      await ensureWorkoutExercise(wid, first.exerciseId)
      useTransient.setState({queue: {entries, index: 0, workoutId: wid}})
      if (editId) restoreEdit()
      if (first.exerciseId !== eid) gotoExercise(first.exerciseId, false, 'next')
      else setDraft(pickDraft(first, exercise))
      closeSheet()
    })
  }
  async function commit() {
    const clean = normalizeDraft(exercise.logType, {...draft, isWarmup: forceWarmup || draft.isWarmup})
    const validation = validateSet(exercise.logType, clean)
    if (validation) { setError(validation); return }
    await guard(async () => {
      const metrics = {...clean, distanceMeters: distanceToMeters(clean.distanceValue, clean.distanceUnit)}
      if (editId !== null) {
        await updateSet(editId, metrics); restoreEdit(); triggerLoggedPulse(); setAnnouncement('Set updated.'); return
      }
      const timestamp = Date.now()
      const row = {...metrics, workoutId: wid, exerciseId: eid, timestamp, isComplete: true,
        supersetGroupId: group ?? null, supersetOrderIndex: group ? instance?.supersetOrderIndex ?? 0 : 0}
      const id = await logSet(row)
      setAddedSetId(id)
      triggerLoggedPulse()
      keypadEditedField.current = null
      setKeypad(null)
      setAnnouncement((clean.isWarmup ? 'Warm-up ' + (warmups.length + 1) : 'Working set ' + (working.length + 1)) + ' logged.')
      const sticky = pickDraft(afterLog(clean, forceWarmup), exercise)
      scratch.set(cacheKey, sticky)
      setDraft(sticky)
      if (prefs.timerAutoStart && defaultRest > 0) startRestTimer(defaultRest)
      const record = replayRecords([...sets, {...row,id}], exercise.logType, exercise.defaultDistanceUnit).find(r => r.setId === id)
      if (record && !record.benchmark) useTransient.setState({banner: {workoutId: wid, exerciseName: exercise.name, labels: record.labels, id, expiresAt: Date.now() + 4500}})
      if (queue && entry?.exerciseId === eid) {
        const nextIndex = queue.index + 1
        const next = queue.entries[nextIndex]
        useTransient.setState({queue: next ? {...queue, index: nextIndex} : null})
        if (next && next.exerciseId !== eid) gotoExercise(next.exerciseId, true, 'next')
      } else if (members.length > 1) {
        gotoExercise(members[(index + 1) % members.length].exerciseId, true, 'next')
      }
    })
  }
  function confirmDelete() {
    const targetId = deleteId
    void guard(async () => {
      if (targetId === null) { closeSheet(); return }
      const animateExit = typeof document !== 'undefined' &&
        document.documentElement.dataset.motion === 'full' &&
        !(typeof window !== 'undefined' && window.matchMedia?.('(prefers-reduced-motion: reduce)').matches)
      if (animateExit) {
        if (editId !== null && editId === targetId) restoreEdit()
        setDeleteId(null)
        closeSheet()
        setExitingSetId(targetId)
        await new Promise<void>(resolve => window.setTimeout(resolve, 210))
        try {
          await deleteSet(targetId)
          if (mounted.current) setAnnouncement('Set deleted.')
        } finally {
          if (mounted.current) setExitingSetId(curr => curr === targetId ? null : curr)
        }
        return
      }
      await deleteSet(targetId)
      if (editId !== null && editId === targetId) restoreEdit()
      setDeleteId(null)
      closeSheet()
      setAnnouncement('Set deleted.')
    })
  }
  function finish() {
    void guard(async () => {
      await finishWorkout(wid)
      stopRestTimer()
      useTransient.setState({queue: null, exitedGroups: [], stepDir: null})
      scratch.clear()
      nav('/summary/' + wid, {replace: true, state: {justFinished: wid}})
    })
  }
  const signedUnit = weightLabel(prefsUnit)
  function fieldUnit(field: PadField) {
    if (field === 'weight') return signedUnit
    if (field === 'reps') return 'reps'
    if (field === 'durationSeconds') return 'mm:ss'
    if (field === 'distanceValue') return distanceLabels[draft.distanceUnit ?? exercise.defaultDistanceUnit]
    if (field === 'calories') return 'kcal'
    return '/ 10'
  }
  function presentation(field: PadField) {
    const value = draft[field]
    if (value === null) return '—'
    if (field === 'weight') return formatWeight(value, prefsUnit)
    if (field === 'durationSeconds') return clockValue(value)
    return String(value)
  }
  const pins: DialPin[] = []
  if (keypad === 'weight' || keypad === 'reps') {
    const toDisplay = (n: number) => keypad === 'weight' ? fromLbs(n, prefsUnit) : n
    if (recent && recent[keypad] !== null) pins.push({label: 'LAST', value: toDisplay(recent[keypad])})
    if (best && best[keypad] !== null) pins.push({label: 'BEST', value: toDisplay(best[keypad])})
    const target = instance?.repMax ?? instance?.repMin ?? exercise.repTarget
    if (keypad === 'reps' && target != null) pins.push({label: 'GOAL', value: target})
  }
  const nextField = keypad && keypad !== 'rpe' ? fields[fields.indexOf(keypad) + 1] : undefined
  const action = editId !== null ? 'Save changes' : 'Log set'
  const nextNumber = editId ? 'Editing set' : draft.isWarmup || forceWarmup ? 'Warm-up ' + (warmups.length + 1) : 'Working set ' + (working.length + 1)
  const selectPath = '/exercises/select?workoutId=' + wid
  const editingSet = editId !== null ? current.find(s => s.id === editId) ?? null : null
  const editingLabel = useMemo(() => {
    if (!editingSet) return ''
    const list = editingSet.isWarmup ? warmups : working
    const pos = list.findIndex(s => s.id === editingSet.id)
    return (editingSet.isWarmup ? 'warm-up ' : 'set ') + (pos >= 0 ? pos + 1 : '')
  }, [editingSet, warmups, working])
  function listRow(s: WorkoutSet, i: number) {
    const isAdded = s.id === addedSetId
    const isExiting = s.id === exitingSetId
    return <li key={s.id} className={'log-set-row' + (isAdded ? '' : ' stagger-in') + (s.id === editId ? ' is-editing' : '') + (isExiting ? ' is-exiting' : '')} data-just-added={isAdded || undefined} data-exiting={isExiting || undefined} style={{ '--stagger-i': Math.min(i, 12) } as React.CSSProperties}>
      <button disabled={isExiting} onClick={() => beginEdit(s)} aria-label={'Edit ' + (s.isWarmup ? 'warm-up ' : 'set ') + (i + 1) + ', ' + setText(s, exercise, prefs)}>
        <span className="log-set-num">{String(i+1).padStart(2,'0')}</span>
        <span className="log-row-value"><strong>{setText(s,exercise,prefs)}</strong>{(s.rpe != null || s.note) && <small>{[s.rpe == null ? '' : 'RPE ' + s.rpe,s.note].filter(Boolean).join(' · ')}</small>}</span>
        {markers.has(s.id) && <Trophy className="log-trophy" size={15} aria-label="Personal record" />}
        <Pencil size={14} className="log-row-edit" />
      </button>
      <button className="icon-button" disabled={isExiting} aria-label={'Delete ' + (s.isWarmup ? 'warm-up ' : 'set ') + (i + 1)} onClick={() => { setDeleteId(s.id); setSheet('delete') }}><Trash2 size={16} /></button>
    </li>
  }
  return <main className="screen log-screen">
    <nav className="log-toolbar" aria-label="Workout controls">
      <button className="icon-button" aria-label="Back to workout" onClick={() => nav('/workout/active')}><ArrowLeft size={20} /></button>
      <TimerIndicator />
      <div className="log-stepper"><button className="icon-button" disabled={index <= 0} aria-label="Previous exercise" onClick={() => gotoExercise(steppers[index-1].exerciseId, false, 'prev')}><ChevronLeft size={18} /></button>
        {steppers.length > 0 && index >= 0 && <span>{index+1} / {steppers.length}</span>}
        <button className="icon-button" disabled={index < 0 || index + 1 >= steppers.length} aria-label="Next exercise" onClick={() => gotoExercise(steppers[index+1].exerciseId, false, 'next')}><ChevronRight size={18} /></button></div>
      <button className="log-finish" onClick={() => setSheet('finish')}>Finish</button>
      <button className="icon-button" aria-label="Exercise actions" onClick={() => setSheet('menu')}><Ellipsis size={20} /></button>
    </nav>
    <header className="log-heading" data-enter-dir={enterDir ?? undefined}>
      <p className="eyebrow">{exercise.targetMuscle} <span aria-hidden="true">/</span> {nextNumber}</p>
      <h1 ref={heading} tabIndex={-1}>{exercise.name}</h1>
    </header>
    <div className="log-telemetry">
      <div><small>All-time best</small><strong>{best ? setText(best,exercise,prefs) : '—'}</strong></div>
      <div><small>Last workout</small><strong>{recent ? setText(recent,exercise,prefs) : '—'}</strong></div>
      <div className="log-telemetry-tools">
        <button className="icon-button" aria-label="Exercise progress" onClick={() => setSheet('progress')}><Activity size={19} /></button>
        {fields.includes('weight') && <button className="icon-button" aria-label="Plate calculator" onClick={() => setSheet('plates')}><Weight size={19} /></button>}
        <button className="icon-button" aria-label="Exercise notes" onClick={() => {setNoteText(exercise.notes ?? ''); setSheet('notes')}}><NotebookPen size={18} /></button>
      </div>
    </div>
    {queue && entry?.exerciseId === eid && <div className="log-context"><ListOrdered size={16} /><strong>Smart Log · Set {queue.index+1} of {queue.entries.length}</strong><button onClick={() => useTransient.setState({queue:null})}>Cancel</button></div>}
    {instance && (instance.plannedSets || instance.notes) && <div className="log-context"><CircleDot size={16} /><span>{instance.plannedSets ? instance.plannedSets + ' sets' : ''}{instance.repMin != null || instance.repMax != null ? ' × ' + [instance.repMin,instance.repMax !== instance.repMin ? instance.repMax : null].filter(v => v != null).join('–') + ' reps' : ''}{instance.notes ? ' · ' + instance.notes : ''}</span></div>}
    {members.length > 1 && <div className="log-members" aria-label="Superset exercises">{members.map((p, memberIdx) => <button key={p.id} aria-pressed={p.exerciseId === eid} onClick={() => { if (p.exerciseId !== eid) gotoExercise(p.exerciseId, false, memberIdx > index ? 'next' : 'prev') }}>{names.get(p.exerciseId)}</button>)}<button aria-label="Exit superset" onClick={() => useTransient.setState(s => ({exitedGroups:[...s.exitedGroups,groupKey]}))}><X size={14} /> Exit</button></div>}
    {planGroup && exitedGroup && <div className="log-members"><button onClick={() => useTransient.setState(s => ({exitedGroups:s.exitedGroups.filter(g => g !== groupKey)}))}><ArrowLeftRight size={15} />Resume superset</button></div>}
    <section className={'log-stage' + (enterDir === 'next' ? ' log-slide-next' : enterDir === 'prev' ? ' log-slide-prev' : '') + (editId !== null ? ' is-editing' : '')} aria-label="Set entry">
      {editingSet && <div key={editingSet.id} className="log-edit-banner">
        <Pencil size={14} aria-hidden="true" />
        <span>Editing {editingLabel.trim()}</span>
        <button type="button" aria-label="Exit edit mode" onClick={restoreEdit}><X size={15} aria-hidden="true" /></button>
      </div>}
      <div className="log-fields">{fields.map(field => {
        const val = presentation(field)
        const isPulsed = pulsedField?.field === field
        return <div className="log-field-wrap" key={field}>
          <button className={'log-field' + (isPulsed ? ' is-pulsed' : '') + (justLogged > 0 ? ' is-celebrating' : '')} data-active-field={keypad === field || undefined} onClick={() => {setKeypad(field); setError('')}} aria-label={'Enter ' + fieldLabels[field].toLowerCase() + ', ' + val + ' ' + fieldUnit(field)}>
            {isPulsed && <span key={pulsedField.tick} className="log-field-flash" aria-hidden="true" />}
            <span>{fieldLabels[field]}</span><strong><span key={val} className="log-field-val">{val}</span><small>{fieldUnit(field)}</small></strong>
          </button>
          {field === 'distanceValue' && <label className="log-unit-select">Unit <select aria-label="Distance unit" value={draft.distanceUnit ?? exercise.defaultDistanceUnit} onChange={e => patchDraft({distanceUnit: DISTANCE_UNITS.find(k => k === e.target.value) ?? exercise.defaultDistanceUnit})}>{DISTANCE_UNITS.map(k => <option key={k} value={k}>{distanceLabels[k]}</option>)}</select></label>}
        </div>
      })}</div>
      <div className="log-options">
        <button className="log-warm-toggle" disabled={forceWarmup} aria-pressed={forceWarmup || draft.isWarmup} onClick={() => patchDraft({isWarmup: !draft.isWarmup})}><span aria-hidden="true" />Warm-up</button>
        <button aria-expanded={extras} onClick={() => setExtras(!extras)}><SlidersHorizontal size={15} />RPE / Note <ChevronDown size={14} /></button>
      </div>
      {extras && <div className="log-extras"><button onClick={() => setKeypad('rpe')}><span>RPE</span><strong>{draft.rpe ?? '—'} / 10</strong></button><label>Set note<textarea rows={2} maxLength={500} value={draft.note ?? ''} onChange={e => patchDraft({note: e.target.value})} /></label></div>}
      {error && !keypad && !sheet && <p className="log-error" role="alert">{error}</p>}
      <button className={'btn primary log-commit' + (justLogged > 0 ? ' is-logged' : '')} disabled={busy} onClick={() => void commit()}>
        {justLogged > 0 && <span key={'w' + justLogged} className="log-commit-wave" aria-hidden="true" />}
        {justLogged > 0 && <span key={'f' + justLogged} className="log-commit-flash" aria-hidden="true" />}
        {justLogged > 0
          ? <span key={'s' + justLogged} className="log-save-stamp" aria-hidden="true"><Check size={20} /></span>
          : <Check size={20} aria-hidden="true" />}
        {action}
      </button>
      {editId !== null && <button className="log-cancel" onClick={restoreEdit}>Cancel editing</button>}
    </section>
    <RecordBanner workoutId={wid} isGator={isGator} audioEnabled={prefs.timerAudioEnabled} />
    <RestTimerBar defaultSeconds={defaultRest} />
    <section className="log-history"><header><h2>This workout</h2><span key={working.length} className="log-count-pop">{working.length} working {working.length === 1 ? 'set' : 'sets'}</span></header>
      {warmups.length > 0 && <><button className="log-warm-head" onClick={() => setWarmExpanded(!warmExpanded)} aria-expanded={warmExpanded}>Warm-ups · {warmups.length}<ChevronDown size={16} /></button>{warmExpanded && <ol className="log-warm-list">{warmups.map(listRow)}</ol>}</>}
      {working.length > 0 ? <ol>{working.map(listRow)}</ol> : !warmups.length && <p>No sets logged yet.</p>}
    </section>
    <footer className="log-actions"><button onClick={() => nav(selectPath)}><Plus size={18} />Exercise</button><button onClick={() => nav('/workout/active')}><ListOrdered size={18} />Overview</button></footer>
    <span role="status" className="log-sr-only">{announcement}</span>
    {keypad && <Keypad key={keypad} field={keypad} unit={fieldUnit(keypad)} weightUnit={prefsUnit}
      value={keypad === 'weight' && draft.weight != null ? fromLbs(draft.weight,prefsUnit) : draft[keypad]}
      pins={pins} error={error} busy={busy} action={action} onChange={v => {
        patchDraft({[keypad]: keypad === 'weight' && v !== null ? toLbs(v,prefsUnit) : v})
        keypadEditedField.current = keypad
        pulseField(keypad)
      }}
      onNext={nextField ? () => {
        const edited = keypadEditedField.current
        keypadEditedField.current = null
        if (edited) pulseField(edited)
        setKeypad(nextField); setError('')
      } : undefined} onCommit={() => void commit()} onClose={() => {
        const edited = keypadEditedField.current
        keypadEditedField.current = null
        setKeypad(null); setError('')
        if (edited) pulseField(edited)
      }} />}
    {sheet === 'progress' && <ProgressSheet exerciseId={eid} excludeWorkoutId={wid} onClose={closeSheet} />}
    {sheet === 'plates' && <PlateCalculatorSheet initialWeightLb={draft.weight ?? prefs.barWeight} onUse={lbs => {patchDraft({weight:lbs}); pulseField('weight'); closeSheet()}} onClose={closeSheet} />}
    {sheet === 'smart' && <SmartLogSheet initialExerciseId={eid} onOpenQueue={openQueue} onClose={closeSheet} />}
    {sheet === 'menu' && <BottomSheet title="Exercise actions" onClose={closeSheet}><div className="log-menu">
      <button onClick={() => nav('/exercises/'+eid+'/edit')}><Pencil /> Edit exercise</button>
      <button onClick={() => nav(selectPath + '&mode=' + (group ? 'add_to_superset' : 'build_superset') + '&exerciseId=' + eid)}><ArrowLeftRight />{group ? 'Add to superset' : 'Start superset'}</button>
      <button onClick={() => setSheet('smart')}><Sparkles />Smart Log</button>
      <button onClick={() => nav('/settings/theme')}><SlidersHorizontal />Theme</button>
    </div></BottomSheet>}
    {sheet === 'notes' && <BottomSheet title="Exercise notes" onClose={closeSheet}><div className="log-notes"><label>{exercise.name}<textarea rows={5} maxLength={1000} value={noteText} onChange={e => setNoteText(e.target.value)} /></label>
      {error && <p role="alert">{error}</p>}<button disabled={busy} className="btn primary" onClick={() => void guard(async () => {await saveExercise({...exercise,notes:noteText.trim() || null}); closeSheet()})}><AlignLeft size={16} />Save notes</button></div></BottomSheet>}
    {sheet === 'finish' && <BottomSheet title="Finish workout?" onClose={closeSheet}><div className="log-confirm">{error && <p role="alert">{error}</p>}<button className="btn primary" disabled={busy} onClick={finish}>Finish workout</button><button className="btn" onClick={closeSheet}>Keep training</button></div></BottomSheet>}
    {sheet === 'delete' && <BottomSheet title="Delete set?" onClose={closeSheet}><div className="log-confirm"><p>This permanently removes the set.</p>{error && <p role="alert">{error}</p>}<button disabled={busy} className="btn log-danger" onClick={confirmDelete}>Delete set</button><button className="btn" onClick={closeSheet}>Cancel</button></div></BottomSheet>}
  </main>
}
